package net.ghue.ktp.gradle.lukestack

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import net.ghue.ktp.gradle.project.DEV_SERVER_PORT_KEY
import net.ghue.ktp.gradle.project.PACKAGE_JSON
import net.ghue.ktp.gradle.project.ProjectMode
import net.ghue.ktp.gradle.project.booleanProperty
import net.ghue.ktp.gradle.project.layoutMode
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.named

/** Also read by the settings plugin, which must agree on whether a dev server is going to run. */
internal const val DEV_SERVER_ENABLED_KEY = "ktp.vite"
internal const val FRONTEND_DIR = "frontend"

/** Read by ktp-ktor's `ViteFrontendConfig.vitePort`, the dev proxy target. */
private const val VITE_PORT_ENV = "KTP_VITE_PORT"

/** Stands in for the port in a dev-server command until the service has picked one. */
internal const val PORT_TOKEN = "{port}"

/** How long the first Kotlin/Wasm compile may take before the dev server is given up on. */
private const val READY_TIMEOUT_MINUTES = 5L

/** Preferred ports come from here: above the well-known range, below the ephemeral one. */
private const val PREFERRED_PORT_BASE = 20000
private const val PREFERRED_PORT_SPAN = 20000

/**
 * Starts the frontend dev server alongside the Ktor `run` task, so a single `gradlew run` brings up
 * the full dev stack. The dev server gets its own port, handed to the backend as `KTP_VITE_PORT`,
 * so any number of apps run at once. Two kinds of frontend are recognised, and a build may have
 * only one of them:
 * - `<root>/frontend` with a `package.json`: `bun run dev` (Vite). The directory name is a KTP
 *   convention, not configurable.
 * - a sibling project in `compose` mode: a second Gradle build runs its webpack dev server with
 *   `--continuous`, which is the only way Kotlin recompiles on change while this build is held by
 *   `run`. Killing that build's client cancels it in the daemon and webpack stops with it.
 *
 * `ktp.vite` in `gradle.properties`: `false` disables both (run the dev server yourself on 5173,
 * the backend proxy's default; for a Compose module that is `-Pktp.devServerPort=5173`), `true`
 * requires one (the build fails if no frontend is found).
 */
internal fun Project.configureDevServer() {
    val forced = booleanProperty(DEV_SERVER_ENABLED_KEY)
    if (forced == false) return

    val vite = viteLaunch()
    val compose = composeLaunch()
    if (vite != null && compose != null) {
        error(
            "Both '${vite.source}' and the Compose module '${compose.source}' want the dev server " +
                "behind `run`. Set '$DEV_SERVER_ENABLED_KEY=false' and start one yourself."
        )
    }
    val launch = vite ?: compose
    if (launch == null) {
        // Auto-detection quietly finding no frontend is fine, but explicitly requiring the dev
        // server when there is none is a broken build that should fail, not silently no-op.
        if (forced == true) {
            error(
                "'$DEV_SERVER_ENABLED_KEY=true' is set but there is neither a package.json in " +
                    "'${rootProject.file(FRONTEND_DIR)}' nor a Compose web module."
            )
        }
        return
    }

    val service =
        gradle.sharedServices.registerIfAbsent("ktpDevServer", DevServerService::class.java) {
            parameters.workingDir.set(launch.workingDir)
            parameters.command.set(launch.command)
            parameters.label.set(launch.label)
            parameters.preferredPort.set(preferredPort(rootDir))
        }

    tasks.named<JavaExec>(ApplicationPlugin.TASK_RUN_NAME) {
        usesService(service)
        launch.installTask?.let { dependsOn(it) }
        doFirst { environment(VITE_PORT_ENV, service.get().start()) }
    }
}

/**
 * A dev server to run: the command with [PORT_TOKEN] where its port goes, a log prefix, what it
 * serves (for messages), and a task that must run first.
 */
internal class DevServerLaunch(
    val label: String,
    val source: String,
    val workingDir: Directory,
    val command: List<String>,
    val installTask: String? = null,
)

private fun Project.viteLaunch(): DevServerLaunch? {
    val frontendDir = rootProject.layout.projectDirectory.dir(FRONTEND_DIR)
    if (!frontendDir.file(PACKAGE_JSON).asFile.exists()) return null
    return DevServerLaunch(
        label = "vite",
        source = frontendDir.asFile.path,
        workingDir = frontendDir,
        // Strict, so losing the tiny race between the port probe and Vite binding fails the build
        // instead of moving Vite off the proxied port.
        command = listOf("bun", "run", "dev", "--port", PORT_TOKEN, "--strictPort"),
        // On a fresh clone Vite would die instantly on missing node_modules; the install task only
        // exists when the frontend is included as a project in this build.
        installTask = ":$FRONTEND_DIR:install".takeIf { findProject(":$FRONTEND_DIR") != null },
    )
}

private fun Project.composeLaunch(): DevServerLaunch? {
    // By layout: reading a sibling's properties would apply its plugins early and cycle on :backend.
    val compose =
        rootProject.subprojects.firstOrNull { layoutMode(it.projectDir) == ProjectMode.COMPOSE }
    return compose?.let {
        DevServerLaunch(
            label = "webpack",
            source = it.path,
            workingDir = rootProject.layout.projectDirectory,
            command = composeDevServerCommand(it.path, rootDir, isWindows = isWindows()),
        )
    }
}

