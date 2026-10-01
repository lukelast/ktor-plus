package net.ghue.ktp.gradle.lukestack

import io.ktor.plugin.features.KtorExtension
import io.ktor.plugin.features.OpenApiExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

private const val EXPORT_TEST = "*OpenApiExportTest"

internal fun Project.configureKtpOpenApi() {
    if (!openApiEnabled()) {
        return
    }
    tasks.named<Test>(JavaPlugin.TEST_TASK_NAME) { filter { excludeTestsMatching(EXPORT_TEST) } }
    val ktor = extensions.getByType<KtorExtension>() as ExtensionAware
    ktor.extensions.configure<OpenApiExtension> { enabled.set(true) }

    val testSourceSet =
        extensions.getByType<SourceSetContainer>().named(SourceSet.TEST_SOURCE_SET_NAME).get()
    val spec = openApiSpec
    tasks.register<Test>(OPEN_API_EXPORT_TASK) {
        description = "Runs OpenApiExportTest to write the OpenAPI contract into build/openapi/."
        group = LifecycleBasePlugin.BUILD_GROUP
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        filter { includeTestsMatching(EXPORT_TEST) }
        outputs.file(spec)
        doFirst { spec.get().asFile.delete() }
        doLast {
            check(spec.get().asFile.isFile) { "OpenApiExportTest did not write the contract." }
        }
    }
}
