package net.ghue.ktp.ktor.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import java.net.ServerSocket
import kotlin.io.path.Path
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

class ViteFrontendTest :
    StringSpec({
        "production mode serves index for missing specific resource" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "false")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin) { indexFile = Path("nonexistent.html") }
                }

                client.get("/").status shouldBe HttpStatusCode.NotFound
                client.get("/p/some/page").status shouldBe HttpStatusCode.NotFound
            }
        }

        "production mode serves non-existent static resources as 404" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "false")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin)
                }

                client.get("/static/nonexistent.css").status shouldBe HttpStatusCode.NotFound
            }
        }

        "ViteFrontendConfig has correct default values" {
            val config = ViteFrontendConfig()

            config.vitePort shouldBe 5173
            config.indexFile shouldBe Path("src", "index.html")
            config.staticPathSegment shouldBe "static"
            config.staticDir shouldBe Path("static")
            config.frontendDist shouldBe Path("frontend", "dist")
            config.browserUriPathPrefix shouldBe "p"
            config.frontendRoute shouldBe "/p/{...}"
            config.indexFilePath shouldBe Path("static").resolve(Path("src", "index.html"))
        }

        "ViteFrontendConfig custom values work correctly" {
            val config = ViteFrontendConfig()
            config.vitePort = 3000
            config.indexFile = Path("custom.html")
            config.staticPathSegment = "assets"
            config.staticDir = Path("public")
            config.frontendDist = Path("build")
            config.browserUriPathPrefix = "app"

            config.vitePort shouldBe 3000
            config.indexFile shouldBe Path("custom.html")
            config.staticPathSegment shouldBe "assets"
            config.staticDir shouldBe Path("public")
            config.frontendDist shouldBe Path("build")
            config.browserUriPathPrefix shouldBe "app"
            config.frontendRoute shouldBe "/app/{...}"
            config.indexFilePath shouldBe Path("public").resolve(Path("custom.html"))
        }

        "dev mode proxies to Vite when localDev is true" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "true")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin)
                }

                client.get("/").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("Test Index Page")
                }

                client.get("/p/some/page").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("Test Index Page")
                }
            }
        }

        "dev mode starts even when frontend dist fallback is missing" {
            testApplication {
                val unavailablePort = ServerSocket(0).use { it.localPort }
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "true")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin) {
                        vitePort = unavailablePort
                        frontendDist = Path("missing-frontend", "dist")
                    }
                }

                client.get("/static/missing.css").status shouldBe HttpStatusCode.NotFound
            }
        }

        "production mode serves static resources correctly" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "false")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin)
                }

                client.get("/static/app.css").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("color: blue")
                }

                client.get("/static/nonexistent.css").status shouldBe HttpStatusCode.NotFound
            }
        }

        "production mode serves index HTML correctly" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "false")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin)
                }

                client.get("/").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("Test Index Page")
                }

                client.get("/p/some/page").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("Test Index Page")
                }
            }
        }

        "custom static URI routes are configured correctly" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "false")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin) { staticPathSegment = "assets" }
                }

                client.get("/assets/nonexistent.js").status shouldBe HttpStatusCode.NotFound
            }
        }

        "custom browser URI path prefix routes are configured correctly" {
            testApplication {
                val config = KtpConfig.create {
                    setUnitTestEnv()
                    overrideValue("env.localDev", "false")
                }

                application {
                    install(Koin) { modules(module { single { config } }) }
                    install(ViteFrontendPlugin) { browserUriPathPrefix = "app" }
                }

                client.get("/app/dashboard").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("Test Index Page")
                }

                client.get("/app/settings/profile").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("Test Index Page")
                }
            }
        }
    })
