package net.ghue.ktp.ktor.app.debug

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import net.ghue.ktp.config.ConfigRecord
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.toRecords

fun Application.installConfigDebugInfo(config: KtpConfig) {
    routing {
        route("/debug") {
            get("/config") {
                val records = config.config.toRecords().toMutableList()
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
                call.respondText(
                    CONFIG_TEMPLATE.trimIndent().replace("{{TABLE}}", tableContents),
                    ContentType.Text.Html.withCharset(Charsets.UTF_8),
                )
            }

            get("/gclog") {
                val gcLogFile = Paths.get("/tmp", "gc.log")
                if (gcLogFile.isRegularFile()) {
                    call.respondText(gcLogFile.readText())
                } else {
                    call.respondText("No $gcLogFile file found")
                }
            }

            get("/version") { call.respondText(config.data.app.version) }
        }
    }
}

// language=HTML
private const val CONFIG_TEMPLATE =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>App Configuration</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                margin: 32px;
                background-color: #f4f4f4;
            }
            table {
                border-collapse: collapse;
                width: 100%;
                max-width: 1600px;
                margin: 16px 0;
                font-size: 14px;
                text-align: left;
                background-color: #ffffff;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
            }
            th, td {
                padding: 8px 16px;
                border-bottom: 1px solid #dddddd;
                word-break: break-word;
            }
            th {
                background-color: #009879;
                color: #ffffff;
            }
            tr:hover {
                background-color: #f1f1f1;
            }
            caption {
                caption-side: top;
                font-size: 24px;
                font-weight: bold;
                padding: 8px;
            }
        </style>
    </head>
    <body>
        <table>
            <thead>
                <tr>
                    <th>Path</th>
                    <th>Value</th>
                    <th>Source</th>
                </tr>
            </thead>
            <tbody>
                {{TABLE}}
            </tbody>
        </table>
    </body>
    </html>
    """
