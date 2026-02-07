package net.ghue.ktp.ktor.error

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KtorErrorResponseHandlerTest :
    StringSpec({
        "processKtpRspEx returns RFC 9457 problem details and includes extraFields" {
            testApplication {
                application {
                    install(StatusPages) { exception<KtpRspEx>(::processKtpRspEx) }
                    routing {
                        get("/problem") {
                            throw KtpRspEx(
                                status = HttpStatusCode.BadRequest,
                                type = "https://example.com/problems/invalid-input",
                                title = "Invalid input",
                                detail = "Email is required",
                                extraFields = mapOf("correlationId" to "abc-123", "attempt" to 2),
                            )
                        }
                    }
                }

                val response = client.get("/problem?source=test")
                response.status shouldBe HttpStatusCode.BadRequest
                response.contentType()?.withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["type"]?.jsonPrimitive?.content shouldBe
                    "https://example.com/problems/invalid-input"
                body["title"]?.jsonPrimitive?.content shouldBe "Invalid input"
                body["status"]?.jsonPrimitive?.int shouldBe HttpStatusCode.BadRequest.value
                body["detail"]?.jsonPrimitive?.content shouldBe "Email is required"
                body["instance"]?.jsonPrimitive?.content shouldBe "/problem"
                body["correlationId"]?.jsonPrimitive?.content shouldBe "abc-123"
                body["attempt"]?.jsonPrimitive?.int shouldBe 2
            }
        }

        "processKtpRspEx applies RFC defaults and omits blank detail" {
            testApplication {
                application {
                    install(StatusPages) { exception<KtpRspEx>(::processKtpRspEx) }
                    routing {
                        get("/defaults") {
                            throw KtpRspEx(
                                status = HttpStatusCode.Forbidden,
                                type = "",
                                title = "",
                                detail = "",
                            )
                        }
                    }
                }

                val response = client.get("/defaults")
                response.status shouldBe HttpStatusCode.Forbidden
                response.contentType()?.withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["type"]?.jsonPrimitive?.content shouldBe "about:blank"
                body["title"]?.jsonPrimitive?.content shouldBe HttpStatusCode.Forbidden.description
                body["status"]?.jsonPrimitive?.int shouldBe HttpStatusCode.Forbidden.value
                body["instance"]?.jsonPrimitive?.content shouldBe "/defaults"
                body["detail"] shouldBe null
                body["class"] shouldBe null
            }
        }

        "processKtpRspEx does not allow extraFields to overwrite problem keys" {
            testApplication {
                application {
                    install(StatusPages) { exception<KtpRspEx>(::processKtpRspEx) }
                    routing {
                        get("/reserved-keys") {
                            throw KtpRspEx(
                                status = HttpStatusCode.BadRequest,
                                type = "https://example.com/problems/original",
                                title = "Original title",
                                detail = "Original detail",
                                extraFields =
                                    mapOf(
                                        "type" to "https://example.com/problems/overwritten",
                                        "title" to "Overwritten title",
                                        "status" to 999,
                                        "detail" to "Overwritten detail",
                                        "instance" to "/overwritten",
                                        "safe" to "ok",
                                    ),
                            )
                        }
                    }
                }

                val response = client.get("/reserved-keys")
                response.status shouldBe HttpStatusCode.BadRequest

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["type"]?.jsonPrimitive?.content shouldBe
                    "https://example.com/problems/original"
                body["title"]?.jsonPrimitive?.content shouldBe "Original title"
                body["status"]?.jsonPrimitive?.int shouldBe HttpStatusCode.BadRequest.value
                body["detail"]?.jsonPrimitive?.content shouldBe "Original detail"
                body["instance"]?.jsonPrimitive?.content shouldBe "/reserved-keys"
                body["safe"]?.jsonPrimitive?.content shouldBe "ok"
            }
        }

        "processKtpRspEx includes subclass fields discovered via reflection" {
            testApplication {
                application {
                    install(StatusPages) { exception<KtpRspEx>(::processKtpRspEx) }
                    routing {
                        get("/extended") {
                            throw ExtendedKtpRspEx(
                                userTitle = "User missing",
                                userDetail = "No user with id 42",
                                id = "42",
                                retryable = false,
                            )
                        }
                    }
                }

                val response = client.get("/extended")
                response.status shouldBe HttpStatusCode.NotFound
                response.contentType()?.withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["id"]?.jsonPrimitive?.content shouldBe "42"
                body["retryable"]?.jsonPrimitive?.boolean shouldBe false
                body["internalCode"] shouldBe null
                body["localizedMessage"] shouldBe null
                body["stackTrace"] shouldBe null
                body["suppressed"] shouldBe null
                body["class"]?.jsonPrimitive?.content shouldBe ExtendedKtpRspEx::class.qualifiedName
            }
        }

        "processKtpRspEx does not expose internalMessage or throwable fields" {
            testApplication {
                application {
                    install(StatusPages) { exception<KtpRspEx>(::processKtpRspEx) }
                    routing {
                        get("/secret") {
                            throw KtpRspEx(
                                internalMessage = "database password leaked",
                                status = HttpStatusCode.BadRequest,
                                title = "Bad Request",
                                detail = "Input is invalid",
                                cause = IllegalStateException("boom"),
                            )
                        }
                    }
                }

                val response = client.get("/secret?token=abcd")
                response.status shouldBe HttpStatusCode.BadRequest

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                body["instance"]?.jsonPrimitive?.content shouldBe "/secret"
                body["internalMessage"] shouldBe null
                body["message"] shouldBe null
                body["localizedMessage"] shouldBe null
                body["stackTrace"] shouldBe null
                body["suppressed"] shouldBe null
                body["cause"] shouldBe null
            }
        }
    })

private class ExtendedKtpRspEx(
    userTitle: String,
    userDetail: String,
    val id: String,
    val retryable: Boolean,
    private val internalCode: String = "secret",
) : KtpRspEx(status = HttpStatusCode.NotFound, title = userTitle, detail = userDetail)
