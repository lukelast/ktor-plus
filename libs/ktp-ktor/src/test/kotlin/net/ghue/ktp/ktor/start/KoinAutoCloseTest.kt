package net.ghue.ktp.ktor.start

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import net.ghue.ktp.config.KtpConfig
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.ktor.ext.getKoin

class KoinAutoCloseTest :
    StringSpec({
        "closes created AutoCloseable singles when Koin closes" {
            val koinApp = koinApplication { modules(module { single { Resource() } }) }
            koinApp.koin.autoCloseInstances()
            val resource = koinApp.koin.get<Resource>()
            koinApp.close()
            resource.closed shouldBe true
        }

        "does not create singles just to close them" {
            var created = 0
            val koinApp = koinApplication {
                modules(
                    module {
                        single {
                            created++
                            Resource()
                        }
                    }
                )
            }
            koinApp.koin.autoCloseInstances()
            koinApp.close()
            created shouldBe 0
        }

        "keeps a definition's own onClose" {
            var explicitCalls = 0
            val koinApp = koinApplication {
                modules(module { single { Resource() } onClose { explicitCalls++ } })
            }
            koinApp.koin.autoCloseInstances()
            val resource = koinApp.koin.get<Resource>()
            koinApp.close()
            explicitCalls shouldBe 1
            resource.closed shouldBe false
        }

        "a throwing close is logged, not propagated" {
            val koinApp = koinApplication { modules(module { single { Failing() } }) }
            koinApp.koin.autoCloseInstances()
            koinApp.koin.get<Failing>()
            shouldNotThrowAny { koinApp.close() }
        }

        "closes every created instance even when one close fails" {
            val koinApp = koinApplication {
                modules(
                    module {
                        single { Failing() }
                        single(named("a")) { Resource() }
                        single(named("b")) { Resource() }
                    }
                )
            }
            koinApp.koin.autoCloseInstances()
            koinApp.koin.get<Failing>()
            val a = koinApp.koin.get<Resource>(named("a"))
            val b = koinApp.koin.get<Resource>(named("b"))
            koinApp.close()
            a.closed shouldBe true
            b.closed shouldBe true
        }

        "installKoin closes AutoCloseable singles when the application stops" {
            lateinit var resource: Resource
            testApplication {
                application {
                    KtpApp(
                            config = KtpConfig.create { setUnitTestEnv() },
                            modules = listOf(module { single { Resource() } }),
                            koinConfigs = emptyList(),
                            appInits = emptyList(),
                        )
                        .installKoin(this)
                    resource = getKoin().get()
                }
                startApplication()
            }
            resource.closed shouldBe true
        }
    })

private class Resource : AutoCloseable {
    var closed = false

    override fun close() {
        closed = true
    }
}

private class Failing : AutoCloseable {
    override fun close(): Unit = throw IllegalStateException("boom")
}
