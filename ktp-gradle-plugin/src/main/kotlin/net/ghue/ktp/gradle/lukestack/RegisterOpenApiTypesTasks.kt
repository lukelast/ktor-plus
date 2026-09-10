package net.ghue.ktp.gradle.lukestack

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.process.CommandLineArgumentProvider

/** Run via bunx: it needs TypeScript 5, which cannot coexist with the app's TypeScript. */
private const val GENERATOR = "openapi-typescript@7.13.0"
private const val SCHEMA = "src/api/schema.d.ts"

internal fun Project.registerOpenApiTypesTasks() {
    if (!openApiEnabled()) {
        return
    }
    // The export task must already be registered on the backend project.
    val backend = evaluationDependsOn(":backend")
    val openApiExport = backend.tasks.named(OPEN_API_EXPORT_TASK)
    val spec = backend.openApiSpec
    val schema = layout.projectDirectory.file(SCHEMA).asFile
    val arguments = CommandLineArgumentProvider {
        listOf(spec.get().asFile.path, "-o", schema.path)
    }
    val generate =
        tasks.register<Exec>("apiGenerate") {
            group = LifecycleBasePlugin.BUILD_GROUP
            description = "Generates $SCHEMA from the backend's OpenAPI export."
            commandLine("bunx", GENERATOR)
            argumentProviders.add(arguments)
            inputs.files(openApiExport)
            outputs.file(schema)
        }
    val check =
        tasks.register<Exec>("apiCheck") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Checks that $SCHEMA matches the backend's OpenAPI export."
            commandLine("bunx", GENERATOR)
            argumentProviders.add(arguments)
            argumentProviders.add(CommandLineArgumentProvider { listOf("--check") })
            mustRunAfter(generate)
            inputs.files(openApiExport, schema)
            outputs.upToDateWhen { true }
        }
    tasks.named("check") { dependsOn(check) }
    listOf("format", "lint", "test", "bundle").forEach { name ->
        tasks.named(name) { mustRunAfter(generate) }
    }
}
