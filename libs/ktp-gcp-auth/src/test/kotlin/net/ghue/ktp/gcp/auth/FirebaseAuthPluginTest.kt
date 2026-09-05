package net.ghue.ktp.gcp.auth

import com.google.api.services.identitytoolkit.v2.IdentityToolkit
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2ClientConfig
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Config
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Email
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2SignInConfig
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.Date
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.LOCAL_DEV_ENV_NAME
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated

class FirebaseAuthPluginTest :
    StringSpec({
        "firebaseAuthModule reuses an existing default Firebase app" {
            deleteDefaultFirebaseApp()

            val existingApp =
                FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                        .setCredentials(
                            GoogleCredentials.create(
                                AccessToken("test-token", Date(System.currentTimeMillis() + 60_000))
                            )
                        )
                        .setProjectId("test-project")
                        .build()
                )

            try {
                val koinApp = koinApplication { modules(firebaseAuthModule()) }
                try {
                    koinApp.koin.get<FirebaseApp>() shouldBeSameInstanceAs existingApp
                } finally {
                    koinApp.close()
                }
            } finally {
                deleteDefaultFirebaseApp()
            }
        }

        "installs plugin with default configuration" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)

                    pluginOrNull(Sessions) shouldNotBe null
                }
            }
        }

        "does not build the auth client config service at startup" {
            var initializationAttempts = 0

            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }
                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                                single<FirebaseAuthClientConfigService> {
                                    initializationAttempts++
                                    throw IOException("ADC unavailable")
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)
                }

                startApplication()

                initializationAttempts shouldBe 0
            }
        }

        "serves public auth client configuration" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)
                val projectConfig =
                    GoogleCloudIdentitytoolkitAdminV2Config()
                        .setClient(
                            GoogleCloudIdentitytoolkitAdminV2ClientConfig()
                                .setApiKey("firebase-key")
                                .setFirebaseSubdomain("auth-example")
                        )
                        .setSignIn(
                            GoogleCloudIdentitytoolkitAdminV2SignInConfig()
                                .setEmail(
                                    GoogleCloudIdentitytoolkitAdminV2Email()
                                        .setEnabled(true)
                                        .setPasswordRequired(true)
                                )
                        )
                val idpConfigResponse =
                    GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse()
                        .setDefaultSupportedIdpConfigs(
                            listOf(
                                GoogleCloudIdentitytoolkitAdminV2DefaultSupportedIdpConfig()
                                    .setName(
                                        "projects/test-project/defaultSupportedIdpConfigs/google.com"
                                    )
                                    .setEnabled(true)
                            )
                        )
                val authClientConfigService =
                    FirebaseAuthClientConfigService(
                        firebaseApp = mockFirebaseApp(),
                        identityToolkit =
                            mockIdentityToolkit(projectConfig, idpConfigResponse).identityToolkit,
                    )

                application {
                    install(KoinIsolated) {
                        modules(
                            authTestModule(
                                config,
                                mockFirebaseAuth,
                                mockLifecycle,
                                authClientConfigService,
                            )
                        )
                    }

                    install(ContentNegotiation) { json() }
                    install(FirebaseAuthPlugin)
                }

                client.get(AuthUrls.CLIENT_CONFIG).apply {
                    status shouldBe HttpStatusCode.OK
                    headers[HttpHeaders.CacheControl] shouldBe "public, max-age=600"
                    contentType() shouldBe ContentType.Application.Json
                    Json.parseToJsonElement(bodyAsText()) shouldBe
                        Json.parseToJsonElement(
                            """{
                              "firebase": {
                                "apiKey": "firebase-key",
                                "projectId": "test-project",
                                "authDomain": "auth-example.firebaseapp.com"
                              },
                              "enabledProviders": ["google.com", "password"],
                              "devLogin": false
                            }"""
                        )
                }
            }
        }

        "returns service unavailable when GCP auth configuration cannot be loaded" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)
                val failingToolkit = mockk<IdentityToolkit>()
                every { failingToolkit.projects() } throws IOException("GCP unavailable")
                val authClientConfigService =
                    FirebaseAuthClientConfigService(
                        firebaseApp = mockFirebaseApp(),
                        identityToolkit = failingToolkit,
                    )

                application {
                    install(KoinIsolated) {
                        modules(
                            authTestModule(
                                config,
                                mockFirebaseAuth,
                                mockLifecycle,
                                authClientConfigService,
                            )
                        )
                    }

                    install(FirebaseAuthPlugin)
                }

                client.get("/auth/config").apply {
                    status shouldBe HttpStatusCode.ServiceUnavailable
                    headers[HttpHeaders.CacheControl] shouldBe "no-store"
                }
            }
        }

        "uses non-secure cookies in local dev environment" {
            testApplication {
                val config = KtpConfig.create { env = Env(LOCAL_DEV_ENV_NAME) }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns
                    createMockFirebaseToken()
                coEvery { mockLifecycle.onLogin(any()) } returns
                    UserSession(
                        userId = UserId("test-user-id"),
                        tenantId = TenantId("test-tenant"),
                        email = "test@example.com",
                        name = "Test User",
                        roles = setOf("user"),
                    )

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)
                }

                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("valid-token")))
                    }
                response.status shouldBe HttpStatusCode.OK

                val cookies = response.headers.getAll(HttpHeaders.SetCookie)
                cookies shouldNotBe null
                cookies?.none { it.contains("Secure") } shouldBe true
            }
        }

        "creates session cookie with correct attributes" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns mockToken
                coEvery { mockLifecycle.onLogin(any()) } returns
                    UserSession(
                        userId = UserId("test-user-id"),
                        tenantId = TenantId("test-tenant"),
                        email = "test@example.com",
                        name = "Test User",
                        roles = setOf("user"),
                    )

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)
                }

                val loginResponse =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("valid-token")))
                    }

                loginResponse.status shouldBe HttpStatusCode.OK

                val cookies = loginResponse.headers.getAll(HttpHeaders.SetCookie)
                cookies shouldNotBe null
                cookies?.any { it.contains("HttpOnly") } shouldBe true
                cookies?.any { it.contains("SameSite=Lax") || it.contains("SameSite=lax") } shouldBe
                    true
                // Outside local dev the default is auth.secureCookies = true.
                cookies?.any { it.contains("Secure") } shouldBe true
            }
        }

        "unauthenticated user gets 401 on protected routes" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            get("/protected") { call.respondText("Protected content") }
                        }
                    }
                }

                val response = client.get("/protected")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "login endpoint sets session cookie" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns mockToken
                coEvery { mockLifecycle.onLogin(any()) } returns
                    UserSession(
                        userId = UserId("test-user-id"),
                        tenantId = TenantId("test-tenant"),
                        email = "test@example.com",
                        name = "Test User",
                        roles = setOf("user"),
                    )

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)
                }

                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("valid-firebase-token")))
                    }

                response.status shouldBe HttpStatusCode.OK
                val responseBody = response.bodyAsText()
                responseBody.contains("test-user-id") shouldBe true
                responseBody.contains("test@example.com") shouldBe true

                val cookies = response.headers.getAll(HttpHeaders.SetCookie)
                cookies shouldNotBe null
            }
        }

        "logout endpoint clears the session cookie" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    // The session cookie is named after the app, so a blank name breaks it.
                    overrideValue("app.name", "test.app")
                    // The test client talks plain HTTP and won't replay a Secure cookie.
                    overrideValue("auth.secureCookies", "false")
                }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns mockToken
                coEvery { mockLifecycle.onLogin(any()) } returns
                    UserSession(
                        userId = UserId("test-user-id"),
                        tenantId = TenantId("test-tenant"),
                        email = "test@example.com",
                        name = "Test User",
                        roles = setOf("user"),
                    )
                coEvery { mockLifecycle.onLogout(any()) } returns Unit

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)
                }

                // Login first so the logout request carries a real session cookie.
                val cookieClient = createClient { install(HttpCookies) }
                val loginResponse =
                    cookieClient.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("valid-token")))
                    }
                loginResponse.status shouldBe HttpStatusCode.OK

                val logoutResponse = cookieClient.post("/auth/logout")
                logoutResponse.status shouldBe HttpStatusCode.NoContent

                // Stateless sessions: this Set-Cookie deletion is the entire logout mechanism.
                val cookies = logoutResponse.headers.getAll(HttpHeaders.SetCookie)
                cookies shouldNotBe null
                cookies?.any { it.contains("01 Jan 1970") } shouldBe true

                coVerify(exactly = 1) { mockLifecycle.onLogout(any()) }
            }
        }

        "invalid Firebase token returns error" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                every { mockFirebaseAuth.verifyIdToken(any()) } throws
                    RuntimeException("Invalid token")

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)
                }

                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("invalid-token")))
                    }

                // Only FirebaseAuthException maps to a 4xx; any other failure is a 500.
                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }

        "malformed login payload returns bad request" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(authTestModule(config, mockFirebaseAuth, mockLifecycle))
                    }

                    install(FirebaseAuthPlugin)
                }

                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody("{bad json")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldBe "{}"
            }
        }
    })

@Serializable private data class LoginRequest(val idToken: String)

private fun createMockFirebaseToken(): FirebaseToken {
    val mockToken = mockk<FirebaseToken>()
    every { mockToken.uid } returns "test-user-id"
    every { mockToken.email } returns "test@example.com"
    every { mockToken.name } returns "Test User"
    every { mockToken.isEmailVerified } returns true
    every { mockToken.claims } returns emptyMap()
    return mockToken
}

private fun deleteDefaultFirebaseApp() {
    FirebaseApp.getApps()
        .filter { it.name == FirebaseApp.DEFAULT_APP_NAME }
        .forEach(FirebaseApp::delete)
}

private fun authTestModule(
    config: KtpConfig,
    firebaseAuth: FirebaseAuth,
    lifecycle: AuthLifecycleHandler,
    authClientConfigService: FirebaseAuthClientConfigService =
        FirebaseAuthClientConfigService(
            firebaseApp = mockFirebaseApp(),
            identityToolkit = mockk(),
        ),
) = module {
    single { config }
    single { FirebaseAuthService(firebaseAuth = firebaseAuth, lifecycle = lifecycle) }
    single { authClientConfigService }
}
