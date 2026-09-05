// This file's public API is the plugin and route DSL; RbacConfig supports that API.
@file:Suppress("MatchingDeclarationName")

package net.ghue.ktp.gcp.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.log.log

/** Configuration for the RBAC (Role-Based Access Control) plugin. */
class RbacConfig {
    /** Role required for the route; leaving it [emptyRole] makes the plugin deny every request. */
    var requiredRole: Role = emptyRole
}

/**
 * Route-scoped RBAC plugin that rejects callers lacking [RbacConfig.requiredRole]; see
 * [requireRole].
 */
val RbacPlugin =
    createRouteScopedPlugin(name = "RbacPlugin", createConfiguration = ::RbacConfig) {
        val config = pluginConfig

        on(AuthenticationChecked) { call ->
            // Authentication may already have answered 401 or a retryable 503.
            if (call.isHandled) return@on
            if (config.requiredRole.name.isBlank()) {
                call.application.log.error(
                    "RbacPlugin installed with no required role. Denying access."
                )
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
                        "User ${user.userId} denied access. Required role: ${config.requiredRole}, " +
                            "User roles: [$userRolesStr]"
                    }
                call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
                return@on
            }
        }
    }

/**
 * Restricts routes built by [build] to users holding [role]; must be nested inside an
 * authentication block such as `authenticateFirebase {}`.
 */
fun Route.requireRole(role: Role, build: Route.() -> Unit): Route {
    // A child route scopes the plugin to this block so sibling requireRole calls can demand
    // different roles.
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
