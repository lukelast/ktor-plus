package net.ghue.ktp.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

class ConfigFileScannerKtTest :
    StringSpec({
        val configFiles =
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

        "scanConfigFiles sorts files" {
            val files = scanConfigFiles()
            files.map { it.fileName }.shouldContainExactly(configFiles)
        }

        "filterForEnv filters by environment name" {
            val files = scanConfigFiles()
            val expected =
                listOf("0", "1.a.a", "1.b.a", "1.a", "1.b", "2.a", "9.config").map { "$it.conf" }

            files
                .filter { it.filterForEnv(Env("a")) }
                .map { it.fileName }
                .shouldContainExactly(expected)
        }
    })
