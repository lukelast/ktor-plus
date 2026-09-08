package net.ghue.ktp.ktor.app.debug

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.Serializable
import net.ghue.ktp.ktor.error.KtpRspExNotFound
import net.ghue.ktp.ktor.error.ktpRspError
import net.ghue.ktp.log.log

/** Request body for the decode-failure cases. */
@Serializable internal data class DebugErrorBody(val name: String)

/** One button on the errors page: the request it sends and the failure the handler raises. */
internal class DebugErrorCase(
    val id: String,
    val description: String,
    val expectedStatus: HttpStatusCode,
    val method: HttpMethod = HttpMethod.Get,
    val requestContentType: ContentType? = null,
    val requestBody: String? = null,
    val handler: suspend RoutingContext.() -> Unit,
)

/** Every error path the default plugins handle, so each one's log output can be inspected. */
internal val debugErrorCases: List<DebugErrorCase> =
    listOf(
        DebugErrorCase(
            id = "unhandled",
            description =
                "Plain unhandled exception: the catch-all handler answers 500 and logs once at " +
                    "ERROR with the exception's stack trace.",
            expectedStatus = HttpStatusCode.InternalServerError,
        ) {
            throw IllegalStateException("Debug: unhandled exception")
        },
        DebugErrorCase(
            id = "unhandled-chain",
            description =
                "Unhandled exception with a cause chain: the log line names both the exception " +
                    "and its root cause.",
            expectedStatus = HttpStatusCode.InternalServerError,
        ) {
            throw IllegalStateException("Debug: outer failure", IOException("Debug: root cause"))
        },
        DebugErrorCase(
            id = "unhandled-future",
            description =
                "Failure on another thread surfaced by CompletableFuture.join(), as Firestore " +
                    "calls do: a CompletionException wrapping the real cause.",
            expectedStatus = HttpStatusCode.InternalServerError,
        ) {
            CompletableFuture.supplyAsync<String> {
                    throw IllegalStateException("Debug: failed on another thread")
                }
                .join()
        },
        DebugErrorCase(
            id = "ktp-500",
            description =
                "ktpRspError 500 with detail, internalMessage, and cause: ERROR log with the " +
                    "cause's stack trace; internalMessage stays out of the response.",
            expectedStatus = HttpStatusCode.InternalServerError,
        ) {
            ktpRspError {
                title = "Debug Server Error"
                detail = "A deliberate 500 raised through ktpRspError."
                internalMessage = "Debug: server-side only text"
                cause = IllegalStateException("Debug: cause of the 500")
            }
        },
        DebugErrorCase(
            id = "ktp-500-no-cause",
            description =
                "ktpRspError 500 without a cause: ERROR log with the KtpRspEx's own stack trace.",
            expectedStatus = HttpStatusCode.InternalServerError,
        ) {
            ktpRspError {
                title = "Debug Server Error"
                detail = "A deliberate 500 with no underlying exception."
            }
        },
        DebugErrorCase(
            id = "ktp-422",
            description = "ktpRspError 4xx with an extra field: INFO log, no stack trace.",
            expectedStatus = HttpStatusCode.UnprocessableEntity,
        ) {
            ktpRspError {
                status = HttpStatusCode.UnprocessableEntity
                title = "Debug Client Error"
                detail = "A deliberate 422 raised through ktpRspError."
                internalMessage = "Debug: server-side only text"
                extra("field", "name")
            }
        },
        DebugErrorCase(
            id = "not-found",
            description =
                "KtpRspEx subclass (KtpRspExNotFound): 404 whose public properties are reflected " +
                    "into the response.",
            expectedStatus = HttpStatusCode.NotFound,
        ) {
            throw KtpRspExNotFound(name = "Widget", id = "debug-123")
        },
        DebugErrorCase(
            id = "bad-json",
            description =
                "Malformed JSON body: BadRequestException becomes a generic 400 and the " +
                    "serializer message is only logged.",
            expectedStatus = HttpStatusCode.BadRequest,
            method = HttpMethod.Post,
            requestContentType = ContentType.Application.Json,
            requestBody = "{ not json",
        ) {
            call.respondText("Decoded ${call.receive<DebugErrorBody>()}")
        },
        DebugErrorCase(
            id = "wrong-content-type",
            description =
                "JSON body sent as text/plain: no converter runs, so a " +
                    "ContentTransformationException becomes a generic 400.",
            expectedStatus = HttpStatusCode.BadRequest,
            method = HttpMethod.Post,
            requestContentType = ContentType.Text.Plain,
            requestBody = """{"name":"debug"}""",
        ) {
            call.respondText("Decoded ${call.receive<DebugErrorBody>()}")
        },
        DebugErrorCase(
            id = "logged-only",
            description =
                "Handled failure: a WARN entry and an ERROR entry with a stack trace, but the " +
                    "request still answers 200.",
            expectedStatus = HttpStatusCode.OK,
        ) {
            log {}.warn { "Debug: warning without an exception" }
            log {}
                .error(IllegalStateException("Debug: logged exception")) {
                    "Debug: error with an exception"
                }
            call.respondText("Logged a WARN and an ERROR entry; check the logs.")
        },
    )

/**
 * Serves the error-trigger page at this route and one endpoint per [debugErrorCases] under it, so
 * the log entries every error path produces can be checked in a deployed environment. Requires the
 * default plugins (StatusPages, ContentNegotiation) to be installed.
 */
fun Route.installDebugErrorRoutes(accessControl: (suspend ApplicationCall.() -> Boolean)? = null) {
    debugErrorCases.forEach { case ->
        route(case.id, case.method) {
            handle {
                if (accessControl?.invoke(call) == false) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@handle
                }
                case.handler(this)
            }
        }
    }
    // Holds nothing sensitive, so no access check.
    get(ERRORS_SCRIPT_FILE) {
        call.respondText(ERRORS_SCRIPT.trimIndent(), ContentType.Text.JavaScript)
    }
    get("") {
        if (accessControl?.invoke(call) == false) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.respondDebugErrors()
    }
}

/** HTML page with one button per [debugErrorCases] that runs it and shows the response. */
suspend fun RoutingCall.respondDebugErrors() {
    val rows =
        debugErrorCases.joinToString("\n") { case ->
            val request = "${case.method.value} ${case.id}"
            """
            <tr>
                <td class="case-cell">
                    <button type="button" data-id="${case.id}" data-method="${case.method.value}"
                        data-expected="${case.expectedStatus.value}"
                        data-content-type="${case.requestContentType?.toString().orEmpty()}"
                        data-body="${case.requestBody.orEmpty().escapeHtml()}">$request</button>
                </td>
                <td class="description-cell">${case.description.escapeHtml()}</td>
                <td class="expected-cell">${case.expectedStatus.value}</td>
                <td class="result-cell"><pre id="result-${case.id}"></pre></td>
            </tr>
            """
                .trimIndent()
        }
    val html =
        ERRORS_TEMPLATE.trimIndent()
            .replace("{{SCRIPT_URL}}", "${request.path().trimEnd('/')}/$ERRORS_SCRIPT_FILE")
            .replace("{{CASE_ROWS}}", rows)
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
}
