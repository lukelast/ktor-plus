package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import kotlinx.serialization.Serializable

private val secretPathWords = listOf("secret", "applicationKey", "password")

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
            configRecord.copy(value = maskConfigDisplayValue(configRecord.path, configRecord.value))
        }
        .sortedBy { it.path }

fun maskConfigDisplayValue(path: String, value: String): String =
    if (
        path.equals(KtpConfig.KTP_CONFIG_ENV_VAR, ignoreCase = true) ||
            secretPathWords.any { word -> path.contains(word, ignoreCase = true) }
    ) {
        "${value.length} chars"
    } else {
        value
    }

@Serializable data class ConfigRecord(val path: String, val value: String, val source: String = "")
