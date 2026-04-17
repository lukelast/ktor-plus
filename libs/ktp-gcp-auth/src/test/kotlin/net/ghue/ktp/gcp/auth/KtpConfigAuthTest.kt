package net.ghue.ktp.gcp.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import net.ghue.ktp.config.KtpConfig

class KtpConfigAuthTest :
    StringSpec({
        "auth config parses session timeout in days" {
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("auth.sessionTimeout", "7d")
            }

            config.auth.sessionTimeout shouldBe "7d"
            config.auth.sessionTimeoutDuration shouldBe 7.days
        }

        "auth config parses session timeout in hours" {
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("auth.sessionTimeout", "48h")
            }

            config.auth.sessionTimeout shouldBe "48h"
            config.auth.sessionTimeoutDuration shouldBe 48.hours
        }

        "auth config reads all URL values" {
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("auth.loginUrl", "/custom/login")
                overrideValue("auth.logoutUrl", "/custom/logout")
                overrideValue("auth.redirectAfterLogout", "/dashboard")
                overrideValue("auth.sessionTimeout", "14d")
            }

            config.auth.loginUrl shouldBe "/custom/login"
            config.auth.logoutUrl shouldBe "/custom/logout"
            config.auth.redirectAfterLogout shouldBe "/dashboard"
            config.auth.sessionTimeout shouldBe "14d"
            config.auth.sessionTimeoutDuration shouldBe 14.days
        }
    })
