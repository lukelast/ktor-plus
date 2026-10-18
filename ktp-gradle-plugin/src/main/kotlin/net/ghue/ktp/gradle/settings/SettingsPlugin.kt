package net.ghue.ktp.gradle.settings

import net.ghue.ktp.gradle.lukestack.LukestackPlugin
import net.ghue.ktp.gradle.project.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
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
        settings.rootDir
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") && it.name != "buildSrc" }
            .filter { it.resolve("build.gradle.kts").exists() || it.resolve("package.json").exists() }
            .forEach { settings.include(":${it.name}") }

        // Download missing JDKs on demand.
        settings.pluginManager.apply(FoojayToolchainsConventionPlugin::class.java)

        autoApplyPlugin(settings)
    }

    private fun autoApplyPlugin(settings: Settings) {
        // Avoid per-project plugin declarations.
        val pluginName = settings.providers.gradleProperty(AUTO_APPLY_KEY).getOrElse(DEFAULT_AUTO_APPLY)
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
