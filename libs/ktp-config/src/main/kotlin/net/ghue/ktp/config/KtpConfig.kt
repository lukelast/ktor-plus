package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigResolveOptions
import java.util.logging.Logger

object KtpConfig {
    const val CONF_FILE_EXT = "conf"
    const val CONF_FILE_DIR = "ktp"
    const val ENV_PATH = "env"
    const val SYS_ENV_PREFIX = "sysenv"

    init {
        // https://github.com/lightbend/config#optional-system-or-env-variable-overrides
        // Tell the config library to allow environment variables to override config values.
        // Is this needed if we add the overrides explicitly below?
        System.setProperty("config.override_with_env_vars", "true")
    }

    fun createManager(): KtpConfigManager {
        val env = findEnvironment()
        return createManagerForEnv(env)
    }

    fun createManagerForTest(overrideMap: Map<String, Any> = emptyMap()): KtpConfigManager =
        createManagerForEnv(Env.TEST_UNIT, overrideMap)

    fun createManagerForEnv(
        env: Env,
        overrideMap: Map<String, Any> = emptyMap(),
    ): KtpConfigManager {
        val config = createConfigForEnv(env, overrideMap)
        return KtpConfigManager(config, env)
    }

    fun createConfigForEnv(env: Env, overrideMap: Map<String, Any> = emptyMap()): Config {
        val allConfigFiles = scanConfigFiles()
        val usedConfigFiles = allConfigFiles.filter { it.filterForEnv(env) }
        val ignoredFiles = allConfigFiles - usedConfigFiles.toSet()
        Logger.getLogger(this::class.java.name)
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
}
