package net.ghue.ktp.ktor.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.slf4j.MDC

class RequestVirtualThreadPluginTest :
    StringSpec({
        "request runs on one dedicated virtual thread across suspensions" {
            testApplication {
                application {
                    install(RequestVirtualThreadPlugin)
                    routing {
                        get("/thread") {
                            val before = Thread.currentThread()
                            delay(10)
                            val after = Thread.currentThread()
                            call.respondText(
                                listOf(
                                        before.isVirtual,
                                        before.name.startsWith("req-"),
                                        before === after,
                                    )
                                    .joinToString(",")
                            )
                        }
                    }
                }
                client.get("/thread").bodyAsText() shouldBe "true,true,true"
            }
        }

        "mdc value survives suspension points" {
            testApplication {
                application {
                    install(RequestVirtualThreadPlugin)
                    routing {
                        get("/mdc") {
                            MDC.put("key", "value")
                            delay(10)
                            call.respondText(MDC.get("key") ?: "missing")
                        }
                    }
                }
                client.get("/mdc").bodyAsText() shouldBe "value"
            }
        }

        "mdc does not leak between sequential requests" {
            testApplication {
                application {
                    install(RequestVirtualThreadPlugin)
                    routing {
                        get("/set") {
                            MDC.put("leak", "reqA")
                            call.respondText("set")
                        }
                        get("/check") { call.respondText(MDC.get("leak") ?: "clean") }
                    }
                }
                client.get("/set").bodyAsText() shouldBe "set"
                client.get("/check").bodyAsText() shouldBe "clean"
            }
        }

        "concurrent requests keep isolated mdc" {
            testApplication {
                val readyA = CompletableDeferred<Unit>()
                val readyB = CompletableDeferred<Unit>()
                application {
                    install(RequestVirtualThreadPlugin)
                    routing {
                        // Each handler sets its own MDC value, then waits until the other request
                        // is also in flight before reading it back.
                        get("/a") {
                            MDC.put("who", "a")
                            readyA.complete(Unit)
                            readyB.await()
                            call.respondText(MDC.get("who") ?: "missing")
                        }
                        get("/b") {
                            MDC.put("who", "b")
                            readyB.complete(Unit)
                            readyA.await()
                            call.respondText(MDC.get("who") ?: "missing")
                        }
                    }
                }
                withTimeout(10.seconds) {
                    coroutineScope {
                        val a = async { client.get("/a").bodyAsText() }
                        val b = async { client.get("/b").bodyAsText() }
                        a.await() shouldBe "a"
                        b.await() shouldBe "b"
                    }
                }
            }
        }

        "blocking a handler does not block other requests" {
            testApplication {
                val blockerRunning = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                application {
                    install(RequestVirtualThreadPlugin)
                    routing {
                        get("/blocker") {
                            blockerRunning.complete(Unit)
                            // Thread.sleep parks only this request's virtual thread.
                            while (!release.isCompleted) {
                                Thread.sleep(5)
                            }
                            call.respondText("released")
                        }
                        get("/fast") { call.respondText("fast") }
                    }
                }
                withTimeout(10.seconds) {
                    coroutineScope {
                        val blocked = async { client.get("/blocker").bodyAsText() }
                        blockerRunning.await()
                        client.get("/fast").bodyAsText() shouldBe "fast"
                        release.complete(Unit)
                        blocked.await() shouldBe "released"
                    }
                }
            }
        }
    })
