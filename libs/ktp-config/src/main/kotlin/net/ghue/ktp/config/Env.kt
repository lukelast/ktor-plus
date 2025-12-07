package net.ghue.ktp.config

import com.typesafe.config.ConfigFactory

private val envVarNames = listOf("KTP_ENV", "ENV", "KUBERNETES_NAMESPACE")
private const val LOCAL_DEV_PATH = "localDevEnv"
const val DEFAULT_ENV = "localdev"

/** Represents a runtime environment. */
data class Env(val name: String, val isCiTest: Boolean = false) {
    init {
        require(name.isNotBlank()) { "env must not be blank" }
        require(name.matches(Regex("[a-z0-9-]+"))) {
            "env must only contain lowercase letters, numbers, and dashes"
        }
    }

    override fun toString(): String = name

    val isDefault = name == DEFAULT_ENV && !isCiTest

    val isLocalDev =
        !isCiTest && (DEFAULT_ENV == name || name.startsWith("$DEFAULT_ENV-", ignoreCase = true))

    companion object {
        val TEST_UNIT = Env("test-unit", isCiTest = true)
        val TEST_INTEGRATION = Env("test-int", isCiTest = true)
    }
}

fun findEnvironment(): Env {
    envVarNames
        .flatMap { listOf(System.getenv(it), System.getProperty(it)) }
        .firstOrNull { !it.isNullOrBlank() }
        ?.let {
            return Env(it)
        }
    try {
        val configFileName = "${KtpConfig.CONF_FILE_DIR}/0.${KtpConfig.CONF_FILE_EXT}"
        val localConfigFile = ConfigFactory.parseResources(configFileName)
        val localDevEnv = localConfigFile.getString(LOCAL_DEV_PATH)
        if (!localDevEnv.isNullOrBlank()) {
            return Env(localDevEnv)
        }
    } catch (_: Exception) {
        // Doesn't exist, ignore.
    }
    // If nothing is configured.
    return Env(DEFAULT_ENV)
}
