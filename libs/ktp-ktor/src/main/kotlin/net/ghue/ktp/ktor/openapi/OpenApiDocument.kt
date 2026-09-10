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
    return json.encodeToString(canonicalJson(document.withRequiredBodies())) + "\n"
}

private fun String.matchesPrefix(prefix: String): Boolean =
    this == prefix.trimEnd('/') || startsWith(prefix.trimEnd('/') + "/")

/** KTP routes call `receive<T>()` unconditionally; Ktor's inference leaves bodies optional. */
private fun JsonObject.withRequiredBodies(): JsonObject {
    val paths = this["paths"]?.jsonObject ?: return this
    val required = paths.mapValues { (_, item) ->
        JsonObject(
            item.jsonObject.mapValues { (_, operation) ->
                val body = (operation as? JsonObject)?.get("requestBody") as? JsonObject
                if (body == null) operation
                else {
                    val requiredBody = JsonObject(body + ("required" to JsonPrimitive(true)))
                    JsonObject(operation + ("requestBody" to requiredBody))
                }
            }
        )
    }
    return JsonObject(this + ("paths" to JsonObject(required)))
}

private fun canonicalJson(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { canonicalJson(it.value) })
        is JsonArray -> JsonArray(element.map(::canonicalJson))
        else -> element
    }
