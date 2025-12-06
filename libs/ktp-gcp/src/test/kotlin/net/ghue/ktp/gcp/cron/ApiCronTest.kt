package net.ghue.ktp.gcp.cron

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated

class ApiCronTest :
    StringSpec({
        "authorized request runs cron handler" {
            val cronHandler = mockk<CronHandler>()
            coEvery { cronHandler.hourly(any()) } returns Result(runAgain = false)

            testApplication {
                application {
                    val config = mockk<KtpConfig> { coEvery { env } returns Env("localdev") }

                    install(ContentNegotiation)
                    install(Resources)

                    install(KoinIsolated) {
                        modules(
                            module {
                                single { cronHandler }
                                single { config }
                            }
                        )
                    }

                    routing { installApiCronRoutes() }
                }

                val client = createClient { install(io.ktor.client.plugins.resources.Resources) }

                client.get(Api.Cron.Hourly()).apply { status shouldBe HttpStatusCode.OK }

                coVerify(exactly = 1) { cronHandler.hourly(any()) }
            }
        }

        "run again returns 429" {
            val cronHandler = mockk<CronHandler>()
            coEvery { cronHandler.hourly(any()) } returns Result(runAgain = true)

            testApplication {
                application {
                    val config = mockk<KtpConfig> { coEvery { env } returns Env("localdev") }
                    install(Resources)
                    install(KoinIsolated) {
                        modules(
                            module {
                                single { cronHandler }
                                single { config }
                            }
                        )
                    }
                    routing { installApiCronRoutes() }
                }

                val client = createClient { install(io.ktor.client.plugins.resources.Resources) }

                client.post(Api.Cron.Hourly()).apply {
                    println("Status: $status")
                    println("Body: ${bodyAsText()}")
                    status shouldBe HttpStatusCode.TooManyRequests
                }
                coVerify(exactly = 1) { cronHandler.hourly(any()) }
            }
        }
    })
