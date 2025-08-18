package ktp.example

import net.ghue.ktp.config.scanConfigFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigTest {

    @Test
    fun `scanConfigFiles should read all config files`() {
        val files = scanConfigFiles()
        // Filter to only files from the demo resources
        val demoFiles = files.filter { it.fileName.contains("example") }
        assertEquals(listOf("9.example.conf"), demoFiles.map { it.fileName })
    }
}
