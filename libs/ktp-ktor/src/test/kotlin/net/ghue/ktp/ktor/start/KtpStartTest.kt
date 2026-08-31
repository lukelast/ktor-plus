package net.ghue.ktp.ktor.start

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module

class KtpStartTest :
    StringSpec({
        "update creates a new builder with additional modules and inits" {
            val originalFactory = ktpAppCreate {
                addModule(module {})
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
            }

            val updatedFactory = originalFactory.update {
                addModule(module {})
                addAppInit { _ -> }
            }

            val originalBuilder = originalFactory()
            val updatedBuilder = updatedFactory()

            originalBuilder.modules.size shouldBe 1
            originalBuilder.appInits.size shouldBe 0
            updatedBuilder.modules.size shouldBe 2
            updatedBuilder.appInits.size shouldBe 1
            updatedFactory shouldNotBeSameInstanceAs originalFactory
        }

        "update preserves custom configuration manager" {
            var customConfigCalled = false
            val customConfigManager = {
                customConfigCalled = true
                KtpConfig.create { setUnitTestEnv() }
            }

            val originalFactory = ktpAppCreate {
                createKtpConfig = customConfigManager
                addModule(module {})
            }

            val updatedFactory = originalFactory.update { addModule(module {}) }

            val updatedBuilder = updatedFactory()
            updatedBuilder.createKtpConfig()

            customConfigCalled.shouldBeTrue()
            updatedBuilder.modules.size shouldBe 2
        }

        "update supports chaining additional changes" {
            val originalFactory = ktpAppCreate {
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
            }

            val firstUpdateFactory = originalFactory.update { addModule(module {}) }

            val secondUpdateFactory = firstUpdateFactory.update {
                addModule(module {})
                addAppInit { _ -> }
            }

            val finalBuilder = secondUpdateFactory()

            finalBuilder.modules.size shouldBe 2
            finalBuilder.appInits.size shouldBe 1
        }

        "update with empty block still produces new builder" {
            val originalFactory = ktpAppCreate {
                addModule(module {})
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
            }

            val originalModulesCount = originalFactory().modules.size

            val updatedFactory = originalFactory.update {}

            val updatedBuilder = updatedFactory()

            updatedBuilder.modules.size shouldBe originalModulesCount
            updatedFactory shouldNotBeSameInstanceAs originalFactory
        }

        "update returns idempotent builder with preserved changes" {
            val originalFactory = ktpAppCreate {
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
            }

            val updatedFactory = originalFactory.update {
                addModule(module {})
                addAppInit { _ -> }
            }

            val firstCall = updatedFactory()
            val secondCall = updatedFactory()

            firstCall.modules.size shouldBe secondCall.modules.size
            firstCall.appInits.size shouldBe secondCall.appInits.size
            firstCall.modules.size shouldBe 1
            firstCall.appInits.size shouldBe 1
        }

        "build does not accumulate config modules across repeated invocations" {
            val updatedFactory = ktpAppCreate {
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
                addModule(module {})
            }
                .update { addModule(module {}) }

            val firstBuild = updatedFactory().build()
            val secondBuild = updatedFactory().build()

            firstBuild.modules.size shouldBe 3
            secondBuild.modules.size shouldBe 3
        }
    })
