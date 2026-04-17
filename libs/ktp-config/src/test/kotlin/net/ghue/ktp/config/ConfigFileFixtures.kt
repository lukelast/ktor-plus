package net.ghue.ktp.config

fun fakeConfig(
    priority: Int,
    configName: String = "",
    env: String = "",
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
        envName = env,
        text = text,
    )
