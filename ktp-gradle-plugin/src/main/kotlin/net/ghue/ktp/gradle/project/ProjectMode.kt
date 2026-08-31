package net.ghue.ktp.gradle.project

import org.gradle.api.Project

enum class ProjectMode {
    LIBRARY,
    KTOR,
    FRONTEND,
    ROOT,
}

private val DEFAULT_MODE = ProjectMode.KTOR
private const val MODE_KEY = "ktp.mode"

fun Project.resolveProjectMode(): ProjectMode {
    val prop = findProperty(MODE_KEY)?.toString()
        ?: // Auto-detect: the root of a multi-project build is the aggregation/deployment root,
        // and a project with a package.json is a frontend. Everywhere else the default applies.
        return when {
            this == rootProject && subprojects.isNotEmpty() -> ProjectMode.ROOT
            projectDir.resolve("package.json").exists() -> ProjectMode.FRONTEND
            else -> DEFAULT_MODE
        }
    val mode =
        try {
            ProjectMode.valueOf(prop.uppercase())
        } catch (e: IllegalArgumentException) {
            error(
                "File 'gradle.properties', field '${MODE_KEY}', has invalid value '$prop'. " +
                    "Valid values are: ${
                        ProjectMode.entries
                            .filter { it != ProjectMode.ROOT }
                            .joinToString(", ") { it.name.lowercase() }
                    } ('root' is detected automatically). " +
                    "Default value is '${DEFAULT_MODE.name.lowercase()}'."
            )
        }
    if (mode == ProjectMode.ROOT && this != rootProject) {
        // Values in the root gradle.properties propagate to every subproject, so an explicit
        // root marker there would silently turn app/library projects into root projects.
        error(
            "'$MODE_KEY=root' is set for non-root project '$path'. Root mode is detected " +
                "automatically; remove the property."
        )
    }
    return mode
}
