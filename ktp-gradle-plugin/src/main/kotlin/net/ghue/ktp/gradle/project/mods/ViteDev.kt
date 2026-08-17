package net.ghue.ktp.gradle.project.mods

import java.io.File
import java.net.Socket
import java.util.concurrent.TimeUnit
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.named

private const val ENABLED_KEY = "ktp.vite"
private const val PORT_KEY = "ktp.vite.port"
private const val FRONTEND_DIR = "frontend"
private const val DEFAULT_PORT = 5173

/**
 * Starts a frontend dev server (`<package manager> run dev`, typically Vite) alongside the Ktor
 * `run` task, so a single `gradlew run` brings up the full dev stack.
 *
 * The frontend always lives in `<root>/frontend`; this is a KTP convention, not configurable.
 * Enabled automatically when that directory contains a `package.json`. Configured through
 * `gradle.properties`:
 * - `ktp.vite`: `false` disables it, `true` requires it (the build fails if no frontend is found).
 * - `ktp.vite.port`: port checked to detect an externally started dev server. Default: `5173`.
 */
fun Project.applyViteDev() {
    val forced =
        when (val enabledProp = findProperty(ENABLED_KEY)?.toString()) {
            null -> null
            "true" -> true
            "false" -> false
            else ->
                error(
                    "File 'gradle.properties', field '$ENABLED_KEY', has invalid value " +
                        "'$enabledProp'. Valid values are: true, false."
                )
        }
    if (forced == false) return

    val frontendDir = rootProject.layout.projectDirectory.dir(FRONTEND_DIR)
    if (!frontendDir.file("package.json").asFile.exists()) {
        // Auto-detection quietly finding no frontend is fine, but explicitly requiring the dev
        // server when there is none is a broken build that should fail, not silently no-op.
        if (forced == true) {
            error(
                "'$ENABLED_KEY=true' is set but there is no package.json in " +
                    "'${frontendDir.asFile}'."
            )
        }
        return
    }

    val portProp = findProperty(PORT_KEY)?.toString()
    val port =
        portProp?.let {
            it.toIntOrNull()
                ?: error(
                    "File 'gradle.properties', field '$PORT_KEY', has invalid value '$it'. " +
                        "It must be an integer port number."
                )
        } ?: DEFAULT_PORT

    val service =
        gradle.sharedServices.registerIfAbsent("ktpViteDev", ViteDevService::class.java) {
            parameters.frontendDir.set(frontendDir)
            parameters.port.set(port)
            parameters.command.set(devServerCommand(frontendDir.asFile))
        }

    tasks.named<JavaExec>("run") {
        usesService(service)
        doFirst { service.get().start() }
    }
}

/**
 * Picks the package manager by lock file. On Windows npm/pnpm/yarn are `.cmd` shims which
 * [ProcessBuilder] cannot launch by their bare name, while bun ships a native executable.
 */
private fun devServerCommand(frontendDir: File): List<String> {
    val windows = System.getProperty("os.name").startsWith("Windows")
    fun shim(name: String) = if (windows) "$name.cmd" else name
    val packageManager =
        when {
            frontendDir.resolve("bun.lock").exists() || frontendDir.resolve("bun.lockb").exists() ->
                "bun"
            frontendDir.resolve("pnpm-lock.yaml").exists() -> shim("pnpm")
            frontendDir.resolve("yarn.lock").exists() -> shim("yarn")
            else -> shim("npm")
        }
    return listOf(packageManager, "run", "dev")
}

/**
 * Runs the Vite dev server as a shared build service so Gradle closes it when the build ends. The
 * daemon runs [close] even on Ctrl+C / IDE stop, and killing the full process tree is required on
 * Windows where terminating the `.cmd` shim alone would orphan the node process under it.
 */
abstract class ViteDevService : BuildService<ViteDevService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val frontendDir: DirectoryProperty
        val port: Property<Int>
        val command: ListProperty<String>
    }

    private var process: Process? = null

    @Synchronized
    fun start() {
        if (process?.isAlive == true) return
        if (portInUse()) {
            println("Vite dev server already running on port ${parameters.port.get()}")
            return
        }
        val proc =
            ProcessBuilder(parameters.command.get())
                .directory(parameters.frontendDir.get().asFile)
                .redirectErrorStream(true)
                .start()
        process = proc
        Thread { proc.inputStream.bufferedReader().forEachLine { println("[vite] $it") } }
            .apply {
                isDaemon = true
                start()
            }
    }

    private fun portInUse(): Boolean = runCatching {
        Socket("127.0.0.1", parameters.port.get()).use { true }
    }
        .getOrDefault(false)

    @Synchronized
    override fun close() {
        val proc = process ?: return
        process = null
        val tree = proc.toHandle().descendants().toList() + proc.toHandle()
        tree.forEach { it.destroy() }
        if (!proc.waitFor(3, TimeUnit.SECONDS)) {
            tree.forEach { it.destroyForcibly() }
        }
        println("Stopped Vite dev server")
    }
}
