package net.ghue.ktp.ktor.app

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.app.debug.ConfigDebugInfoPlugin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

class DebugEndpointsTest :
    StringSpec({
        "/debug/version returns the configured version" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        configValue("app.version", "69")
                                    }
                                }
                            }
                        )
                    }
                    install(ConfigDebugInfoPlugin)
                }
                with(client.get("/debug/version")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "69"
                }
            }
        }

        "/debug/config returns HTML with configuration" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        configValue("app.version", "1.0.0")
                                        configValue("app.name", "test-app")
                                    }
                                }
                            }
                        )
                    }
                    install(ConfigDebugInfoPlugin)
                }
                with(client.get("/debug/config")) {
                    status shouldBe HttpStatusCode.OK
                    contentType() shouldBe ContentType.Text.Html.withCharset(Charsets.UTF_8)
                    val body = bodyAsText()
                    body shouldContain "app.version"
                    body shouldContain "1.0.0"
                }
            }
        }

        "access control blocks unauthorized requests" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        configValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(ConfigDebugInfoPlugin) {
                        // Block all access
                        accessControl = { false }
                    }
                }
                with(client.get("/debug/version")) { status shouldBe HttpStatusCode.Forbidden }
                with(client.get("/debug/config")) { status shouldBe HttpStatusCode.Forbidden }
            }
        }

        "access control allows authorized requests" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        configValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(ConfigDebugInfoPlugin) {
                        // Allow only requests with specific header
                        accessControl = { request.headers["X-Debug-Token"] == "secret" }
                    }
                }
                // Without token - forbidden
                with(client.get("/debug/version")) { status shouldBe HttpStatusCode.Forbidden }
                // With token - allowed
                with(client.get("/debug/version") { header("X-Debug-Token", "secret") }) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "1.0.0"
                }
            }
        }

        "individual endpoints can be disabled" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        configValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(ConfigDebugInfoPlugin) {
                        enableVersionEndpoint = true
                        enableConfigEndpoint = false
                        enableGcLogEndpoint = false
                    }
                }
                with(client.get("/debug/version")) { status shouldBe HttpStatusCode.OK }
                with(client.get("/debug/config")) { status shouldBe HttpStatusCode.NotFound }
                with(client.get("/debug/gclog")) { status shouldBe HttpStatusCode.NotFound }
            }
        }

        "custom route prefix works" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        configValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(ConfigDebugInfoPlugin) { routePrefix = "/admin/debug" }
                }
                // Old route doesn't work
                with(client.get("/debug/version")) { status shouldBe HttpStatusCode.NotFound }
                // New route works
                with(client.get("/admin/debug/version")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "1.0.0"
                }
            }
        }
    })
