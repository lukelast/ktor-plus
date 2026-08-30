// A cohesive typed query DSL; many small delegating functions is its natural shape.
@file:Suppress("TooManyFunctions")

package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.Query
import kotlin.reflect.KProperty1
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

/**
 * Filters where [property] equals [value]. The field name comes from the property and the operand
 * runs through [FirestoreSerializer] — the same conversion writes use — so value classes, enums,
 * and types with custom serializers (e.g. `Instant`) match their stored representation instead of
 * silently matching nothing. A null [value] is Firestore's is-null filter.
 */
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

/** Filters where [property] is null. */
fun <T> Query.whereNull(property: KProperty1<T, *>): Query = whereEqualTo(fieldName(property), null)

/** Filters where [property] is not null. */
fun <T> Query.whereNotNull(property: KProperty1<T, *>): Query =
    whereNotEqualTo(fieldName(property), null)

/** Orders results by [property], ascending. */
fun <T> Query.orderByAsc(property: KProperty1<T, *>): Query = orderBy(fieldName(property))

/** Orders results by [property], descending. */
fun <T> Query.orderByDesc(property: KProperty1<T, *>): Query =
    orderBy(fieldName(property), Query.Direction.DESCENDING)

/**
 * Resolves the Firestore field name for [property], rejecting [DocTimes] metadata properties — they
 * mirror server metadata that is never stored in the document, so a filter or ordering on them
 * would silently match nothing.
 */
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
