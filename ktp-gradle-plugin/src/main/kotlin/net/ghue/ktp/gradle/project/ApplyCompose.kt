package net.ghue.ktp.gradle.project

import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin

/** Compose Multiplatform artifacts; hot reload lives under this prefix too but has its own version. */
private const val COMPOSE_GROUP_PREFIX = "org.jetbrains.compose."
private const val HOT_RELOAD_GROUP = "org.jetbrains.compose.hot-reload"

/**
 * The Compose Multiplatform UI module: a [applyKmp] module plus the Compose Gradle plugin (desktop
 * packaging, hot reload, `compose.desktop.currentOs`) and the Compose compiler plugin, which must
 * match the Kotlin version on this plugin's classpath exactly and so is applied from here rather
 * than versioned by the app. detekt gains the Compose rule set.
 */
internal fun Project.applyCompose() {
    applyKmp(composeRules = true)
    pluginManager.apply(ComposePlugin::class.java)
    pluginManager.apply(ComposeCompilerGradleSubplugin::class.java)

    // Compose ships no BOM and deprecated the plugin's `compose.*` accessors, so this is what lets
    // an app write `implementation("org.jetbrains.compose.ui:ui")` and still follow KTP's version.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val group = requested.group
            if (
                group.startsWith(COMPOSE_GROUP_PREFIX) &&
                    group != HOT_RELOAD_GROUP &&
                    requested.version.isNullOrEmpty()
            ) {
                useVersion(KtpVersion.COMPOSE)
                because("KTP compose mode pins Compose Multiplatform")
            }
        }
    }
}
