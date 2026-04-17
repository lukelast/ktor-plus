package net.ghue.ktp.config

data class ConfigFile(
    /** Resource URI for the config file. Could be in a jar. */
    val resourceUri: String,
    /** Name of the original file like `default.config.conf`. */
    val fileName: String,
    /**
     * The priority is the first dot-separated token of the file name (a digit 0-9) like `5` in
     * `5.database.prod.conf`. Lower values have higher precedence when configs are merged.
     */
    val priority: Int,
    /**
     * The optional config name of the file like `config` in `default.config.conf`. If not present,
     * this will be an empty string.
     */
    val configName: String,
    val envName: String,

    /** The text content of the config file. */
    val text: String,
) : Comparable<ConfigFile> {
    override fun compareTo(other: ConfigFile): Int =
        compareValuesBy(
            this,
            other,
            { it.priority },
            // Files with an env come first, sorted by env name.
            { it.envName.ifEmpty { Char.MAX_VALUE.toString() } },
            // Then files with a base name, sorted by base name.
            { it.configName.ifEmpty { Char.MAX_VALUE.toString() } },
            // Fallback to path for stable sort
            { it.resourceUri.reversed() },
        )

    companion object {
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
            if (priority !in 0..9) {
                error("Invalid config file priority: $resourceUri. Must be between 0 and 9")
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
    @Suppress("ReturnCount")
    fun appliesTo(env: Env): Boolean {
        // Prevent unit tests from picking up local dev override configs.
        if (env.isTest && envName.isEmpty() && (configName == "local" || configName.isEmpty())) {
            return false
        }
        if (envName.isEmpty()) {
            return true // No specific environment, so it applies to all.
        }
        if (envName == env.name) {
            return true // Exact match
        }
        return false
    }
}
