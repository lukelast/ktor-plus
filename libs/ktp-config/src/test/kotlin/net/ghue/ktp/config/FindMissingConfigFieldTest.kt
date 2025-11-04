package net.ghue.ktp.config

import com.typesafe.config.ConfigFactory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FindMissingConfigFieldTest :
    StringSpec({
        data class SimpleConfig(val name: String, val value: Int)

        data class NestedInner(val innerValue: String)

        data class NestedConfig(val outer: String, val nested: NestedInner)

        "returns null when all fields are present" {
            val config =
                ConfigFactory.parseMap(mapOf("simple.name" to "test", "simple.value" to 42))

            val result = findMissingConfigField(config, SimpleConfig::class, "simple")

            result shouldBe null
        }

        "returns missing field path for simple config" {
            val config =
                ConfigFactory.parseMap(
                    mapOf(
                        "simple.name" to "test"
                        // missing "simple.value"
                    )
                )

            val result = findMissingConfigField(config, SimpleConfig::class, "simple")

            result shouldBe "simple.value"
        }

        "returns first missing field when multiple are missing" {
            val config =
                ConfigFactory.parseMap(
                    mapOf(
                        // missing both "simple.name" and "simple.value"
                    )
                )

            val result = findMissingConfigField(config, SimpleConfig::class, "simple")

            // Should return the first missing field (order depends on reflection)
            result shouldBe "simple.name"
        }

        "returns null when all nested fields are present" {
            val config =
                ConfigFactory.parseMap(
                    mapOf(
                        "nested.outer" to "outer-value",
                        "nested.nested.innerValue" to "inner-value",
                    )
                )

            val result = findMissingConfigField(config, NestedConfig::class, "nested")

            result shouldBe null
        }

        "returns missing nested field path" {
            val config =
                ConfigFactory.parseMap(
                    mapOf(
                        "nested.outer" to "outer-value"
                        // missing "nested.nested.innerValue"
                    )
                )

            val result = findMissingConfigField(config, NestedConfig::class, "nested")

            result shouldBe "nested.nested.innerValue"
        }
    })
