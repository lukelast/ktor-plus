package net.ghue.ktp.ktor.app.debug

import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Responds with [generateThreadDump]. Diagnostic only; protect it with access control. */
suspend fun RoutingCall.respondThreadDump() {
    val threadDump = generateThreadDump()
    respondText(threadDump)
}
