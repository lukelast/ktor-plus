package net.ghue.ktp.ktor.start

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.ghue.ktp.config.KtpConfig
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun ktpStart(koinModule: Module.() -> Unit) {

    val config = KtpConfig.createManager()
    val server =
        embeddedServer(
            Netty,
            port = config.data.app.server.port,
            host = config.data.app.server.host,
        ) {
            val application: Application = this
            install(Koin) {
                slf4jLogger()
                modules(
                    module {
                        single { config }
                        single { application }
                    },
                    createKoinModule(koinModule),
                )
            }
        }

    server.start(true)
}

private fun createKoinModule(koinModule: Module.() -> Unit): Module {
    return module { koinModule() }
}
