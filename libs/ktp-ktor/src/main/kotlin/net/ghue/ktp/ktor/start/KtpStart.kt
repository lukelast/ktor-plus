package net.ghue.ktp.ktor.start

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.time.Clock
import kotlin.time.Duration.Companion.seconds
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.plugin.RequestVirtualThreadPlugin
import net.ghue.ktp.log.configureLocalDevConsoleLogFormat
import net.ghue.ktp.log.installSlf4jBridge
import net.ghue.ktp.log.log
import org.koin.core.module.Module
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.KoinIsolated
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

/** Lazily supplies the [KtpAppBuilder] to run; may return the same instance on every call. */
typealias KtpAppBuilderFactory = () -> KtpAppBuilder

/**
 * Mutable builder for the immutable [KtpApp].
 *
 * Koin definitions load in this order, and a later definition of the same type replaces an earlier
 * one: the built-in [KtpConfig], system-UTC [Clock], and [Application] singles, then [addModule]
 * modules in the order added (library modules such as `firebaseAuthModule()`), then [addKoinConfig]
 * configs (the app's compiler-plugin `@Single`/`@Factory` definitions, so an app definition beats a
 * library one), then [addOverrideModule] modules. A test swapping a `@Singleton` service for a mock
 * therefore uses [addOverrideModule]; an [addModule] definition would lose to the compiler-plugin
 * one.
 */
class KtpAppBuilder {
    init {
        installSlf4jBridge()
    }

    internal val modules = mutableListOf<Module>()
    internal val koinConfigs = mutableListOf<KoinConfiguration>()
    internal val overrideModules = mutableListOf<Module>()
    internal val appInits: MutableList<suspend Application.(KtpConfig) -> Unit> = mutableListOf()

    var createKtpConfig: () -> KtpConfig = { KtpConfig.create() }

    fun addModule(module: Module) {
        modules.add(module)
    }

    /** Adds a compiler-plugin-generated Koin config, e.g. `koinConfiguration<MyApp>()`. */
    fun addKoinConfig(config: KoinConfiguration) {
        koinConfigs.add(config)
    }

    fun addModule(configModule: Module.() -> Unit) {
        addModule(module { configModule() })
    }

    /**
     * Adds a module loaded after every [addModule] module and [addKoinConfig] config, so its
     * definitions replace same-type definitions from both. For tests: `addOverrideModule {
     * single<FirestoreService> { mockk() } }`.
     */
    fun addOverrideModule(module: Module) {
        overrideModules.add(module)
    }

    fun addOverrideModule(configModule: Module.() -> Unit) {
        addOverrideModule(module { configModule() })
    }

    fun addAppInit(appInit: suspend Application.(KtpConfig) -> Unit) {
        appInits.add(appInit)
    }

    fun clearAppInits() {
        appInits.clear()
    }

    // Must not mutate this builder: [update] factories reuse one instance across builds.
    fun build(): KtpApp {
        val config = createKtpConfig()
        val allModules = buildList {
            add(
                module {
                    single { config }
                    // Injected into services so tests can fix time; an app definition replaces it.
                    single<Clock> { Clock.systemUTC() }
                }
            )
            addAll(modules)
        }
        return KtpApp(
            config = config,
            modules = allModules,
            koinConfigs = koinConfigs.toList(),
            appInits = appInits.toList(),
            overrideModules = overrideModules.toList(),
        )
    }
}

data class KtpApp(
    val config: KtpConfig,
    val modules: List<Module>,
    val koinConfigs: List<KoinConfiguration>,
    val appInits: List<suspend Application.(KtpConfig) -> Unit>,
    val overrideModules: List<Module> = emptyList(),
) {

    /** Loads Koin in the order [KtpAppBuilder] documents: modules, then configs, then overrides. */
    fun installKoin(app: Application) {
        app.install(KoinIsolated) {
            slf4jLogger()
            modules(module { single { app } })
            modules(modules)
            koinConfigs.forEach { config -> config.appDeclaration(this) }
            modules(overrideModules)
        }
        app.getKoin().autoCloseInstances()
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

fun ktpAppStart(ktpAppBuilder: () -> KtpAppBuilder) {
    val ktpApp = ktpAppBuilder().build()
    if (ktpApp.config.env.isLocalDev) {
        configureLocalDevConsoleLogFormat()
    }

    val ktorEnv = applicationEnvironment { this.log = LoggerFactory.getLogger("ktor") }
    val serverConfig =
        serverConfig(ktorEnv) {
            developmentMode = ktpApp.config.env.isLocalDev
            module {
                install(RequestVirtualThreadPlugin)
                ktpApp.installKoin(this)
                ktpApp.runAppInits(this)
            }
        }
    val server =
        embeddedServer(
            factory = Netty,
            rootConfig = serverConfig,
            configure = {
                // One acceptor thread is enough for a single connector.
                connectionGroupSize = 1
                // RequestVirtualThreadPlugin runs every call on a virtual thread, so the event
                // loops only do I/O and dispatch. Share one group of workerGroupSize (CPUs/2+1)
                // threads; Ktor adds callGroupSize to it when shared, so 0 keeps it that size.
                // callGroupSize = 0 is only valid with shareWorkGroup = true: Netty silently sizes
                // a standalone 0-thread group to 2xCPUs instead of failing.
                callGroupSize = 0
                shareWorkGroup = true
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
                // Grace period: how long Ktor waits for in-flight requests to finish.
                server.stop(4.seconds.inWholeMilliseconds, 8.seconds.inWholeMilliseconds)
                log {}.info { "Server is shut down" }
            }
        )
    server.start(true)
}
