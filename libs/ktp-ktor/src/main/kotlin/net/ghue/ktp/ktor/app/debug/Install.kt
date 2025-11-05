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
