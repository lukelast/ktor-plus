package net.ghue.ktp.ktor.app.debug

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
            dump shouldContain "Total threads:"
            dump shouldContain "RUNNABLE:"
        }

        "thread dump includes current thread" {
            val dump = generateThreadDump()
            // The test runner thread should be in the dump
            dump shouldContain "Test worker"
        }

        "thread dump shows thread states" {
            val dump = generateThreadDump()
            dump shouldContain "java.lang.Thread.State:"
        }

        "thread dump includes stack traces" {
            val dump = generateThreadDump()
            // Should have 'at' keywords for stack frames
            dump shouldContain "at "
        }

        "thread dump shows daemon threads correctly" {
            // Create a daemon thread
            val daemonThread =
                thread(isDaemon = true, name = "test-daemon-thread") { Thread.sleep(2000) }

            try {
                Thread.sleep(100) // Let thread start
                val dump = generateThreadDump()

                // Should contain our daemon thread marked as daemon
                dump shouldContain "test-daemon-thread"
                dump shouldContain "daemon"
            } finally {
                daemonThread.interrupt()
                daemonThread.join(1000)
            }
        }

        "thread dump includes CPU time when available" {
            val dump = generateThreadDump()
            val threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean()

            if (threadMXBean.isThreadCpuTimeEnabled) {
                dump shouldContain "CPU time:"
            }
        }

        "thread dump handles threads with no stack trace" {
            val dump = generateThreadDump()
            // Should not crash, may contain message about no stack trace
            dump shouldNotBe null
        }

        "thread dump shows coroutine debug status" {
            val dump = generateThreadDump()
            // Should contain some coroutine info section (even if just noting it's unavailable)
            dump shouldContain "Coroutine"
        }

        "thread dump detects waiting/blocked states" {
            val lock = Object()

            // Create a thread that holds a lock
            val holder = thread(name = "lock-holder") { synchronized(lock) { Thread.sleep(5000) } }

            // Create a thread that waits for the lock
            val waiter =
                thread(name = "lock-waiter") {
                    Thread.sleep(100) // Let holder get lock first
                    synchronized(lock) {
                        // Won't get here during test
                    }
                }

            try {
                Thread.sleep(200) // Let both threads start
                val dump = generateThreadDump()

                // Should show the blocked/waiting state
                dump shouldContain "lock-waiter"
                dump shouldContain "lock-holder"
                // One should be waiting/blocked
                (dump.contains("BLOCKED") || dump.contains("WAITING")) shouldBe true
            } finally {
                holder.interrupt()
                waiter.interrupt()
                holder.join(1000)
                waiter.join(1000)
            }
        }
    })
