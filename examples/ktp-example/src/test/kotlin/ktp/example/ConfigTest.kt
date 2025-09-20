package ktp.example

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import net.ghue.ktp.config.scanConfigFiles

class ConfigTest :
    StringSpec({
        "scanConfigFiles reads all example config files" {
            val files = scanConfigFiles()
            val demoFiles = files.filter { it.fileName.contains("example") }
            demoFiles.map { it.fileName } shouldBe listOf("9.example.conf")
        }
    })
