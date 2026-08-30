package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.GeoPoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import net.ghue.ktp.ktor.error.KtpRspEx

class DeserializeTest :
    StringSpec({
        "primitives deserialization" {
            FirestoreDeserializer.deserialize(123L, Int::class.createType()) shouldBe 123
            FirestoreDeserializer.deserialize("hello", String::class.createType()) shouldBe "hello"
            FirestoreDeserializer.deserialize(true, Boolean::class.createType()) shouldBe true
        }

        "all numeric target types are converted" {
            FirestoreDeserializer.deserialize(42L, Long::class.createType()) shouldBe 42L
            FirestoreDeserializer.deserialize(42L, Double::class.createType()) shouldBe 42.0
            FirestoreDeserializer.deserialize(42L, Float::class.createType()) shouldBe 42.0f
            FirestoreDeserializer.deserialize(42L, Short::class.createType()) shouldBe 42.toShort()
            FirestoreDeserializer.deserialize(42L, Byte::class.createType()) shouldBe 42.toByte()
        }

        "null is allowed only for nullable target types" {
            FirestoreDeserializer.deserialize(
                null,
                String::class.createType(nullable = true),
            ) shouldBe null

            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize(null, String::class.createType())
                }
            error.detail shouldContain "non-nullable type"
        }

        "timestamp to instant" {
            val now = Instant.now()
            val ts = Timestamp.ofTimeSecondsAndNanos(now.epochSecond, now.nano)

            val result = FirestoreDeserializer.deserialize(ts, Instant::class.createType())
            result shouldBe now
        }

        "timestamp to date" {
            val date = Date()
            val ts = Timestamp.of(date)
            val result = FirestoreDeserializer.deserialize(ts, Date::class.createType())

            // Timestamp holds nanos but Date only millis; starting from a Date keeps this lossless.
            result shouldBe date
        }

        "value class deserialization" {
            // Value classes are stored unwrapped, so DCount(42) is read back from the bare number.
            val result = FirestoreDeserializer.deserialize(42L, DCount::class.createType())
            result shouldBe DCount(42)

            val email = FirestoreDeserializer.deserialize("test@a.com", DEmail::class.createType())
            email shouldBe DEmail("test@a.com")
        }

        "lists iterables sets and maps deserialize their elements" {
            val numbers = listOf(1L, 2L, 2L)

            FirestoreDeserializer.deserialize(numbers, typeOf<List<Int>>()) shouldBe listOf(1, 2, 2)
            FirestoreDeserializer.deserialize(numbers, typeOf<ArrayList<Int>>()) shouldBe
                arrayListOf(1, 2, 2)
            FirestoreDeserializer.deserialize(numbers, typeOf<Iterable<Int>>()) shouldBe
                listOf(1, 2, 2)
            FirestoreDeserializer.deserialize(numbers, typeOf<Set<Int>>()) shouldBe setOf(1, 2)
            FirestoreDeserializer.deserialize(listOf(1L), typeOf<List<*>>()) shouldBe listOf(1L)

            val values = mapOf<Any, Any>(1 to 2L, "three" to 3L)
            FirestoreDeserializer.deserialize(values, typeOf<Map<String, Int>>()) shouldBe
                mapOf("1" to 2, "three" to 3)
            FirestoreDeserializer.deserialize(values, typeOf<HashMap<String, Int>>()) shouldBe
                hashMapOf("1" to 2, "three" to 3)
        }

        "Firestore native values and unknown types pass through unchanged" {
            val geoPoint = GeoPoint(1.0, 2.0)
            val marker = Any()

            FirestoreDeserializer.deserialize(
                geoPoint,
                GeoPoint::class.createType(),
            ) shouldBeSameInstanceAs geoPoint
            FirestoreDeserializer.deserialize(
                marker,
                Any::class.createType(),
            ) shouldBeSameInstanceAs marker
        }

        "custom deserializer registered by class converts values" {
            FirestoreDeserializer.registerDeserializer(DCustom::class.java) {
                DCustom(it.toString())
            }

            FirestoreDeserializer.deserialize("value", DCustom::class.createType()) shouldBe
                DCustom("value")
        }

        "data class deserialization" {
            val data =
                mapOf(
                    "name" to "Foo",
                    "count" to 42L, // Firestore returns integers as Long
                    "type" to "A",
                )

            val result = FirestoreDeserializer.deserialize(data, DData::class.createType())
            result shouldBe DData("Foo", 42, DEnum.A)
        }

        "nested data class" {
            val data = mapOf("data" to mapOf("name" to "Inner", "count" to 1L, "type" to "B"))
            val result = FirestoreDeserializer.deserialize(data, DNested::class.createType())
            result shouldBe DNested(DData("Inner", 1, DEnum.B))
        }

        "id injection" {
            val data = mapOf("id" to "doc-123", "value" to "content")
            val result = FirestoreDeserializer.deserialize(data, DWithId::class.createType())
            result shouldBe DWithId("doc-123", "content")
        }

        "DocTimes types get createTime and updateTime from snapshot metadata" {
            val created = Instant.parse("2024-01-01T00:00:00Z")
            val updated = Instant.parse("2024-01-02T00:00:00Z")
            val stale = Instant.parse("2020-01-01T00:00:00Z")
            val doc = mockk<DocumentSnapshot>()
            every { doc.data } returns
                // A stored "updateTime" field must lose to the snapshot metadata.
                mapOf("value" to "content", "updateTime" to stale.toTimestamp())
            every { doc.id } returns "doc-1"
            every { doc.createTime } returns created.toTimestamp()
            every { doc.updateTime } returns updated.toTimestamp()

            doc.deserialize<DTimed>() shouldBe
                DTimed(id = "doc-1", value = "content", createTime = created, updateTime = updated)
        }

        "non-DocTimes types read stored createTime as a normal field" {
            val at = Instant.parse("2024-01-01T00:00:00Z")
            val doc = mockk<DocumentSnapshot>()
            every { doc.data } returns mapOf("createTime" to at.toTimestamp())
            every { doc.id } returns "doc-1"
            // No metadata stubs: a non-DocTimes type must not touch the snapshot's times.

            doc.deserialize<DStoredTime>() shouldBe DStoredTime(id = "doc-1", createTime = at)
        }

        "missing optional parameter uses default" {
            val data = mapOf<String, Any>()
            val result =
                FirestoreDeserializer.deserialize(data, DataWithDefault::class.createType())
            result shouldBe DataWithDefault("default")
        }

        "missing nullable parameter becomes null" {
            FirestoreDeserializer.deserialize(
                emptyMap<String, Any>(),
                DNullable::class.createType(),
            ) shouldBe DNullable(null)
        }

        "missing required parameter fails with its name" {
            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize(
                        emptyMap<String, Any>(),
                        DRequired::class.createType(),
                    )
                }

            error.detail shouldContain "Missing required parameter value"
        }

        "class without a primary constructor fails descriptively" {
            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize(
                        mapOf("value" to "x"),
                        DSecondary::class.createType(),
                    )
                }

            error.detail shouldContain "No primary constructor"
        }

        "raw values with the wrong constructor type fail descriptively" {
            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize(
                        mapOf("value" to "raw"),
                        DUnknownHolder::class.createType(),
                    )
                }

            error.detail shouldContain "Constructing DUnknownHolder failed"
        }

        "instant round-trip truncates to Firestore's microsecond precision" {
            val precise = Instant.ofEpochSecond(1234567890L, 123456789)
            val stored = FirestoreSerializer.serialize(precise)

            FirestoreDeserializer.deserialize(stored, Instant::class.createType()) shouldBe
                precise.truncatedTo(ChronoUnit.MICROS)
        }

        "unknown enum value fails with a descriptive error" {
            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize("C", DEnum::class.createType())
                }
            error.detail shouldBe "Unknown DEnum enum value 'C'"
        }

        "failing value class validation surfaces a descriptive error" {
            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize(-1L, DPositive::class.createType())
                }
            error.detail shouldBe "Constructing DPositive failed: must be positive"
        }

        "failing data class validation surfaces a descriptive error" {
            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize(
                        mapOf("name" to ""),
                        DValidated::class.createType(),
                    )
                }
            error.detail shouldBe "Constructing DValidated failed: name must not be empty"
        }

        "custom deserializer returning null for a non-nullable target fails" {
            FirestoreDeserializer.registerDeserializer<DNullCustom> { null }

            FirestoreDeserializer.deserialize(
                "x",
                DNullCustom::class.createType(nullable = true),
            ) shouldBe null

            val error =
                shouldThrow<KtpRspEx> {
                    FirestoreDeserializer.deserialize("x", DNullCustom::class.createType())
                }
            error.detail shouldContain "DNullCustom"
        }
    })

data class DataWithDefault(val value: String = "default")

// D-prefixed to avoid clashing with SerializeTest's top-level fixtures in this package.

@JvmInline value class DCount(val value: Int)

@JvmInline value class DEmail(val value: String)

enum class DEnum {
    A,
    B,
}

data class DData(val name: String, val count: Int, val type: DEnum)

data class DNested(val data: DData)

data class DWithId(val id: String, val value: String)

data class DTimed(
    val id: String,
    val value: String,
    override val createTime: Instant? = null,
    override val updateTime: Instant? = null,
) : DocTimes

data class DStoredTime(val id: String, val createTime: Instant)

@JvmInline
value class DPositive(val value: Int) {
    init {
        require(value > 0) { "must be positive" }
    }
}

data class DValidated(val name: String) {
    init {
        require(name.isNotEmpty()) { "name must not be empty" }
    }
}

class DNullCustom

data class DCustom(val value: String)

data class DNullable(val value: String?)

data class DRequired(val value: String)

class DSecondary {
    val value: String

    constructor(value: String) {
        this.value = value
    }
}

class DUnknown

data class DUnknownHolder(val value: DUnknown)
