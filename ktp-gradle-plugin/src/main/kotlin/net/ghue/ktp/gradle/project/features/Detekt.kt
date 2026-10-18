package net.ghue.ktp.gradle.project.features

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project

/** KTP's rules, shipped as a resource of this plugin. */
private const val KTP_RULES = "detekt.yml"

/** What Compose code honestly does differently, layered over [KTP_RULES]. */
private const val COMPOSE_RULES = "detekt-compose.yml"

/** A project's own overrides, beside its build script, layered last. */
private const val PROJECT_OVERRIDES = "detekt.yml"

/**
 * detekt with KTP's rules plus a `detekt.yml` beside the project's build script when it has one
 * (later files win). [allSources] runs the one `detekt` task over `src/` as a whole, without type
 * resolution: a multiplatform module has no `src/main`, and the per-compilation tasks would drag
 * every target's compile into linting. [composeRules] adds the Compose rule set and its config.
 */
internal fun Project.applyDetekt(allSources: Boolean = false, composeRules: Boolean = false) {
    plugins.apply(DetektPlugin::class.java)

    val ruleFiles = listOfNotNull(KTP_RULES, COMPOSE_RULES.takeIf { composeRules })
    val configDir = layout.buildDirectory.dir("detekt")
    // Copied out at build time so a KTP version bump carries rule changes to every consumer.
    val generateConfig =
        tasks.register("generateDetektConfig") {
            outputs.files(ruleFiles.map { configDir.map { dir -> dir.file(it) } })
            doLast {
                ruleFiles.forEach { name ->
                    val rules =
                        DetektPlugin::class.java.classLoader.getResourceAsStream(name)
                            ?: error("Could not find $name in resources")
                    val file = configDir.get().file(name).asFile
                    file.parentFile.mkdirs()
                    rules.use { file.writeBytes(it.readAllBytes()) }
                }
            }
        }

    extensions.configure(DetektExtension::class.java) {
        buildUponDefaultConfig.set(true)
        // One provider per file, in order: detekt applies later files over earlier ones.
        config.setFrom(ruleFiles.map { name -> generateConfig.map { configDir.get().file(name) } })
        val overrides = layout.projectDirectory.file(PROJECT_OVERRIDES)
        if (overrides.asFile.exists()) config.from(overrides)
        if (allSources) source.setFrom(layout.projectDirectory.dir("src"))
    }

    if (composeRules) {
        dependencies.add(
            "detektPlugins",
            "io.nlopez.compose.rules:detekt:${KtpVersion.COMPOSE_RULES}",
        )
    }
}
