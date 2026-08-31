package net.ghue.ktp.core

import com.github.benmanes.caffeine.cache.Ticker
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class FakeTicker : Ticker {
    private val nanos = AtomicLong()

    override fun read(): Long = nanos.get()

    fun advance(duration: Duration) {
        nanos.addAndGet(duration.inWholeNanoseconds)
    }
}

class LazyWithRefreshTest :
    StringSpec({
        "loads once and caches the value" {
            val loads = AtomicInteger()
            val holder =
                object {
                    val value by lazyWithRefresh(1.minutes) { loads.incrementAndGet() }
                }

            holder.value shouldBe 1
            holder.value shouldBe 1
            loads.get() shouldBe 1
        }

        "failures before the first success propagate and the next read retries" {
            val loads = AtomicInteger()
            val holder =
                object {
                    val value by
                        lazyWithRefresh<Int>(1.minutes) {
                            loads.incrementAndGet()
                            throw IllegalStateException("load failed")
                        }
                }

            shouldThrow<IllegalStateException> { holder.value }
            shouldThrow<IllegalStateException> { holder.value }
            loads.get() shouldBe 2
        }

        "a read past twice the refresh interval blocks on a fresh load" {
            val ticker = FakeTicker()
            val loads = AtomicInteger()
            val holder =
                object {
                    val value by LazyWithRefresh(1.minutes, ticker) { loads.incrementAndGet() }
                }

            holder.value shouldBe 1
            ticker.advance(3.minutes)
            holder.value shouldBe 2
            loads.get() shouldBe 2
        }

        "a failed blocking reload serves the last good value" {
            val ticker = FakeTicker()
            val loads = AtomicInteger()
            val holder =
                object {
                    val value by
                        LazyWithRefresh(1.minutes, ticker) {
                            if (loads.incrementAndGet() > 1) {
                                throw IllegalStateException("reload failed")
                            }
                            "good"
                        }
                }

            holder.value shouldBe "good"
            ticker.advance(3.minutes)
            // The failed reload is served from lastGood and re-cached: no error, no extra load.
            holder.value shouldBe "good"
            holder.value shouldBe "good"
            loads.get() shouldBe 2
        }

        "a stale read serves the old value and refreshes in the background" {
            val ticker = FakeTicker()
            val loads = AtomicInteger()
            val gate = CountDownLatch(1)
            val holder =
                object {
                    val value by
                        LazyWithRefresh(1.minutes, ticker) {
                            val load = loads.incrementAndGet()
                            if (load > 1) {
                                gate.await()
                            }
                            load
                        }
                }

            holder.value shouldBe 1
            ticker.advance(90.seconds)
            // The reload is gated on the latch, so the stale value must be served immediately;
            // a blocking read here would deadlock until the test times out.
            holder.value shouldBe 1
            gate.countDown()
            eventually(5.seconds) { holder.value shouldBe 2 }
        }
    })
