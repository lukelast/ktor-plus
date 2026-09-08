package net.ghue.ktp.ktor.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.serialization.json.JsonObject
import net.ghue.ktp.config.KtpConfig

private const val DEFAULT_LIMIT = 32
private const val UPLOAD_LIMIT = 1024L
private const val TINY_LIMIT = 4L

/** Every route echoes the received body length. */
private fun Application.limitedApp() {
    installDefaultPlugins(
        KtpConfig.create {
            setUnitTestEnv()
            overrideValue("bodyLimit.default", "$DEFAULT_LIMIT B")
        }
    )
    routing {
        post("/plain") { call.respondText(call.receive<String>().length.toString()) }
        route("/upload") {
            bodyLimit(UPLOAD_LIMIT)
            post { call.respondText(call.receive<String>().length.toString()) }
            route("/nested") { post { call.respondText(call.receive<String>().length.toString()) } }
        }
        route("/tiny") {
            bodyLimit(TINY_LIMIT)
            post { call.respondText(call.receive<String>().length.toString()) }
        }
    }
}

private suspend fun ApplicationTestBuilder.postBytes(path: String, size: Int): HttpStatusCode =
    client.post(path) { setBody("x".repeat(size)) }.status

class BodyLimitTest :
    StringSpec({
        "the configured default bounds every route" {
            testApplication {
                application { limitedApp() }
                postBytes("/plain", DEFAULT_LIMIT) shouldBe HttpStatusCode.OK
                postBytes("/plain", DEFAULT_LIMIT + 1) shouldBe HttpStatusCode.PayloadTooLarge
            }
        }

        "a streamed body without content-length is cut off at the limit" {
            testApplication {
                application { limitedApp() }
                val response =
                    client.post("/plain") { setBody(ByteReadChannel(ByteArray(DEFAULT_LIMIT + 1))) }
                response.status shouldBe HttpStatusCode.PayloadTooLarge
            }
        }

        "oversized streamed json remains a 413 when the converter wraps the failure" {
            testApplication {
                application {
                    installDefaultPlugins(
                        KtpConfig.create {
                            setUnitTestEnv()
                            overrideValue("bodyLimit.default", "3 MiB")
                        }
                    )
                    routing {
                        post("/json") {
                            call.request.headers[HttpHeaders.ContentLength] shouldBe null
                            val body = call.receive<JsonObject>()
                            call.respondText(body.toString())
                        }
                    }
                }
                val response =
                    client.post("/json") {
                        setBody(
                            object : OutgoingContent.WriteChannelContent() {
                                override val contentType = ContentType.Application.Json

                                override suspend fun writeTo(channel: ByteWriteChannel) {
                                    channel.writeStringUtf8("{\"value\":\"")
                                    // Exceed the channel buffers so JSON conversion starts before
                                    // the body limit is reached, exercising its exception wrapper.
                                    val chunk = "x".repeat(1024 * 1024)
                                    repeat(4) { channel.writeStringUtf8(chunk) }
                                    channel.writeStringUtf8("\"}")
                                }
                            }
                        )
                    }
                response.status shouldBe HttpStatusCode.PayloadTooLarge
                response.bodyAsText() shouldContain "The request body exceeds the size limit."
            }
        }

        "a route raises the limit for itself and its children" {
            testApplication {
                application { limitedApp() }
                postBytes("/upload", UPLOAD_LIMIT.toInt()) shouldBe HttpStatusCode.OK
                postBytes("/upload/nested", UPLOAD_LIMIT.toInt()) shouldBe HttpStatusCode.OK
                postBytes("/upload", UPLOAD_LIMIT.toInt() + 1) shouldBe
                    HttpStatusCode.PayloadTooLarge
            }
        }

        "a route lowers the limit below the default" {
            testApplication {
                application { limitedApp() }
                postBytes("/tiny", TINY_LIMIT.toInt()) shouldBe HttpStatusCode.OK
                postBytes("/tiny", TINY_LIMIT.toInt() + 1) shouldBe HttpStatusCode.PayloadTooLarge
            }
        }

        "413 is a problem response like every other ktp error" {
            testApplication {
                application { limitedApp() }
                val response = client.post("/plain") { setBody("x".repeat(DEFAULT_LIMIT + 1)) }
                response.status shouldBe HttpStatusCode.PayloadTooLarge
                response.bodyAsText() shouldContain "Payload Too Large"
            }
        }

        "the shipped default is 16 MiB" {
            KtpConfig.create { setUnitTestEnv() }.bodyLimit.default.toBytes() shouldBe
                16L * 1024 * 1024
        }
    })
