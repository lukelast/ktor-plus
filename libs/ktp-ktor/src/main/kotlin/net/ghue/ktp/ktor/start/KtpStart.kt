package net.ghue.ktp.ktor.start

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.installLocalDevConsoleLogger
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.bridge.SLF4JBridgeHandler

fun ktpStart(koinModule: Module.() -> Unit) {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    val config = KtpConfig.createManager()
    if (config.env.isLocalDev) {
        installLocalDevConsoleLogger()
    }
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
