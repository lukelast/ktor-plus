package net.ghue.ktp.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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

        "localDev flag comes from config file override" {
            val env = findEnvironment()
            env.name shouldBe "123"
        }

        "default env is treated as local dev" {
            val env = Env(DEFAULT_ENV)
            env.isLocalDev shouldBe true
        }

        "default env suffix still counts as local dev" {
            val env = Env("$DEFAULT_ENV-xyz")
            env.isLocalDev shouldBe true
        }

        "TEST_UNIT is recognised as CI environment" {
            val env = Env.TEST_UNIT
            env.isCiTest shouldBe true
            env.isLocalDev shouldBe false
            env.isDefault shouldBe false
        }
    })
