package net.ghue.ktp.gradle.lukestack

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevServerTest {

    @Test
    fun `the compose child build runs the dev-server task continuously with the port property`() {
        assertEquals(
            listOf(
                File("/repo/gradlew").path,
                ":composeApp:wasmJsBrowserDevelopmentRun",
                "--continuous",
                "--console=plain",
                "-Pktp.devServerPort={port}",
            ),
            composeDevServerCommand(":composeApp", File("/repo"), isWindows = false),
        )
    }

    @Test
    fun `on Windows the wrapper runs through cmd`() {
        assertEquals(
            listOf("cmd", "/c", File("/repo/gradlew.bat").path),
            composeDevServerCommand(":composeApp", File("/repo"), isWindows = true).take(3),
        )
    }

    @Test
    fun `the preferred port is stable per checkout and inside the chosen range`() {
        val a = preferredPort(File("/repo/a"))
        assertEquals(a, preferredPort(File("/repo/a")))
        assertTrue(a in 20000 until 40000, "port $a")
    }
}
