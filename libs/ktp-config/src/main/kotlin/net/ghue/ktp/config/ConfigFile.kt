package net.ghue.ktp.config

data class ConfigFile(
    /** Fullest, absolute path to the config file. Could be in a jar. */
    val absolutePath: String,
    /** Name of the original file like `default.config.conf`. */
    val fileName: String,
    /**
     * The group is the first part of the file name like `default` in `default.config.conf`. It
     * represents the ordering importance of the config.
     */
    val priority: Int,
    /**
     * The optional name of the config file like `config` in `default.config.conf`. If not present,
     * this will be an empty string.
     */
    val name: String,
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
            // Then files with a name, sorted by name.
            { it.name.ifEmpty { Char.MAX_VALUE.toString() } },
            // Fallback to path for stable sort
            { it.absolutePath.reversed() },
        )

    companion object {
        fun create(fullPath: String, content: String): ConfigFile {
            val fileName = fullPath.substringAfterLast("/")
            val nameTokens = fileName.split(".").filter { it != KtpConfig.CONF_FILE_EXT }
            if (nameTokens.isEmpty()) {
                error("Invalid config file name: $fullPath")
            }
            val priority =
                nameTokens[0].toIntOrNull()
                    ?: error(
                        "Invalid config file name: $fullPath. Must start with a priority number."
                    )
            val name = nameTokens.getOrElse(1) { "" }
            return ConfigFile(
                absolutePath = fullPath,
                fileName = fileName,
                priority = priority,
                name = name,
                envName = nameTokens.getOrNull(2) ?: "",
                text = content,
            )
        }
    }

    /** Does this [ConfigFile] belong in the given [env]? */
    fun filterForEnv(env: Env): Boolean {
        if (envName.isEmpty()) {
            return true // No specific environment, so it applies to all.
        }
        return envName == env.name
    }
}
