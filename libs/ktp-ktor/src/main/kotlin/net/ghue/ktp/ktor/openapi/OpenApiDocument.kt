package net.ghue.ktp.ktor.openapi

import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.path
import io.ktor.server.routing.routingRoot
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val excluded = AttributeKey<Unit>("KtpOpenApiExcluded")

/** Keeps this route and its children out of [openApiDocument]: for routes the SPA never calls. */
fun Route.excludeFromOpenApi(): Route = apply { attributes.put(excluded, Unit) }

private fun Route.isExcluded(): Boolean =
    generateSequence(this) { it.parent }.any { it.attributes.contains(excluded) }

/**
 * Builds a deterministic contract from installed routes, without environment or deployment data.
 */
fun Application.openApiDocument(
    title: String = "Application API",
    includePrefixes: List<String> = listOf("/api"),
): String {
    val source =
        OpenApiDocSource.Routing(
            routes = {
                routingRoot.descendants().filter { route ->
                    val path = route.path()
                    includePrefixes.any { path.matchesPrefix(it) } && !route.isExcluded()
                }
            }
        )
    val defaults = OpenApiDoc(info = OpenApiInfo(title, "1"))
    val document = Json.parseToJsonElement(source.read(this, defaults).content).jsonObject
    val json = Json { prettyPrint = true }
    val contract = document.mapOperations { it.withRequiredBody().withoutHeaderParameters() }
    return json.encodeToString(canonicalJson(contract)) + "\n"
}

private fun String.matchesPrefix(prefix: String): Boolean =
    this == prefix.trimEnd('/') || startsWith(prefix.trimEnd('/') + "/")

/** The operations are the objects in a path item; its other members are strings and arrays. */
private fun JsonObject.mapOperations(transform: (JsonObject) -> JsonObject): JsonObject {
    val paths = this["paths"]?.jsonObject ?: return this
    val mapped = paths.mapValues { (_, item) ->
        JsonObject(
            item.jsonObject.mapValues { (_, member) ->
                if (member is JsonObject) transform(member) else member
            }
        )
    }
    return JsonObject(this + ("paths" to JsonObject(mapped)))
}

/** KTP routes call `receive<T>()` unconditionally; Ktor's inference leaves bodies optional. */
private fun JsonObject.withRequiredBody(): JsonObject {
    val body = this["requestBody"] as? JsonObject ?: return this
    val requiredBody = JsonObject(body + ("required" to JsonPrimitive(true)))
    return JsonObject(this + ("requestBody" to requiredBody))
}

/**
 * Ktor records every request header a handler reads (a logged `X-Forwarded-For`, say) as a
 * parameter of the operation. The SPA sets no headers, its session is a cookie, so they would only
 * be noise in its generated types.
 */
private fun JsonObject.withoutHeaderParameters(): JsonObject {
    val parameters = this["parameters"] as? JsonArray ?: return this
    val kept = parameters.filterNot { (it as? JsonObject)?.get("in") == JsonPrimitive("header") }
    return if (kept.isEmpty()) JsonObject(this - "parameters")
    else JsonObject(this + ("parameters" to JsonArray(kept)))
}

private fun canonicalJson(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { canonicalJson(it.value) })
        is JsonArray -> JsonArray(element.map(::canonicalJson))
        else -> element
    }
