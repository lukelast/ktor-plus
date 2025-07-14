package net.ghue.ktp.gradle.project

import org.gradle.api.Project

enum class ProjectMode {
    LIBRARY,
    KTOR,
}

private val PROJECT_TYPE_DEFAULT = ProjectMode.LIBRARY
private const val MODE_KEY = "ktp.mode"

fun Project.findProjectMode(): ProjectMode {
    val prop = findProperty(MODE_KEY)?.toString() ?: PROJECT_TYPE_DEFAULT.name
    try {
        return ProjectMode.valueOf(prop.uppercase())
    } catch (e: IllegalArgumentException) {
        error(
            "File 'gradle.properties', field '${MODE_KEY}', has invalid value '$prop'. " +
                "Valid values are: ${ProjectMode.values().joinToString(", ") { it.name.lowercase() }}. " +
                "Default value is '${PROJECT_TYPE_DEFAULT.name.lowercase()}'."
        )
    }
}
