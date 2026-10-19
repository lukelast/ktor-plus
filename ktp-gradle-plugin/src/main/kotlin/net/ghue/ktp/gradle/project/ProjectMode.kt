package net.ghue.ktp.gradle.project

import java.io.File
import org.gradle.api.Project

internal enum class ProjectMode {
    LIBRARY,
    KTOR,
    FRONTEND,
    /** A Kotlin Multiplatform library shared by the backend and the UI targets. */
    KMP,
    /** The Kotlin Multiplatform module that is the UI: Compose, with a browser executable. */
    COMPOSE,
    ROOT,
}

private val DEFAULT_MODE = ProjectMode.KTOR
internal const val MODE_KEY = "ktp.mode"

/** What marks a directory as a frontend project. */
internal const val PACKAGE_JSON = "package.json"

/** The page a Compose web target boots from; a shared library never has one. */
private val WEB_INDEX_PAGES =
    listOf("src/wasmJsMain/resources/index.html", "src/jsMain/resources/index.html")

/**
 * The mode a project directory's layout implies, which is all a plugin applied before the build
 * script can see: a package.json is a frontend, a web index page in a multiplatform module is the
 * Compose UI, any other common source set is a shared multiplatform library. Null when the layout
 * says nothing (a plain JVM module).
 */
internal fun layoutMode(projectDir: File): ProjectMode? =
    when {
        projectDir.resolve(PACKAGE_JSON).exists() -> ProjectMode.FRONTEND
        WEB_INDEX_PAGES.any { projectDir.resolve(it).exists() } -> ProjectMode.COMPOSE
        projectDir.resolve("src/commonMain").exists() -> ProjectMode.KMP
        else -> null
    }

internal fun Project.resolveProjectMode(): ProjectMode {
    // Without the property, the root of a multi-project build is the aggregation/deployment root
    // and everything else goes by its layout.
    val prop =
        findProperty(MODE_KEY)?.toString()
            ?: return when {
                this == rootProject && subprojects.isNotEmpty() -> ProjectMode.ROOT
                else -> layoutMode(projectDir) ?: DEFAULT_MODE
            }
    val mode =
        try {
            ProjectMode.valueOf(prop.uppercase())
        } catch (_: IllegalArgumentException) {
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
