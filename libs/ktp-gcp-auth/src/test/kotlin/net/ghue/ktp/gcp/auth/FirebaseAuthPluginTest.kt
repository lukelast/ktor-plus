package net.ghue.ktp.gcp.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated

class FirebaseAuthPluginTest :
    StringSpec({
        "installs plugin with default configuration" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                        configValue("env.name", "test")
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)

                    // Verify Sessions plugin was installed
                    pluginOrNull(Sessions) shouldNotBe null
                }
            }
        }

        "uses non-secure cookies in local dev environment" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "true")
                        configValue("env.name", "test")
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            get("/test") { call.respondText("OK") }
                        }
                    }
                }
            }
        }

        "creates session cookie with correct attributes" {
            testApplication {
                val config = KtpConfig.create { setUnitTestEnv() }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns mockToken
                coEvery { mockLifecycle.fetchRoles(any()) } returns setOf("user")
                coEvery { mockLifecycle.onLogin(any(), any()) } returns Unit

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)
                }

                // Login to get a session cookie
                val loginResponse =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("valid-token")))
                    }

                loginResponse.status shouldBe HttpStatusCode.OK

                // Check that cookie was set
                val cookies = loginResponse.headers.getAll(HttpHeaders.SetCookie)
                cookies shouldNotBe null
                cookies?.any { it.contains("HttpOnly") } shouldBe true
                cookies?.any { it.contains("SameSite=Lax") || it.contains("SameSite=lax") } shouldBe
                    true
            }
        }

        "unauthenticated user gets 401 on protected routes" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            get("/protected") { call.respondText("Protected content") }
                        }
                    }
                }

                // Try to access protected route without login
                val response = client.get("/protected")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "login endpoint sets session cookie" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns mockToken
                coEvery { mockLifecycle.fetchRoles(any()) } returns setOf("user")
                coEvery { mockLifecycle.onLogin(any(), any()) } returns Unit

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
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

                // Verify cookie was set
                val cookies = response.headers.getAll(HttpHeaders.SetCookie)
                cookies shouldNotBe null
            }
        }

        "logout endpoint clears session and redirects" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                        configValue("env.name", "test")
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                        configValue("auth.redirectAfterLogout", "/goodbye")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any()) } returns mockToken
                coEvery { mockLifecycle.fetchRoles(any()) } returns setOf("user")
                coEvery { mockLifecycle.onLogin(any(), any()) } returns Unit
                coEvery { mockLifecycle.onLogout(any()) } returns Unit

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)
                }

                // Login first
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(LoginRequest("valid-token")))
                }

                // Then logout
                val logoutResponse = client.post("/auth/logout")
                logoutResponse.status shouldBe HttpStatusCode.Found
                logoutResponse.headers[HttpHeaders.Location] shouldBe "/goodbye"
            }
        }

        "invalid Firebase token returns error" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                        configValue("env.name", "test")
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>(relaxed = true)

                every { mockFirebaseAuth.verifyIdToken(any()) } throws
                    RuntimeException("Invalid token")

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)
                }

                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("invalid-token")))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }

        "custom login and logout URLs are used" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("data.app.secret", "test-secret-key-for-encryption-purposes")
                        configValue("auth.loginUrl", "/custom/signin")
                        configValue("auth.logoutUrl", "/custom/signout")
                    }

                val mockFirebaseAuth = mockk<FirebaseAuth>()
                val mockLifecycle = mockk<AuthLifecycleHandler>()
                val mockToken = createMockFirebaseToken()

                every { mockFirebaseAuth.verifyIdToken(any(), any()) } returns mockToken
                coEvery { mockLifecycle.fetchRoles(any()) } returns setOf("user")
                coEvery { mockLifecycle.onLogin(any(), any()) } returns Unit
                coEvery { mockLifecycle.onLogout(any()) } returns Unit

                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        ktpConfig = config,
                                        firebaseAuth = mockFirebaseAuth,
                                        lifecycle = mockLifecycle,
                                    )
                                }
                            }
                        )
                    }

                    install(FirebaseAuthPlugin)
                }

                // Test custom login URL
                val loginResponse =
                    client.post("/custom/signin") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(LoginRequest("valid-token")))
                    }
                loginResponse.status shouldBe HttpStatusCode.OK

                // Test custom logout URL
                val logoutResponse = client.post("/custom/signout")
                logoutResponse.status shouldBe HttpStatusCode.Found
            }
        }
    })

@Serializable private data class LoginRequest(val idToken: String)

private fun createMockFirebaseToken(): FirebaseToken {
    val mockToken = mockk<FirebaseToken>()
    every { mockToken.uid } returns "test-user-id"
    every { mockToken.email } returns "test@example.com"
    every { mockToken.name } returns "Test User"
    return mockToken
}
