package net.ghue.ktp.ktor.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.java.JavaHttpConfig
import io.ktor.client.plugins.HttpTimeout
import java.net.http.HttpClient as JdkHttpClient
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.asCoroutineDispatcher

private val CONNECT_TIMEOUT = 10.seconds
private val REQUEST_TIMEOUT = 15.seconds

// Shared by every client for the JVM lifetime; a per-task executor keeps no idle workers.
private val clientExecutor =
    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ktp-http-", 0).factory())
private val clientDispatcher = clientExecutor.asCoroutineDispatcher()

/**
 * Creates a JDK client for blocking calls on virtual threads; the caller owns and closes it.
 * Defaults to a 10s connect timeout and otherwise the JDK's own (HTTP/2 preference, no redirects).
 * Set the request timeout on each HttpRequest, as the JDK has no client-wide one.
 */
fun createJdkHttpClient(configure: JdkHttpClient.Builder.() -> Unit = {}): JdkHttpClient =
    JdkHttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT.toJavaDuration())
        .executor(clientExecutor)
        .apply(configure)
        .build()

/**
 * Creates a Java-engine Ktor client; the caller owns and closes it. Defaults to a 10s connect
 * timeout, a 15s request timeout and otherwise Ktor's own (HTTP/2 preference, GET/HEAD redirects
 * followed, no exception for non-success statuses). Override any of them in [configure], e.g. with
 * [useHttp1], [connectTimeout] and [requestTimeout].
 */
fun createKtorHttpClient(configure: HttpClientConfig<JavaHttpConfig>.() -> Unit = {}): HttpClient =
    HttpClient(Java) {
        engine { dispatcher = clientDispatcher }
        connectTimeout(CONNECT_TIMEOUT)
        configure()
        // Installed last so a caller's HttpRequestRetry wraps it and each attempt gets its own
        // timeout. Ktor chains config blocks per plugin, so a caller-set timeout is kept.
        install(HttpTimeout) {
            if (requestTimeoutMillis == null) {
                requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
            }
        }
    }

/** Speaks HTTP/1.1 only, for servers that mishandle the JDK's HTTP/2 upgrade attempt. */
fun HttpClientConfig<JavaHttpConfig>.useHttp1() {
    engine { protocolVersion = JdkHttpClient.Version.HTTP_1_1 }
}

/** Limits establishing the TCP and TLS connection. */
fun HttpClientConfig<JavaHttpConfig>.connectTimeout(timeout: Duration) {
    engine { config { connectTimeout(timeout.toJavaDuration()) } }
}

/** Limits the whole request, including receiving the response body. */
fun HttpClientConfig<JavaHttpConfig>.requestTimeout(timeout: Duration) {
    install(HttpTimeout) { requestTimeoutMillis = timeout.inWholeMilliseconds }
}
