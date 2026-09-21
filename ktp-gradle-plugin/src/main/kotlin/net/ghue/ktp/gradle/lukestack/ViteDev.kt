package net.ghue.ktp.gradle.lukestack

import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import net.ghue.ktp.gradle.project.booleanProperty
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.named

private const val ENABLED_KEY = "ktp.vite"
private const val FRONTEND_DIR = "frontend"

/** Read by ktp-ktor's `ViteFrontendConfig.vitePort`, the dev proxy target. */
internal const val VITE_PORT_ENV = "KTP_VITE_PORT"

/**
 * Starts the frontend dev server (`bun run dev`, typically Vite) alongside the Ktor `run` task,
 * so a single `gradlew run` brings up the full dev stack. The dev server gets a free port on each
 * run, handed to the backend as `KTP_VITE_PORT`, so any number of apps run at once.
 *
 * The frontend always lives in `<root>/frontend`; this is a KTP convention, not configurable.
 * Enabled automatically when that directory contains a `package.json`. Configured through
 * `gradle.properties`:
 * - `ktp.vite`: `false` disables it (run the dev server yourself on Vite's default 5173), `true`
 *   requires it (the build fails if no frontend is found).
 */
internal fun Project.applyViteDev() {
    val forced = booleanProperty(ENABLED_KEY)
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

    val service =
        gradle.sharedServices.registerIfAbsent("ktpViteDev", ViteDevService::class.java) {
            parameters.frontendDir.set(frontendDir)
            parameters.command.set(listOf("bun", "run", "dev"))
        }

    tasks.named<JavaExec>("run") {
        usesService(service)
        // On a fresh clone the dev server would die instantly on missing node_modules; the
        // install task only exists when the frontend is included as a project in this build.
        if (findProject(":$FRONTEND_DIR") != null) {
            dependsOn(":$FRONTEND_DIR:install")
        }
        doFirst { environment(VITE_PORT_ENV, service.get().start()) }
    }
}

/**
 * Runs the Vite dev server as a shared build service so Gradle closes it when the build ends. The
 * daemon runs [close] even on Ctrl+C / IDE stop, and killing the full process tree is required on
 * Windows where terminating the `.cmd` shim alone would orphan the node process under it.
 */
abstract class ViteDevService : BuildService<ViteDevService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val frontendDir: DirectoryProperty
        val command: ListProperty<String>
    }

    private val logger = Logging.getLogger(ViteDevService::class.java)
    private var process: Process? = null
    private var port = 0

    /** Starts the dev server unless this build already did, and returns the port it listens on. */
    @Synchronized
    fun start(): Int {
        if (process?.isAlive == true) return port
        // The OS picks a port nothing holds. Strict, so losing the tiny race between this probe
        // closing and Vite binding fails the build instead of moving Vite off the proxied port.
        port = ServerSocket(0).use { it.localPort }
        val command = parameters.command.get() + listOf("--port", "$port", "--strictPort")
        val proc =
            ProcessBuilder(command)
                .directory(parameters.frontendDir.get().asFile)
                .redirectErrorStream(true)
                .start()
        process = proc
        Thread { proc.inputStream.bufferedReader().forEachLine { logger.lifecycle("[vite] $it") } }
            .apply {
                isDaemon = true
                start()
            }
        // A dev server that dies on startup (bad script, broken config) must fail the build
        // loudly, not leave the backend running with a silently broken stack. A healthy server
        // outlives this wait; the cost is a one-time pause while the backend is launching.
        if (proc.waitFor(2, TimeUnit.SECONDS)) {
            process = null
            error(
                "The dev server (${command.joinToString(" ")}) exited immediately with code " +
                    "${proc.exitValue()}. See the [vite] output above."
            )
        }
        return port
    }

    @Synchronized
    override fun close() {
        val proc = process ?: return
        process = null
        val tree = proc.toHandle().descendants().toList() + proc.toHandle()
        tree.forEach { it.destroy() }
        if (proc.waitFor(3, TimeUnit.SECONDS)) {
            logger.lifecycle("Stopped Vite dev server")
        } else {
            tree.forEach { it.destroyForcibly() }
            logger.lifecycle("Vite dev server did not stop gracefully; force-killed it")
        }
    }
}
