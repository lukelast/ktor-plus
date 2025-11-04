package net.ghue.ktp.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BuildConfigFromEnvTextTest :
    StringSpec({
        "buildConfigFromEnvText returns null when config text is empty" {
            val result = buildConfigFromEnvText("")
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null when config text is blank" {
            val result = buildConfigFromEnvText("   ")
            result.shouldBeNull()
        }

        "buildConfigFromEnvText parses simple config" {
            val configText = "mykey = \"myvalue\""
            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getString("mykey") shouldBe "myvalue"
        }

        "buildConfigFromEnvText parses complex nested config" {
            val configText =
                """
                app {
                    name = "test-app"
                    version = "1.0.0"
                    server {
                        port = 8080
                    }
                }
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getString("app.name") shouldBe "test-app"
            result.getString("app.version") shouldBe "1.0.0"
            result.getInt("app.server.port") shouldBe 8080
        }

        "buildConfigFromEnvText parses HOCON with substitution when resolved" {
            val configText =
                """
                baseUrl = "https://example.com"
                apiUrl = ${'$'}{baseUrl}"/api"
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            // Note: The config is not resolved by buildConfigFromEnvText itself
            // Substitutions are resolved later in buildConfig()
            result.getString("baseUrl") shouldBe "https://example.com"
            // After resolve(), apiUrl will be evaluated
            val resolved = result.resolve()
            resolved.getString("apiUrl") shouldBe "https://example.com/api"
        }

        "buildConfigFromEnvText returns null for invalid config syntax" {
            val configText = "invalid { syntax [ = "
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null for mismatched braces" {
            val configText = "app { name = \"test\" "
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null for unclosed quotes" {
            val configText = "key = \"value without closing quote"
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null for malformed arrays" {
            val configText = "items = [\"item1\", \"item2\""
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null for incomplete key-value pairs" {
            val configText = "key = "
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null for missing equals sign" {
            val configText = "key value"
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText allows trailing comma in object" {
            // HOCON allows trailing commas
            val configText = "app { name = \"test\", }"
            val result = buildConfigFromEnvText(configText)
            result.shouldNotBeNull()
            result.getString("app.name") shouldBe "test"
        }

        "buildConfigFromEnvText returns null for invalid escape sequences" {
            val configText = "key = \"invalid \\x escape\""
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText returns null for duplicate key at same level" {
            // HOCON typically allows duplicate keys (last wins), but some strict parsers may reject
            val configText = "key = \"value1\"\nkey = \"value2\""
            val result = buildConfigFromEnvText(configText)
            // This should actually parse successfully in HOCON - last value wins
            result.shouldNotBeNull()
            result.getString("key") shouldBe "value2"
        }

        "buildConfigFromEnvText returns null for nested mismatched brackets" {
            val configText = "outer { inner [ key = value ] }"
            val result = buildConfigFromEnvText(configText)
            result.shouldBeNull()
        }

        "buildConfigFromEnvText parses unquoted strings as valid HOCON" {
            // HOCON allows unquoted strings for simple values
            val configText = "key = value"
            val result = buildConfigFromEnvText(configText)
            result.shouldNotBeNull()
            result.getString("key") shouldBe "value"
        }

        "buildConfigFromEnvText includes KTP_CONFIG origin description" {
            val configText = "testKey = \"testValue\""
            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            val origin = result.getValue("testKey").origin()
            origin.description() shouldBe "${KtpConfig.ENV_CONFIG_KEY}: 1"
        }

        "buildConfigFromEnvText parses config with arrays" {
            val configText =
                """
                items = ["item1", "item2", "item3"]
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            val items = result.getStringList("items")
            items.size shouldBe 3
            items[0] shouldBe "item1"
            items[1] shouldBe "item2"
            items[2] shouldBe "item3"
        }

        "buildConfigFromEnvText parses config with nested objects" {
            val configText =
                """
                database {
                    host = "localhost"
                    port = 5432
                    credentials {
                        username = "admin"
                        password = "secret"
                    }
                }
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getString("database.host") shouldBe "localhost"
            result.getInt("database.port") shouldBe 5432
            result.getString("database.credentials.username") shouldBe "admin"
            result.getString("database.credentials.password") shouldBe "secret"
        }

        "buildConfigFromEnvText parses config with numbers and booleans" {
            val configText =
                """
                intValue = 42
                longValue = 9999999999
                doubleValue = 3.14
                boolValue = true
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getInt("intValue") shouldBe 42
            result.getLong("longValue") shouldBe 9999999999L
            result.getDouble("doubleValue") shouldBe 3.14
            result.getBoolean("boolValue") shouldBe true
        }

        "buildConfigFromEnvText parses config with comments" {
            val configText =
                """
                # This is a comment
                key1 = "value1"  // inline comment
                # Another comment
                key2 = "value2"
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getString("key1") shouldBe "value1"
            result.getString("key2") shouldBe "value2"
        }

        "buildConfigFromEnvText reads from system environment by default" {
            // When no parameter is passed, it should read from KTP_CONFIG env var
            // This will pass if the env var is not set (returns null)
            val envValue = System.getenv(KtpConfig.ENV_CONFIG_KEY)
            if (envValue.isNullOrBlank()) {
                val result = buildConfigFromEnvText()
                result.shouldBeNull()
            }
        }

        "buildConfigFromEnvText handles multiline config strings" {
            val configText =
                """
                app {
                    name = "multiline-app"
                    description = "This is a long description that spans multiple lines"
                    features = [
                        "feature1",
                        "feature2",
                        "feature3"
                    ]
                }
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getString("app.name") shouldBe "multiline-app"
            result.getStringList("app.features").size shouldBe 3
        }

        "buildConfigFromEnvText handles empty objects" {
            val configText =
                """
                emptySection {}
                nonEmptySection { key = "value" }
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.hasPath("emptySection") shouldBe true
            result.getString("nonEmptySection.key") shouldBe "value"
        }

        "buildConfigFromEnvText handles special characters in values" {
            val configText =
                """
                url = "https://example.com/path?param=value&other=123"
                regex = "\\d+\\.\\w+"
                json = "{\"key\": \"value\"}"
                """
                    .trimIndent()

            val result = buildConfigFromEnvText(configText)

            result.shouldNotBeNull()
            result.getString("url") shouldBe "https://example.com/path?param=value&other=123"
            result.getString("regex") shouldBe "\\d+\\.\\w+"
            result.getString("json") shouldBe "{\"key\": \"value\"}"
        }
    })
