package net.ghue.ktp.config

import com.typesafe.config.Config
import io.github.config4k.extract
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class KtpConfigManager(val config: Config, val env: Env) {
    val data: KtpConfigData = config.extract()
    private val cache = ConcurrentHashMap<KClass<*>, Any>()

    /**
     * Deserialize part of the configuration into a data class. The name of the data class must
     * match the name of the configuration. For example, if your class name is `Blah` it will read
     * the configuration object under "blah".
     */
    inline fun <reified T> extractChild(): T {
        val path = T::class.simpleName!!.replaceFirstChar { it.lowercase() }
        return try {
            config.extract<T>(path)
        } catch (ex: Exception) {
            // The Config4K library just throws an NPE when a data class field has no default value
            // and no value in the config.
            // Here we attempt to detect which field is missing to give a better error message.
            val allPaths = getLeafPaths(T::class).map { "$path.$it" }
            for (fieldPath in allPaths) {
                if (!config.hasPath(fieldPath)) {
                    throw IllegalStateException(
                        "Unable to construct config data class: ${T::class.qualifiedName}. Missing field: $fieldPath",
                        ex,
                    )
                }
            }
            throw ex
        }
    }

    /**
     * Get an instance of a sub configuration. A sub configuration class must have a single public
     * constructor which has one parameter of type [KtpConfig].
     */
    inline fun <reified T : Any> get(): T = get(T::class)

    /** Internal method but has to be public for inline reified to use it. */
    fun <T : Any> get(klass: KClass<T>): T {
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
                    !it.path.startsWith("${KtpConfig.SYS_ENV_PREFIX}.")
            }
            .associate { it.path to it.value }

    fun logAllConfig() {
        val txt = getAllConfig().entries.joinToString(", ") { "${it.key} = ${it.value}" }
        Logger.getLogger(this::class.java.name).info("All config values: $txt")
    }
}

/** Turn a tree of data classes into a list of field paths. */
fun getLeafPaths(kClass: KClass<*>, prefix: String = ""): List<String> = buildList {
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
