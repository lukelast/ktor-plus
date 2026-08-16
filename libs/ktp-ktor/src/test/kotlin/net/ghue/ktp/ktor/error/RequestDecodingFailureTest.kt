package net.ghue.ktp.ktor.error

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.plugin.installDefaultPlugins

class RequestDecodingFailureTest :
    StringSpec({
        "malformed JSON answers 400 problem json with a generic detail" {
            testApplication {
                installTestApp()
                val response = postDto("{ not json")
                response.shouldBeGenericBadRequest()
                response.bodyAsText() shouldNotContain "JSON"
                response.bodyAsText() shouldNotContain "offset"
            }
        }

        "missing non-null field answers 400 without leaking the serializer message" {
            testApplication {
                installTestApp()
                val response = postDto("""{"flavor":"vanilla"}""")
                response.shouldBeGenericBadRequest()
                response.bodyAsText() shouldNotContain "is required"
                response.bodyAsText() shouldNotContain "DecodeTestDto"
            }
        }

        "unknown enum value answers 400 without leaking the serializer message" {
            testApplication {
                installTestApp()
                val response = postDto("""{"name":"a","flavor":"bogus"}""")
                response.shouldBeGenericBadRequest()
                response.bodyAsText() shouldNotContain "Unknown flavor"
                response.bodyAsText() shouldNotContain "bogus"
            }
        }

        "unsupported content type answers 400 instead of 500" {
            testApplication {
                installTestApp()
                val response =
                    client.post("/dto") {
                        contentType(ContentType.Text.Plain)
                        setBody("""{"name":"a","flavor":"vanilla"}""")
                    }
                response.shouldBeGenericBadRequest()
            }
        }

        "valid body still reaches the route handler" {
            testApplication {
                installTestApp()
                val response = postDto("""{"name":"a","flavor":"vanilla"}""")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "ok"
            }
        }

        "a RuntimeException from a route still answers 500 via the catch-all" {
            testApplication {
                installTestApp()
                val response = client.get("/boom")
                response.status shouldBe HttpStatusCode.InternalServerError
                response.contentType()?.withoutParameters() shouldBe
                    ContentType.Application.ProblemJson
                response.bodyAsText() shouldNotContain "kaboom"
            }
        }

        "a thrown KtpRspEx keeps its own status, title, and detail" {
            testApplication {
                installTestApp()
                val response = client.get("/conflict")
                response.status shouldBe HttpStatusCode.Conflict
                response.contentType()?.withoutParameters() shouldBe
                    ContentType.Application.ProblemJson

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["status"]?.jsonPrimitive?.int shouldBe HttpStatusCode.Conflict.value
                body["title"]?.jsonPrimitive?.content shouldBe "Duplicate Roast"
                body["detail"]?.jsonPrimitive?.content shouldBe "This roast already exists."
            }
        }
    })

private fun ApplicationTestBuilder.installTestApp() {
    application {
        installDefaultPlugins(KtpConfig.create { setUnitTestEnv() })
        routing {
            post("/dto") {
                call.receive<DecodeTestDto>()
                call.respondText("ok")
            }
            get("/boom") { @Suppress("TooGenericExceptionThrown") throw RuntimeException("kaboom") }
            get("/conflict") {
                throw KtpRspEx(
                    status = HttpStatusCode.Conflict,
                    title = "Duplicate Roast",
                    detail = "This roast already exists.",
                )
            }
        }
    }
}

private suspend fun ApplicationTestBuilder.postDto(body: String): HttpResponse =
    client.post("/dto") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private suspend fun HttpResponse.shouldBeGenericBadRequest() {
    status shouldBe HttpStatusCode.BadRequest
    contentType()?.withoutParameters() shouldBe ContentType.Application.ProblemJson

    val body = Json.parseToJsonElement(bodyAsText()).jsonObject
    body["type"]?.jsonPrimitive?.content shouldBe "about:blank"
    body["title"]?.jsonPrimitive?.content shouldBe "Bad Request"
    body["status"]?.jsonPrimitive?.int shouldBe HttpStatusCode.BadRequest.value
    body["detail"]?.jsonPrimitive?.content shouldBe "The request body could not be parsed."
    body["instance"]?.jsonPrimitive?.content shouldBe "/dto"
    body["class"] shouldBe null
}

@Serializable
private data class DecodeTestDto(
    val name: String,
    @Serializable(with = DecodeTestFlavorSerializer::class) val flavor: DecodeTestFlavor,
)

private enum class DecodeTestFlavor(val slug: String) {
    VANILLA("vanilla"),
    CHOCOLATE("chocolate"),
}

/** Mirrors downstream strict slug serializers that throw on unknown values. */
private object DecodeTestFlavorSerializer : KSerializer<DecodeTestFlavor> {
    override val descriptor = PrimitiveSerialDescriptor("DecodeTestFlavor", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DecodeTestFlavor {
        val slug = decoder.decodeString()
        return DecodeTestFlavor.entries.firstOrNull { it.slug == slug }
            ?: throw IllegalArgumentException("Unknown flavor: $slug")
    }

    override fun serialize(encoder: Encoder, value: DecodeTestFlavor) {
        encoder.encodeString(value.slug)
    }
}
