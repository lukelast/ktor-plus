package net.ghue.ktp.gcp.auth

import com.google.api.services.identitytoolkit.v2.IdentityToolkit
import com.google.api.services.identitytoolkit.v2.IdentityToolkitScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.ServiceOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import net.ghue.ktp.config.KtpConfig
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

// google-http-client defaults to 20s each; the config endpoint holds its cache lock while
// loading, so keep the worst case bounded.
private const val GCP_CONNECT_TIMEOUT_MS = 5_000
private const val GCP_READ_TIMEOUT_MS = 10_000

/**
 * Firebase Admin, Identity Toolkit, and the auth services. Session timing uses the
 * `java.time.Clock` the app builder binds (system UTC unless the app defines its own).
 */
fun firebaseAuthModule() = module {
    single {
        // initializeApp throws if the JVM-global default app exists (Koin rebuilt between tests).
        synchronized(FirebaseApp::class.java) {
            FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(ServiceOptions.getDefaultProjectId())
                        .build()
                )
        }
    }

    single { FirebaseAuth.getInstance(get()) }

    single {
        val options = get<FirebaseApp>().options
        // CLOUD_PLATFORM rather than the narrower FIREBASE scope: GCE metadata tokens are
        // limited to the VM's granted scopes, which include cloud-platform but not firebase.
        val credentials =
            GoogleCredentials.getApplicationDefault()
                .createScoped(IdentityToolkitScopes.CLOUD_PLATFORM)
        val credentialsAdapter = HttpCredentialsAdapter(credentials)
        IdentityToolkit.Builder(options.httpTransport, options.jsonFactory) { request ->
                credentialsAdapter.initialize(request)
                request.connectTimeout = GCP_CONNECT_TIMEOUT_MS
                request.readTimeout = GCP_READ_TIMEOUT_MS
            }
            .setApplicationName(get<KtpConfig>().data.app.name)
            .build()
    }

    single {
        FirebaseAuthClientConfigService(
            firebaseApp = get(),
            identityToolkit = get(),
            devLogin = get<KtpConfig>().env.isLocalDev,
        )
    }
    factoryOf(::FirebaseAuthService)
    factoryOf(::DevLoginService)
}
