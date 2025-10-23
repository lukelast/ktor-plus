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
            val originalBuilder = ktpAppCreate {
                addModule(module {})
                createConfigManager = { KtpConfig.create { setUnitTestEnv() } }
            }

            val updatedBuilder =
                originalBuilder.update {
                    addModule(module {})
                    init { _ -> }
                }

            val originalApp = originalBuilder()
            val updatedApp = updatedBuilder()

            originalApp.modules.size shouldBe 1
            originalApp.appInits.size shouldBe 0
            updatedApp.modules.size shouldBe 2
            updatedApp.appInits.size shouldBe 1
            updatedBuilder shouldNotBeSameInstanceAs originalBuilder
        }

        "update preserves custom configuration manager" {
            var customConfigCalled = false
            val customConfigManager = {
                customConfigCalled = true
                KtpConfig.create { setUnitTestEnv() }
            }

            val originalBuilder = ktpAppCreate {
                createConfigManager = customConfigManager
                addModule(module {})
            }

            val updatedBuilder = originalBuilder.update { addModule(module {}) }

            val updatedApp = updatedBuilder()
            updatedApp.createConfigManager()

            customConfigCalled.shouldBeTrue()
            updatedApp.modules.size shouldBe 2
        }

        "update supports chaining additional changes" {
            val originalBuilder = ktpAppCreate {
                createConfigManager = { KtpConfig.create { setUnitTestEnv() } }
            }

            val firstUpdate = originalBuilder.update { addModule(module {}) }

            val secondUpdate =
                firstUpdate.update {
                    addModule(module {})
                    init { _ -> }
                }

            val finalApp = secondUpdate()

            finalApp.modules.size shouldBe 2
            finalApp.appInits.size shouldBe 1
        }

        "update with empty block still produces new builder" {
            val originalBuilder = ktpAppCreate {
                addModule(module {})
                createConfigManager = { KtpConfig.create { setUnitTestEnv() } }
            }

            val originalModulesCount = originalBuilder().modules.size

            val updatedBuilder = originalBuilder.update {}

            val updatedApp = updatedBuilder()

            updatedApp.modules.size shouldBe originalModulesCount
            updatedBuilder shouldNotBeSameInstanceAs originalBuilder
        }

        "update returns idempotent builder with preserved changes" {
            val originalBuilder = ktpAppCreate {
                createConfigManager = { KtpConfig.create { setUnitTestEnv() } }
            }

            val updatedBuilder =
                originalBuilder.update {
                    addModule(module {})
                    init { _ -> }
                }

            val firstCall = updatedBuilder()
            val secondCall = updatedBuilder()

            firstCall.modules.size shouldBe secondCall.modules.size
            firstCall.appInits.size shouldBe secondCall.appInits.size
            firstCall.modules.size shouldBe 1
            firstCall.appInits.size shouldBe 1
        }
    })
