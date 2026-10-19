package net.ghue.ktp.gradle.project.features

import net.ghue.ktp.lib.KtpLibs
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.CompileUsingKotlinDaemon
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin

/**
 * Applies Kotlin JVM plus `ktp-bom` on [bomConfiguration], so any KTP-managed dependency is written
 * without a version.
 */
internal fun Project.applyKotlin(
    bomConfiguration: String = JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
) {
    // The main kotlin JVM gradle plugin: org.jetbrains.kotlin.jvm
    // https://github.com/JetBrains/kotlin/blob/ce97a8357f385448c313bd563109bd09b525986a/libraries/tools/kotlin-gradle-plugin/build.gradle.kts#L236-L241
    pluginManager.apply(KotlinPluginWrapper::class.java)
    dependencies.add(bomConfiguration, dependencies.platform(KtpLibs.bom))
    applySerialization()
    configureKotlinCompileOptions()
}

internal fun Project.applyKotlinMultiplatform() {
    // https://github.com/JetBrains/kotlin/blob/ce97a8357f385448c313bd563109bd09b525986a/libraries/tools/kotlin-gradle-plugin/build.gradle.kts#L248-L253
    pluginManager.apply(KotlinMultiplatformPluginWrapper::class.java)
    extensions.configure<KotlinMultiplatformExtension> {
        // `api` so consumers of this module resolve its versionless deps the same way.
        sourceSets
            .named { it == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME }
            .configureEach { dependencies { api(project.dependencies.platform(KtpLibs.bom)) } }
    }
    applySerialization()
    configureKotlinCompileOptions()
}

private fun Project.applySerialization() {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-serialization/build.gradle.kts
    pluginManager.apply(SerializationGradleSubplugin::class.java)
}

/**
 * Heap for the Kotlin compile daemon, which every build on a machine shares. Its 512m default runs
 * out after a few full app compiles in a row, and a Compose/Wasm production compile needs more than
 * that on its own. One value everywhere, so all KTP builds reuse a single daemon.
 */
private const val KOTLIN_DAEMON_HEAP = "-Xmx2g"

private fun Project.configureKotlinCompileOptions() {
    project.tasks.withType<KotlinCompile>().configureEach {
        // Strict nullability for Java types; add, not set, so -Xjdk-release and app flags survive.
        compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
    }
    project.tasks.withType<CompileUsingKotlinDaemon>().configureEach {
        kotlinDaemonJvmArguments.add(KOTLIN_DAEMON_HEAP)
    }
}
