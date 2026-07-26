package net.ghue.ktp.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class LazyWithExpirationTest :
    StringSpec({
        "loads once and caches the value" {
            val loads = AtomicInteger()
            val holder =
                object {
                    val value by lazyWithExpiration(1.minutes) { loads.incrementAndGet() }
                }

            holder.value shouldBe 1
            holder.value shouldBe 1
            loads.get() shouldBe 1
        }
    })
