package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.DocumentSnapshot
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import net.ghue.ktp.ktor.error.ktpRspError

object FirestoreDeserializer {
    private val customDeserializers = mutableMapOf<Class<*>, (Any) -> Any?>()

    // Also run by FirestoreSerializer so either may init first; re-registration is idempotent.
    init {
        FirestoreTypes.registerDefaults()
    }

    fun <T : Any> registerDeserializer(clazz: Class<T>, deserializer: (Any) -> T?) {
        customDeserializers[clazz] = deserializer
    }

    inline fun <reified T : Any> registerDeserializer(noinline deserializer: (Any) -> T?) {
        registerDeserializer(T::class.java, deserializer)
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun deserialize(value: Any?, targetType: KType): Any? {
        if (value == null) {
            if (targetType.isMarkedNullable) return null
            ktpRspError {
                title = "Deserialization Error"
                detail = "Constructing non-nullable type $targetType but value is null"
            }
        }

        val kClass = targetType.jvmErasure

        customDeserializers[kClass.java]?.let {
            val converted = it(value)
            if (converted == null && !targetType.isMarkedNullable) {
                ktpRspError {
                    title = "Deserialization Error"
                    detail =
                        "Custom deserializer for ${kClass.simpleName} returned null " +
                            "for non-nullable target"
                }
            }
            return converted
        }

        if (kClass == String::class) return value.toString()
        if (value is Number) {
            when (kClass) {
                Int::class -> return value.toInt()
                Long::class -> return value.toLong()
                Double::class -> return value.toDouble()
                Float::class -> return value.toFloat()
                Short::class -> return value.toShort()
                Byte::class -> return value.toByte()
            }
        }

        if (kClass == Boolean::class) return value as Boolean

        if (kClass.java.isEnum) {
            @Suppress("UNCHECKED_CAST") val constants = kClass.java.enumConstants as Array<Enum<*>>
            return constants.firstOrNull { it.name == value.toString() }
                ?: ktpRspError {
                    title = "Deserialization Error"
                    detail = "Unknown ${kClass.simpleName} enum value '$value'"
                }
        }

        // Value classes are stored unwrapped, so rebuild from the single constructor parameter.
        if (kClass.isValue) {
            val constructor = kClass.primaryConstructor!!
            val param = constructor.parameters.first()
            val paramVal = deserialize(value, param.type)
            return construct(kClass) { constructor.call(paramVal) }
        }

        if (
            kClass == List::class ||
                kClass == ArrayList::class ||
                kClass == Iterable::class ||
                kClass == Set::class
        ) {
            val list = value as List<*>
            val elementType = targetType.arguments.first().type ?: Any::class.createType()
            val items = list.map { deserialize(it, elementType) }
            return if (kClass == Set::class) {
                items.toSet()
            } else {
                items
            }
        }

        if (kClass == Map::class || kClass == HashMap::class) {
            val map = value as Map<*, *>
            val valueType = targetType.arguments[1].type ?: Any::class.createType()
            // Firestore map keys are always strings, so the declared key type is ignored.
            return map.entries.associate { (k, v) -> k.toString() to deserialize(v, valueType) }
        }

        // Firestore native types (GeoPoint, DocumentReference, ...) are returned as-is.
        if (kClass.java.packageName.startsWith("com.google.cloud.firestore")) {
            return value
        }

        if (value is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return deserializeObject(value as Map<String, Any?>, kClass)
        }

        // Unknown type: pass the raw value through for the caller or constructor call to reject.
        return value
    }

    private fun deserializeObject(map: Map<String, Any?>, kClass: KClass<*>): Any {
        val constructor =
            kClass.primaryConstructor
                ?: ktpRspError {
                    title = "Deserialization Error"
                    detail = "No primary constructor for $kClass"
                }

        val callArgs =
            constructor.parameters
                .mapNotNull { param ->
                    val paramName = param.name
                    if (map.containsKey(paramName)) {
                        param to deserialize(map[paramName], param.type)
                    } else {
                        if (param.isOptional) {
                            null // Omitted from callBy so the declared default applies.
                        } else if (param.type.isMarkedNullable) {
                            param to null
                        } else {
                            ktpRspError {
                                title = "Deserialization Error"
                                detail = "Missing required parameter $paramName for $kClass"
                            }
                        }
                    }
                }
                .toMap()

        return construct(kClass) { constructor.callBy(callArgs) }
    }

    /** Invokes a constructor, surfacing validation failures as descriptive errors. */
    private fun <R> construct(kClass: KClass<*>, invoke: () -> R): R =
        try {
            invoke()
        } catch (e: InvocationTargetException) {
            constructionError(kClass, e.cause ?: e)
        } catch (e: IllegalArgumentException) {
            // Reflection's own type-mismatch error, e.g. raw value from the unknown-type fallback.
            constructionError(kClass, e)
        }

    private fun constructionError(kClass: KClass<*>, error: Throwable): Nothing = ktpRspError {
        title = "Deserialization Error"
        detail = "Constructing ${kClass.simpleName} failed: ${error.message}"
        cause = error
    }
}

/**
 * Null if the document is missing; otherwise [kClass] built with `id` from the document id and, for
 * [DocTimes] types, `createTime`/`updateTime` from snapshot metadata overriding stored fields.
 */
fun <T : Any> DocumentSnapshot.deserialize(kClass: KClass<T>): T? {
    val rawData = data ?: return null
    val dataWithId =
        if (kClass.isSubclassOf(DocTimes::class)) {
            rawData +
                mapOf(
                    "id" to id,
                    DocTimes.CREATE_TIME to createTime,
                    DocTimes.UPDATE_TIME to updateTime,
                )
        } else {
            rawData + mapOf("id" to id)
        }
    val result = FirestoreDeserializer.deserialize(dataWithId, kClass.createType())
    if (!kClass.isInstance(result)) {
        ktpRspError {
            title = "Deserialization Error"
            detail =
                "Document '$id' deserialized to ${result?.javaClass?.simpleName} " +
                    "instead of ${kClass.simpleName}"
        }
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** Reified convenience for [deserialize]. */
inline fun <reified T : Any> DocumentSnapshot.deserialize(): T? = deserialize(T::class)
