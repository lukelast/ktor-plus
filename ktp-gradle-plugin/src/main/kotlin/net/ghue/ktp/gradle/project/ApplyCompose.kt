package net.ghue.ktp.gradle.project

import org.gradle.api.Project
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin

/**
 * The Compose Multiplatform UI module: a [applyKmp] module plus the Compose Gradle plugin (the
 * `compose.*` dependency accessors, desktop packaging, hot reload) and the Compose compiler plugin,
 * which must match the Kotlin version on this plugin's classpath exactly and so is applied from
 * here rather than versioned by the app. detekt gains the Compose rule set.
 */
internal fun Project.applyCompose() {
    applyKmp(composeRules = true)
    pluginManager.apply(ComposePlugin::class.java)
    pluginManager.apply(ComposeCompilerGradleSubplugin::class.java)
}
