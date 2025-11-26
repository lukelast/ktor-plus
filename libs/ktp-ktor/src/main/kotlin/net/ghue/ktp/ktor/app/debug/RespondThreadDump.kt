package net.ghue.ktp.ktor.app.debug

import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Responds with a comprehensive thread dump including stack traces, lock information, CPU
 * statistics, and coroutine debug info.
 *
 * The thread dump follows JVM standard format and includes:
 * - All threads with their states and priorities
 * - Complete stack traces
 * - Lock/monitor information for deadlock detection
 * - Per-thread CPU time statistics
 * - Kotlin coroutine debug info (if available)
 *
 * This is a diagnostic endpoint and should be protected with access control.
 */
suspend fun RoutingCall.respondThreadDump() {
    val threadDump = generateThreadDump()
    respondText(threadDump)
}
