package net.ghue.ktp.ktor.plugin

import com.typesafe.config.ConfigMemorySize
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingPipelineCall
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import net.ghue.ktp.config.KtpConfig

val KtpConfig.bodyLimit: BodyLimit
    get() = this.extractChild()

/** The `bodyLimit` config block; see `9.bodyLimit.conf`. */
data class BodyLimit(
    /** Cap for routes without their own [Route.bodyLimit]. */
    val default: ConfigMemorySize
)

private val routeBodyLimitKey = AttributeKey<Long>("KtpRouteBodyLimit")

/**
 * Caps request bodies for this route and its children, overriding `bodyLimit.default`. Over the cap
 * is a 413, from `Content-Length` up front or once the stream passes it. Do not install Ktor's
 * `RequestBodyLimit` on a route: it can only tighten the app-wide limit.
 */
fun Route.bodyLimit(maxBytes: Long) {
    attributes.put(routeBodyLimitKey, maxBytes)
}

/** Installs the request body limit; part of [installDefaultPlugins]. */
internal fun Application.installBodyLimit(config: KtpConfig) {
    val defaultBytes = config.bodyLimit.default.toBytes()
    // On the routing root so the matched route is known.
    routing {
        install(RequestBodyLimit) { bodyLimit { call -> call.routeBodyLimit() ?: defaultBytes } }
    }
}

/** Nearest [Route.bodyLimit] at or above the matched route. */
private fun ApplicationCall.routeBodyLimit(): Long? {
    val matched: RoutingNode =
        when (this) {
            is RoutingCall -> route
            is RoutingPipelineCall -> route
            else -> return null
        }
    return generateSequence(matched) { it.parent }
        .firstNotNullOfOrNull { it.attributes.getOrNull(routeBodyLimitKey) }
}
