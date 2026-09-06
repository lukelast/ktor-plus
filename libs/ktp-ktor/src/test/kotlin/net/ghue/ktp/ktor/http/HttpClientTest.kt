package net.ghue.ktp.ktor.http

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.java.JavaHttpConfig
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withContext

class HttpClientTest :
    StringSpec({
        "JDK client uses virtual threads and the default connect timeout" {
            createJdkHttpClient().use { client ->
                client.connectTimeout().orElseThrow() shouldBe Duration.ofSeconds(10)
                client.executor().orElseThrow().runsOnVirtualThread() shouldBe true
            }
        }

        "JDK client applies caller configuration" {
            createJdkHttpClient { connectTimeout(Duration.ofSeconds(2)) }
                .use { it.connectTimeout().orElseThrow() shouldBe Duration.ofSeconds(2) }
        }

        "JDK client returns redirects instead of following them" {
            redirectServer().use { server ->
                val request =
                    HttpRequest.newBuilder(URI.create(server.url))
                        .timeout(Duration.ofSeconds(5))
                        .build()
                createJdkHttpClient().use {
                    it.send(request, BodyHandlers.ofString()).statusCode() shouldBe 302
                }
            }
        }

        "Ktor client follows redirects on virtual threads" {
            redirectServer().use { server ->
                createKtorHttpClient().use { client ->
                    val response = client.get(server.url)
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldBe "ok"
                    withContext(client.engine.dispatcher) {
                        Thread.currentThread().isVirtual
                    } shouldBe true
                }
            }
        }

        "Ktor client applies caller configuration" {
            redirectServer().use { server ->
                createKtorHttpClient { followRedirects = false }
                    .use { it.get(server.url).status shouldBe HttpStatusCode.Found }
            }
        }

        "useHttp1 switches the engine from the HTTP/2 default" {
            createKtorHttpClient().use { it.protocolVersion shouldBe JdkHttpClient.Version.HTTP_2 }
            createKtorHttpClient { useHttp1() }
                .use { it.protocolVersion shouldBe JdkHttpClient.Version.HTTP_1_1 }
        }

        "requestTimeout overrides the default" {
            silentServer().use { server ->
                createKtorHttpClient { requestTimeout(100.milliseconds) }
                    .use { shouldThrow<HttpRequestTimeoutException> { it.get(server.url) } }
            }
        }

        "caller-installed HttpTimeout overrides the default" {
            silentServer().use { server ->
                createKtorHttpClient { install(HttpTimeout) { requestTimeoutMillis = 100 } }
                    .use { shouldThrow<HttpRequestTimeoutException> { it.get(server.url) } }
            }
        }

        "closing one Ktor client leaves the shared dispatcher usable by another" {
            redirectServer().use { server ->
                createKtorHttpClient().use { survivor ->
                    createKtorHttpClient().use {
                        it.get(server.url).status shouldBe HttpStatusCode.OK
                    }
                    survivor.get(server.url).bodyAsText() shouldBe "ok"
                }
            }
        }
    })

private val io.ktor.client.HttpClient.protocolVersion: JdkHttpClient.Version
    get() = (engine.config as JavaHttpConfig).protocolVersion

private class TestServer(handler: HttpHandler) : AutoCloseable {
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/", handler)
            start()
        }
    val url = "http://127.0.0.1:${server.address.port}/"

    override fun close() = server.stop(0)
}

/** Redirects every path to `/target`, which answers 200 with body "ok". */
private fun redirectServer() = TestServer { exchange ->
    if (exchange.requestURI.path == "/target") {
        exchange.sendResponseHeaders(200, 2)
        exchange.responseBody.use { it.write("ok".toByteArray()) }
    } else {
        exchange.responseHeaders.add("Location", "/target")
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }
}

/** Never sends response headers, so clients hit their request timeout. */
private fun silentServer() = TestServer {}

private fun Executor.runsOnVirtualThread(): Boolean {
    val virtual = CompletableFuture<Boolean>()
    execute { virtual.complete(Thread.currentThread().isVirtual) }
    return virtual.get(5, TimeUnit.SECONDS)
}
