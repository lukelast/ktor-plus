package net.ghue.ktp.ktor.vite

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.path.Path
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.plugin.ViteConfig
import net.ghue.ktp.ktor.plugin.installViteFrontend

class ViteFrontendTest :
    StringSpec({
        "production mode serves index for missing specific resource" {
            testApplication {
                val viteConfig = ViteConfig(indexFile = Path("nonexistent.html"))
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                    }

                application { installViteFrontend(config, viteConfig) }

                client.get("/").status shouldBe HttpStatusCode.NotFound
                client.get("/p/some/page").status shouldBe HttpStatusCode.NotFound
            }
        }

        "production mode serves non-existent static resources as 404" {
            testApplication {
                val viteConfig = ViteConfig()
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                    }

                application { installViteFrontend(config, viteConfig) }

                client.get("/static/nonexistent.css").status shouldBe HttpStatusCode.NotFound
            }
        }

        "ViteConfig has correct default values" {
            val config = ViteConfig()

            config.vitePort shouldBe 5173
            config.indexFile shouldBe Path("src", "index.html")
            config.staticUri shouldBe "static"
            config.staticDir shouldBe Path("static")
            config.frontEndDist shouldBe Path("frontend", "dist")
            config.browserUriPathPrefix shouldBe "p"
            config.frontendRoute shouldBe "/p/{...}"
            config.indexFilePath shouldBe Path("static").resolve(Path("src", "index.html"))
        }

        "ViteConfig custom values work correctly" {
            val config =
                ViteConfig(
                    vitePort = 3000,
                    indexFile = Path("custom.html"),
                    staticUri = "assets",
                    staticDir = Path("public"),
                    frontEndDist = Path("build"),
                    browserUriPathPrefix = "app",
                )

            config.vitePort shouldBe 3000
            config.indexFile shouldBe Path("custom.html")
            config.staticUri shouldBe "assets"
            config.staticDir shouldBe Path("public")
            config.frontEndDist shouldBe Path("build")
            config.browserUriPathPrefix shouldBe "app"
            config.frontendRoute shouldBe "/app/{...}"
            config.indexFilePath shouldBe Path("public").resolve(Path("custom.html"))
        }

        "dev mode proxies to Vite when localDev is true" {
            testApplication {
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "true")
                    }
                val viteConfig = ViteConfig()

                application { installViteFrontend(config, viteConfig) }

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

        "production mode serves static resources correctly" {
            testApplication {
                val viteConfig = ViteConfig()
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                    }

                application { installViteFrontend(config, viteConfig) }

                client.get("/static/app.css").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().shouldContain("color: blue")
                }

                client.get("/static/nonexistent.css").status shouldBe HttpStatusCode.NotFound
            }
        }

        "production mode serves index HTML correctly" {
            testApplication {
                val viteConfig = ViteConfig()
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                    }

                application { installViteFrontend(config, viteConfig) }

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
                val viteConfig = ViteConfig(staticUri = "assets")
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                    }

                application { installViteFrontend(config, viteConfig) }

                client.get("/assets/nonexistent.js").status shouldBe HttpStatusCode.NotFound
            }
        }

        "custom browser URI path prefix routes are configured correctly" {
            testApplication {
                val viteConfig = ViteConfig(browserUriPathPrefix = "app")
                val config =
                    KtpConfig.create {
                        setUnitTestEnv()
                        configValue("env.localDev", "false")
                    }

                application { installViteFrontend(config, viteConfig) }

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
