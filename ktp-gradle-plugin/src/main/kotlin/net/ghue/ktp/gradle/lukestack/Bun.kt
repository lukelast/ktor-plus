package net.ghue.ktp.gradle.lukestack

import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Wraps the frontend's package.json scripts in Gradle tasks so the root lifecycle covers the
 * frontend too: `assemble` bundles, `check` verifies without writing anything, and `verify`
 * formats first — the same strict-check/fixing-verify split the JVM modules follow.
 *
 * Lukestack frontends are built with bun. The package.json script contract is:
 * - `dev`: dev server (started by the Vite dev-server integration, not a task here)
 * - `format`: Biome fix mode, mutates sources (`biome check --write src`)
 * - `lint`: strict Biome + TypeScript checks, never writes (`biome ci src && tsc --noEmit`)
 * - `build`: production bundle into `dist/`
 * - `test`: test suite
 */
internal fun Project.registerBunTasks() {
    // Everything except dependencies and outputs feeds the scripts: sources, public assets,
    // configs, .env files, helper scripts. A denylist keeps newly added files covered as inputs
    // without needing a plugin change.
    val sourceFiles =
        fileTree(projectDir) { exclude("node_modules/**", "dist/**", "build/**", ".gradle/**") }

    val install =
        tasks.register<Exec>("install") {
            description = "Installs dependencies."
            group = LifecycleBasePlugin.BUILD_GROUP
            workingDir = projectDir
            commandLine("bun", "install")
            inputs.file("package.json")
            inputs.files("bun.lock", "bun.lockb")
            // Deliberately not an output directory: Gradle would fingerprint the tens of
            // thousands of files in node_modules before and after every run. Directory
            // existence plus the manifest inputs above decide whether install must rerun.
            val nodeModules = projectDir.resolve("node_modules")
            outputs.upToDateWhen { nodeModules.isDirectory }
        }

    val format =
        tasks.register<Exec>("format") {
            description = "Formats and auto-fixes the frontend sources with Biome."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "format")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.upToDateWhen { true }
        }

    val lint =
        tasks.register<Exec>("lint") {
            description = "Runs strict Biome + TypeScript checks without modifying sources."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "lint")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.upToDateWhen { true }
        }

    val bundle =
        tasks.register<Exec>("bundle") {
            description = "Builds the production bundle into dist/."
            group = LifecycleBasePlugin.BUILD_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "build")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.dir("dist")
        }

    val test =
        tasks.register<Exec>("test") {
            description = "Runs the frontend tests."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "test")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.upToDateWhen { true }
        }

    // Ordering only, never dependencies: plain `check` stays a strict verifier that runs no
    // formatter, while `verify` (format + check in one task graph) is race-free because the
    // source-reading tasks wait for the formatter when both are scheduled.
    listOf(lint, bundle, test).forEach { task -> task.configure { mustRunAfter(format) } }

    tasks.named<Delete>(LifecycleBasePlugin.CLEAN_TASK_NAME) { delete("dist") }
    tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { dependsOn(bundle) }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
        dependsOn(lint)
        dependsOn(test)
    }
    tasks.named("verify") { dependsOn(format) }
}
