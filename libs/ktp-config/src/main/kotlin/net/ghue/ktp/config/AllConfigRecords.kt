package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import kotlinx.serialization.Serializable

private val protectedPathWords = listOf("secret", "applicationKey", "password")

/** List all configuration values for informational purposes. Make sure secrets are hidden. */
fun Config.toRecords(): List<ConfigRecord> =
    entrySet()
        .map {
            ConfigRecord(
                path = it.key,
                value =
                    it.value
                        .render(ConfigRenderOptions.concise().setJson(false))
                        .replace("\"", "")
                        .take(200),
                source =
                    with(it.value.origin()) {
                        if (!resource().isNullOrBlank()) {
                            resource()
                        } else if (!filename().isNullOrBlank()) {
                            filename()
                        } else {
                            description()
                        }
                    },
            )
        }
        .map { configRecord ->
            if (
                protectedPathWords.any { word ->
                    configRecord.path.contains(word, ignoreCase = true)
                }
            ) {
                configRecord.copy(value = "${configRecord.value.length} chars")
            } else {
                configRecord
            }
        }
        // Remove uninteresting noise.
        .filter {
            it.path !in
                setOf(
                    "java.class.path",
                    "java.class.version",
                    "java.library.path",
                    "java.runtime.name",
                    "java.runtime.version",
                    "java.specification.name",
                    "java.specification.vendor",
                    "java.specification.version",
                    "java.vendor.url.bug",
                    "java.vm.specification.name",
                    "java.vm.specification.vendor",
                    "java.vm.specification.version",
                    "jdk.debug",
                    "stderr.encoding",
                    "stdout.encoding",
                    "sun.arch.data.model",
                    "sun.boot.library.path",
                    "sun.cpu.endian",
                    "sun.cpu.isalist",
                    "sun.io.unicode.encoding",
                    "sun.java.launcher",
                    "sun.jnu.encoding",
                    "sun.management.compiler",
                    "sun.os.patch.level",
                    "sun.stderr.encoding",
                    "sun.stdout.encoding",
                    "user.country",
                    "user.language",
                    "user.script",
                    "user.variant",
                )
        }
        .sortedBy { it.path }

@Serializable data class ConfigRecord(val path: String, val value: String, val source: String)
