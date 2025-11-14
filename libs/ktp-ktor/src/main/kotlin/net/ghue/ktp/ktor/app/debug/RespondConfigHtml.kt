package net.ghue.ktp.ktor.app.debug

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.config.ConfigRecord
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.toRecords
import org.koin.ktor.ext.inject

/** Creates an HTML table with all the configuration values. */
suspend fun RoutingCall.respondConfigHtml() {
    val ktpConfig: KtpConfig by inject()

    val configRecords = ktpConfig.config.toRecords()
    val runtimeRecords =
        listOf(
            collectGcInfo(),
            collectMaxGcHeapMib(),
            collectCpuCores(),
            ConfigRecord(path = "kotlin version", value = KotlinVersion.CURRENT.toString()),
        )
    val environmentRecords =
        System.getenv()
            .entries
            .sortedBy { it.key }
            .map {
                val sanitizedValue =
                    if (
                        it.key.equals(KtpConfig.ENV_CONFIG_KEY, ignoreCase = true) ||
                            it.key.contains("secret", ignoreCase = true)
                    ) {
                        "${it.value.length} chars"
                    } else {
                        it.value
                    }
                ConfigRecord(path = it.key, value = sanitizedValue)
            }
    val systemPropertyRecords =
        System.getProperties().stringPropertyNames().sorted().map { key ->
            ConfigRecord(path = key, value = System.getProperty(key).orEmpty())
        }

    val html =
        CONFIG_TEMPLATE.trimIndent()
            .replace("{{CONFIG_ROWS}}", configRecords.toHtmlRows(includeSource = true))
            .replace("{{RUNTIME_ROWS}}", runtimeRecords.toHtmlRows(includeSource = false))
            .replace("{{ENVIRONMENT_ROWS}}", environmentRecords.toHtmlRows(includeSource = false))
            .replace("{{SYSTEM_ROWS}}", systemPropertyRecords.toHtmlRows(includeSource = false))

    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
}

private fun Iterable<ConfigRecord>.toHtmlRows(includeSource: Boolean): String =
    joinToString("\n") { record ->
        listOfNotNull(
                """<td class="path-cell">${record.path}</td>""",
                """<td class="value-cell">${record.value}</td>""",
                if (includeSource) {
                    """<td class="source-cell">${record.source}</td>"""
                } else {
                    null
                },
            )
            .joinToString(separator = "", prefix = "<tr>", postfix = "</tr>")
    }
