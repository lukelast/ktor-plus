package net.ghue.ktp.ktor.start

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.BindException
import java.net.ServerSocket
import java.time.Clock
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

    private val modules = mutableListOf<Module>()
    private val koinConfigs = mutableListOf<KoinConfiguration>()
    private val overrideModules = mutableListOf<Module>()
    private val appInits: MutableList<suspend Application.(KtpConfig) -> Unit> = mutableListOf()

    var createKtpConfig: () -> KtpConfig = { KtpConfig.create() }

    fun addModule(koinModule: Module) {
        modules.add(koinModule)
    }

    /** Adds a compiler-plugin-generated Koin config, e.g. `koinConfiguration<MyApp>()`. */
    fun addKoinConfig(koinConfig: KoinConfiguration) {
        koinConfigs.add(koinConfig)
    }

    fun addModule(koinModuleBuilder: Module.() -> Unit) {
        addModule(module { koinModuleBuilder() })
    }

    /**
     * Adds a module loaded after every [addModule] module and [addKoinConfig] config, so its
     * definitions replace same-type definitions from both. For tests: `addOverrideModule {
     * single<Service> { mockk() } }`.
     */
    fun addOverrideModule(koinModule: Module) {
        overrideModules.add(koinModule)
    }

    fun addOverrideModule(koinModuleBuilder: Module.() -> Unit) {
        addOverrideModule(module { koinModuleBuilder() })
    }

    fun addAppInit(appInit: suspend Application.(KtpConfig) -> Unit) {
        appInits.add(appInit)
    }

    fun clearAppInits() {
        appInits.clear()
    }

    // Must not mutate this builder: [update] factories reuse one instance across builds.
    fun build(): KtpApp {
        val ktpConfig = createKtpConfig()
        val allModules = buildList {
            add(
                module {
                    single { ktpConfig }
                    // Injected into services so tests can fix time; an app definition replaces it.
                    single<Clock> { Clock.systemUTC() }
                }
            )
            addAll(modules)
        }
        return KtpApp(
            config = ktpConfig,
            modules = allModules,
            koinConfigs = koinConfigs.toList(),
            appInits = appInits.toList(),
            overrideModules = overrideModules.toList(),
        )
    }
}

data class KtpApp(
    internal val config: KtpConfig,
    internal val modules: List<Module>,
    private val koinConfigs: List<KoinConfiguration>,
    internal val appInits: List<suspend Application.(KtpConfig) -> Unit>,
    private val overrideModules: List<Module> = emptyList(),
) {

    /** The one boot path, shared by [start] and ktp-test so tests run what deploys run. */
    suspend fun install(app: Application) {
        app.install(RequestVirtualThreadPlugin)
        installKoin(app)
        runAppInits(app)
    }

    /** Loads Koin in the order [KtpAppBuilder] documents: modules, then configs, then overrides. */
    private fun installKoin(app: Application) {
        app.install(KoinIsolated) {
            slf4jLogger()
            modules(module { single { app } })
            modules(modules)
            koinConfigs.forEach { koinConfig -> koinConfig.appDeclaration(this) }
            modules(overrideModules)
        }
        app.getKoin().autoCloseInstances()
    }

    private suspend fun runAppInits(app: Application) {
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

fun KtpAppBuilderFactory.update(updateBlock: KtpAppBuilder.() -> Unit): KtpAppBuilderFactory {
    val ktpAppBuilder = this()
    ktpAppBuilder.updateBlock()
    return { ktpAppBuilder }
}

fun KtpAppBuilderFactory.start() {
    val ktpApp = this().build()
    if (ktpApp.config.env.isLocalDev) {
        configureLocalDevConsoleLogFormat()
    }

    val ktorEnv = applicationEnvironment { this.log = LoggerFactory.getLogger("ktor") }
    val serverConfig =
        serverConfig(ktorEnv) {
            developmentMode = ktpApp.config.env.isLocalDev
            module {
                // Ktor's own shutdown hook stops the server but logs nothing about it.
                monitor.subscribe(ApplicationStopping) { log {}.info { "Shutting down" } }
                monitor.subscribe(ApplicationStopped) { log {}.info { "Server is shut down" } }
                ktpApp.install(this)
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
    try {
        server.start(true)
    } catch (ex: Exception) {
        val port = ktpApp.config.data.app.server.port
        val portTaken = generateSequence<Throwable>(ex) { it.cause }.any { it is BindException }
        if (portTaken && ktpApp.config.env.isLocalDev) {
            throw IllegalStateException(localDevPortTakenMessage(port), ex)
        }
        throw ex
    }
}

/**
 * Two apps, or two worktrees of one app, cannot share a port, and whoever hits that (often an agent
 * in a fresh worktree) needs the exact fix rather than a bare BindException.
 */
private fun localDevPortTakenMessage(port: Int): String {
    val free =
        (port + 1..port + 100).firstOrNull { runCatching { ServerSocket(it).close() }.isSuccess }
    return "Port $port (config 'app.server.port') is already in use, probably by another app or " +
        "another worktree of this one. Give this checkout its own port: add the line " +
        "'app.server.port = ${free ?: "<free port>"}' to the git-ignored file " +
        "'backend/src/main/resources/ktp/0.local.localdev.conf' (create it if missing), then run " +
        "again and open http://localhost:${free ?: "<free port>"}."
}
