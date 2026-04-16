package net.ghue.ktp.ktor.error

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.ghue.ktp.log.log

private val standardProblemKeys = setOf("type", "status", "title", "detail", "instance")
private val ignoredFieldNames =
    setOf("message", "internalMessage", "extraFields", "cause", "serialVersionUID", "Companion")

suspend fun processKtpRspEx(call: ApplicationCall, ex: KtpRspEx) {
    val requestPath = call.request.path()
    val reflectedFields = extractExtendedFields(ex)
    val problemJson = buildJsonObject {
        put("type", ex.type.ifBlank { "about:blank" })
        put("title", ex.title.ifBlank { ex.status.description })
        put("status", ex.status.value)
        put("instance", requestPath)
        if (ex.detail.isNotBlank()) {
            put("detail", ex.detail)
        }
        if (ex::class != KtpRspEx::class) {
            put("class", ex::class.qualifiedName ?: "Unknown")
        }
        reflectedFields.forEach { (key, value) ->
            if (key !in standardProblemKeys) {
                put(key, value)
            }
        }
        ex.extraFields.forEach { (key, value) ->
            if (key !in standardProblemKeys) {
                put(key, value.toJsonElement())
            }
        }
    }

    log {}
        .error(ex) {
            "Error processing ${call.request.httpMethod.value} on $requestPath: '${ex.message}' $problemJson"
        }

    call.respondText(
        text = problemJson.toString(),
        contentType = ContentType.Application.ProblemJson,
        status = ex.status,
    )
}

private fun extractExtendedFields(ex: KtpRspEx): JsonObject = buildJsonObject {
    if (ex::class == KtpRspEx::class) {
        return@buildJsonObject
    }
    ex::class.declaredMemberProperties.forEach { property ->
        if (property.visibility != KVisibility.PUBLIC) {
            return@forEach
        }
        val propertyName = property.name
        if (
            propertyName.startsWith("$") ||
                propertyName in ignoredFieldNames ||
                propertyName in standardProblemKeys
        ) {
            return@forEach
        }
        property.getter.isAccessible = true
        put(propertyName, property.getter.call(ex).toJsonElement())
    }
}

@Suppress("CyclomaticComplexMethod")
private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Enum<*> -> JsonPrimitive(this.name)
        is Map<*, *> ->
            buildJsonObject {
                this@toJsonElement.forEach { (key, value) ->
                    key?.toString()?.let { put(it, value.toJsonElement()) }
                }
            }
        is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
        is Array<*> -> JsonArray(this.map { it.toJsonElement() })
        is IntArray -> JsonArray(this.map { JsonPrimitive(it) })
        is LongArray -> JsonArray(this.map { JsonPrimitive(it) })
        is ShortArray -> JsonArray(this.map { JsonPrimitive(it) })
        is FloatArray -> JsonArray(this.map { JsonPrimitive(it) })
        is DoubleArray -> JsonArray(this.map { JsonPrimitive(it) })
        is BooleanArray -> JsonArray(this.map { JsonPrimitive(it) })
        is ByteArray -> JsonArray(this.map { JsonPrimitive(it) })
        is CharArray -> JsonArray(this.map { JsonPrimitive(it.toString()) })
        else -> JsonPrimitive(this.toString())
    }
