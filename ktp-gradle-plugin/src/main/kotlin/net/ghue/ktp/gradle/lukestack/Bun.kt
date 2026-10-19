package net.ghue.ktp.gradle.lukestack

import net.ghue.ktp.gradle.project.PACKAGE_JSON
import net.ghue.ktp.gradle.project.features.VERIFY_TASK
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

private const val FORMAT_TASK = "format"
private const val LINT_TASK = "lint"
private const val BUNDLE_TASK = "bundle"
private const val TEST_TASK = "test"

/** The bun tasks that read the frontend sources. */
internal val BUN_SOURCE_TASKS = listOf(FORMAT_TASK, LINT_TASK, TEST_TASK, BUNDLE_TASK)

/** Calls to the frontend's package.json. */
internal fun Project.registerBunTasks() {
    val sourceFiles =
        fileTree(projectDir) { exclude("node_modules/**", "dist/**", "build/**", ".gradle/**") }

    val install =
        tasks.register<Exec>("install") {
            description = "Installs dependencies."
            group = LifecycleBasePlugin.BUILD_GROUP
            workingDir = projectDir
            commandLine("bun", "install")
            inputs.file(PACKAGE_JSON)
            inputs.files("bun.lock", "bun.lockb")
            // Not an output: fingerprinting node_modules costs more than rerunning install.
            val nodeModules = projectDir.resolve("node_modules")
            outputs.upToDateWhen { nodeModules.isDirectory }
        }

    val format =
        tasks.register<Exec>(FORMAT_TASK) {
            description = "Formats and auto-fixes the frontend sources with Biome."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "format")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.upToDateWhen { true }
        }

    val lint =
        tasks.register<Exec>(LINT_TASK) {
            description = "Runs strict Biome + TypeScript checks without modifying sources."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "lint")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.upToDateWhen { true }
        }

    val bundle =
        tasks.register<Exec>(BUNDLE_TASK) {
            description = "Builds the production bundle into dist/."
            group = LifecycleBasePlugin.BUILD_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "build")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.dir("dist")
        }

    val test =
        tasks.register<Exec>(TEST_TASK) {
            description = "Runs the frontend tests."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            workingDir = projectDir
            commandLine("bun", "run", "test")
            dependsOn(install)
            inputs.files(sourceFiles)
            outputs.upToDateWhen { true }
        }

    listOf(lint, bundle, test).forEach { task -> task.configure { mustRunAfter(format) } }

    tasks.named<Delete>(LifecycleBasePlugin.CLEAN_TASK_NAME) { delete("dist") }
    tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { dependsOn(bundle) }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
        dependsOn(lint)
        dependsOn(test)
    }
    tasks.named(VERIFY_TASK) { dependsOn(format) }
    registerOpenApiTypesTasks()
}
