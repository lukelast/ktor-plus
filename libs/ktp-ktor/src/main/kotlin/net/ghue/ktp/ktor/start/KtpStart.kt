package net.ghue.ktp.ktor.start

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.KtpConfigManager
import net.ghue.ktp.log.installLocalDevConsoleLogger
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.bridge.SLF4JBridgeHandler

typealias KtpAppBuilder = () -> KtpApp

class KtpApp {
    internal val modules = mutableListOf<Module>()
    internal val appInits: MutableList<suspend Application.(KtpConfigManager) -> Unit> =
        mutableListOf()
    var createConfigManager: () -> KtpConfigManager = { KtpConfig.createManager() }

    fun addModule(module: Module) {
        modules.add(module)
    }

    fun createModule(configModule: Module.() -> Unit) {
        addModule(module { configModule() })
    }

    fun init(appInit: suspend Application.(KtpConfigManager) -> Unit) {
        appInits.add(appInit)
    }

    fun build(): KtpAppInstance {
        val config = createConfigManager()
        modules.addFirst(module { single { config } })
        return KtpAppInstance(
            config = config,
            modules = modules.toList(),
            appInits = appInits.toList(),
        )
    }
}

data class KtpAppInstance(
    val config: KtpConfigManager,
    val modules: List<Module>,
    val appInits: List<suspend Application.(KtpConfigManager) -> Unit>,
) {

    fun installKoin(app: Application) {
        app.install(Koin) {
            slf4jLogger()
            modules(module { single { app } })
            modules(modules)
        }
    }

    suspend fun appInit(app: Application) {
        for (appInit in appInits) {
            app.appInit(config)
        }
    }
}

fun ktpAppCreate(buildBlock: KtpApp.() -> Unit): () -> KtpApp = {
    val ktpApp = KtpApp()
    ktpApp.buildBlock()
    ktpApp
}

fun KtpAppBuilder.start() {
    ktpStart(this)
}

fun ktpStart(buildConfig: () -> KtpApp) {
    val appInstance = buildConfig().build()
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    if (appInstance.config.env.isLocalDev) {
        installLocalDevConsoleLogger()
        System.setProperty("io.ktor.development", "true")
    }
    val server =
        embeddedServer(
            factory = Netty,
            port = appInstance.config.data.app.server.port,
            host = appInstance.config.data.app.server.host,
        ) {
            appInstance.installKoin(this)
            appInstance.appInit(this)
        }
    server.start(true)
}
