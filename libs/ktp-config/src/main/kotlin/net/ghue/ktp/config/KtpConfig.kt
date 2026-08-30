package net.ghue.ktp.config

import com.typesafe.config.*
import io.github.config4k.extract
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * [rawConfig] must be fully resolved (no `${}` substitutions), or construction throws
 * [com.typesafe.config.ConfigException.NotResolved]. String values are trimmed on construction, so
 * [config] holds a trimmed copy rather than the instance passed in.
 */
class KtpConfig(rawConfig: Config, val env: Env) {
    companion object {
        const val CONFIG_FILE_EXT = "conf"
        const val CONFIG_FILE_DIR = "ktp"
        const val ENV_CONFIG_PATH = "env"
        const val KTP_CONFIG_ENV_VAR = "KTP_CONFIG"

        init {
            // Env-var overrides; maybe redundant with buildConfig's systemEnvironmentOverrides().
            System.setProperty("config.override_with_env_vars", "true")
        }

        fun create(configure: KtpConfigBuilder.() -> Unit = {}): KtpConfig {
            val builder = KtpConfigBuilder()
            builder.configure()
            return builder.build()
        }
    }

    /** The fully resolved config with string values trimmed. */
    val config: Config = rawConfig.withTrimmedStrings()

    val data: KtpConfigData = config.extract()

    @PublishedApi internal val cache = ConcurrentHashMap<KClass<*>, Any>()

    /** Extracts the config object keyed by [T]'s lower-camel name (`Blah` reads "blah"); cached. */
    inline fun <reified T> extractChild(): T {
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(T::class) {
            val configPathRoot = T::class.simpleName!!.replaceFirstChar { it.lowercase() }
            try {
                config.extract<T>(configPathRoot)
            } catch (ex: Exception) {
                // Config4K throws a bare NPE on a missing field with no default; name it instead.
                val missingField = findMissingConfigField(config, T::class, configPathRoot)
                if (missingField != null) {
                    throw IllegalStateException(
                        "Unable to construct config data class: ${T::class.qualifiedName}. " +
                            "Missing field: $missingField",
                        ex,
                    )
                }
                throw ex
            }
        } as T
    }

    /** Returns the cached [T] instance; its primary constructor must take only a [KtpConfig]. */
    inline fun <reified T : Any> get(): T = createInstance(T::class)

    fun <T : Any> createInstance(klass: KClass<T>): T {
        try {
            @Suppress("UNCHECKED_CAST")
            return cache.getOrPut(klass) { klass.primaryConstructor!!.call(this) } as T
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        } catch (ex: Exception) {
            throw IllegalStateException(
                "Your config class must have a primary constructor " +
                    "which takes one parameter '${this::class.simpleName}'.",
                ex,
            )
        }
    }

    fun getAllConfig(): Map<String, String> = config.toRecords().associate { it.path to it.value }

    fun logAllConfig() {
        val txt = getAllConfig().entries.joinToString(", ") { "${it.key} = ${it.value}" }
        Logger.getLogger(this::class.java.name).info("All config values: $txt")
    }

    /** Render the text of a config file listing every known value, for use as a template. */
    fun renderTemplate(): String {
        val options =
            ConfigRenderOptions.defaults()
                .setJson(false)
                .setFormatted(true)
                .setComments(true)
                .setOriginComments(true)
        val filteredConfig =
            filterConfig(config.root()) { path, _ -> path != ENV_CONFIG_PATH } ?: error("No config")
        // Drop the noisy "hardcoded value" origin comments.
        val extraComments = Regex("^.*# hardcoded value.*\\R?", RegexOption.MULTILINE)
        val renderedConfig = filteredConfig.render(options).replace(extraComments, "")
        return renderedConfig
    }
}

/** Turn a tree of data classes into a list of field paths. */
@PublishedApi
internal fun getLeafPaths(kClass: KClass<*>, prefix: String = ""): List<String> = buildList {
    for (property in kClass.memberProperties) {
        val propertyName = if (prefix.isNotEmpty()) "$prefix.${property.name}" else property.name
        val propertyType = property.returnType.jvmErasure
        if (propertyType.qualifiedName?.startsWith("kotlin") == true) {
            // Kotlin stdlib types are treated as primitive leaves.
            add(propertyName)
        } else {
            addAll(getLeafPaths(propertyType, propertyName))
        }
    }
}

/** Returns the path of the first [kClass] leaf field missing from [config], or null if none. */
@PublishedApi
internal fun findMissingConfigField(
    config: Config,
    kClass: KClass<*>,
    pathPrefix: String,
): String? {
    val allPaths = getLeafPaths(kClass).map { "$pathPrefix.$it" }
    return allPaths.firstOrNull { fieldPath -> !config.hasPath(fieldPath) }
}

/** Keeps leaves whose full dotted path passes [predicate]; returns null if none survive. */
private fun filterConfig(
    value: ConfigValue,
    path: String = "",
    predicate: (path: String, value: ConfigValue) -> Boolean,
): ConfigValue? {
    return when (value.valueType()) {
        ConfigValueType.OBJECT -> {
            val originalObject = value as ConfigObject
            val filteredMap = mutableMapOf<String, ConfigValue>()

            for ((key, childValue) in originalObject) {
                val childPath = if (path.isEmpty()) key else "$path.$key"
                val filteredChild = filterConfig(childValue, childPath, predicate)
                if (filteredChild != null) {
                    filteredMap[key] = filteredChild
                }
            }

            if (filteredMap.isNotEmpty()) ConfigValueFactory.fromMap(filteredMap) else null
        }
        ConfigValueType.LIST -> {
            val originalList = value as ConfigList
            val filteredList = originalList.mapIndexedNotNull { index, item ->
                val itemPath = "$path[$index]"
                filterConfig(item, itemPath, predicate)
            }

            if (filteredList.isNotEmpty()) ConfigValueFactory.fromIterable(filteredList) else null
        }
        else -> {
            if (predicate(path, value)) value else null
        }
    }
}
