package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.GeoPoint
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

object FirestoreSerializer {
    private val customSerializers = mutableMapOf<Class<*>, (Any) -> Any?>()

    init {
        FirestoreTypes.registerDefaults()
    }

    fun <T : Any> registerSerializer(clazz: Class<T>, serializer: (T) -> Any?) {
        @Suppress("UNCHECKED_CAST")
        customSerializers[clazz] = {
            serializer(it as T)
        }
    }

    inline fun <reified T : Any> registerSerializer(noinline serializer: (T) -> Any?) {
        registerSerializer(T::class.java, serializer)
    }

    fun serialize(value: Any?): Any? {
        if (value == null) return null

        // Check custom serializers first
        customSerializers[value::class.java]?.let {
            return it(value)
        }
        // Handle subclasses for custom serializers
        customSerializers.entries
            .find { it.key.isInstance(value) }
            ?.let {
                return it.value(value)
            }

        return when (value) {
            is String,
            is Number,
            is Boolean -> value
            is Timestamp,
            is GeoPoint,
            is Blob,
            is DocumentReference -> value
            is Enum<*> -> value.name
            is Map<*, *> ->
                value.entries.associate { (k, v) -> (k?.toString() ?: "null") to serialize(v) }
            is Iterable<*> -> value.map { serialize(it) }
            is Array<*> -> value.map { serialize(it) }
            else -> {
                if (value::class.isValue) {
                    val prop = value::class.memberProperties.first()
                    return serialize(prop.call(value))
                }
                serializeObject(value)
            }
        }
    }

    private fun serializeObject(obj: Any): Map<String, Any?> {
        val kClass = obj::class
        return kClass.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .associate { prop -> prop.name to serialize(prop.call(obj)) }
    }
}

/** Serializes any object to a Firestore-compatible Map. */
fun <T> T.serialize(): Map<String, Any> {
    val result = FirestoreSerializer.serialize(this)
    @Suppress("UNCHECKED_CAST")
    val map =
        (result as? Map<String, Any>)
            ?: error("Serialized result is not a Map. It was: ${result?.javaClass?.simpleName}")
    // `id` field is a special copy of the document ID so we don't want to store it.
    return map - "id"
}
