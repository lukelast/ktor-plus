package net.ghue.ktp.ktor.error

import io.ktor.http.HttpStatusCode

class KtpRspExNotFound(name: String, val id: String, cause: Throwable? = null) :
    KtpRspEx(
        status = HttpStatusCode.NotFound,
        title = "$name Not Found",
        detail = "The $name with ID '$id' does not exist.",
        cause = cause,
    )

/**
 * [processKtpRspEx] sends every public property a subclass declares (and its class name) to the
 * client as Problem JSON members, so never expose sensitive data as a public property.
 */
open class KtpRspEx(
    val internalMessage: String? = null,
    val status: HttpStatusCode = HttpStatusCode.InternalServerError,
    val type: String = "",
    val title: String = "",
    val detail: String = "",
    val extraFields: Map<String, Any> = emptyMap(),
    override val cause: Throwable? = null,
) : RuntimeException(internalMessage, cause)

/**
 * DSL builder for [ktpRspError]. Fields map to RFC 7807 Problem JSON members; `instance` is set
 * from the request path, and [extra] keys that collide with a standard member are silently dropped.
 */
class KtpRspExBuilder {
    var status: HttpStatusCode = HttpStatusCode.InternalServerError

    /** Problem type URI; `"about:blank"` when blank. */
    var type: String = ""

    /** Short summary of the problem type; the status reason phrase when blank. */
    var title: String = ""

    /** Occurrence-specific explanation; omitted from the response when blank. */
    var detail: String = ""

    /** Logged server-side but never sent to the client; becomes [RuntimeException.message]. */
    var internalMessage: String = ""

    /** Never sent to the client; its stack trace is logged only for 5xx (4xx are client faults). */
    var cause: Throwable? = null

    private val extraFields = mutableMapOf<String, Any>()

    fun extra(key: String, value: String) {
        extraFields[key] = value
    }

    fun extra(key: String, value: Int) {
        extraFields[key] = value
    }

    fun extra(key: String, value: Boolean) {
        extraFields[key] = value
    }

    fun buildExtraFields(): Map<String, Any> = extraFields.toMap()
}

inline fun ktpRspError(builder: KtpRspExBuilder.() -> Unit): Nothing {
    val builderInstance = KtpRspExBuilder().apply(builder)
    throw KtpRspEx(
        internalMessage = builderInstance.internalMessage,
        status = builderInstance.status,
        type = builderInstance.type,
        title = builderInstance.title,
        detail = builderInstance.detail,
        extraFields = builderInstance.buildExtraFields(),
        cause = builderInstance.cause,
    )
}
