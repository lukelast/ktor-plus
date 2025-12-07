package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import kotlinx.serialization.Serializable

private val protectedPathWords = listOf("secret", "applicationKey", "password")

private const val MAX_VALUE_SIZE = 200

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
                        .take(MAX_VALUE_SIZE),
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
        .sortedBy { it.path }

@Serializable data class ConfigRecord(val path: String, val value: String, val source: String = "")
