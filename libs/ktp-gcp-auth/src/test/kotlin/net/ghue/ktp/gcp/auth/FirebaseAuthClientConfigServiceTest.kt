package net.ghue.ktp.gcp.auth

import com.google.api.services.identitytoolkit.v2.IdentityToolkit
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Anonymous
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2ClientConfig
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Config
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Email
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2HashConfig
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2PhoneNumber
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2SignInConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.verify
import kotlinx.serialization.json.Json

class FirebaseAuthClientConfigServiceTest :
    StringSpec({
        "loads Firebase client config and enabled auth providers from Identity Platform" {
            val projectConfig =
                identityProjectConfig()
                    .setSignIn(
                        GoogleCloudIdentitytoolkitAdminV2SignInConfig()
                            .setEmail(
                                GoogleCloudIdentitytoolkitAdminV2Email()
                                    .setEnabled(true)
                                    .setPasswordRequired(true)
                            )
                            .setAnonymous(
                                GoogleCloudIdentitytoolkitAdminV2Anonymous().setEnabled(true)
                            )
                            .setPhoneNumber(
                                GoogleCloudIdentitytoolkitAdminV2PhoneNumber().setEnabled(true)
                            )
                    )
            val idpConfigResponse =
                idpConfigs(
                    idpConfig("google.com", enabled = true),
                    idpConfig("github.com", enabled = false),
                )
            val sdk = mockIdentityToolkit(projectConfig, idpConfigResponse)

            withService(sdk.identityToolkit) { service ->
                service.getClientConfig() shouldBe
                    AuthClientConfig(
                        firebase =
                            FirebaseClientConfig(
                                apiKey = "firebase-api-key",
                                projectId = "test-project",
                                authDomain = "auth-example.firebaseapp.com",
                            ),
                        enabledProviders = listOf("anonymous", "google.com", "password", "phone"),
                    )
            }
        }

        "caches the loaded client config across calls" {
            val sdk = mockIdentityToolkit(identityProjectConfig(), idpConfigs())

            withService(sdk.identityToolkit) { service ->
                service.getClientConfig() shouldBe service.getClientConfig()
            }

            verify(exactly = 1) { sdk.getConfigRequest.execute() }
            verify(exactly = 1) { sdk.listIdpConfigsRequest.execute() }
        }

        "includes emailLink when passwordless email sign-in is enabled" {
            // With "Allow passwordless login" on, the API omits passwordRequired entirely
            // (proto3 JSON drops false values) — it does not send an explicit false.
            val projectConfig =
                identityProjectConfig()
                    .setSignIn(
                        GoogleCloudIdentitytoolkitAdminV2SignInConfig()
                            .setEmail(GoogleCloudIdentitytoolkitAdminV2Email().setEnabled(true))
                    )
            val sdk = mockIdentityToolkit(projectConfig, idpConfigs())

            withService(sdk.identityToolkit) { service ->
                service.getClientConfig().enabledProviders shouldBe listOf("emailLink", "password")
            }
        }

        "excludes emailLink when a password is required" {
            val projectConfig =
                identityProjectConfig()
                    .setSignIn(
                        GoogleCloudIdentitytoolkitAdminV2SignInConfig()
                            .setEmail(
                                GoogleCloudIdentitytoolkitAdminV2Email()
                                    .setEnabled(true)
                                    .setPasswordRequired(true)
                            )
                    )
            val sdk = mockIdentityToolkit(projectConfig, idpConfigs())

            withService(sdk.identityToolkit) { service ->
                service.getClientConfig().enabledProviders shouldBe listOf("password")
            }
        }

        "returns no providers when nothing is enabled" {
            val sdk = mockIdentityToolkit(identityProjectConfig(), idpConfigs())

            withService(sdk.identityToolkit) { service ->
                service.getClientConfig().enabledProviders shouldBe emptyList()
            }
        }

        "requires Identity Platform client configuration" {
            val sdk = mockIdentityToolkit(GoogleCloudIdentitytoolkitAdminV2Config(), idpConfigs())

            withService(sdk.identityToolkit) { service ->
                shouldThrow<IllegalArgumentException> { service.getClientConfig() }.message shouldBe
                    "Identity Platform project 'test-project' did not include client configuration"
            }
        }

        "requires the client apiKey" {
            val projectConfig =
                GoogleCloudIdentitytoolkitAdminV2Config()
                    .setClient(
                        GoogleCloudIdentitytoolkitAdminV2ClientConfig()
                            .setFirebaseSubdomain("auth-example")
                    )
            val sdk = mockIdentityToolkit(projectConfig, idpConfigs())

            withService(sdk.identityToolkit) { service ->
                shouldThrow<IllegalArgumentException> { service.getClientConfig() }.message shouldBe
                    "Identity Platform project config did not include client apiKey"
            }
        }

        "requires the client firebaseSubdomain" {
            val projectConfig =
                GoogleCloudIdentitytoolkitAdminV2Config()
                    .setClient(
                        GoogleCloudIdentitytoolkitAdminV2ClientConfig()
                            .setApiKey("firebase-api-key")
                    )
            val sdk = mockIdentityToolkit(projectConfig, idpConfigs())

            withService(sdk.identityToolkit) { service ->
                shouldThrow<IllegalArgumentException> { service.getClientConfig() }.message shouldBe
                    "Identity Platform project config did not include client firebaseSubdomain"
            }
        }

        "fails when the IdP config listing is unexpectedly paginated" {
            val idpConfigResponse =
                idpConfigs(idpConfig("google.com", enabled = true)).setNextPageToken("more")
            val sdk = mockIdentityToolkit(identityProjectConfig(), idpConfigResponse)

            withService(sdk.identityToolkit) { service ->
                shouldThrow<IllegalStateException> { service.getClientConfig() }
            }
        }

        "never serializes Identity Platform secrets" {
            val projectConfig =
                identityProjectConfig()
                    .setSignIn(
                        GoogleCloudIdentitytoolkitAdminV2SignInConfig()
                            .setEmail(GoogleCloudIdentitytoolkitAdminV2Email().setEnabled(true))
                            .setHashConfig(
                                GoogleCloudIdentitytoolkitAdminV2HashConfig()
                                    .setSignerKey("hash-signer-key")
                            )
                    )
            val idpConfigResponse =
                idpConfigs(
                    idpConfig("google.com", enabled = true)
                        .setClientId("oauth-client-id")
                        .setClientSecret("oauth-client-secret")
                )
            val sdk = mockIdentityToolkit(projectConfig, idpConfigResponse)

            withService(sdk.identityToolkit) { service ->
                val json =
                    Json.encodeToString(AuthClientConfig.serializer(), service.getClientConfig())
                json shouldNotContain "oauth-client-secret"
                json shouldNotContain "oauth-client-id"
                json shouldNotContain "hash-signer-key"
            }
        }
    })

private fun identityProjectConfig(): GoogleCloudIdentitytoolkitAdminV2Config =
    GoogleCloudIdentitytoolkitAdminV2Config()
        .setClient(
            GoogleCloudIdentitytoolkitAdminV2ClientConfig()
                .setApiKey("firebase-api-key")
                .setFirebaseSubdomain("auth-example")
        )

private fun idpConfig(
    providerId: String,
    enabled: Boolean,
): GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig =
    GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig()
        .setName("projects/test-project/defaultSupportedIdpConfigs/$providerId")
        .setEnabled(enabled)

private fun idpConfigs(
    vararg configs: GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig
): GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse =
    GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse()
        .setDefaultSupportedIdpConfigs(configs.toList())

private fun withService(
    identityToolkit: IdentityToolkit,
    block: (FirebaseAuthClientConfigService) -> Unit,
) {
    block(
        FirebaseAuthClientConfigService(
            firebaseApp = mockFirebaseApp(),
            identityToolkit = identityToolkit,
        )
    )
}
