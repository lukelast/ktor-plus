package net.ghue.ktp.gradle.lukestack

import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

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
            inputs.file("package.json")
            inputs.files("bun.lock", "bun.lockb")
            // Not an output: fingerprinting node_modules costs more than rerunning install.
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

    listOf(lint, bundle, test).forEach { task -> task.configure { mustRunAfter(format) } }

    tasks.named<Delete>(LifecycleBasePlugin.CLEAN_TASK_NAME) { delete("dist") }
    tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { dependsOn(bundle) }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
        dependsOn(lint)
        dependsOn(test)
    }
    tasks.named("verify") { dependsOn(format) }
    registerOpenApiTypesTasks()
}
