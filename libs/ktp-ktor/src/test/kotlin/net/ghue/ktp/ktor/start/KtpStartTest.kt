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

            val originalApp = originalFactory().build()
            val updatedApp = updatedFactory().build()

            originalApp.modules.size shouldBe 1 + BUILT_IN_MODULES
            originalApp.appInits.size shouldBe 0
            updatedApp.modules.size shouldBe 2 + BUILT_IN_MODULES
            updatedApp.appInits.size shouldBe 1
            updatedFactory shouldNotBeSameInstanceAs originalFactory
        }

        "update preserves a custom createKtpConfig" {
            var customConfigCalled = false
            val createCustomConfig = {
                customConfigCalled = true
                KtpConfig.create { setUnitTestEnv() }
            }

            val originalFactory = ktpAppCreate {
                createKtpConfig = createCustomConfig
                addModule(module {})
            }

            val updatedFactory = originalFactory.update { addModule(module {}) }

            val updatedApp = updatedFactory().build()

            customConfigCalled.shouldBeTrue()
            updatedApp.modules.size shouldBe 2 + BUILT_IN_MODULES
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

            val finalApp = secondUpdateFactory().build()

            finalApp.modules.size shouldBe 2 + BUILT_IN_MODULES
            finalApp.appInits.size shouldBe 1
        }

        "update with empty block still produces new builder" {
            val originalFactory = ktpAppCreate {
                addModule(module {})
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
            }

            val originalModulesCount = originalFactory().build().modules.size

            val updatedFactory = originalFactory.update {}

            updatedFactory().build().modules.size shouldBe originalModulesCount
            updatedFactory shouldNotBeSameInstanceAs originalFactory
        }

        "updated factory preserves changes across repeated invocations" {
            val originalFactory = ktpAppCreate {
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
            }

            val updatedFactory = originalFactory.update {
                addModule(module {})
                addAppInit { _ -> }
            }

            val firstApp = updatedFactory().build()
            val secondApp = updatedFactory().build()

            firstApp.modules.size shouldBe secondApp.modules.size
            firstApp.appInits.size shouldBe secondApp.appInits.size
            firstApp.modules.size shouldBe 1 + BUILT_IN_MODULES
            firstApp.appInits.size shouldBe 1
        }

        "build does not accumulate the built-in module across repeated invocations" {
            val updatedFactory = ktpAppCreate {
                createKtpConfig = { KtpConfig.create { setUnitTestEnv() } }
                addModule(module {})
            }
                .update { addModule(module {}) }

            val firstBuild = updatedFactory().build()
            val secondBuild = updatedFactory().build()

            firstBuild.modules.size shouldBe 2 + BUILT_IN_MODULES
            secondBuild.modules.size shouldBe 2 + BUILT_IN_MODULES
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
                    app.install(this)
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
                    app.install(this)
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
                    app.install(this)
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
                    app.install(this)
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
                    app.install(this)
                    getKoin().get<Clock>() shouldBe fixed
                }
                startApplication()
            }
        }
    })

// build() prepends one module holding the KtpConfig and Clock singles.
private const val BUILT_IN_MODULES = 1
