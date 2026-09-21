package net.ghue.ktp.ktor.start

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.net.ServerSocket
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.LOCAL_DEV_ENV_NAME

class KtpStartPortTest :
    StringSpec({
        "a taken port in local dev fails with the fix, not a bare BindException" {
            ServerSocket(0).use { taken ->
                val app = ktpAppCreate {
                    createKtpConfig = {
                        KtpConfig.create {
                            env = Env(LOCAL_DEV_ENV_NAME)
                            overrideValue("app.server.port", taken.localPort)
                        }
                    }
                }

                val ex = shouldThrow<IllegalStateException> { ktpAppStart(app) }

                ex.message shouldContain "Port ${taken.localPort}"
                ex.message shouldContain "0.local.localdev.conf"
                ex.message shouldContain "app.server.port = "
            }
        }
    })
