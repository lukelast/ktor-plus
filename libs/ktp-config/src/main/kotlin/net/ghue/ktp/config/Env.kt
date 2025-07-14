package net.ghue.ktp.config

import com.typesafe.config.ConfigFactory

private val envVarNames = listOf("KTP_ENV", "ENV", "KUBERNETES_NAMESPACE")
const val LOCAL_DEV_PATH = "localDevEnv"
const val DEFAULT_ENV = "dev"

/** Represents a runtime environment. */
data class Env(val name: String) {
    init {
        require(name.isNotBlank()) { "env must not be blank" }
        require(name.matches(Regex("[a-z0-9-]+"))) {
            "env must only contain lowercase letters, numbers, and dashes"
        }
    }

    override fun toString(): String = name
}

fun findEnvironment(): Env {
    envVarNames
        .flatMap { listOf(System.getenv(it), System.getProperty(it)) }
        .firstOrNull { !it.isNullOrBlank() }
        ?.let {
            return Env(it)
        }
    try {
        val configFileName = "${KtpConfig.CONF_FILE_DIR}/${0}.${KtpConfig.CONF_FILE_EXT}"
        val localConfigFile = ConfigFactory.parseResources(configFileName)
        val localDevEnv = localConfigFile.getString(LOCAL_DEV_PATH)
        if (!localDevEnv.isNullOrBlank()) {
            return Env(localDevEnv)
        }
    } catch (ex: Exception) {
        // Doesn't exist, ignore.
    }
    // If nothing is configured.
    return Env(DEFAULT_ENV)
}
