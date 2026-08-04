package net.ghue.ktp.ktor.app.debug

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class ThreadDumpGeneratorTest :
    StringSpec({
        "generateThreadDump returns non-empty string" {
            val dump = generateThreadDump()
            dump.isNotEmpty() shouldBe true
        }

        "thread dump contains header with timestamp" {
            val dump = generateThreadDump()
            dump shouldContain "Full thread dump"
            dump shouldContain "JVM:"
            dump shouldContain "Kotlin:"
        }

        "thread dump contains thread summary" {
            val dump = generateThreadDump()
            dump shouldContain "Thread Summary:"
            dump shouldContain "Total threads (including virtual):"
            dump shouldContain "Platform threads:"
        }

        "thread dump includes current thread" {
            val dump = generateThreadDump()
            // The test runner thread should be in the dump
            dump shouldContain "Test worker"
        }

        "thread dump includes stack traces" {
            val dump = generateThreadDump()
            // Stack frames are module-qualified in the plain text format
            dump shouldContain "java.base/"
        }

        "thread dump includes virtual threads" {
            val started = CountDownLatch(1)
            val virtualThread =
                Thread.ofVirtual().name("test-virtual-thread").start {
                    started.countDown()
                    try {
                        Thread.sleep(10_000)
                    } catch (_: InterruptedException) {
                        // Expected on cleanup.
                    }
                }

            try {
                started.await()
                val dump = generateThreadDump()

                // ThreadMXBean-based dumps cannot see this thread; the new API must.
                dump shouldContain "test-virtual-thread"
            } finally {
                virtualThread.interrupt()
                virtualThread.join(1000)
            }
        }

        "thread dump includes named platform threads" {
            val started = CountDownLatch(1)
            val daemonThread =
                thread(isDaemon = true, name = "test-daemon-thread") {
                    started.countDown()
                    try {
                        Thread.sleep(10_000)
                    } catch (_: InterruptedException) {
                        // Expected on cleanup.
                    }
                }

            try {
                started.await()
                val dump = generateThreadDump()
                dump shouldContain "test-daemon-thread"
            } finally {
                daemonThread.interrupt()
                daemonThread.join(1000)
            }
        }

        "thread dump shows coroutine debug status" {
            val dump = generateThreadDump()
            // Should contain some coroutine info section (even if just noting it's unavailable)
            dump shouldContain "Coroutine"
        }
    })
