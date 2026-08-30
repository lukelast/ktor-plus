package net.ghue.ktp.gcp.firestore

import java.time.Instant

/**
 * Firestore's document metadata times, filled on read, stripped on write, and never queryable;
 * declare both as nullable constructor params defaulting to null.
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
