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
 * Mounts the ktp-ktor debug pages under [DebugEndpoints.BASE] behind `authenticateFirebase` and
 * [role]. Installing `FirebaseAuthPlugin` must come first.
 */
fun Application.installDebugRoutes(role: Role = Role.ADMIN) {
    routing {
        // Public so deploy checks can read the version.
        route(DebugEndpoints.BASE) { get(DebugEndpoints.VERSION) { call.respondVersion() } }
        authenticateFirebase {
            requireRole(role) {
                route(DebugEndpoints.BASE) {
                    val defaultConfig = DebugEndpointsConfig()

                    get(DebugEndpoints.CONFIG) { call.respondConfigHtml() }
                    get(DebugEndpoints.GC_LOG) { call.respondGcLog() }
                    get(DebugEndpoints.THREADS) { call.respondThreadDump() }
                    route(DebugEndpoints.ERRORS) { installDebugErrorRoutes() }

                    get("") { call.respondDebugIndex(defaultConfig) }
                }
            }
        }
    }
}
