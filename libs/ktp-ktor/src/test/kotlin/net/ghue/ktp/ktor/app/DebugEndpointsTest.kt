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
import net.ghue.ktp.ktor.app.debug.DebugEndpoints
import net.ghue.ktp.ktor.app.debug.DebugEndpointsPlugin
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
                                        overrideValue("app.version", "69")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin)
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
                                        overrideValue("app.version", "1.0.0")
                                        overrideValue("app.name", "test-app")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin)
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

        "/debug/config escapes HTML values and masks sensitive system properties" {
            val htmlProperty = "ktp.debug.html"
            val secretProperty = "ktp.debug.password"
            val htmlValue = "<b>danger</b>"
            val secretValue = "supersecret!"

            System.setProperty(htmlProperty, htmlValue)
            System.setProperty(secretProperty, secretValue)
            try {
                testApplication {
                    application {
                        install(Koin) {
                            modules(
                                module {
                                    single {
                                        KtpConfig.create {
                                            setUnitTestEnv()
                                            overrideValue("app.name", "<script>alert(1)</script>")
                                        }
                                    }
                                }
                            )
                        }
                        install(DebugEndpointsPlugin)
                    }
                    with(client.get("/debug/config")) {
                        status shouldBe HttpStatusCode.OK
                        val body = bodyAsText()
                        body shouldContain "&lt;script&gt;alert(1)&lt;/script&gt;"
                        body.contains("<script>alert(1)</script>") shouldBe false
                        body shouldContain "&lt;b&gt;danger&lt;/b&gt;"
                        body.contains("<b>danger</b>") shouldBe false
                        body shouldContain "${secretValue.length} chars"
                    }
                }
            } finally {
                System.clearProperty(htmlProperty)
                System.clearProperty(secretProperty)
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
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) {
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
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) {
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
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) {
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
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) { routePrefix = "/admin/debug" }
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

        "/debug/threads returns thread dump" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin)
                }
                with(client.get("/debug${DebugEndpoints.THREADS}")) {
                    status shouldBe HttpStatusCode.OK
                    val body = bodyAsText()
                    // Verify basic thread dump structure
                    body shouldContain "Full thread dump"
                    body shouldContain "Thread Summary:"
                    body shouldContain "java.lang.Thread.State:"
                }
            }
        }

        "thread dump endpoint respects access control" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) {
                        accessControl = { request.headers["X-Debug-Token"] == "secret" }
                    }
                }
                // Without token - forbidden
                with(client.get("/debug${DebugEndpoints.THREADS}")) {
                    status shouldBe HttpStatusCode.Forbidden
                }
                // With token - allowed
                with(
                    client.get("/debug${DebugEndpoints.THREADS}") {
                        header("X-Debug-Token", "secret")
                    }
                ) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldContain "Full thread dump"
                }
            }
        }

        "thread dump endpoint can be disabled" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) { enableThreadDumpEndpoint = false }
                }
                with(client.get("/debug${DebugEndpoints.THREADS}")) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        "/debug index page returns HTML with endpoint list" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin)
                }
                with(client.get("/debug")) {
                    status shouldBe HttpStatusCode.OK
                    contentType() shouldBe ContentType.Text.Html.withCharset(Charsets.UTF_8)
                    val body = bodyAsText()
                    body shouldContain "Debug Endpoints"
                    body shouldContain "/debug${DebugEndpoints.CONFIG}"
                    body shouldContain "/debug${DebugEndpoints.VERSION}"
                    body shouldContain "/debug${DebugEndpoints.THREADS}"
                    body shouldContain "/debug${DebugEndpoints.GC_LOG}"
                    body shouldContain "Enabled"
                }
            }
        }

        "index page shows disabled endpoints with correct status" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) {
                        enableConfigEndpoint = true
                        enableVersionEndpoint = false
                        enableGcLogEndpoint = false
                        enableThreadDumpEndpoint = true
                    }
                }
                with(client.get("/debug")) {
                    status shouldBe HttpStatusCode.OK
                    val body = bodyAsText()
                    // Should show all endpoints
                    body shouldContain "/debug${DebugEndpoints.CONFIG}"
                    body shouldContain "/debug${DebugEndpoints.VERSION}"
                    body shouldContain "/debug${DebugEndpoints.GC_LOG}"
                    body shouldContain "/debug${DebugEndpoints.THREADS}"
                    // Check for status indicators
                    body shouldContain "Enabled"
                    body shouldContain "Disabled"
                }
            }
        }

        "index page respects access control" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) {
                        accessControl = { request.headers["X-Debug-Token"] == "secret" }
                    }
                }
                // Without token - forbidden
                with(client.get("/debug")) { status shouldBe HttpStatusCode.Forbidden }
                // With token - allowed
                with(client.get("/debug") { header("X-Debug-Token", "secret") }) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldContain "Debug Endpoints"
                }
            }
        }

        "index page works with custom route prefix" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "1.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin) { routePrefix = "/admin/debug" }
                }
                // Old route doesn't work
                with(client.get("/debug")) { status shouldBe HttpStatusCode.NotFound }
                // New route works and shows correct paths
                with(client.get("/admin/debug")) {
                    status shouldBe HttpStatusCode.OK
                    val body = bodyAsText()
                    body shouldContain "/admin/debug${DebugEndpoints.CONFIG}"
                    body shouldContain "/admin/debug${DebugEndpoints.VERSION}"
                }
            }
        }

        "index page doesn't interfere with child routes" {
            testApplication {
                application {
                    install(Koin) {
                        modules(
                            module {
                                single {
                                    KtpConfig.create {
                                        setUnitTestEnv()
                                        overrideValue("app.version", "2.0.0")
                                    }
                                }
                            }
                        )
                    }
                    install(DebugEndpointsPlugin)
                }
                // Index works
                with(client.get("/debug")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldContain "Debug Endpoints"
                }
                // Child routes still work
                with(client.get("/debug${DebugEndpoints.VERSION}")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "2.0.0"
                }
                with(client.get("/debug${DebugEndpoints.CONFIG}")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldContain "App Configuration"
                }
            }
        }
    })
