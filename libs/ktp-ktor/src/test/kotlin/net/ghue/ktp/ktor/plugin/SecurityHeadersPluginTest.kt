package net.ghue.ktp.ktor.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.KtpConfigBuilder
import net.ghue.ktp.config.LOCAL_DEV_ENV_NAME

private const val CSP = SecurityHeaderNames.CONTENT_SECURITY_POLICY
private const val CSP_REPORT_ONLY = SecurityHeaderNames.CONTENT_SECURITY_POLICY_REPORT_ONLY

private fun Application.routes() = routing {
    get("/page") { call.respondText("<html></html>", ContentType.Text.Html) }
    get("/data") { call.respondText("{}", ContentType.Application.Json) }
    get("/custom") {
        call.response.headers.append(SecurityHeaderNames.REFERRER_POLICY, "no-referrer")
        call.respondText("<html></html>", ContentType.Text.Html)
    }
}

private fun testConfig(configure: KtpConfigBuilder.() -> Unit = {}) = KtpConfig.create {
    setUnitTestEnv()
    configure()
}

/** The one directive of a policy that starts with [name], e.g. `script-src 'self'`. */
private fun String.directive(name: String): String = split("; ").single { it.startsWith("$name ") }

class SecurityHeadersPluginTest :
    StringSpec({
        "html responses carry a report-only csp and the browser hardening headers" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) { ktpConfig = testConfig() }
                    routes()
                }

                val response = client.get("/page")
                val csp = response.headers[CSP_REPORT_ONLY].shouldNotBeNull()
                response.headers[CSP].shouldBeNull()
                csp.directive("default-src") shouldBe "default-src 'self'"
                csp.directive("script-src") shouldBe "script-src 'self'"
                csp.directive("style-src") shouldBe
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com"
                csp.directive("frame-ancestors") shouldBe "frame-ancestors 'none'"
                csp.directive("frame-src") shouldBe "frame-src 'none'"
                csp.directive("form-action") shouldBe "form-action 'self'"
                csp.directive("report-uri") shouldBe "report-uri $CSP_REPORT_PATH"
                csp shouldNotContain "unsafe-eval"
                response.headers[SecurityHeaderNames.CROSS_ORIGIN_OPENER_POLICY] shouldBe
                    "same-origin-allow-popups"
                response.headers[SecurityHeaderNames.X_FRAME_OPTIONS] shouldBe "DENY"
                response.headers[SecurityHeaderNames.PERMISSIONS_POLICY]
                    .shouldNotBeNull() shouldContain "camera=()"
                response.headers[SecurityHeaderNames.X_CONTENT_TYPE_OPTIONS] shouldBe "nosniff"
                response.headers[SecurityHeaderNames.REFERRER_POLICY] shouldBe
                    "strict-origin-when-cross-origin"

                // Static policy: the same header on every page, so HTML stays cacheable.
                client.get("/page").headers[CSP_REPORT_ONLY] shouldBe csp
            }
        }

        "non-html responses get only the always-on headers" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) { ktpConfig = testConfig() }
                    routes()
                }

                val response = client.get("/data")
                response.headers[SecurityHeaderNames.X_CONTENT_TYPE_OPTIONS] shouldBe "nosniff"
                response.headers[SecurityHeaderNames.REFERRER_POLICY] shouldBe
                    "strict-origin-when-cross-origin"
                response.headers[CSP_REPORT_ONLY].shouldBeNull()
                response.headers[CSP].shouldBeNull()
                response.headers[SecurityHeaderNames.CROSS_ORIGIN_OPENER_POLICY].shouldBeNull()
                response.headers[SecurityHeaderNames.X_FRAME_OPTIONS].shouldBeNull()
            }
        }

        "reportOnly false enforces the policy" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) {
                        ktpConfig = testConfig {
                            overrideValue("securityHeaders.csp.reportOnly", false)
                        }
                    }
                    routes()
                }

                val response = client.get("/page")
                response.headers[CSP].shouldNotBeNull()
                response.headers[CSP_REPORT_ONLY].shouldBeNull()
            }
        }

        "a header the app already set is left alone" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) { ktpConfig = testConfig() }
                    routes()
                }

                client.get("/custom").headers[SecurityHeaderNames.REFERRER_POLICY] shouldBe
                    "no-referrer"
            }
        }

        "configured and registered sources land in their directives" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) {
                        ktpConfig = testConfig {
                            overrideValue(
                                "securityHeaders.csp.imgSrc",
                                listOf("data:", "https://storage.googleapis.com"),
                            )
                        }
                    }
                    // Registered after the plugin, like a lib plugin installed later would.
                    cspSources.add(CspDirective.SCRIPT_SRC, "https://apis.google.com")
                    cspSources.add(CspDirective.FRAME_SRC, "https://*.firebaseapp.com")
                    routes()
                }

                val csp = client.get("/page").headers[CSP_REPORT_ONLY].shouldNotBeNull()
                csp.directive("img-src") shouldBe
                    "img-src 'self' data: https://storage.googleapis.com"
                csp.directive("script-src") shouldBe "script-src 'self' https://apis.google.com"
                csp.directive("frame-src") shouldBe "frame-src https://*.firebaseapp.com"
            }
        }

        "local dev allows vite's inline preamble, hmr socket and ping" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) {
                        ktpConfig = KtpConfig.create { env = Env(LOCAL_DEV_ENV_NAME) }
                    }
                    routes()
                }

                val csp = client.get("/page").headers[CSP_REPORT_ONLY].shouldNotBeNull()
                csp.directive("script-src") shouldBe "script-src 'self' 'unsafe-inline'"
                csp.directive("connect-src") shouldBe
                    "connect-src 'self' ws://localhost:* http://localhost:*"
            }
        }

        "unit test env does not allow local dev sources" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) { ktpConfig = testConfig() }
                    routes()
                }

                val csp = client.get("/page").headers[CSP_REPORT_ONLY].shouldNotBeNull()
                csp shouldNotContain "localhost"
                csp.directive("script-src") shouldNotContain "unsafe-inline"
            }
        }

        "csp reports are accepted" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) { ktpConfig = testConfig() }
                    routes()
                }

                val response =
                    client.post(CSP_REPORT_PATH) {
                        header(HttpHeaders.ContentType, "application/csp-report")
                        setBody("""{"csp-report":{"blocked-uri":"https://evil.example"}}""")
                    }
                response.status shouldBe HttpStatusCode.NoContent
            }
        }

        "disabled sends nothing" {
            testApplication {
                application {
                    install(SecurityHeadersPlugin) {
                        ktpConfig = testConfig { overrideValue("securityHeaders.enabled", false) }
                    }
                    routes()
                }

                val response = client.get("/page")
                response.headers[CSP_REPORT_ONLY].shouldBeNull()
                response.headers[SecurityHeaderNames.X_CONTENT_TYPE_OPTIONS].shouldBeNull()
            }
        }

        "installDefaultPlugins wires the headers in" {
            testApplication {
                application {
                    installDefaultPlugins(testConfig())
                    routes()
                }

                client.get("/data").headers[SecurityHeaderNames.X_CONTENT_TYPE_OPTIONS] shouldBe
                    "nosniff"
                client.get("/page").headers[CSP_REPORT_ONLY].shouldNotBeNull()
            }
        }
    })
