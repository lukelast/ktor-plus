package net.ghue.ktp.ktor.app

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.app.debug.installConfigDebugInfo

class DebugEndpointsTest :
    StringSpec({
        "/debug/version returns the configured version" {
            testApplication {
                application {
                    installConfigDebugInfo(
                        KtpConfig.createManagerForTest(mapOf("app.version" to "69"))
                    )
                }
                with(client.get("/debug/version")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "69"
                }
            }
        }
    })
