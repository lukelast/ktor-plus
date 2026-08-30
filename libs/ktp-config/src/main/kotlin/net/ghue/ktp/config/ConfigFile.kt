package net.ghue.ktp.config

data class ConfigFile(
    /** Resource URI of the config file; may point inside a jar. */
    val resourceUri: String,
    /** Name of the original file like `5.database.prod.conf`. */
    val fileName: String,
    /** First name token, 0-9, like `5` in `5.database.prod.conf`; lower value wins on merge. */
    val priority: Int,
    /** Second name token, like `database` in `5.database.prod.conf`; empty in `0.conf`. */
    val configName: String,
    val envName: String,
    val text: String,
) : Comparable<ConfigFile> {
    override fun compareTo(other: ConfigFile): Int =
        compareValuesBy(
            this,
            other,
            { it.priority },
            // Files with an env come first, sorted by env name.
            { it.envName.ifEmpty { Char.MAX_VALUE.toString() } },
            // Then files with a config name, sorted by config name.
            { it.configName.ifEmpty { Char.MAX_VALUE.toString() } },
            // Fall back to the resource URI so the order is deterministic.
            { it.resourceUri.reversed() },
        )

    companion object {
        private const val MAX_PRIORITY = 9

        fun create(resourceUri: String, text: String): ConfigFile {
            val fileName = resourceUri.substringAfterLast("/")
            val nameTokens = fileName.split(".").filter { it != KtpConfig.CONFIG_FILE_EXT }
            if (nameTokens.isEmpty()) {
                error("Invalid config file name: $resourceUri")
            }
            val priority =
                nameTokens[0].toIntOrNull()
                    ?: error(
                        "Invalid config file name: $resourceUri. Must start with a priority number."
                    )
            if (priority !in 0..MAX_PRIORITY) {
                error(
                    "Invalid config file priority: $resourceUri. " +
                        "Must be between 0 and $MAX_PRIORITY"
                )
            }
            val configName = nameTokens.getOrElse(1) { "" }
            return ConfigFile(
                resourceUri = resourceUri,
                fileName = fileName,
                priority = priority,
                configName = configName,
                envName = nameTokens.getOrNull(2) ?: "",
                text = text,
            )
        }
    }

    /** Does this [ConfigFile] belong in the given [env]? */
    fun appliesTo(env: Env): Boolean {
        // Keep test envs (unit and integration) from picking up local dev override configs.
        if (env.isTest && envName.isEmpty() && (configName == "local" || configName.isEmpty())) {
            return false
        }
        if (envName.isEmpty()) {
            return true
        }
        if (envName == env.name) {
            return true
        }
        return false
    }
}
