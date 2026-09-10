package net.ghue.ktp.ktor.openapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@OptIn(ExperimentalKtorApi::class)
class OpenApiDocumentTest :
    StringSpec({
        "export keeps included prefixes minus excluded routes and is repeatable" {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    routing {
                        listOf("/api/item/{id}", "/api/cronicle", "/auth/login", "/apiculture")
                            .forEach { path ->
                                get(path) { call.respond(HttpStatusCode.NoContent) }
                            }
                        route("/api/webhook") {
                                get("event") { call.respond(HttpStatusCode.NoContent) }
                            }
                            .excludeFromOpenApi()
                    }
                }
                startApplication()
                val first = app.openApiDocument()
                app.openApiDocument() shouldBe first
                val json = Json.parseToJsonElement(first).jsonObject
                json["paths"]!!.jsonObject.keys shouldBe setOf("/api/item/{id}", "/api/cronicle")
            }
        }
        "documented request bodies are required" {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    routing {
                        post("/api/body") { call.respond(HttpStatusCode.NoContent) }
                            .describe {
                                requestBody {
                                    ContentType.Application.Json { schema = jsonSchema<TestBody>() }
                                }
                            }
                        post("/api/empty") { call.respond(HttpStatusCode.NoContent) }
                    }
                }
                startApplication()
                val paths = Json.parseToJsonElement(app.openApiDocument()).jsonObject["paths"]!!
                val body = paths.jsonObject["/api/body"]!!.jsonObject["post"]!!.jsonObject
                body["requestBody"]!!.jsonObject["required"] shouldBe JsonPrimitive(true)
                val empty = paths.jsonObject["/api/empty"]!!.jsonObject["post"]!!.jsonObject
                empty.containsKey("requestBody") shouldBe false
            }
        }
    })

@Serializable private data class TestBody(val name: String)
