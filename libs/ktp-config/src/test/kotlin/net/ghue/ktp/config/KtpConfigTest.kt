package net.ghue.ktp.config

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

class KtpConfigTest :
    StringSpec({
        class CorrectTestConfig(private val config: KtpConfig) {
            val msg = "hi"
        }

        "get provides sub-config instances" {
            val config = newKtpConfig()
            config.get<CorrectTestConfig>().msg shouldBe "hi"
        }

        "sub-config lookup caches instances" {
            val config = newKtpConfig()
            val result1 = config.get<CorrectTestConfig>()
            val result2 = config.get<CorrectTestConfig>()
            result1 shouldBeSameInstanceAs result2
        }

        "get fails when constructor parameter has wrong type" {
            class TestConfig(private val config: String)
            assertBadConstructor<TestConfig> { newKtpConfig() }
        }

        "get fails when constructor has additional parameters" {
            class TestConfig(private val config: KtpConfig, val yo: String)
            assertBadConstructor<TestConfig> { newKtpConfig() }
        }

        "getAllConfigMasked renders masked values" {
            val ktp = newKtpConfig()
            val allConfig = ktp.getAllConfigMasked()
            allConfig shouldBe
                mapOf(
                    "app.name" to "",
                    "app.nameShort" to "",
                    "app.secret" to "0 chars",
                    "app.version" to "",
                    "app.hostname" to "",
                    "app.server.port" to "0",
                    "app.server.host" to "",
                )
        }

        "logAllConfig prints without throwing" {
            val config = newKtpConfig()
            shouldNotThrowAny { config.logAllConfig() }
        }

        "data property deserializes nested config" {
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("app.name", "test-app")
                overrideValue("app.version", "1.0.0")
            }

            config.data.app.name shouldBe "test-app"
            config.data.app.version shouldBe "1.0.0"
        }

        "extractChild deserializes the config block keyed by the class name" {
            data class App(val name: String, val version: String)
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("app.name", "test-app")
                overrideValue("app.version", "1.0.0")
            }

            config.extractChild<App>() shouldBe App(name = "test-app", version = "1.0.0")
        }

        "extractChild uses an explicit path when given" {
            data class RenamedApp(val name: String)
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("app.name", "test-app")
            }

            config.extractChild<RenamedApp>("app").name shouldBe "test-app"
        }

        "config property exposes underlying Typesafe Config" {
            val config = newKtpConfig()
            config.config.hasPath("app.name") shouldBe true
        }

        "env property exposes environment" {
            val config = KtpConfig.create { setUnitTestEnv() }
            config.env shouldBe Env.TEST_UNIT
        }

        "constructor trims string config values" {
            val config = newKtpConfig(appName = "  test app\r\n")

            config.data.app.name shouldBe "test app"
            config.config.getString("app.name") shouldBe "test app"
        }
    })

private fun newKtpConfig(appName: String = ""): KtpConfig =
    KtpConfig(
        ConfigFactory.parseMap(
            mapOf(
                "app.name" to appName,
                "app.nameShort" to "",
                "app.secret" to "",
                "app.version" to "",
                "app.hostname" to "",
                "app.server.port" to 0,
                "app.server.host" to "",
            )
        ),
        findEnvironment(),
    )

private inline fun <reified T : Any> assertBadConstructor(creator: () -> KtpConfig) {
    val ktp = creator()

    val exception = shouldThrow<Exception> { ktp.get<T>() }
    exception.message.shouldContain("class must have a primary constructor")
    exception.message.shouldContain("KtpConfig")
}
