package net.ghue.ktp.test

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.String
import net.ghue.ktp.config.KtpConfig
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

fun testKtpStart(
    koinModule: Module.() -> Unit = {},
    overrideMap: Map<String, Any> = emptyMap(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        val config = KtpConfig.createManagerForTest(overrideMap)

        application {
            install(Koin) {
                modules(
                    module {
                        single { config }
                        single { this@application }
                    },
                    createKoinModule(koinModule),
                )
            }
        }

        test()
    }
}

private fun createKoinModule(koinModule: Module.() -> Unit): Module {
    return module { koinModule() }
}
