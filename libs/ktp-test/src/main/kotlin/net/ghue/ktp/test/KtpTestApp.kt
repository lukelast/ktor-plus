package net.ghue.ktp.test

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.start.KtpAppBuilderFactory
import net.ghue.ktp.ktor.start.ktpAppCreate

fun ktpTestApp(
    appFactory: KtpAppBuilderFactory = ktpAppCreate {},
    /** Set false to do more configuration before the application is started. */
    start: Boolean = true,
    configOverrides: Map<String, Any> = emptyMap(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        val appBuilder = appFactory()
        appBuilder.createKtpConfig = {
            KtpConfig.create {
                setUnitTestEnv()
                configOverrides.forEach { (key, value) -> overrideValue(key, value) }
            }
        }
        val app = appBuilder.build()
        application { app.install(this) }
        client = ktpTestClient()
        if (start) {
            startApplication()
        }
        test()
    }
}

/** The client [ktpTestApp] installs (typed Resources requests, JSON bodies) plus [configure]. */
fun ApplicationTestBuilder.ktpTestClient(
    configure: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}
): HttpClient = createClient {
    install(Resources)
    install(ContentNegotiation) { json() }
    configure()
}
