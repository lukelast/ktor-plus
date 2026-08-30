package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.GeoPoint
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.Date

class FirestoreSerializeTest :
    StringSpec({
        FirestoreSerializer.serialize("hello") shouldBe "hello"
        FirestoreSerializer.serialize(123) shouldBe 123
        FirestoreSerializer.serialize(true) shouldBe true
        FirestoreSerializer.serialize(null) shouldBe null

        "firestore natives serialization" {
            val geo = GeoPoint(1.0, 2.0)
            val ts = Timestamp.now()
            val blob = Blob.fromBytes(byteArrayOf(1, 2, 3))

            FirestoreSerializer.serialize(geo) shouldBe geo
            FirestoreSerializer.serialize(ts) shouldBe ts
            FirestoreSerializer.serialize(blob) shouldBe blob
        }

        "collections serialization" {
            val list = listOf(1, "two", true)
            val map = mapOf("a" to 1, "b" to listOf(2))

            FirestoreSerializer.serialize(list) shouldBe list
            FirestoreSerializer.serialize(map) shouldBe map
        }

        "enum serialization" { FirestoreSerializer.serialize(TestEnum.A) shouldBe "A" }

        "custom serializer serialization (Instant) truncates to microseconds" {
            val instant = Instant.ofEpochSecond(1234567890L, 123456789)
            // Firestore stores microsecond precision, so the serializer drops the trailing nanos.
            val expected = Timestamp.ofTimeSecondsAndNanos(1234567890L, 123456000)

            FirestoreSerializer.serialize(instant) shouldBe expected
        }

        "custom serializer serialization (Date)" {
            val date = Date(1234567890000L)
            val expected = Timestamp.of(date)

            FirestoreSerializer.serialize(date) shouldBe expected
        }

        "object serialization via reflection" {
            val obj = TestData("test", 42, TestEnum.B)

            val result = obj.serialize()

            result shouldBe mapOf("name" to "test", "count" to 42, "type" to "B")
        }

        "nested object serialization" {
            val nested = NestedData(TestData("inner", 1, TestEnum.A))

            val result = nested.serialize()

            result shouldBe mapOf("data" to mapOf("name" to "inner", "count" to 1, "type" to "A"))
        }
        "root object 'id' field is excluded" {
            val obj = DataWithId("doc-id", "some value")

            val result = obj.serialize()

            result shouldBe mapOf("value" to "some value")
        }

        "nested object 'id' field is KEPT" {
            val nested = NestedWithId(DataWithId("inner-id", "inner-val"))

            val result = nested.serialize()

            result shouldBe mapOf("child" to mapOf("id" to "inner-id", "value" to "inner-val"))
        }
        "DocTimes createTime and updateTime are excluded from writes" {
            val doc =
                TimedData(
                    id = "doc-1",
                    value = "v",
                    createTime = Instant.parse("2024-01-01T00:00:00Z"),
                    updateTime = Instant.parse("2024-01-02T00:00:00Z"),
                )

            doc.serialize() shouldBe mapOf("value" to "v")
        }

        "createTime and updateTime on a non-DocTimes class are stored" {
            val at = Instant.parse("2024-01-01T00:00:00Z")
            val doc = PlainTimedData(id = "doc-1", createTime = at, updateTime = at)

            doc.serialize() shouldBe
                mapOf("createTime" to at.toTimestamp(), "updateTime" to at.toTimestamp())
        }

        "value class serialization" {
            val email = Email("test@example.com")
            val count = Count(42)

            FirestoreSerializer.serialize(email) shouldBe "test@example.com"
            // interpreting "convert to string" as "unwrap", but user said "to string".
            // If I unwrap Count(42), it is 42. If I "convert to string", it is "42".
            // I will assume unwrapping is the goal.
            FirestoreSerializer.serialize(count) shouldBe 42
        }
    })

@JvmInline value class Email(val value: String)

@JvmInline value class Count(val value: Int)

enum class TestEnum {
    A,
    B,
}

data class TestData(val name: String, val count: Int, val type: TestEnum)

data class NestedData(val data: TestData)

data class DataWithId(val id: String, val value: String)

data class NestedWithId(val child: DataWithId)

data class TimedData(
    val id: String,
    val value: String,
    override val createTime: Instant? = null,
    override val updateTime: Instant? = null,
) : DocTimes

data class PlainTimedData(val id: String, val createTime: Instant, val updateTime: Instant)
