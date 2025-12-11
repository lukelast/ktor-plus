package net.ghue.ktp.test

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.start.KtpAppBuilder
import net.ghue.ktp.ktor.start.ktpAppCreate

fun testKtpStart(
    ktp: KtpAppBuilder = ktpAppCreate {},
    /**
     * Should the application be started. Disable this if you want to do more configuration before
     * it is started.
     */
    start: Boolean = true,
    overrideMap: Map<String, Any> = emptyMap(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        val ktpApp = ktp()
        ktpApp.createConfigManager = {
            KtpConfig.create {
                setUnitTestEnv()
                overrideMap.forEach { (key, value) -> configValue(key, value) }
            }
        }
        val ktpInstance = ktpApp.build()
        application {
            ktpInstance.installKoin(this)
            ktpInstance.appInit(this)
        }
        client = createClient {
            install(Resources)
            install(ContentNegotiation) { json() }
        }
        if (start) {
            startApplication()
        }
        test()
    }
}
