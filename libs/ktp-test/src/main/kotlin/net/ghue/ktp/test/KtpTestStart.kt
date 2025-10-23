package net.ghue.ktp.test

import io.ktor.server.testing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.start.KtpAppBuilder
import net.ghue.ktp.ktor.start.ktpAppCreate

fun testKtpStart(
    ktp: KtpAppBuilder = ktpAppCreate {},
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
        test()
    }
}
