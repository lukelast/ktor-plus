package net.ghue.ktp.ktor.error

import io.ktor.http.HttpStatusCode

class KtpRspExNotFound(name: String, val id: String, cause: Throwable? = null) :
    KtpRspEx(
        status = HttpStatusCode.NotFound,
        title = "$name Not Found",
        detail = "The $name with ID '$id' does not exist.",
        cause = cause,
    )

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
 * DSL builder for [ktpRspError]. Fields map to an
 * [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) Problem JSON response:
 * ```json
 * {
 *   "type":     <type>,            // defaults to "about:blank"
 *   "title":    <title>,           // defaults to the status reason phrase
 *   "status":   <status code>,     // e.g. 500
 *   "detail":   <detail>,          // omitted when blank
 *   "instance": <request path>     // set automatically from the request
 * }
 * ```
 */
class ErrorBuilder {
    /** HTTP status code for the response. Default: `500 Internal Server Error`. */
    var status: HttpStatusCode = HttpStatusCode.InternalServerError

    /** A URI identifying the problem type. Default: `"about:blank"`. */
    var type: String = ""

    /**
     * A short, human-readable summary of the problem type. When blank the status reason phrase is
     * used (e.g. "Internal Server Error").
     */
    var title: String = ""

    /**
     * A human-readable explanation specific to this occurrence of the problem. Omitted from the
     * response when blank.
     */
    var detail: String = ""

    /**
     * Logged server-side but **never** sent to the client. Passed as [RuntimeException.message].
     */
    var internalMessage: String = ""

    /** Optional underlying exception. Logged server-side but not included in the response. */
    var cause: Throwable? = null

    private val extraFields = mutableMapOf<String, Any>()

    /** Adds a custom extension field to the Problem JSON response. */
    fun extra(key: String, value: String) {
        extraFields[key] = value
    }

    /** Adds a custom extension field to the Problem JSON response. */
    fun extra(key: String, value: Int) {
        extraFields[key] = value
    }

    /** Adds a custom extension field to the Problem JSON response. */
    fun extra(key: String, value: Boolean) {
        extraFields[key] = value
    }

    fun buildExtraFields(): Map<String, Any> = extraFields.toMap()
}

inline fun ktpRspError(builder: ErrorBuilder.() -> Unit): Nothing {
    val builderInstance = ErrorBuilder().apply(builder)
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
