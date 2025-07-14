package net.ghue.ktp.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigFileScannerKtTest {
    private val configFiles =
        listOf(
            "0.a.dev.conf",
            "0.a.test.conf",
            "0.conf",
            "9.config.conf",
            "10.a.conf",
            "20.a.conf",
            "100.a.env1.conf",
            "100.b.env1.conf",
            "100.a.env2.conf",
            "100.a.conf",
            "100.b.conf",
            "100.conf",
        )

    @Test
    fun `scanConfigFiles sorts files`() {
        val files = scanConfigFiles()
        assertEquals(configFiles, files.map { it.fileName })
    }

    @Test
    fun `filterForEnv for dev`() {
        val files = scanConfigFiles()
        assertEquals(
            configFiles.filter { !it.contains("env") }.filter { !it.contains("test") },
            files.filter { it.filterForEnv(Env("dev")) }.map { it.fileName },
        )
    }

    @Test
    fun `filterForEnv for env1`() {
        val files = scanConfigFiles()
        assertEquals(
            configFiles
                .filter { it != "0.a.dev.conf" }
                .filter { it != "0.a.test.conf" }
                .filter { it != "100.a.env2.conf" },
            files.filter { it.filterForEnv(Env("env1")) }.map { it.fileName },
        )
    }
}
