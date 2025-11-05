package net.ghue.ktp.ktor.app.debug

import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** This requires GC logging to be enabled. */
suspend fun RoutingCall.respondGcLog() {
    val gcLogFile = Paths.get("/tmp", "gc.log")
    if (gcLogFile.isRegularFile()) {
        respondText(gcLogFile.readText())
    } else {
        respondText("No $gcLogFile file found")
    }
}
