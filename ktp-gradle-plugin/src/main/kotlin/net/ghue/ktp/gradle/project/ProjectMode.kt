package net.ghue.ktp.gradle.project

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
private const val MODE_KEY = "ktp.mode"

/** The page a Compose web target boots from; a shared library never has one. */
private val WEB_INDEX_PAGES =
    listOf("src/wasmJsMain/resources/index.html", "src/jsMain/resources/index.html")

internal fun Project.resolveProjectMode(): ProjectMode {
    // Without the property, detect from the directory layout, which is all a plugin applied before
    // the build script can see: the root of a multi-project build is the aggregation/deployment
    // root, a package.json is a frontend, a web index page in a multiplatform module is the
    // Compose UI, and any other common source set is a shared multiplatform library.
    val prop =
        findProperty(MODE_KEY)?.toString()
            ?: return when {
                this == rootProject && subprojects.isNotEmpty() -> ProjectMode.ROOT
                projectDir.resolve("package.json").exists() -> ProjectMode.FRONTEND
                WEB_INDEX_PAGES.any { projectDir.resolve(it).exists() } -> ProjectMode.COMPOSE
                projectDir.resolve("src/commonMain").exists() -> ProjectMode.KMP
                else -> DEFAULT_MODE
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
