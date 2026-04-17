package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigResolveOptions
import net.ghue.ktp.config.KtpConfig.Companion.ENV_CONFIG_PATH
import net.ghue.ktp.log.log

class KtpConfigBuilder {
    var env: Env = findEnvironment()
    var overrideMap: MutableMap<String, Any> = mutableMapOf()

    fun overrideValue(key: String, value: Any) = overrideMap.put(key, value)

    fun setUnitTestEnv() {
        env = Env.TEST_UNIT
    }

    fun setIntegrationTestEnv() {
        env = Env.TEST_INTEGRATION
    }

    fun build(): KtpConfig {
        val config = buildConfigForEnv(env, overrideMap)
        return KtpConfig(config, env)
    }
}

private fun buildConfigForEnv(env: Env, overrideMap: Map<String, Any> = emptyMap()): Config {
    val allConfigFiles = scanConfigFiles()
    val usedConfigFiles = allConfigFiles.filter { it.appliesTo(env) }
    val ignoredFiles = allConfigFiles - usedConfigFiles.toSet()
    log {}
        .info {
            val ignored =
                if (ignoredFiles.isNotEmpty()) {
                    "Files ignored: ${ignoredFiles.map { it.fileName }}"
                } else {
                    "No config files ignored"
                }
            "env=(${env.name}) Building config using: ${usedConfigFiles.map { it.fileName }}. $ignored."
        }
    return buildConfig(env, usedConfigFiles, overrideMap)
}

fun buildConfig(
    env: Env,
    configFiles: List<ConfigFile>,
    /** These values have the highest precedence. */
    overrideMap: Map<String, Any> = emptyMap(),
): Config {
    val configs = buildList {
        add(ConfigFactory.parseMap(mapOf(ENV_CONFIG_PATH to env.name), "current environment"))
        add(ConfigFactory.parseMap(overrideMap, "overrides"))
        add(ConfigFactory.systemEnvironmentOverrides())
        buildConfigFromEnvVar()?.let { add(it) }
        configFiles.sorted().forEach { file ->
            add(
                ConfigFactory.parseString(
                    file.text,
                    ConfigParseOptions.defaults().setOriginDescription(file.fileName),
                )
            )
        }
    }
    return configs
        .fold(ConfigFactory.empty()) { left, right -> left.withFallback(right) }
        .resolve(ConfigResolveOptions.defaults())
}

fun buildConfigFromEnvVar(
    configText: String = System.getenv(KtpConfig.KTP_CONFIG_ENV_VAR) ?: ""
): Config? {
    if (configText.isBlank()) {
        return null
    }
    return try {
        ConfigFactory.parseString(
            configText,
            ConfigParseOptions.defaults().setOriginDescription(KtpConfig.KTP_CONFIG_ENV_VAR),
        )
    } catch (ex: Exception) {
        log {}.warn(ex) { "Failed to parse config from ENV var: ${KtpConfig.KTP_CONFIG_ENV_VAR}" }
        null
    }
}
