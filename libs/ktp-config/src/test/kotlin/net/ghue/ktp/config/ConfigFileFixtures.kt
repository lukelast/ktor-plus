package net.ghue.ktp.config

fun fakeConfigFile(
    priority: Int,
    configName: String = "",
    envName: String = "",
    text: String = "",
): ConfigFile =
    ConfigFile(
        resourceUri = "",
        fileName =
            listOf(priority.toString(), configName, KtpConfig.CONFIG_FILE_EXT)
                .filter { it.isNotEmpty() }
                .joinToString("."),
        priority = priority,
        configName = configName,
        envName = envName,
        text = text,
    )
