package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory

private val envVarNames = listOf("KTP_ENV", "ENV", "KUBERNETES_NAMESPACE")
private const val LOCAL_DEV_ENV_CONFIG_PATH = "localDevEnv"
const val LOCAL_DEV_ENV_NAME = "localdev"

/** Represents a runtime environment. */
data class Env(val name: String, val isTest: Boolean = false) {
    init {
        require(name.isNotBlank()) { "env must not be blank" }
        require(name.matches(Regex("[a-z0-9-]+"))) {
            "env must only contain lowercase letters, numbers, and dashes"
        }
    }

    override fun toString(): String = name

    val isDefault = name == LOCAL_DEV_ENV_NAME && !isTest

    val isLocalDev =
        !isTest &&
            (LOCAL_DEV_ENV_NAME == name ||
                name.startsWith("$LOCAL_DEV_ENV_NAME-", ignoreCase = true))

    companion object {
        val TEST_UNIT = Env("test-unit", isTest = true)
        val TEST_INTEGRATION = Env("test-int", isTest = true)
    }
}

fun findEnvironment(): Env {
    envVarNames
        .flatMap { listOf(System.getenv(it), System.getProperty(it)) }
        .firstOrNull { !it.isNullOrBlank() }
        ?.let {
            // Runs before config is built, so TrimConfigStrings can't cover these values.
            return Env(it.trim())
        }
    // Read 0.conf directly; KtpConfig can't load until the env that selects its files is known.
    val configFileName = "${KtpConfig.CONFIG_FILE_DIR}/0.${KtpConfig.CONFIG_FILE_EXT}"
    return localDevEnvOrNull(ConfigFactory.parseResources(configFileName), configFileName)
        ?: Env(LOCAL_DEV_ENV_NAME)
}

/**
 * Returns the [Env] named by [LOCAL_DEV_ENV_CONFIG_PATH] in [config], or `null` when the key (or
 * the whole file) is absent so the caller falls back to the default. A key that is present but
 * holds an invalid env name fails fast instead of being silently ignored.
 */
internal fun localDevEnvOrNull(config: Config, source: String): Env? {
    val localDevEnv =
        try {
            config.getString(LOCAL_DEV_ENV_CONFIG_PATH)
        } catch (_: ConfigException.Missing) {
            return null
        }
    try {
        return Env(localDevEnv.trim())
    } catch (ex: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Invalid $LOCAL_DEV_ENV_CONFIG_PATH value '$localDevEnv' in $source: ${ex.message}",
            ex,
        )
    }
}
