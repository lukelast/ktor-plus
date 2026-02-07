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

class ErrorBuilder {
    var status: HttpStatusCode = HttpStatusCode.InternalServerError
    var type: String = ""
    var title: String = ""
    var detail: String = ""
    var internalMessage: String = ""
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
