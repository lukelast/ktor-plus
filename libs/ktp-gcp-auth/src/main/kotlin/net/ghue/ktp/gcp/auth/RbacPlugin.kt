package net.ghue.ktp.gcp.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.log.log

/**
 * Configuration for the RBAC (Role-Based Access Control) plugin.
 *
 * @property requiredRole The role required to access the route
 */
class RbacConfig {
    var requiredRole: Role = emptyRole
}

/**
 * Route-scoped plugin for Role-Based Access Control (RBAC).
 *
 * This plugin checks if the authenticated user has the required role before allowing access to the
 * route. Must be used within an authentication context.
 *
 * Example usage:
 * ```kotlin
 * routing {
 *     authenticateFirebase {
 *         requireRole("admin") {
 *             get("/admin") {
 *                 // Only users with 'admin' role can access this
 *             }
 *         }
 *     }
 * }
 * ```
 */
val RbacPlugin =
    createRouteScopedPlugin(name = "RbacPlugin", createConfiguration = ::RbacConfig) {
        val config = pluginConfig

        on(AuthenticationChecked) { call ->
            if (config.requiredRole.name.isBlank()) {
                call.application.log.error("RbacPlugin installed with no required role. Denying access.")
                call.respond(HttpStatusCode.Forbidden, "Configuration Error")
                return@on
            }

            val user = call.principal<HasRoles>()

            if (user == null) {
                log {}.warn { "Unable to get user to check roles." }
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@on
            }

            if (!user.hasRole(config.requiredRole)) {
                val userRolesStr = user.roles.joinToString(", ")
                log {}
                    .info {
                        "User ${user.userId} denied access. Required role: ${config.requiredRole}, User roles: [$userRolesStr]"
                    }
                call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
                return@on
            }
        }
    }

/**
 * Requires that the user has the specified role.
 *
 * Must be used within an authentication context (e.g., inside `authenticateFirebase {}`).
 *
 * Example:
 * ```kotlin
 * routing {
 *     authenticateFirebase {
 *         requireRole(Role("admin")) {
 *             get("/admin") {
 *                 // Only users with 'admin' role can access this
 *             }
 *         }
 *     }
 * }
 * ```
 */
fun Route.requireRole(role: Role, build: Route.() -> Unit): Route {
    val route =
        createChild(
            object : RouteSelector() {
                override suspend fun evaluate(
                    context: RoutingResolveContext,
                    segmentIndex: Int,
                ): RouteSelectorEvaluation {
                    return RouteSelectorEvaluation.Constant
                }
            }
        )
    route.install(RbacPlugin) { requiredRole = role }
    route.build()
    return route
}
