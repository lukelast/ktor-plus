package net.ghue.ktp.ktor.app.debug

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Responds with an HTML index page listing all debug endpoints with their status.
 *
 * The index page displays a table showing each endpoint's path, description, and enabled/disabled
 * status. Only enabled endpoints are clickable links.
 */
suspend fun RoutingCall.respondDebugIndex(config: ConfigDebugInfoConfig) {
    val endpoints = buildEndpointList(config)
    val html = INDEX_TEMPLATE.trimIndent().replace("{{ENDPOINT_ROWS}}", endpoints.toHtmlRows())

    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
}

/** Information about a debug endpoint. */
internal data class EndpointInfo(val path: String, val description: String, val isEnabled: Boolean)

private fun buildEndpointList(config: ConfigDebugInfoConfig): List<EndpointInfo> {
    return listOf(
        EndpointInfo(
            path = config.routePrefix + DebugEndpoints.CONFIG,
            description = "Configuration values, runtime info, and environment variables",
            isEnabled = config.enableConfigEndpoint,
        ),
        EndpointInfo(
            path = config.routePrefix + DebugEndpoints.GCLOG,
            description = "Garbage collection log file contents",
            isEnabled = config.enableGcLogEndpoint,
        ),
        EndpointInfo(
            path = config.routePrefix + DebugEndpoints.THREADS,
            description = "Thread dump with stack traces and lock information",
            isEnabled = config.enableThreadDumpEndpoint,
        ),
        EndpointInfo(
            path = config.routePrefix + DebugEndpoints.VERSION,
            description = "Application version string",
            isEnabled = config.enableVersionEndpoint,
        ),
    )
}

private fun List<EndpointInfo>.toHtmlRows(): String =
    joinToString("\n") { endpoint ->
        val statusClass = if (endpoint.isEnabled) "status-enabled" else "status-disabled"
        val statusText = if (endpoint.isEnabled) "Enabled" else "Disabled"
        val rowClass = if (endpoint.isEnabled) "enabled-row" else "disabled-row"
        val linkHtml =
            if (endpoint.isEnabled) {
                """<a href="${endpoint.path}">${endpoint.path}</a>"""
            } else {
                endpoint.path
            }

        """
        <tr class="$rowClass">
            <td class="path-cell">$linkHtml</td>
            <td class="description-cell">${endpoint.description}</td>
            <td class="status-cell"><span class="$statusClass">$statusText</span></td>
        </tr>
        """
            .trimIndent()
    }
