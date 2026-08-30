// A cohesive typed query DSL; many small delegating functions is its natural shape.
@file:Suppress("TooManyFunctions")

package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.Query
import kotlin.reflect.KProperty1
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

/** Filters where [property] equals [value], serialized as on write; null acts as [whereNull]. */
fun <T, V> Query.whereEq(property: KProperty1<T, V>, value: V): Query =
    whereEqualTo(fieldName(property), FirestoreSerializer.serialize(value))

/** Filters where [property] is greater than [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereGt(property: KProperty1<T, V>, value: V & Any): Query =
    whereGreaterThan(fieldName(property), serializeOperand(property, value))

/** Filters where [property] is at least [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereGte(property: KProperty1<T, V>, value: V & Any): Query =
    whereGreaterThanOrEqualTo(fieldName(property), serializeOperand(property, value))

/** Filters where [property] is less than [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereLt(property: KProperty1<T, V>, value: V & Any): Query =
    whereLessThan(fieldName(property), serializeOperand(property, value))

/** Filters where [property] is at most [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereLte(property: KProperty1<T, V>, value: V & Any): Query =
    whereLessThanOrEqualTo(fieldName(property), serializeOperand(property, value))

/**
 * Filters where [property] is an explicit null; a missing field matches neither this nor
 * [whereNotNull]. Library writes always store nulls, so only legacy or external docs differ.
 */
fun <T> Query.whereNull(property: KProperty1<T, *>): Query = whereEqualTo(fieldName(property), null)

/** Filters where [property] is non-null. Skips documents missing the field, as in [whereNull]. */
fun <T> Query.whereNotNull(property: KProperty1<T, *>): Query =
    whereNotEqualTo(fieldName(property), null)

/** Orders results by [property], ascending. */
fun <T> Query.orderByAsc(property: KProperty1<T, *>): Query = orderBy(fieldName(property))

/** Orders results by [property], descending. */
fun <T> Query.orderByDesc(property: KProperty1<T, *>): Query =
    orderBy(fieldName(property), Query.Direction.DESCENDING)

/** Rejects [DocTimes] properties: unstored server metadata would silently match nothing. */
private fun fieldName(property: KProperty1<*, *>): String {
    val name = property.name
    if (name in DocTimes.FIELDS) {
        val receiver = property.instanceParameter?.type?.jvmErasure
        require(receiver == null || !receiver.isSubclassOf(DocTimes::class)) {
            "'$name' on ${receiver?.simpleName} is DocTimes server metadata, not a stored field, " +
                "so Firestore cannot query it. Store your own timestamp field to query by time."
        }
    }
    return name
}

private fun serializeOperand(property: KProperty1<*, *>, value: Any): Any =
    requireNotNull(FirestoreSerializer.serialize(value)) {
        "Serialized query operand for '${property.name}' must not be null"
    }
