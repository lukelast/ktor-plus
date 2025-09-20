package net.ghue.ktp.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.config.KtpConfigManager
import net.ghue.ktp.ktor.start.ktpAppCreate
import org.koin.core.component.inject
import org.koin.ktor.ext.inject

class KtpTestStartTest :
    StringSpec({
        "testKtpStart sets up application context" {
            testKtpStart {
                val response = client.get("/test") {}
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "testKtpStart injects KtpConfigManager" {
            testKtpStart {
                application {
                    routing {
                        get("/config") {
                            val configManager by inject<KtpConfigManager>()
                            configManager.shouldNotBeNull()
                            call.respond(HttpStatusCode.OK, "Config available")
                        }
                    }
                }

                val response = client.get("/config")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "Config available"
            }
        }

        "testKtpStart injects Application instance" {
            testKtpStart {
                application {
                    routing {
                        get("/app") {
                            val app by inject<Application>()
                            app.shouldNotBeNull()
                            call.respond(HttpStatusCode.OK, "Application available")
                        }
                    }
                }

                val response = client.get("/app")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "Application available"
            }
        }

        "testKtpStart supports custom Koin modules" {
            abstract class TestService {
                abstract fun getMessage(): String
            }

            class TestServiceImpl : TestService() {
                override fun getMessage() = "Hello from test service"
            }

            testKtpStart(
                ktpAppCreate { createModule { single<TestService> { TestServiceImpl() } } }
            ) {
                application {
                    routing {
                        get("/service") {
                            val testService by inject<TestService>()
                            call.respond(HttpStatusCode.OK, testService.getMessage())
                        }
                    }
                }

                val response = client.get("/service")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "Hello from test service"
            }
        }

        "testKtpStart supports HTTP client testing" {
            testKtpStart {
                application {
                    routing {
                        get("/health") { call.respond(HttpStatusCode.OK, "OK") }
                        post("/echo") {
                            val body = call.receiveText()
                            call.respond(HttpStatusCode.OK, "Echo: $body")
                        }
                    }
                }

                val getResponse = client.get("/health")
                getResponse.status shouldBe HttpStatusCode.OK
                getResponse.bodyAsText() shouldBe "OK"

                val postResponse =
                    client.post("/echo") {
                        contentType(ContentType.Text.Plain)
                        setBody("test message")
                    }
                postResponse.status shouldBe HttpStatusCode.OK
                postResponse.bodyAsText() shouldBe "Echo: test message"
            }
        }

        "testKtpStart handles multiple routes" {
            testKtpStart {
                application {
                    routing {
                        get("/") { call.respond(HttpStatusCode.OK, "Root") }
                        get("/api/users") { call.respond(HttpStatusCode.OK, "user1,user2") }
                        get("/api/users/{id}") {
                            val id = call.parameters["id"]
                            call.respond(HttpStatusCode.OK, "User: $id")
                        }
                    }
                }

                val rootResponse = client.get("/")
                rootResponse.status shouldBe HttpStatusCode.OK
                rootResponse.bodyAsText() shouldBe "Root"

                val usersResponse = client.get("/api/users")
                usersResponse.status shouldBe HttpStatusCode.OK
                usersResponse.bodyAsText().shouldContain("user1")

                val userResponse = client.get("/api/users/123")
                userResponse.status shouldBe HttpStatusCode.OK
                userResponse.bodyAsText() shouldBe "User: 123"
            }
        }
    })
