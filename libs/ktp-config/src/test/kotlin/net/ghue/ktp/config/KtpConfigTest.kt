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

        "getAllConfig renders masked values" {
            val ktp = newKtpConfig()
            val allConfig = ktp.getAllConfig()
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

        "extractChild deserializes nested config" {
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("app.name", "test-app")
                overrideValue("app.version", "1.0.0")
            }

            config.data.app.name shouldBe "test-app"
            config.data.app.version shouldBe "1.0.0"
        }

        "config property exposes underlying Typesafe Config" {
            val config = newKtpConfig()
            config.config.hasPath("app.name") shouldBe true
        }

        "env property exposes environment" {
            val config = KtpConfig.create { setUnitTestEnv() }
            config.env shouldBe Env.TEST_UNIT
        }
    })

private fun newKtpConfig(): KtpConfig =
    KtpConfig(
        ConfigFactory.parseMap(
            mapOf(
                "app.name" to "",
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
