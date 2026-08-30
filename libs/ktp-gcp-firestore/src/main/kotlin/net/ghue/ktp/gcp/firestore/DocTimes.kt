package net.ghue.ktp.gcp.firestore

import java.time.Instant

/**
 * Opt-in mirror of Firestore's document metadata times: filled from the snapshot on reads, stripped
 * on writes. Declare both as nullable constructor params defaulting to null. Not queryable, and
 * [updateTime] advances on every write, including no-op merges and writes from other tools.
 */
interface DocTimes {
    /** Creation time from the document metadata. */
    val createTime: Instant?

    /** Last write time from the document metadata. */
    val updateTime: Instant?

    companion object {
        const val CREATE_TIME = "createTime"

        const val UPDATE_TIME = "updateTime"

        /** Property names reserved for metadata: never stored, rejected in queries. */
        val FIELDS = setOf(CREATE_TIME, UPDATE_TIME)
    }
}
