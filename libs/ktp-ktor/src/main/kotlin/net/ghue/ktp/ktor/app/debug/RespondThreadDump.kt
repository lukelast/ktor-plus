package net.ghue.ktp.ktor.app.debug

import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Responds with a thread dump of all threads, including the virtual threads that carry KTP
 * requests, with complete stack traces and coroutine debug info (if available).
 *
 * This is a diagnostic endpoint and should be protected with access control.
 */
suspend fun RoutingCall.respondThreadDump() {
    val threadDump = generateThreadDump()
    respondText(threadDump)
}
