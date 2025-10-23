package net.ghue.ktp.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KtpConfigBuilderTest :
    StringSpec({
        "create with setUnitTestEnv defaults to Env.TEST_UNIT" {
            KtpConfig.create { setUnitTestEnv() }.env shouldBe Env.TEST_UNIT
        }

        "create with setIntegrationEnv defaults to Env.TEST_INTEGRATION" {
            KtpConfig.create { setIntegrationEnv() }.env shouldBe Env.TEST_INTEGRATION
        }

        "create allows overriding configuration values" {
            KtpConfig.create { setUnitTestEnv() }.data.app.name shouldBe ""
            KtpConfig.create {
                    setUnitTestEnv()
                    configValue("app.name", "blah")
                }
                .data
                .app
                .name shouldBe "blah"
        }

        "create allows multiple configuration overrides" {
            val config =
                KtpConfig.create {
                    setUnitTestEnv()
                    configValue("app.name", "test-app")
                    configValue("app.version", "1.2.3")
                }

            config.data.app.name shouldBe "test-app"
            config.data.app.version shouldBe "1.2.3"
        }

        "buildConfig chooses highest priority entry" {
            val files =
                listOf(fakeConfig(9, text = """v=default"""), fakeConfig(5, text = """v=app"""))
                    .shuffled()
            val config = buildConfig(Env.TEST_UNIT, files)
            val value = config.getValue("v")

            value.unwrapped() shouldBe "app"
            value.origin().description() shouldBe "5.conf: 1"
        }

        "buildConfig prefers alphabetical names when priority matches" {
            val files =
                listOf(
                        fakeConfig(0, name = "a", text = """v=a"""),
                        fakeConfig(0, name = "b", text = """v=b"""),
                        fakeConfig(0, name = "c", text = """v=c"""),
                    )
                    .shuffled()

            val config = buildConfig(Env("env"), files)
            config.getValue("v").unwrapped() shouldBe "a"
        }

        "buildConfig applies override map with highest precedence" {
            val files = listOf(fakeConfig(0, text = """v=file"""))
            val config = buildConfig(Env.TEST_UNIT, files, mapOf("v" to "override"))

            config.getValue("v").unwrapped() shouldBe "override"
        }

        "builder uses findEnvironment by default" {
            val builder = KtpConfigBuilder()
            builder.env shouldBe findEnvironment()
        }

        "builder configValue adds to override map" {
            val builder = KtpConfigBuilder()
            builder.configValue("key1", "value1")
            builder.configValue("key2", 42)

            builder.overrideMap["key1"] shouldBe "value1"
            builder.overrideMap["key2"] shouldBe 42
        }
    })
