package net.ghue.ktp.ktor.start

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.time.Duration.Companion.seconds
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.installLocalDevConsoleLogger
import net.ghue.ktp.log.installSlf4jBridge
import net.ghue.ktp.log.log
import org.koin.core.module.Module
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

/**
 * A function that creates a new [KtpAppBuilder] instance. This is used because each application run
 * needs to generate its own [KtpAppBuilder] instance.
 */
typealias KtpAppBuilderFactory = () -> KtpAppBuilder

/**
 * Creates a KTP application. This is a mutable builder used to build an instance of the immutable
 * [KtpApp] which is used to run KTP.
 */
class KtpAppBuilder {
    init {
        installSlf4jBridge()
    }

    internal val modules = mutableListOf<Module>()
    internal val koinConfigs = mutableListOf<KoinConfiguration>()
    internal val appInits: MutableList<suspend Application.(KtpConfig) -> Unit> = mutableListOf()

    /** Can be used to override the default [KtpConfig] instance. */
    var createKtpConfig: () -> KtpConfig = { KtpConfig.create() }

    /** Add a KOIN [Module]. */
    fun addModule(module: Module) {
        modules.add(module)
    }

    /**
     * Add a generated KOIN Configuration from the compiler plugin. Example
     * `koinConfiguration<MyApp>()`
     */
    fun addKoinConfig(config: KoinConfiguration) {
        koinConfigs.add(config)
    }

    /** Build and add a KOIN [Module] from a DSL block. */
    fun addModule(configModule: Module.() -> Unit) {
        addModule(module { configModule() })
    }

    /** Register a KTOR application init block. */
    fun addAppInit(appInit: suspend Application.(KtpConfig) -> Unit) {
        appInits.add(appInit)
    }

    /** Remove all previously added app init blocks. */
    fun clearAppInits() {
        appInits.clear()
    }

    fun build(): KtpApp {
        val config = createKtpConfig()
        val allModules = buildList {
            add(module { single { config } })
            addAll(modules)
        }
        return KtpApp(
            config = config,
            modules = allModules,
            koinConfigs = koinConfigs.toList(),
            appInits = appInits.toList(),
        )
    }
}

data class KtpApp(
    val config: KtpConfig,
    val modules: List<Module>,
    val koinConfigs: List<KoinConfiguration>,
    val appInits: List<suspend Application.(KtpConfig) -> Unit>,
) {

    fun installKoin(app: Application) {
        app.install(KoinIsolated) {
            slf4jLogger()
            modules(module { single { app } })
            modules(modules)
            koinConfigs.forEach { config -> config.appDeclaration(this) }
        }
    }

    suspend fun runAppInits(app: Application) {
        for (appInit in appInits) {
            app.appInit(config)
        }
    }
}

fun ktpAppCreate(buildBlock: KtpAppBuilder.() -> Unit): KtpAppBuilderFactory = {
    val ktpAppBuilder = KtpAppBuilder()
    ktpAppBuilder.buildBlock()
    ktpAppBuilder
}

fun KtpAppBuilderFactory.start() {
    ktpAppStart(this)
}

fun KtpAppBuilderFactory.update(updateBlock: KtpAppBuilder.() -> Unit): KtpAppBuilderFactory {
    val ktpAppBuilder = this()
    ktpAppBuilder.updateBlock()
    return { ktpAppBuilder }
}

/** Start the KTP application. */
fun ktpAppStart(ktpAppBuilder: () -> KtpAppBuilder) {
    val ktpApp = ktpAppBuilder().build()
    if (ktpApp.config.env.isLocalDev) {
        installLocalDevConsoleLogger()
    }

    val ktorEnv = applicationEnvironment { this.log = LoggerFactory.getLogger("ktor") }
    val serverConfig =
        serverConfig(ktorEnv) {
            developmentMode = ktpApp.config.env.isLocalDev
            module {
                ktpApp.installKoin(this)
                ktpApp.runAppInits(this)
            }
        }
    val server =
        embeddedServer(
            factory = Netty,
            rootConfig = serverConfig,
            configure = {
                connector {
                    port = ktpApp.config.data.app.server.port
                    host = ktpApp.config.data.app.server.host
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
