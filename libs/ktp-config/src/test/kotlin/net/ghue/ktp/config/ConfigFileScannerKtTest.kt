package net.ghue.ktp.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigFileScannerKtTest {
    private val configFiles =
        listOf(
                "0.a.dev",
                "0.a.test",
                "0",
                "1.a.a",
                "1.b.a",
                "1.b.b",
                "1.a",
                "1.b",
                "2.a",
                "9.config",
            )
            .map { "$it.conf" }

    @Test
    fun `scanConfigFiles sorts files`() {
        val files = scanConfigFiles()
        assertEquals(configFiles, files.map { it.fileName })
    }

    @Test
    fun `filterForEnv for env1`() {
        val files = scanConfigFiles()
        assertEquals(
            listOf("0", "1.a.a", "1.b.a", "1.a", "1.b", "2.a", "9.config").map { "$it.conf" },
            files.filter { it.filterForEnv(Env("a")) }.map { it.fileName },
        )
    }
}
