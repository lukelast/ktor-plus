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

    val records = ktpConfig.config.toRecords().toMutableList()
    records.add(collectGcInfo())
    records.add(collectMaxGcHeapMib())
    records.add(collectCpuCores())
    records.add(
        ConfigRecord(
            path = "kotlin version",
            value = KotlinVersion.CURRENT.toString(),
            source = "library",
        )
    )
    val tableContents =
        records
            .sortedWith(
                compareBy<ConfigRecord> { it.source.contains("env", ignoreCase = true) }
                    .thenBy { it.path }
            )
            .joinToString("\n") {
                // language=HTML
                "<tr></tr><td>${it.path}</td><td>${it.value}</td><td>${it.source}</td></tr>"
            }
    respondText(
        CONFIG_TEMPLATE.trimIndent().replace("{{TABLE}}", tableContents),
        ContentType.Text.Html.withCharset(Charsets.UTF_8),
    )
}
