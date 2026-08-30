package net.ghue.ktp.gcp.firestore

import java.time.Instant

/**
 * Opt-in access to Firestore's server-maintained document metadata times. On reads, [createTime]
 * and [updateTime] are filled from the document snapshot; on writes, both properties are stripped
 * so they are never stored as document fields — the server stays the single source of truth.
 *
 * Declare both as nullable constructor parameters defaulting to null; a locally constructed
 * instance holds nulls until read back from Firestore:
 * ```
 * data class DbUser(
 *     val id: String,
 *     val name: String,
 *     override val createTime: Instant? = null,
 *     override val updateTime: Instant? = null,
 * ) : DocTimes
 * ```
 *
 * These mirror document metadata, not stored fields: Firestore cannot filter or order by them (the
 * query DSL rejects them), and [updateTime] advances on every write — including merges that change
 * nothing and writes from other tools.
 */
interface DocTimes {
    /** When the document was first created, from Firestore's document metadata. */
    val createTime: Instant?

    /** When the document was last written, from Firestore's document metadata. */
    val updateTime: Instant?

    companion object {
        /** Name of the [createTime] property. */
        const val CREATE_TIME = "createTime"

        /** Name of the [updateTime] property. */
        const val UPDATE_TIME = "updateTime"

        /**
         * The property names [DocTimes] reserves for metadata; never stored, rejected in queries.
         */
        val FIELDS = setOf(CREATE_TIME, UPDATE_TIME)
    }
}
