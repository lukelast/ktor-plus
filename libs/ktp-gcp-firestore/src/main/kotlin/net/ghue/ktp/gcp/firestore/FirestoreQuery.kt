package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.Query
import kotlin.reflect.KProperty1

/**
 * Filters where [property] equals [value]. The field name comes from the property and the operand
 * runs through [FirestoreSerializer] — the same conversion writes use — so value classes, enums,
 * and types with custom serializers (e.g. `Instant`) match their stored representation instead of
 * silently matching nothing. A null [value] is Firestore's is-null filter.
 */
fun <T, V> Query.whereEq(property: KProperty1<T, V>, value: V): Query =
    whereEqualTo(property.name, FirestoreSerializer.serialize(value))

/** Filters where [property] is greater than [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereGt(property: KProperty1<T, V>, value: V & Any): Query =
    whereGreaterThan(property.name, serializeOperand(property, value))

/** Filters where [property] is at least [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereGte(property: KProperty1<T, V>, value: V & Any): Query =
    whereGreaterThanOrEqualTo(property.name, serializeOperand(property, value))

/** Filters where [property] is less than [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereLt(property: KProperty1<T, V>, value: V & Any): Query =
    whereLessThan(property.name, serializeOperand(property, value))

/** Filters where [property] is at most [value]. Operand serialization as in [whereEq]. */
fun <T, V> Query.whereLte(property: KProperty1<T, V>, value: V & Any): Query =
    whereLessThanOrEqualTo(property.name, serializeOperand(property, value))

/** Filters where [property] is null. */
fun <T> Query.whereNull(property: KProperty1<T, *>): Query = whereEqualTo(property.name, null)

/** Filters where [property] is not null. */
fun <T> Query.whereNotNull(property: KProperty1<T, *>): Query = whereNotEqualTo(property.name, null)

/** Orders results by [property], ascending. */
fun <T> Query.orderByAsc(property: KProperty1<T, *>): Query = orderBy(property.name)

/** Orders results by [property], descending. */
fun <T> Query.orderByDesc(property: KProperty1<T, *>): Query =
    orderBy(property.name, Query.Direction.DESCENDING)

private fun serializeOperand(property: KProperty1<*, *>, value: Any): Any =
    requireNotNull(FirestoreSerializer.serialize(value)) {
        "Serialized query operand for '${property.name}' must not be null"
    }
