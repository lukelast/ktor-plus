package net.ghue.ktp.gcp.auth

import com.google.firebase.auth.FirebaseAuth
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.plugin.RequestVirtualThreadPlugin
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated

/**
 * `/debug/version` is the one public debug page, so a deploy check can read the running version
 * without a session; every other page stays behind an admin session.
 */
class InstallDebugRoutesTest :
    StringSpec({
        "anonymous visitors read the version and nothing else" {
            testApplication {
                val config = testConfig()
                application {
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { config }
                                single {
                                    FirebaseAuthService(
                                        firebaseAuth = mockk<FirebaseAuth>(),
                                        lifecycle = mockk<AuthLifecycleHandler>(),
                                        ktpConfig = config,
                                    )
                                }
                            }
                        )
                    }
                    install(RequestVirtualThreadPlugin)
                    install(FirebaseAuthPlugin)
                    installDebugRoutes()
                }

                with(client.get("/debug/version")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "69"
                }
                client.get("/debug").status shouldBe HttpStatusCode.Unauthorized
                client.get("/debug/config").status shouldBe HttpStatusCode.Unauthorized
                client.get("/debug/threads").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "a signed-in user without the role reads the version and nothing else" {
            signedInDebugApp(roles = setOf("user")) {
                client.get("/debug/version").status shouldBe HttpStatusCode.OK
                client.get("/debug").status shouldBe HttpStatusCode.Forbidden
                client.get("/debug/config").status shouldBe HttpStatusCode.Forbidden
            }
        }

        "an admin reads every page" {
            signedInDebugApp(roles = setOf(Role.ADMIN.name)) {
                client.get("/debug/version").status shouldBe HttpStatusCode.OK
                client.get("/debug").status shouldBe HttpStatusCode.OK
                client.get("/debug/config").status shouldBe HttpStatusCode.OK
            }
        }
    })

private fun testConfig() = KtpConfig.create {
    setUnitTestEnv()
    overrideValue("app.version", "69")
}

/** The debug routes behind a stand-in session provider whose principal holds [roles]. */
private fun signedInDebugApp(roles: Set<String>, test: suspend ApplicationTestBuilder.() -> Unit) {
    testApplication {
        val config = testConfig()
        application {
            install(KoinIsolated) { modules(module { single { config } }) }
            install(Authentication) {
                dummy(
                    principal =
                        UserSession(
                            userId = UserId("user-1"),
                            tenantId = TenantId("tenant-1"),
                            email = "someone@example.test",
                            name = "Someone",
                            roles = roles,
                        )
                )
            }
            installDebugRoutes()
        }
        test()
    }
}
