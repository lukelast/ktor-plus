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

class KtpConfig(val config: Config, val env: Env) {
    companion object {
        const val CONF_FILE_EXT = "conf"
        const val CONF_FILE_DIR = "ktp"
        const val ENV_PATH = "env"
        const val SYS_ENV_PREFIX = "sysenv"
        const val ENV_CONFIG_KEY = "KTP_CONFIG"

        init {
            // https://github.com/lightbend/config#optional-system-or-env-variable-overrides
            // Tell the config library to allow environment variables to override config values.
            // Is this needed if we add the overrides explicitly below?
            System.setProperty("config.override_with_env_vars", "true")
        }

        fun create(buildConfig: KtpConfigBuilder.() -> Unit = {}): KtpConfig {
            val builder = KtpConfigBuilder()
            builder.buildConfig()
            return builder.build()
        }
    }

    val data: KtpConfigData = config.extract()

    @PublishedApi internal val cache = ConcurrentHashMap<KClass<*>, Any>()

    /**
     * Deserialize part of the configuration into a data class. The name of the data class must
     * match the name of the configuration. For example, if your class name is `Blah` it will read
     * the configuration object under "blah".
     *
     * Results are cached to avoid expensive reflection and parsing operations.
     */
    inline fun <reified T> extractChild(): T {
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(T::class) {
            val configPathRoot = T::class.simpleName!!.replaceFirstChar { it.lowercase() }
            try {
                config.extract<T>(configPathRoot)
            } catch (ex: Exception) {
                // The Config4K library just throws an NPE when a data class field has no default
                // value and no value in the config.
                // Here we attempt to detect which field is missing to give a better error message.
                val missingField = findMissingConfigField(config, T::class, configPathRoot)
                if (missingField != null) {
                    throw IllegalStateException(
                        "Unable to construct config data class: ${T::class.qualifiedName}. Missing field: $missingField",
                        ex,
                    )
                }
                throw ex
            }
        } as T
    }

    /**
     * Get an instance of a sub configuration. A sub configuration class must have a single public
     * constructor which has one parameter of type [KtpConfig].
     */
    inline fun <reified T : Any> get(): T = createInstance(T::class)

    fun <T : Any> createInstance(klass: KClass<T>): T {
        try {
            @Suppress("UNCHECKED_CAST")
            return cache.getOrPut(klass) { klass.primaryConstructor!!.call(this) } as T
        } catch (ex: InvocationTargetException) {
            // Unwrap exceptions thrown in the constructor.
            throw ex.targetException
        } catch (ex: Exception) {
            throw RuntimeException(
                "Your config class must have a primary constructor " +
                    "which takes one parameter '${this::class.simpleName}'.",
                ex,
            )
        }
    }

    fun getAllConfig(): Map<String, String> =
        config
            .toRecords()
            .filter {
                !it.path.startsWith("java.") &&
                    !it.path.startsWith("os.") &&
                    !it.path.startsWith("${SYS_ENV_PREFIX}.")
            }
            .associate { it.path to it.value }

    fun logAllConfig() {
        val txt = getAllConfig().entries.joinToString(", ") { "${it.key} = ${it.value}" }
        Logger.getLogger(this::class.java.name).info("All config values: $txt")
    }

    /** Create a config file containing all possible values. */
    fun genTemplate(): String {
        val options =
            ConfigRenderOptions.defaults()
                .setJson(false)
                .setFormatted(true)
                .setComments(true)
                .setOriginComments(true)
        val filteredConfig =
            filterConfig(config.root()) { path, value ->
                value.origin().description() != "system properties" &&
                    value.origin().description() != "env variables" &&
                    path != "env"
            } ?: error("No config")
        // These comment lines don't seem useful.
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
            // This is a kotlin library type so must be a primitive leaf node.
            add(propertyName)
        } else {
            addAll(getLeafPaths(propertyType, propertyName))
        }
    }
}

/**
 * Finds the first missing configuration field for a given data class.
 *
 * @param config The configuration to check against
 * @param kClass The class to check for missing fields
 * @param pathPrefix The configuration path prefix for the class
 * @return The full path to the first missing field, or null if all fields are present
 */
@PublishedApi
internal fun findMissingConfigField(
    config: Config,
    kClass: KClass<*>,
    pathPrefix: String,
): String? {
    val allPaths = getLeafPaths(kClass).map { "$pathPrefix.$it" }
    return allPaths.firstOrNull { fieldPath -> !config.hasPath(fieldPath) }
}

/**
 * Recursively filters a ConfigValue, providing the full path to the predicate.
 *
 * @param value The ConfigValue to process.
 * @param path The path to the current value.
 * @param predicate The filter condition, which accepts both path and value.
 * @return A new, filtered ConfigValue, or null if the branch is pruned.
 */
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
                // Construct the path for the child element
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
            val filteredList =
                originalList.mapIndexedNotNull { index, item ->
                    // Construct the path for the list item
                    val itemPath = "$path[$index]"
                    filterConfig(item, itemPath, predicate)
                }

            if (filteredList.isNotEmpty()) ConfigValueFactory.fromIterable(filteredList) else null
        }
        else -> {
            // Base case: Apply the path-aware predicate to the primitive value
            if (predicate(path, value)) value else null
        }
    }
}