/**
 * The child build that serves the Compose web module: its dev-server task under `--continuous`,
 * told the port through the property `compose` mode reads. Plain console output, since it is
 * relayed line by line under a prefix. The wrapper is named by its full path: Windows with
 * `NoDefaultCurrentDirectoryInExePath` does not look for `gradlew.bat` in the working directory.
 */
internal fun composeDevServerCommand(
    composeProjectPath: String,
    rootDir: File,
    isWindows: Boolean,
): List<String> {
    val wrapper =
        if (isWindows) listOf("cmd", "/c", rootDir.resolve("gradlew.bat").path)
        else listOf(rootDir.resolve("gradlew").path)
    return wrapper +
        listOf(
            "$composeProjectPath:wasmJsBrowserDevelopmentRun",
            "--continuous",
            "--console=plain",
            "-P$DEV_SERVER_PORT_KEY=$PORT_TOKEN",
        )
}

/**
 * The same port on every run of one checkout, so the child Gradle build, whose configuration cache
 * keys on the `-P` value, reuses its entry instead of reconfiguring each time. Another checkout
 * hashes to another port, and a taken port falls back to a free one at start.
 */
internal fun preferredPort(rootDir: File): Int =
    PREFERRED_PORT_BASE + abs(rootDir.absolutePath.hashCode()) % PREFERRED_PORT_SPAN

private fun isWindows() = System.getProperty("os.name").startsWith("Windows")

/**
 * Runs the dev server as a shared build service so Gradle closes it when the build ends. The daemon
 * runs [close] even on Ctrl+C / IDE stop, and killing the full process tree is required because a
 * `.cmd` shim terminated alone would orphan what it started.
 */
internal abstract class DevServerService : BuildService<DevServerService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val workingDir: DirectoryProperty
        val command: ListProperty<String>
        val label: Property<String>
        val preferredPort: Property<Int>
    }

    private val logger = Logging.getLogger(DevServerService::class.java)
    private var process: Process? = null
    private var port = 0

    /**
     * Starts the dev server unless this build already did, waits until it accepts connections, and
     * returns the port it listens on. Waiting matters for the Compose flavour, whose first compile
     * takes long enough that the backend would otherwise proxy into a closed port.
     */
    @Synchronized
    fun start(): Int {
        if (process?.isAlive == true) return port
        val label = parameters.label.get()
        port = pickPort(parameters.preferredPort.get())
        val command = parameters.command.get().map { it.replace(PORT_TOKEN, "$port") }
        val proc =
            ProcessBuilder(command)
                .directory(parameters.workingDir.get().asFile)
                .redirectErrorStream(true)
                .start()
        process = proc
        Thread {
                proc.inputStream.bufferedReader().forEachLine { logger.lifecycle("[$label] $it") }
            }
            .apply {
                isDaemon = true
                start()
            }
        logger.lifecycle("Starting the $label dev server on port $port")
        try {
            awaitListening(proc, command, label)
        } catch (e: InterruptedException) {
            // Cancelled while waiting: the service of a task that never ran is not closed, so the
            // child is taken down here.
            close()
            throw e
        }
        logger.lifecycle("The $label dev server is ready on port $port")
        return port
    }

    private fun awaitListening(proc: Process, command: List<String>, label: String) {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(READY_TIMEOUT_MINUTES)
        while (!listening(port)) {
            // A dev server that dies (bad script, broken config, a failed compile) must fail the
            // build loudly, not leave the backend running with a silently broken stack.
            if (proc.waitFor(250, TimeUnit.MILLISECONDS)) {
                process = null
                error(
                    "The $label dev server (${command.joinToString(" ")}) exited with code " +
                        "${proc.exitValue()}. See the [$label] output above."
                )
            }
            if (System.nanoTime() > deadline) {
                close()
                error(
                    "The $label dev server did not start listening on port $port within " +
                        "$READY_TIMEOUT_MINUTES minutes. Does the build script set its own port?"
                )
            }
        }
    }

    /** The preferred port when nothing holds it, otherwise one the OS picks. */
    private fun pickPort(preferred: Int): Int =
        try {
            ServerSocket(preferred).use { it.localPort }
        } catch (e: IOException) {
            ServerSocket(0).use { it.localPort }
        }

    /** Node binds `localhost` to whichever address the OS lists first, so every one is tried. */
    private fun listening(port: Int): Boolean =
        InetAddress.getAllByName("localhost").any { address ->
            try {
                Socket().use {
                    it.connect(InetSocketAddress(address, port), 200)
                    true
                }
            } catch (e: IOException) {
                false
            }
        }

    @Synchronized
    override fun close() {
        val proc = process ?: return
        process = null
        val label = parameters.label.get()
        val tree = proc.toHandle().descendants().toList() + proc.toHandle()
        tree.forEach { it.destroy() }
        if (proc.waitFor(3, TimeUnit.SECONDS)) {
            logger.lifecycle("Stopped the $label dev server")
        } else {
            tree.forEach { it.destroyForcibly() }
            logger.lifecycle("The $label dev server did not stop gracefully; force-killed it")
        }
    }
}
