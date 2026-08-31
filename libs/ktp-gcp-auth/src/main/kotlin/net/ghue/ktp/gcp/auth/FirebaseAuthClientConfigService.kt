package net.ghue.ktp.gcp.auth

import com.google.api.services.identitytoolkit.v2.IdentityToolkit
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Config as IdentityProjectConfig
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig as DefaultSupportedIdpConfig
import com.google.firebase.FirebaseApp
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlin.time.Duration.Companion.minutes
import net.ghue.ktp.core.lazyWithRefresh
import net.ghue.ktp.log.log

// Paces console-toggle propagation to the UI; keep it short enough that flipping a provider in
// the console feels responsive.
private val AUTH_CLIENT_CONFIG_REFRESH_AFTER = 10.minutes

// Browsers may cache successful responses as long as the server does, so repeat page loads skip
// the network. Worst-case propagation of a console toggle is both windows back to back.
private val CLIENT_CONFIG_CACHE_CONTROL =
    "public, max-age=${AUTH_CLIENT_CONFIG_REFRESH_AFTER.inWholeSeconds}"
private const val IDP_CONFIG_PAGE_SIZE = 100

internal class FirebaseAuthClientConfigService(
    firebaseApp: FirebaseApp,
    private val identityToolkit: IdentityToolkit,
) {
    private val projectId =
        requireNotNull(firebaseApp.options.projectId) {
            "Firebase project ID is unavailable; set it explicitly or via GOOGLE_CLOUD_PROJECT"
        }
    private val projectName = "projects/$projectId"

    private val cachedConfig by lazyWithRefresh(AUTH_CLIENT_CONFIG_REFRESH_AFTER) { loadFromGcp() }

    suspend fun RoutingContext.handleClientConfig() {
        val authClientConfig =
            try {
                getClientConfig()
            } catch (ex: Exception) {
                log {}.error(ex) { "Unable to load Firebase client configuration" }
                // Never let the browser cache a failure, or recovery waits on its cache too.
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                return call.respond(HttpStatusCode.ServiceUnavailable)
            }
        call.response.headers.append(HttpHeaders.CacheControl, CLIENT_CONFIG_CACHE_CONTROL)
        call.respond(authClientConfig)
    }

    fun getClientConfig(): AuthClientConfig = cachedConfig

    private fun loadFromGcp(): AuthClientConfig {
        // The fetched Admin API objects also carry secrets (IdP client secrets, the password
        // hash signer key, SMTP credentials). Only the explicit projections below may ever
        // reach the public response.
        val projectConfig = identityToolkit.projects().getConfig("$projectName/config").execute()
        val idpConfigs = listDefaultSupportedIdpConfigs()

        return AuthClientConfig(
            firebase = projectConfig.toClientConfig(),
            enabledProviders = enabledProviderIds(projectConfig, idpConfigs),
        )
    }

    private fun listDefaultSupportedIdpConfigs(): List<DefaultSupportedIdpConfig> {
        val response =
            identityToolkit
                .projects()
                .defaultSupportedIdpConfigs()
                .list(projectName)
                .setPageSize(IDP_CONFIG_PAGE_SIZE)
                .execute()
        // Firebase's default IdP catalog is ~15 entries; a second page means the API changed.
        check(response.nextPageToken.isNullOrBlank()) {
            "Unexpected pagination while listing default supported IdP configs"
        }
        return response.defaultSupportedIdpConfigs.orEmpty()
    }

    private fun IdentityProjectConfig.toClientConfig(): FirebaseClientConfig {
        val identityClient =
            requireNotNull(client) {
                "Identity Platform project '$projectId' did not include client configuration"
            }
        val apiKey = identityClient.apiKey
        require(!apiKey.isNullOrBlank()) {
            "Identity Platform project config did not include client apiKey"
        }
        val firebaseSubdomain = identityClient.firebaseSubdomain
        require(!firebaseSubdomain.isNullOrBlank()) {
            "Identity Platform project config did not include client firebaseSubdomain"
        }

        return FirebaseClientConfig(
            apiKey = apiKey,
            projectId = projectId,
            // The Admin API doesn't expose the web app's authDomain, so the default domain is
            // derived from the Firebase subdomain. A custom auth domain would need the Firebase
            // Management API (webApps.getConfig) as the source instead.
            authDomain = "$firebaseSubdomain.firebaseapp.com",
        )
    }

    private fun enabledProviderIds(
        projectConfig: IdentityProjectConfig,
        idpConfigs: List<DefaultSupportedIdpConfig>,
    ): List<String> = buildSet {
        val signIn = projectConfig.signIn
        if (signIn?.email?.enabled == true) {
            add("password")
            // The console's "Allow passwordless login" clears passwordRequired, and proto3 JSON
            // omits false values — so the field is null (never false) when email link is enabled
            // and an explicit true when it is not.
            if (signIn.email?.passwordRequired != true) {
                add("emailLink")
            }
        }
        if (signIn?.anonymous?.enabled == true) {
            add("anonymous")
        }
        if (signIn?.phoneNumber?.enabled == true) {
            add("phone")
        }
        idpConfigs
            .filter { it.enabled == true }
            .mapNotNullTo(this) { it.name?.substringAfterLast('/')?.takeIf(String::isNotBlank) }
    }
        .sorted()
}
