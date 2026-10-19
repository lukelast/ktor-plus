package net.ghue.ktp.gradle.settings

import java.io.File
import net.ghue.ktp.gradle.lukestack.DEV_SERVER_ENABLED_KEY
import net.ghue.ktp.gradle.lukestack.FRONTEND_DIR
import net.ghue.ktp.gradle.lukestack.LukestackPlugin
import net.ghue.ktp.gradle.project.PACKAGE_JSON
import net.ghue.ktp.gradle.project.ProjectMode
import net.ghue.ktp.gradle.project.ProjectPlugin
import net.ghue.ktp.gradle.project.layoutMode
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin

private const val AUTO_APPLY_KEY = "ktp.plugin"

// Every real consumer is a lukestack app; the generic plugin is the opt-in.
private const val DEFAULT_AUTO_APPLY = "lukestack"

private const val ROOT_NAME_KEY = "rootProject.name"

class SettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        // Keep the project name stable across checkout directories.
        settings.providers.gradleProperty(ROOT_NAME_KEY).orNull?.let {
            settings.rootProject.name = it
        }

        // Include conventional frontends without requiring a Gradle build file.
        val projectDirs =
            settings.rootDir
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && !it.name.startsWith(".") && it.name != "buildSrc" }
                .filter {
                    it.resolve("build.gradle.kts").exists() || it.resolve(PACKAGE_JSON).exists()
                }
        projectDirs.forEach { settings.include(":${it.name}") }

        val pluginName =
            settings.providers.gradleProperty(AUTO_APPLY_KEY).getOrElse(DEFAULT_AUTO_APPLY)
        val excluded =
            desktopRunExclusions(
                taskNames = settings.startParameter.taskNames,
                projectDirs = projectDirs,
                devServerStartsWithRun =
                    pluginName == "lukestack" &&
                        settings.providers.gradleProperty(DEV_SERVER_ENABLED_KEY).orNull != "false",
            )
        if (excluded.isNotEmpty()) {
            settings.startParameter.setExcludedTaskNames(
                settings.startParameter.excludedTaskNames + excluded
            )
        }

        // Download missing JDKs on demand.
        settings.pluginManager.apply(FoojayToolchainsConventionPlugin::class.java)

        autoApplyPlugin(settings, pluginName)
    }

    private fun autoApplyPlugin(settings: Settings, pluginName: String) {
        // Avoid per-project plugin declarations.
        val plugin =
            when (pluginName) {
                "ktp" -> ProjectPlugin::class.java
                "lukestack" -> LukestackPlugin::class.java
                else ->
                    error(
                        "File 'gradle.properties', field '$AUTO_APPLY_KEY', has invalid value " +
                            "'$pluginName'. Valid values are: ktp, lukestack (the default)."
                    )
            }
        settings.gradle.lifecycle.beforeProject { pluginManager.apply(plugin) }
    }
}

/**
 * The Compose desktop `run` tasks a bare `gradlew run` must leave out, as `-x` would. That `run`
 * means the backend, whose lukestack `run` starts the Compose module's web dev server; by task-name
 * matching it would also launch the desktop window and compile the module while the dev-server
 * build does. Nothing is excluded when no dev server starts with `run` (the generic plugin, or
 * `ktp.vite=false`) or when a `frontend/` takes it instead, and `gradlew :composeApp:run` still
 * runs the desktop app. Detection is by layout only, as everything at settings time is.
 */
internal fun desktopRunExclusions(
    taskNames: List<String>,
    projectDirs: List<File>,
    devServerStartsWithRun: Boolean,
): List<String> {
    if (!devServerStartsWithRun || taskNames.none { it == ApplicationPlugin.TASK_RUN_NAME }) {
        return emptyList()
    }
    if (projectDirs.any { it.name == FRONTEND_DIR && layoutMode(it) == ProjectMode.FRONTEND }) {
        return emptyList()
    }
    return projectDirs
        .filter { layoutMode(it) == ProjectMode.COMPOSE }
        .map { ":${it.name}:${ApplicationPlugin.TASK_RUN_NAME}" }
}
