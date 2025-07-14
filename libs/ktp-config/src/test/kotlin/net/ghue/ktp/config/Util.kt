package net.ghue.ktp.config

import net.ghue.ktp.config.KtpConfig.CONF_FILE_EXT

fun fakeConfig(priority: Int, name: String = "", env: String = "", text: String = ""): ConfigFile =
    ConfigFile(
        absolutePath = "",
        fileName =
            listOf(priority.toString(), name, CONF_FILE_EXT)
                .filter { it.isNotEmpty() }
                .joinToString("."),
        priority = priority,
        name = name,
        envName = env,
        text = text,
    )
