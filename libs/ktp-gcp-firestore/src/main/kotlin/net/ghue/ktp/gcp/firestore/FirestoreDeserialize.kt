package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlin.collections.plus
import kotlinx.serialization.json.Json
import net.ghue.ktp.log.log

val gson: Gson = GsonBuilder().create()
val json = Json { ignoreUnknownKeys = true }

/** If the type [T] has an `id` property, then its value will be set with the document id. */
inline fun <reified T> DocumentSnapshot.deserialize(): T? {
    val rawData = data ?: return null
    val existingId = rawData["id"]
    if (existingId != null && existingId != id) {
        log {}.error { "Document has an id field of $existingId but document ID: $id." }
    }
    val dataWithId = rawData + mapOf("id" to id)
    val docStr = gson.toJson(dataWithId)
    val decoded: T = json.decodeFromString(docStr)
    return decoded
}
