package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigResolveOptions
import java.util.logging.Logger
import net.ghue.ktp.config.KtpConfig.Companion.ENV_PATH
import net.ghue.ktp.config.KtpConfig.Companion.SYS_ENV_PREFIX

class KtpConfigBuilder() {
    var env: Env = findEnvironment()
    var overrideMap: MutableMap<String, Any> = mutableMapOf()

    fun configValue(key: String, value: Any) = overrideMap.put(key, value)

    fun setUnitTestEnv() {
        env = Env.TEST_UNIT
    }

    fun setIntegrationEnv() {
        env = Env.TEST_INTEGRATION
    }

    fun build(): KtpConfig {
        val config = createConfigForEnv(env, overrideMap)
        return KtpConfig(config, env)
    }
}

private fun createConfigForEnv(env: Env, overrideMap: Map<String, Any> = emptyMap()): Config {
    val allConfigFiles = scanConfigFiles()
    val usedConfigFiles = allConfigFiles.filter { it.filterForEnv(env) }
    val ignoredFiles = allConfigFiles - usedConfigFiles.toSet()
    Logger.getLogger(KtpConfig::class.java.name)
        .info(
            "Building config using: ${usedConfigFiles.map { it.fileName }}. " +
                "Files ignored because env=(${env.name}): ${ignoredFiles.map { it.fileName }}."
        )
    return buildConfig(env, usedConfigFiles, overrideMap)
}

fun buildConfig(
    env: Env,
    configFiles: List<ConfigFile>,
    /** These values have the highest precedence. */
    overrideMap: Map<String, Any> = emptyMap(),
): Config {
    val configs = buildList {
        add(ConfigFactory.parseMap(mapOf(ENV_PATH to env.name), "current environment"))
        add(ConfigFactory.parseMap(overrideMap, "overrides"))
        add(ConfigFactory.systemEnvironmentOverrides())
        add(ConfigFactory.systemEnvironment().atPath(SYS_ENV_PREFIX))
        add(ConfigFactory.systemProperties())
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
