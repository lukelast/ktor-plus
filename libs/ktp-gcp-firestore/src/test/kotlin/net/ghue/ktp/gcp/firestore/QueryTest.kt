package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.Query
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class QueryTest :
    StringSpec({
        "whereEq unwraps value class operands" {
            val query = mockk<Query>()
            val next = mockk<Query>()
            every { query.whereEqualTo("id", "value-1") } returns next

            query.whereEq(ValueUser::id, UserId("value-1")) shouldBe next
        }

        "whereEq converts Instant operands to Timestamp" {
            data class Event(val id: String, val at: Instant)

            val at = Instant.parse("2024-01-01T00:00:00Z")
            val query = mockk<Query>()
            val next = mockk<Query>()
            every { query.whereEqualTo("at", at.toTimestamp()) } returns next

            query.whereEq(Event::at, at) shouldBe next
        }

        "whereEq serializes enum operands by name" {
            data class Task(val id: String, val status: QueryTestStatus)

            val query = mockk<Query>()
            val next = mockk<Query>()
            every { query.whereEqualTo("status", "ACTIVE") } returns next

            query.whereEq(Task::status, QueryTestStatus.ACTIVE) shouldBe next
        }

        "whereEq with null is the is-null filter" {
            data class Entry(val id: String, val settledAt: Instant?)

            val query = mockk<Query>()
            val next = mockk<Query>()
            every { query.whereEqualTo("settledAt", null) } returns next

            query.whereEq(Entry::settledAt, null) shouldBe next
        }

        "range filters serialize operands" {
            data class Event(val id: String, val at: Instant?)

            val at = Instant.parse("2024-01-01T00:00:00Z")
            val query = mockk<Query>()
            val next = mockk<Query>()
            every { query.whereGreaterThan("at", at.toTimestamp()) } returns next
            every { query.whereGreaterThanOrEqualTo("at", at.toTimestamp()) } returns next
            every { query.whereLessThan("at", at.toTimestamp()) } returns next
            every { query.whereLessThanOrEqualTo("at", at.toTimestamp()) } returns next

            query.whereGt(Event::at, at) shouldBe next
            query.whereGte(Event::at, at) shouldBe next
            query.whereLt(Event::at, at) shouldBe next
            query.whereLte(Event::at, at) shouldBe next
        }

        "range filters reject operands that serialize to null" {
            data class Event(val id: String, val value: NullQueryOperand)

            FirestoreSerializer.registerSerializer<NullQueryOperand> { null }
            val query = mockk<Query>()

            val error =
                shouldThrow<IllegalArgumentException> {
                    query.whereGt(Event::value, NullQueryOperand("value"))
                }
            error.message shouldContain "must not be null"
        }

        "whereNull and whereNotNull filter on null" {
            data class Entry(val id: String, val settledAt: Instant?)

            val query = mockk<Query>()
            val isNull = mockk<Query>()
            val notNull = mockk<Query>()
            every { query.whereEqualTo("settledAt", null) } returns isNull
            every { query.whereNotEqualTo("settledAt", null) } returns notNull

            query.whereNull(Entry::settledAt) shouldBe isNull
            query.whereNotNull(Entry::settledAt) shouldBe notNull
        }

        "DocTimes metadata properties are rejected in filters and ordering" {
            data class Timed(
                val id: String,
                override val createTime: Instant? = null,
                override val updateTime: Instant? = null,
            ) : DocTimes

            val query = mockk<Query>()
            val at = Instant.parse("2024-01-01T00:00:00Z")

            val error =
                shouldThrow<IllegalArgumentException> { query.whereEq(Timed::updateTime, at) }
            error.message shouldContain "server metadata"
            shouldThrow<IllegalArgumentException> { query.whereGt(Timed::updateTime, at) }
            shouldThrow<IllegalArgumentException> { query.whereNull(Timed::createTime) }
            shouldThrow<IllegalArgumentException> { query.orderByDesc(Timed::createTime) }
        }

        "createTime on a non-DocTimes class queries the stored field" {
            data class Legacy(val id: String, val createTime: Instant)

            val query = mockk<Query>()
            val next = mockk<Query>()
            every { query.orderBy("createTime") } returns next

            query.orderByAsc(Legacy::createTime) shouldBe next
        }

        "orderByAsc and orderByDesc order by the property" {
            data class Event(val id: String, val at: Instant)

            val query = mockk<Query>()
            val asc = mockk<Query>()
            val desc = mockk<Query>()
            every { query.orderBy("at") } returns asc
            every { query.orderBy("at", Query.Direction.DESCENDING) } returns desc

            query.orderByAsc(Event::at) shouldBe asc
            query.orderByDesc(Event::at) shouldBe desc
        }
    })

private enum class QueryTestStatus {
    ACTIVE
}

private data class NullQueryOperand(val value: String)
