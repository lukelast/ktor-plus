package net.ghue.ktp.gradle.project.mods

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin

fun Project.applyKotlin() {
    // The main kotlin JVM gradle plugin: org.jetbrains.kotlin.jvm
    // https://github.com/JetBrains/kotlin/blob/ce97a8357f385448c313bd563109bd09b525986a/libraries/tools/kotlin-gradle-plugin/build.gradle.kts#L236-L241
    pluginManager.apply(KotlinPluginWrapper::class.java)
    applySerialization()
    applyKotlinCompileOptions()
}

fun Project.applyKotlinMultiplatform() {
    // https://github.com/JetBrains/kotlin/blob/ce97a8357f385448c313bd563109bd09b525986a/libraries/tools/kotlin-gradle-plugin/build.gradle.kts#L248-L253
    pluginManager.apply(KotlinMultiplatformPluginWrapper::class.java)

    applySerialization()
    applyKotlinCompileOptions()
}

fun Project.applySerialization() {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-serialization/build.gradle.kts
    pluginManager.apply(SerializationGradleSubplugin::class.java)
}

fun Project.applyKotlinCompileOptions() {
    project.tasks.withType<KotlinCompile>().configureEach {
        // the "-Xjsr305=strict" option enables strict nullability checks for java types.
        compilerOptions { freeCompilerArgs.set(listOf("-Xjsr305=strict")) }
    }
}
