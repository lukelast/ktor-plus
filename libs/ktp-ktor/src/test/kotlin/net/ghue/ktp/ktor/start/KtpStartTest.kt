package net.ghue.ktp.ktor.start

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin

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

        "a Koin config definition replaces an addModule one of the same type" {
            val app =
                ktpAppCreate {
                        createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
                        addModule { single<CharSequence> { "module" } }
                        addKoinConfig(
                            koinConfiguration {
                                modules(module { single<CharSequence> { "config" } })
                            }
                        )
                    }()
                    .build()

            testApplication {
                application {
                    app.installKoin(this)
                    getKoin().get<CharSequence>() shouldBe "config"
                }
                startApplication()
            }
        }

        "an override module replaces both addModule and Koin config definitions" {
            val app =
                ktpAppCreate {
                        createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
                        addModule { single<CharSequence> { "module" } }
                        addKoinConfig(
                            koinConfiguration {
                                modules(module { single<CharSequence> { "config" } })
                            }
                        )
                    }
                        .update { addOverrideModule { single<CharSequence> { "override" } } }()
                    .build()

            testApplication {
                application {
                    app.installKoin(this)
                    getKoin().get<CharSequence>() shouldBe "override"
                }
                startApplication()
            }
        }

        "the builder binds a system UTC Clock" {
            val app =
                ktpAppCreate { createKtpConfig = { KtpConfig.create { setUnitTestEnv() } } }()
                    .build()

            testApplication {
                application {
                    app.installKoin(this)
                    getKoin().get<Clock>() shouldBe Clock.systemUTC()
                }
                startApplication()
            }
        }

        "an addModule Clock replaces the built-in one" {
            val fixed = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
            val app =
                ktpAppCreate {
                        createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
                        addModule { single<Clock> { fixed } }
                    }()
                    .build()

            testApplication {
                application {
                    app.installKoin(this)
                    getKoin().get<Clock>() shouldBe fixed
                }
                startApplication()
            }
        }

        "an override module Clock replaces the built-in one" {
            val fixed = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
            val app =
                ktpAppCreate { createKtpConfig = { KtpConfig.create { setUnitTestEnv() } } }
                        .update { addOverrideModule { single<Clock> { fixed } } }()
                    .build()

            testApplication {
                application {
                    app.installKoin(this)
                    getKoin().get<Clock>() shouldBe fixed
                }
                startApplication()
            }
        }
    })
