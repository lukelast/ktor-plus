package net.ghue.ktp.gradle.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopRunExclusionsTest {

    private val root = Files.createTempDirectory("ktp-settings").toFile()

    private fun module(name: String, vararg markers: String): File =
        root.resolve(name).also { dir ->
            dir.mkdirs()
            markers.forEach { dir.resolve(it).apply { parentFile.mkdirs() }.writeText("") }
        }

    private val backend = module("backend", "build.gradle.kts")
    private val compose =
        module(
            "composeApp",
            "build.gradle.kts",
            "src/wasmJsMain/resources/index.html",
            "src/desktopMain/kotlin/main.kt",
        )

    @Test
    fun `a bare run excludes the Compose desktop run`() {
        assertEquals(
            listOf(":composeApp:run"),
            desktopRunExclusions(listOf("run"), listOf(backend, compose), true),
        )
    }

    @Test
    fun `a jvmMain source set is a desktop target too`() {
        val jvmCompose =
            module(
                "webApp",
                "build.gradle.kts",
                "src/wasmJsMain/resources/index.html",
                "src/jvmMain/kotlin/main.kt",
            )
        assertEquals(
            listOf(":webApp:run"),
            desktopRunExclusions(listOf("run"), listOf(backend, jvmCompose), true),
        )
    }

    @Test
    fun `a web-only Compose module has no desktop run to exclude`() {
        val webOnly = module("webApp", "build.gradle.kts", "src/wasmJsMain/resources/index.html")
        assertEquals(
            emptyList(),
            desktopRunExclusions(listOf("run"), listOf(backend, webOnly), true),
        )
    }

    @Test
    fun `an explicit task path or another task excludes nothing`() {
        assertEquals(
            emptyList(),
            desktopRunExclusions(listOf(":backend:run"), listOf(backend, compose), true),
        )
        assertEquals(
            emptyList(),
            desktopRunExclusions(listOf("check"), listOf(backend, compose), true),
        )
    }

    @Test
    fun `nothing is excluded when no dev server starts with run`() {
        assertEquals(
            emptyList(),
            desktopRunExclusions(listOf("run"), listOf(backend, compose), false),
        )
    }

    @Test
    fun `a frontend directory takes the dev server, so the desktop run stays`() {
        val frontend = module("frontend", "package.json")
        assertEquals(
            emptyList(),
            desktopRunExclusions(listOf("run"), listOf(backend, compose, frontend), true),
        )
    }
}
