@file:Suppress("MatchingDeclarationName")

package net.ghue.ktp.gcp.firestore

import java.time.Instant
import kotlin.reflect.full.memberProperties
import net.ghue.ktp.ktor.error.ktpRspError

/**
 * Firestore document metadata times: filled on read, stripped on write, never queryable. Implement
 * as nullable constructor params defaulting to null.
 */
interface DocTimes {
    /** Creation time from the document metadata. */
    val createTime: Instant?

    /** Last write time; advances on every write, even no-op merges and writes from other tools. */
    val updateTime: Instant?

    companion object {
        const val CREATE_TIME = "createTime"

        const val UPDATE_TIME = "updateTime"

        /** Property names reserved for metadata: never stored, rejected in queries. */
        val FIELDS = setOf(CREATE_TIME, UPDATE_TIME)
    }
}

/** Extracts the value of the 'id' property from the given data object. */
fun idFieldValue(document: Any): String {
    val idProperty =
        document::class.memberProperties.find { it.name == "id" }
            ?: ktpRspError {
                title = "Missing ID Property"
                detail = "Property 'id' not found on ${document::class.simpleName}"
            }

    val idValue =
        idProperty.getter.call(document)
            ?: ktpRspError {
                title = "Null ID"
                detail = "Property 'id' is null on ${document::class.simpleName}"
            }

    val stringValue =
        if (idValue::class.isValue) {
            // Value class toString() is "UserId(value=...)"; use the backing property instead.
            val property =
                idValue::class.memberProperties.firstOrNull()
                    ?: ktpRspError {
                        title = "Invalid Value Class"
                        detail = "Value class ${idValue::class.simpleName} has no properties"
                    }
            val innerValue =
                property.getter.call(idValue)
                    ?: ktpRspError {
                        title = "Null ID"
                        detail = "Property 'id' is null on ${document::class.simpleName}"
                    }
            innerValue.toString()
        } else {
            idValue.toString()
        }

    if (stringValue.isEmpty()) {
        ktpRspError {
            title = "Empty ID"
            detail = "Property 'id' is empty on ${document::class.simpleName}"
        }
    }
    return stringValue
}
