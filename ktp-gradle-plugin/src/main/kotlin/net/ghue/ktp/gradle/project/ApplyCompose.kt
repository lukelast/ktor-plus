package net.ghue.ktp.gradle.project

import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBrowserDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

/**
 * Compose Multiplatform artifacts; hot reload lives under this prefix too but has its own version.
 */
private const val COMPOSE_GROUP_PREFIX = "org.jetbrains.compose."
private const val HOT_RELOAD_GROUP = "org.jetbrains.compose.hot-reload"

/**
 * Gradle property naming the webpack dev-server port. The lukestack `run` task passes it to the
 * child build it starts for the Compose module; unset, webpack keeps its own default.
 */
internal const val DEV_SERVER_PORT_KEY = "ktp.devServerPort"

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

    configureWebDevServer()
}

/**
 * The webpack dev server behind the backend's dev proxy: it listens on the port the `run` task
 * hands over, and never opens a browser of its own, since the app is reached through the backend.
 * Registered after the build script so the handed-over port beats one the script sets; otherwise
 * the backend would wait on a port nothing listens to.
 */
private fun Project.configureWebDevServer() {
    val devPort =
        findProperty(DEV_SERVER_PORT_KEY)?.toString()?.let {
            it.toIntOrNull() ?: error("'$DEV_SERVER_PORT_KEY' must be a port number, got '$it'.")
        }
    afterEvaluate {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinJsIrTarget>().configureEach {
                subTargets.configureEach {
                    (this as? KotlinJsBrowserDsl)?.commonWebpackConfig {
                        devServer =
                            (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                                open = false
                                devPort?.let { port = it }
                            }
                    }
                }
            }
        }
    }
}
