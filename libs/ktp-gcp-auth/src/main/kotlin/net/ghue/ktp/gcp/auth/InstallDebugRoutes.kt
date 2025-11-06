package net.ghue.ktp.gcp.auth

import io.ktor.server.application.*
import io.ktor.server.routing.*
import net.ghue.ktp.ktor.app.debug.respondConfigHtml
import net.ghue.ktp.ktor.app.debug.respondGcLog
import net.ghue.ktp.ktor.app.debug.respondVersion

fun Application.installDebugRoutes(role: Role = Role("admin")) {
    routing {
        authenticateFirebase {
            requireRole(role) {
                route("/debug") {
                    get("/config") { call.respondConfigHtml() }
                    get("/gclog") { call.respondGcLog() }
                    get("/version") { call.respondVersion() }
                }
            }
        }
    }
}
