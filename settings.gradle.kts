/*
 * https://docs.gradle.org/current/userguide/intro_multi_project_builds.html
 */
pluginManagement { repositories { gradlePluginPortal() } }

rootProject.name = "ktp"
// The directory containing all the library projects.
val libsDir = "libs"

includeBuild("ktp-gradle-plugin")

/** Every directory in [libsDir] is included as a project. */
rootDir
    .resolve(libsDir)
    .listFiles()
    ?.filter { it.isDirectory }
    ?.forEach { include("$libsDir:${it.name}") }

include("examples:ktp-demo")
