package net.ghue.ktp.gcp.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

class RbacPluginTest :
    StringSpec({
        "allows access when user has required role" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "admin@example.com",
                                    name = "Admin User",
                                    roles = setOf("admin"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "Success"
                }
            }
        }

        "denies access with 403 when user lacks required role" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "user@example.com",
                                    name = "Regular User",
                                    roles = setOf("user"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldContain "Insufficient permissions"
                }
            }
        }

        "denies access with 403 when user has no roles" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "user@example.com",
                                    name = "No Role User",
                                    roles = emptySet(),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldContain "Insufficient permissions"
                }
            }
        }

        "denies access with 401 when no user is authenticated" {
            testApplication {
                application {
                    install(Authentication) {
                        // Register an auth provider that doesn't set any principal
                        register(
                            object :
                                AuthenticationProvider(
                                    object : Config(AuthProviderName.FIREBASE_SESSION) {}
                                ) {
                                override suspend fun onAuthenticate(
                                    context: AuthenticationContext
                                ) {
                                    // Don't set any principal - simulates unauthenticated user
                                }
                            }
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.Unauthorized
                    bodyAsText() shouldContain "Authentication required"
                }
            }
        }

        "allows access when user has one of multiple roles" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "user@example.com",
                                    name = "Multi Role User",
                                    roles = setOf("user", "admin", "editor"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "Success"
                }
            }
        }

        "denies access when user has none of the required roles" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "user@example.com",
                                    name = "Limited User",
                                    roles = setOf("user", "viewer"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldContain "Insufficient permissions"
                }
            }
        }

        "denies access when plugin configured with empty role" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "user@example.com",
                                    name = "Test User",
                                    roles = emptySet(),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(emptyRole) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldBe "Configuration Error"
                }
            }
        }

        "role matching is case sensitive" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "admin@example.com",
                                    name = "Admin User",
                                    roles = setOf("Admin"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/protected") { call.respondText("Success") }
                            }
                        }
                    }
                }

                with(client.get("/protected")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldContain "Insufficient permissions"
                }
            }
        }

        "requireRole helper function works correctly" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "editor@example.com",
                                    name = "Editor User",
                                    roles = setOf("editor"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("editor")) {
                                get("/edit") { call.respondText("Edit Success") }
                                get("/edit/nested") { call.respondText("Nested Success") }
                            }
                        }
                    }
                }

                with(client.get("/edit")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "Edit Success"
                }

                with(client.get("/edit/nested")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "Nested Success"
                }
            }
        }

        "works within authentication block" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "manager@example.com",
                                    name = "Manager User",
                                    roles = setOf("manager"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            route("/api") {
                                requireRole(Role("manager")) {
                                    get("/dashboard") { call.respondText("Dashboard") }
                                }
                                requireRole(Role("admin")) {
                                    get("/settings") { call.respondText("Settings") }
                                }
                            }
                        }
                    }
                }

                with(client.get("/api/dashboard")) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "Dashboard"
                }

                with(client.get("/api/settings")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldContain "Insufficient permissions"
                }
            }
        }

        "returns correct error messages in response body" {
            testApplication {
                application {
                    install(Authentication) {
                        dummy(
                            principal =
                                UserSession(
                                    userId = UserId("user123"),
                                    tenId = TenantId("test-tenant"),
                                    email = "user@example.com",
                                    name = "Test User",
                                    roles = setOf("user"),
                                )
                        )
                    }

                    routing {
                        authenticate(AuthProviderName.FIREBASE_SESSION) {
                            requireRole(Role("admin")) {
                                get("/admin") { call.respondText("Admin Panel") }
                            }
                        }
                    }
                }

                // Test 403 Forbidden message
                with(client.get("/admin")) {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldBe "Insufficient permissions"
                }
            }
        }
    })
