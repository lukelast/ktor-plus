package net.ghue.ktp.ktor.start

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.time.Duration.Companion.seconds
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.Slf4jBridgeInstall
import net.ghue.ktp.log.installLocalDevConsoleLogger
import net.ghue.ktp.log.log
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

/**
 * A function that creates a new [KtpApp] instance. This is used because each application run needs
 * to generate its own [KtpApp] instance.
 */
typealias KtpAppBuilder = () -> KtpApp

/**
 * Creates a KTP application. This is a mutable builder used to build an instance of the immutable
 * [KtpAppInstance] which is used to run KTP.
 */
class KtpApp {
    init {
        Slf4jBridgeInstall()
    }

    internal val modules = mutableListOf<Module>()
    internal val appInits: MutableList<suspend Application.(KtpConfig) -> Unit> = mutableListOf()

    /** Can be used to override the default [KtpConfig] instance. */
    var createConfigManager: () -> KtpConfig = { KtpConfig.create() }

    /** Add a KOIN [Module]. */
    fun addModule(module: Module) {
        modules.add(module)
    }

    /** Create a KOIN [Module]. */
    fun createModule(configModule: Module.() -> Unit) {
        addModule(module { configModule() })
    }

    /** Configure the KTOR application. */
    fun appInit(appInit: suspend Application.(KtpConfig) -> Unit) {
        appInits.add(appInit)
    }

    /** Remove all previously added app init blocks. */
    fun clearAppInit() {
        appInits.clear()
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
    val config: KtpConfig,
    val modules: List<Module>,
    val appInits: List<suspend Application.(KtpConfig) -> Unit>,
) {

    fun installKoin(app: Application) {
        app.install(KoinIsolated) {
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

fun KtpAppBuilder.update(updateBlock: KtpApp.() -> Unit): KtpAppBuilder {
    val ktpApp = this()
    ktpApp.updateBlock()
    return { ktpApp }
}

/** Start the KTP application. */
fun ktpStart(buildConfig: () -> KtpApp) {
    val appInstance = buildConfig().build()
    if (appInstance.config.env.isLocalDev) {
        installLocalDevConsoleLogger()
    }

    val ktorEnv = applicationEnvironment { this.log = LoggerFactory.getLogger("ktor") }
    val serverConfig =
        serverConfig(ktorEnv) {
            developmentMode = appInstance.config.env.isLocalDev
            module {
                appInstance.installKoin(this)
                appInstance.appInit(this)
            }
        }
    val server =
        embeddedServer(
            factory = Netty,
            rootConfig = serverConfig,
            configure = {
                connector {
                    port = appInstance.config.data.app.server.port
                    host = appInstance.config.data.app.server.host
                }
                enableHttp2 = false
                enableH2c = false
            },
        )
    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                log {}.info { "Received shutdown signal. Shutting down" }
                // Grace period is the time ktor waits for in progress requests to finish.
                server.stop(4.seconds.inWholeMilliseconds, 8.seconds.inWholeMilliseconds)
                log {}.info { "Server is shut down" }
            }
        )
    server.start(true)
}
