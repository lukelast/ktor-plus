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

    /**
     * Override a single config value. The [key] is a path relative to the config root, such as
     * `"app.secret"`. It must match a path that already exists in the merged config files,
     * otherwise [build] fails fast: an override key nothing reads would be a silent no-op.
     */
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
    val envConfig =
        ConfigFactory.parseMap(mapOf(ENV_CONFIG_PATH to env.name), "current environment")
    val baseConfigs = buildList {
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
    val base = mergeConfigs(listOf(envConfig) + baseConfigs)
    if (overrideMap.isEmpty()) {
        return base
    }
    validateOverrideKeys(overrideMap.keys, base)
    val overrides = ConfigFactory.parseMap(overrideMap, "overrides")
    return mergeConfigs(listOf(envConfig, overrides) + baseConfigs)
}

private fun mergeConfigs(configs: List<Config>): Config =
    configs
        .fold(ConfigFactory.empty()) { left, right -> left.withFallback(right) }
        .resolve(ConfigResolveOptions.defaults())

/**
 * An override key that doesn't match an existing config path would be written into the config but
 * read by nothing, silently doing nothing. Fail fast so mistakes like `data.app.secret` (instead of
 * `app.secret`) surface immediately.
 */
private fun validateOverrideKeys(keys: Set<String>, base: Config) {
    require(ENV_CONFIG_PATH !in keys) {
        "Config override '$ENV_CONFIG_PATH' has no effect because the environment always takes " +
            "precedence. Set KtpConfigBuilder.env instead."
    }
    val unknownKeys = keys.filterNot { base.hasPath(it) }
    require(unknownKeys.isEmpty()) {
        "Unknown config override path(s): $unknownKeys. Override keys are relative to the config " +
            "root and must match a config path that already exists, e.g. \"app.secret\"."
    }
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
