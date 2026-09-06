package net.ghue.ktp.gcp.auth

import io.ktor.server.application.*
import io.ktor.server.routing.*
import net.ghue.ktp.ktor.app.debug.DebugEndpoints
import net.ghue.ktp.ktor.app.debug.DebugEndpointsConfig
import net.ghue.ktp.ktor.app.debug.installDebugErrorRoutes
import net.ghue.ktp.ktor.app.debug.respondConfigHtml
import net.ghue.ktp.ktor.app.debug.respondDebugIndex
import net.ghue.ktp.ktor.app.debug.respondGcLog
import net.ghue.ktp.ktor.app.debug.respondThreadDump
import net.ghue.ktp.ktor.app.debug.respondVersion

/**
 * Mounts the ktp-ktor debug pages under `/debug` behind `authenticateFirebase` and [role].
 * Installing `FirebaseAuthPlugin` must come first. Every route, `/debug/version` included, needs a
 * signed-in session holding [role]; the 401 for a missing cookie is answered from the cookie alone.
 */
fun Application.installDebugRoutes(role: Role = Role.ADMIN) {
    routing {
        authenticateFirebase {
            requireRole(role) {
                route(DebugEndpoints.BASE) {
                    val defaultConfig = DebugEndpointsConfig()

                    get(DebugEndpoints.CONFIG) { call.respondConfigHtml() }
                    get(DebugEndpoints.GC_LOG) { call.respondGcLog() }
                    get(DebugEndpoints.THREADS) { call.respondThreadDump() }
                    get(DebugEndpoints.VERSION) { call.respondVersion() }
                    route(DebugEndpoints.ERRORS) { installDebugErrorRoutes() }

                    get("") { call.respondDebugIndex(defaultConfig) }
                }
            }
        }
    }
}
