package net.ghue.ktp.gcp.cron

import com.google.auth.oauth2.TokenVerifier
import com.google.cloud.ServiceOptions
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.log
import org.koin.ktor.ext.inject

private val GcpCronPlugin =
    createRouteScopedPlugin(name = "GcpCronPlugin") {
        onCall { call ->
            val config by call.inject<KtpConfig>()

            if (config.env.isLocalDev) {
                return@onCall
            }

            val authHeader = call.request.headers[HttpHeaders.Authorization]
            if (authHeader == null) {
                log {}.warn { "Missing Authorization header" }
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                return@onCall
            }

            val token = authHeader.substringAfter("Bearer ")
            val verifier =
                TokenVerifier.newBuilder()
                    .setAudience("https://${call.request.origin.serverHost}")
                    .build()
            try {
                val jws = verifier.verify(token)
                val email = jws.payload.get("email") as String
                val gcpProjectId = ServiceOptions.getDefaultProjectId()
                val expectedEmail = "scheduler@$gcpProjectId.iam.gserviceaccount.com"
                if (email != expectedEmail) {
                    log {}.warn { "Invalid email: $email" }
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@onCall
                }
            } catch (ex: TokenVerifier.VerificationException) {
                log {}.warn(ex) { "Error verifying token" }
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                return@onCall
            }
        }
    }

internal fun Route.authenticateGcpCron(build: Route.() -> Unit): Route {
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
    route.install(GcpCronPlugin)
    route.build()
    return route
}
