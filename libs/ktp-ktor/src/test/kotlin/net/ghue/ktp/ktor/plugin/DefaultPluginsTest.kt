package net.ghue.ktp.ktor.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.ghue.ktp.config.KtpConfig

class DefaultPluginsTest :
    StringSpec({
        "unexpected exceptions return problem json without leaking details" {
            testApplication {
                application {
                    installDefaultPlugins(KtpConfig.create { setUnitTestEnv() })

                    routing {
                        get("/boom") {
                            @Suppress("TooGenericExceptionThrown") throw RuntimeException("leak me")
                        }
                    }
                }

                val response = client.get("/boom")
                response.status shouldBe HttpStatusCode.InternalServerError
                response.contentType()?.withoutParameters() shouldBe
                    ContentType.Application.ProblemJson

                val bodyText = response.bodyAsText()
                bodyText.contains("leak me") shouldBe false
                bodyText.contains("RuntimeException") shouldBe false

                val body = Json.parseToJsonElement(bodyText).jsonObject
                body["status"]?.jsonPrimitive?.int shouldBe HttpStatusCode.InternalServerError.value
                body["instance"]?.jsonPrimitive?.content shouldBe "/boom"
                body["detail"] shouldBe null
            }
        }
    })
