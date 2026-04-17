package net.ghue.ktp.config

fun fakeConfig(
    priority: Int,
    baseName: String = "",
    env: String = "",
    text: String = "",
): ConfigFile =
    ConfigFile(
        absolutePath = "",
        fileName =
            listOf(priority.toString(), baseName, KtpConfig.CONFIG_FILE_EXT)
                .filter { it.isNotEmpty() }
                .joinToString("."),
        priority = priority,
        baseName = baseName,
        envName = env,
        text = text,
    )
