package net.ghue.ktp.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ConfigFileTest :
    StringSpec({
        "config files sort by priority first" {
            val files =
                listOf(
                        fakeConfig(9, name = "z", text = """v=9"""),
                        fakeConfig(5, name = "a", text = """v=5"""),
                        fakeConfig(1, name = "m", text = """v=1"""),
                    )
                    .shuffled()

            val sorted = files.sorted()
            sorted.map { it.priority }.shouldContainExactly(listOf(1, 5, 9))
        }

        "config files prefer environment-specific variants for equal priority" {
            val files =
                listOf(
                        fakeConfig(5, name = "config", env = "", text = """v=no_env"""),
                        fakeConfig(5, name = "config", env = "prod", text = """v=prod"""),
                    )
                    .shuffled()

            val sorted = files.sorted()
            sorted[0].envName shouldBe "prod"
            sorted[1].envName shouldBe ""
        }

        "config files prefer named variants over unnamed ones" {
            val files =
                listOf(
                        fakeConfig(5, name = "", env = "", text = """v=no_name"""),
                        fakeConfig(5, name = "config", env = "", text = """v=with_name"""),
                    )
                    .shuffled()

            val sorted = files.sorted()
            sorted[0].name shouldBe "config"
            sorted[1].name shouldBe ""
        }

        "config files sort names alphabetically when all else matches" {
            val files =
                listOf(
                        fakeConfig(5, name = "zebra", env = "", text = """v=zebra"""),
                        fakeConfig(5, name = "apple", env = "", text = """v=apple"""),
                        fakeConfig(5, name = "banana", env = "", text = """v=banana"""),
                    )
                    .shuffled()

            val sorted = files.sorted()
            sorted.map { it.name }.shouldContainExactly(listOf("apple", "banana", "zebra"))
        }

        "config files sort by priority, env, and name in sequence" {
            val files =
                listOf(
                        fakeConfig(8, name = "config", env = "dev", text = """v=8_config_dev"""),
                        fakeConfig(8, name = "config", env = "", text = """v=8_config_all"""),
                        fakeConfig(3, name = "config", env = "dev", text = """v=3_config_dev"""),
                        fakeConfig(3, name = "config", env = "", text = """v=3_config_all"""),
                        fakeConfig(3, name = "", env = "", text = """v=3_no_name"""),
                    )
                    .shuffled()

            val sorted = files.sorted()

            sorted[0].priority shouldBe 3
            sorted[0].envName shouldBe "dev"
            sorted[0].name shouldBe "config"

            sorted[1].priority shouldBe 3
            sorted[1].envName shouldBe ""
            sorted[1].name shouldBe "config"

            sorted[2].priority shouldBe 3
            sorted[2].envName shouldBe ""
            sorted[2].name shouldBe ""
        }

        "config files sort environment names alphabetically" {
            val files =
                listOf(
                        fakeConfig(5, name = "config", env = "zebra", text = """v=zebra"""),
                        fakeConfig(5, name = "config", env = "apple", text = """v=apple"""),
                        fakeConfig(5, name = "config", env = "banana", text = """v=banana"""),
                    )
                    .shuffled()

            val sorted = files.sorted()
            sorted.map { it.envName }.shouldContainExactly(listOf("apple", "banana", "zebra"))
        }

        "config files respect the overall sorting contract" {
            val files =
                listOf(
                        fakeConfig(9, name = "z", env = "prod", text = """v=9_z_prod"""),
                        fakeConfig(2, name = "a", env = "dev", text = """v=2_a_dev"""),
                        fakeConfig(2, name = "a", env = "", text = """v=2_a_all"""),
                        fakeConfig(2, name = "", env = "test", text = """v=2_no_name_test"""),
                        fakeConfig(2, name = "", env = "", text = """v=2_no_name_all"""),
                        fakeConfig(6, name = "b", env = "staging", text = """v=6_b_staging"""),
                    )
                    .shuffled()

            val sorted = files.sorted()

            sorted[0].priority shouldBe 2
            sorted[0].envName shouldBe "dev"
            sorted[0].name shouldBe "a"

            sorted[1].priority shouldBe 2
            sorted[1].envName shouldBe "test"
            sorted[1].name shouldBe ""

            sorted[2].priority shouldBe 2
            sorted[2].envName shouldBe ""
            sorted[2].name shouldBe "a"

            sorted[3].priority shouldBe 2
            sorted[3].envName shouldBe ""
            sorted[3].name shouldBe ""

            sorted[4].priority shouldBe 6
            sorted[4].envName shouldBe "staging"
            sorted[4].name shouldBe "b"

            sorted[5].priority shouldBe 9
            sorted[5].envName shouldBe "prod"
            sorted[5].name shouldBe "z"
        }

        "config file name sorting matches expected sequence" {
            val expected =
                listOf(
                    "0.app.dev.conf",
                    "0.database.dev.conf",
                    "0.local.dev.conf",
                    "0.app.prod.conf",
                    "0.database.prod.conf",
                    "0.local.prod.conf",
                    "0.app.test.conf",
                    "0.app.conf",
                    "0.database.conf",
                    "0.local.conf",
                    "0.conf",
                    "1.app.dev.conf",
                    "1.cache.dev.conf",
                    "1.app.prod.conf",
                    "1.cache.prod.conf",
                    "1.app.staging.conf",
                    "1.app.conf",
                    "1.cache.conf",
                    "1.conf",
                    "5.feature.dev.conf",
                    "5.feature.prod.conf",
                    "5.feature.test.conf",
                    "5.feature.conf",
                    "5.conf",
                    "9.final.dev.conf",
                    "9.final.prod.conf",
                    "9.final.staging.conf",
                    "9.final.conf",
                    "9.conf",
                )

            val files = expected.shuffled().map { ConfigFile.create(it, "") }.sorted()
            files.map { it.fileName }.shouldContainExactly(expected)
        }

        "ConfigFile.create parses file names correctly" {
            val file = ConfigFile.create("5.myapp.prod.conf", "content")

            file.priority shouldBe 5
            file.name shouldBe "myapp"
            file.envName shouldBe "prod"
            file.fileName shouldBe "5.myapp.prod.conf"
            file.text shouldBe "content"
        }

        "ConfigFile.create handles file without environment" {
            val file = ConfigFile.create("3.app.conf", "content")

            file.priority shouldBe 3
            file.name shouldBe "app"
            file.envName shouldBe ""
        }

        "ConfigFile.create handles file with only priority" {
            val file = ConfigFile.create("0.conf", "content")

            file.priority shouldBe 0
            file.name shouldBe ""
            file.envName shouldBe ""
        }

        "filterForEnv returns true for files without environment" {
            val file = fakeConfig(5, name = "app", env = "")
            file.filterForEnv(Env("prod")) shouldBe true
            file.filterForEnv(Env.TEST_UNIT) shouldBe true
        }

        "filterForEnv returns true when environment matches" {
            val file = fakeConfig(5, name = "app", env = "prod")
            file.filterForEnv(Env("prod")) shouldBe true
        }

        "filterForEnv returns false when environment does not match" {
            val file = fakeConfig(5, name = "app", env = "prod")
            file.filterForEnv(Env("dev")) shouldBe false
        }

        "filterForEnv filters out unnamed local files in CI test environments" {
            val file = fakeConfig(5, name = "", env = "")
            file.filterForEnv(Env.TEST_UNIT) shouldBe false
        }

        "filterForEnv filters out local named files in CI test environments" {
            val file = fakeConfig(5, name = "local", env = "")
            file.filterForEnv(Env.TEST_UNIT) shouldBe false
        }

        "filterForEnv allows named non-local files in CI test environments" {
            val file = fakeConfig(5, name = "app", env = "")
            file.filterForEnv(Env.TEST_UNIT) shouldBe true
        }
    })
