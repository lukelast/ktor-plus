package net.ghue.ktp.ktor.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.statuspages.StatusPages
import net.ghue.ktp.ktor.error.KtpRspEx
import net.ghue.ktp.ktor.error.processKtpRspEx

/**
 * Problem JSON for every unhandled exception: [KtpRspEx] as thrown, decode failures 400, oversized
 * body 413, anything else 500.
 */
internal fun Application.installStatusPages() {
    install(StatusPages) {
        exception<KtpRspEx>(::processKtpRspEx)
        // A ContentTransformationException subclass; mapped on its own so it is not a 400.
        exception<PayloadTooLargeException>(::processPayloadTooLarge)
        // Decode errors; ContentTransformationException when no converter ran (wrong Content-Type).
        exception<BadRequestException>(::processRequestDecodingFailure)
        exception<ContentTransformationException>(::processRequestDecodingFailure)

        // processKtpRspEx logs every 5xx with the stack trace.
        exception<Throwable> { call, cause ->
            val root = generateSequence(cause) { it.cause }.last()
            val summary = if (root === cause) "$cause" else "$cause, root cause: $root"
            processKtpRspEx(call, KtpRspEx(internalMessage = "Unhandled $summary", cause = cause))
        }
    }
}

private suspend fun processRequestDecodingFailure(call: ApplicationCall, cause: Throwable) {
    // ContentNegotiation wraps failures encountered while reading a streamed JSON body.
    val oversized =
        generateSequence(cause) { it.cause }
            .filterIsInstance<PayloadTooLargeException>()
            .firstOrNull()
    if (oversized != null) {
        processPayloadTooLarge(call, oversized)
        return
    }
    processKtpRspEx(
        call,
        KtpRspEx(
            // Server-side only; serializer internals stay out of the response.
            internalMessage = generateSequence(cause) { it.cause }.last().message,
            status = HttpStatusCode.BadRequest,
            title = "Bad Request",
            detail = "The request body could not be parsed.",
            cause = cause,
        ),
    )
}

/** Over `bodyLimit.default` or the route's [Route.bodyLimit]. */
private suspend fun processPayloadTooLarge(call: ApplicationCall, cause: PayloadTooLargeException) {
    processKtpRspEx(
        call,
        KtpRspEx(
            internalMessage = cause.message,
            status = HttpStatusCode.PayloadTooLarge,
            title = "Payload Too Large",
            detail = "The request body exceeds the size limit.",
            cause = cause,
        ),
    )
}
