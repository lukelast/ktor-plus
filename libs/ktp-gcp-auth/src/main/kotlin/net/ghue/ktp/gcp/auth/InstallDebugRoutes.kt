package net.ghue.ktp.gcp.auth

import io.ktor.server.application.*
import io.ktor.server.routing.*
import net.ghue.ktp.ktor.app.debug.ConfigDebugInfoConfig
import net.ghue.ktp.ktor.app.debug.DebugEndpoints
import net.ghue.ktp.ktor.app.debug.respondConfigHtml
import net.ghue.ktp.ktor.app.debug.respondDebugIndex
import net.ghue.ktp.ktor.app.debug.respondGcLog
import net.ghue.ktp.ktor.app.debug.respondThreadDump
import net.ghue.ktp.ktor.app.debug.respondVersion

fun Application.installDebugRoutes(role: Role = Role("admin")) {
    routing {
        authenticateFirebase {
            requireRole(role) {
                route(DebugEndpoints.BASE) {
                    // Create default config for index page
                    val defaultConfig = ConfigDebugInfoConfig()

                    get(DebugEndpoints.CONFIG) { call.respondConfigHtml() }
                    get(DebugEndpoints.GCLOG) { call.respondGcLog() }
                    get(DebugEndpoints.THREADS) { call.respondThreadDump() }
                    get(DebugEndpoints.VERSION) { call.respondVersion() }

                    // Index route registered last
                    get("") { call.respondDebugIndex(defaultConfig) }
                }
            }
        }
    }
}
