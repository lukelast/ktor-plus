package net.ghue.ktp.config

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class EnvTest :
    StringSpec({
        "findEnvironment picks up KTP_ENV system property" {
            val envVar = "KTP_ENV"
            val value = "frank"
            System.setProperty(envVar, value)

            try {
                val env = findEnvironment()
                env.name shouldBe value
            } finally {
                System.clearProperty(envVar)
            }
        }

        "findEnvironment trims whitespace from env var value" {
            val envVar = "KTP_ENV"
            System.setProperty(envVar, " prod\n")

            try {
                val env = findEnvironment()
                env.name shouldBe "prod"
            } finally {
                System.clearProperty(envVar)
            }
        }

        "localDevEnvOrNull trims whitespace from the value" {
            val config = ConfigFactory.parseString("""localDevEnv = " staging\n"""")
            localDevEnvOrNull(config, "0.conf")?.name shouldBe "staging"
        }

        "invalid localDevEnv fails fast with the source in the message" {
            val config = ConfigFactory.parseString("""localDevEnv = "Not Valid"""")
            val ex = shouldThrow<IllegalArgumentException> { localDevEnvOrNull(config, "0.conf") }
            ex.message shouldContain "localDevEnv"
            ex.message shouldContain "Not Valid"
            ex.message shouldContain "0.conf"
        }

        "blank localDevEnv fails fast instead of silently using the default" {
            val config = ConfigFactory.parseString("""localDevEnv = """"")
            shouldThrow<IllegalArgumentException> { localDevEnvOrNull(config, "0.conf") }
        }

        "absent localDevEnv returns null so findEnvironment uses the default" {
            localDevEnvOrNull(ConfigFactory.parseString(""), "0.conf").shouldBeNull()
        }

        "null localDevEnv is treated as absent" {
            localDevEnvOrNull(ConfigFactory.parseString("localDevEnv = null"), "0.conf")
                .shouldBeNull()
        }

        "localDev flag comes from config file override" {
            // Expects the localDevEnv value from src/test/resources/ktp/0.conf.
            val env = findEnvironment()
            env.name shouldBe "123"
        }

        "default env is treated as local dev" {
            val env = Env(LOCAL_DEV_ENV_NAME)
            env.isLocalDev shouldBe true
        }

        "default env suffix still counts as local dev" {
            val env = Env("$LOCAL_DEV_ENV_NAME-xyz")
            env.isLocalDev shouldBe true
        }

        "TEST_UNIT is recognised as CI environment" {
            val env = Env.TEST_UNIT
            env.isTest shouldBe true
            env.isLocalDev shouldBe false
            env.isDefault shouldBe false
        }
    })
