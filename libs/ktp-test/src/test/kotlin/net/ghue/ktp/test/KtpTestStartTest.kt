package net.ghue.ktp.test

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.ghue.ktp.config.KtpConfigManager
import net.ghue.ktp.ktor.start.ktpAppCreate
import org.junit.jupiter.api.Test
import org.koin.core.component.inject
import org.koin.test.KoinTest

class KtpTestStartTest : KoinTest {

    @Test
    fun `testKtpStart should set up basic application context`() {
        testKtpStart {
            // Test that the application context is properly set up
            val response =
                client.get("/test") {
                    // This will fail if no route is defined, but context should be available
                }
            // We expect a 404 since no routes are defined, but the app should be running
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `testKtpStart should inject KtpConfigManager`() {
        testKtpStart {
            application {
                routing {
                    get("/config") {
                        val configManager by inject<KtpConfigManager>()
                        assertNotNull(configManager)
                        call.respond(HttpStatusCode.OK, "Config available")
                    }
                }
            }

            val response = client.get("/config")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Config available", response.bodyAsText())
        }
    }

    @Test
    fun `testKtpStart should inject Application instance`() {
        testKtpStart {
            application {
                routing {
                    get("/app") {
                        val app by inject<Application>()
                        assertNotNull(app)
                        call.respond(HttpStatusCode.OK, "Application available")
                    }
                }
            }

            val response = client.get("/app")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Application available", response.bodyAsText())
        }
    }

    @Test
    fun `testKtpStart should support custom Koin modules`() {
        abstract class TestService {
            abstract fun getMessage(): String
        }

        class TestServiceImpl : TestService() {
            override fun getMessage() = "Hello from test service"
        }

        testKtpStart(ktpAppCreate { createModule { single<TestService> { TestServiceImpl() } } }) {
            application {
                routing {
                    get("/service") {
                        val testService by inject<TestService>()
                        call.respond(HttpStatusCode.OK, testService.getMessage())
                    }
                }
            }

            val response = client.get("/service")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello from test service", response.bodyAsText())
        }
    }

    @Test
    fun `testKtpStart should support HTTP client testing`() {
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

            // Test GET request
            val getResponse = client.get("/health")
            assertEquals(HttpStatusCode.OK, getResponse.status)
            assertEquals("OK", getResponse.bodyAsText())

            // Test POST request
            val postResponse =
                client.post("/echo") {
                    contentType(ContentType.Text.Plain)
                    setBody("test message")
                }
            assertEquals(HttpStatusCode.OK, postResponse.status)
            assertEquals("Echo: test message", postResponse.bodyAsText())
        }
    }

    @Test
    fun `testKtpStart should handle multiple routes`() {
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
            assertEquals(HttpStatusCode.OK, rootResponse.status)
            assertEquals("Root", rootResponse.bodyAsText())

            val usersResponse = client.get("/api/users")
            assertEquals(HttpStatusCode.OK, usersResponse.status)
            assertTrue(usersResponse.bodyAsText().contains("user1"))

            val userResponse = client.get("/api/users/123")
            assertEquals(HttpStatusCode.OK, userResponse.status)
            assertEquals("User: 123", userResponse.bodyAsText())
        }
    }
}
