package net.ghue.ktp.gcp.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import net.ghue.ktp.log.log

// Query parameters of [AuthUrls.DEV_LOGIN].
/** Short name selecting the dev user; each one owns its own tenant, so its own data. */
internal const val DEV_LOGIN_PARAM_USER = "user"
/** Comma-separated roles added to the session on top of the stored ones. */
internal const val DEV_LOGIN_PARAM_ROLES = "roles"
/** Same-origin path to land on afterwards. */
internal const val DEV_LOGIN_PARAM_REDIRECT = "redirect"

/** The user id of the unnamed dev user; named ones are `dev-<name>`. */
internal const val DEV_USER_ID = "dev"
internal const val DEV_EMAIL_DOMAIN = "dev.test"
private val DEV_USER_SLUG = Regex("[a-z0-9][a-z0-9-]{0,31}")

/**
 * Passwordless login for local instances, so a browser without the developer's Firebase state (an
 * agent, a second profile) can still get a real session backed by real user records. Mounted only
 * when the env is local dev, so the route does not exist anywhere else.
 */
internal class DevLoginService(private val lifecycle: AuthLifecycleHandler) {

    suspend fun RoutingContext.handleDevLogin() {
        val params = call.request.queryParameters
        call.response.headers.append(HttpHeaders.CacheControl, NO_STORE)

        // Absent or blank selects the unnamed dev user.
        val slug = params[DEV_LOGIN_PARAM_USER]?.takeIf(String::isNotBlank)
        if (slug != null && !DEV_USER_SLUG.matches(slug)) {
            call.respondText(
                "${DEV_LOGIN_PARAM_USER} must match ${DEV_USER_SLUG.pattern}",
                status = HttpStatusCode.BadRequest,
            )
            return
        }
        val redirect = params[DEV_LOGIN_PARAM_REDIRECT] ?: "/"
        if (!isSameOriginPath(redirect)) {
            call.respondText(
                "${DEV_LOGIN_PARAM_REDIRECT} must be a same-origin path",
                status = HttpStatusCode.BadRequest,
            )
            return
        }
        val roles =
            params[DEV_LOGIN_PARAM_ROLES]
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.toSet()
                .orEmpty()

        val session =
            call.startSession(
                lifecycle,
                devLoginIdentity(slug),
                extraRoles = roles + Role.ADMIN.name,
            )
        log {}.info { "Dev login as ${session.userId.value} with roles ${session.roles}" }
        call.respondRedirect(redirect)
    }
}

/**
 * Deterministic per slug, so a dev user keeps its tenant (and data) across restarts. No slug is the
 * unnamed dev user `dev`; `alice-b` is `dev-alice-b`, "Alice B".
 */
internal fun devLoginIdentity(slug: String?): LoginIdentity {
    val local = slug ?: DEV_USER_ID
    return LoginIdentity(
        userId = UserId(if (slug == null) DEV_USER_ID else "$DEV_USER_ID-$slug"),
        email = "$local@$DEV_EMAIL_DOMAIN",
        name = local.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) },
    )
}

/** Even a dev-only route must not become an open redirect. */
private fun isSameOriginPath(path: String): Boolean =
    path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\")
