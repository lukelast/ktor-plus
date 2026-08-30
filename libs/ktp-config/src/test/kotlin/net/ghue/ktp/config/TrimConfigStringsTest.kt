package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/** Parses and resolves HOCON with a stable origin description for origin assertions. */
private fun parse(text: String): Config =
    ConfigFactory.parseString(text, ConfigParseOptions.defaults().setOriginDescription("test"))
        .resolve()

class TrimConfigStringsTest :
    StringSpec({
        "trims strings throughout the config tree" {
            val config =
                parse(
                        """
                        root {
                            direct = "  value  "
                            words = "  two  words  "
                            "dotted.key" = " x "
                            list = [" one ", 2, { nested = "\tthree\r\n" }]
                            number = 4
                            enabled = true
                            nothing = null
                        }
                        """
                            .trimIndent()
                    )
                    .withTrimmedStrings()

            config.getString("root.direct") shouldBe "value"
            config.getString("root.words") shouldBe "two  words"
            config.getString("root.\"dotted.key\"") shouldBe "x"
            val list = config.getList("root.list")
            list[0].unwrapped() shouldBe "one"
            list[1].unwrapped() shouldBe 2
            (list[2] as ConfigObject)["nested"]?.unwrapped() shouldBe "three"
            config.getInt("root.number") shouldBe 4
            config.getBoolean("root.enabled") shouldBe true
            config.getIsNull("root.nothing") shouldBe true
        }

        "trims only ascii whitespace and control characters" {
            val config = parse("""a = "  x\r\n", b = "x\u00a0", c = "\u00a0x"""")

            val trimmed = config.withTrimmedStrings()

            trimmed.getString("a") shouldBe "x"
            trimmed.getString("b") shouldBe "x\u00A0"
            trimmed.getString("c") shouldBe "\u00A0x"
        }

        "whitespace-only value becomes empty" {
            parse("""v = "   """").withTrimmedStrings().getString("v") shouldBe ""
        }

        "retains origins on trimmed values and their siblings" {
            val original = parse("padded = \"  x  \"\nclean = y")
            val paddedOrigin = original.getValue("padded").origin()
            val cleanOrigin = original.getValue("clean").origin()

            val trimmed = original.withTrimmedStrings()

            trimmed.getValue("padded").origin() shouldBe paddedOrigin
            trimmed.getValue("clean").origin() shouldBe cleanOrigin
        }

        "retains origins on containers and non-string values" {
            val original =
                parse(
                    """
                    outer {
                        inner = "  x  "
                        num = 1
                        flag = true
                        nada = null
                    }
                    items = ["  a  ", 2]
                    """
                        .trimIndent()
                )

            val trimmed = original.withTrimmedStrings()

            trimmed.root().origin() shouldBe original.root().origin()
            trimmed.getObject("outer").origin() shouldBe original.getObject("outer").origin()
            trimmed.getValue("outer.num").origin() shouldBe original.getValue("outer.num").origin()
            trimmed.getValue("outer.flag").origin() shouldBe
                original.getValue("outer.flag").origin()
            trimmed.getObject("outer")["nada"]?.origin() shouldBe
                original.getObject("outer")["nada"]?.origin()
            trimmed.getValue("items").origin() shouldBe original.getValue("items").origin()
            trimmed.getList("items")[0].origin() shouldBe original.getList("items")[0].origin()
            trimmed.getList("items")[1].origin() shouldBe original.getList("items")[1].origin()
        }

        "returns the same instance when nothing needs trimming" {
            val config = parse("""a = "x", nested { b = 1 }, list = ["y"]""")

            config.withTrimmedStrings() shouldBeSameInstanceAs config
        }

        "reports every trimmed path with its origin, sorted" {
            val config =
                parse(
                    """
                    b { c = "  x  " }
                    a = "  y  "
                    list = ["  p  ", "q", "  r  "]
                    clean = "z"
                    """
                        .trimIndent()
                )

            val (_, report) = config.trimStringsWithReport()

            report shouldBe
                listOf("a (test: 2)", "b.c (test: 1)", "list[0] (test: 3)", "list[2] (test: 3)")
        }

        "override padding cannot leak into substitution concatenations" {
            val files =
                listOf(
                    fakeConfig(
                        0,
                        text =
                            $$"""
                            name = file
                            derived = ${name}"-suffix"
                            """
                                .trimIndent(),
                    )
                )

            val config = buildConfig(Env.TEST_UNIT, files, mapOf("name" to "padded\n"))

            config.getString("name") shouldBe "padded"
            config.getString("derived") shouldBe "padded-suffix"
        }

        "trims CONFIG_FORCE_ env-var overrides before substitutions resolve" {
            val files =
                listOf(
                    fakeConfig(
                        0,
                        text =
                            $$"""
                            name = file
                            derived = ${name}"-suffix"
                            """
                                .trimIndent(),
                    )
                )
            val envOverrides = ConfigFactory.parseMap(mapOf("name" to " env\n"), "env variables")

            val config = buildConfig(Env.TEST_UNIT, files, envOverrides = envOverrides)

            config.getString("name") shouldBe "env"
            config.getString("derived") shouldBe "env-suffix"
        }

        "buildConfig trims file values" {
            val files = listOf(fakeConfig(0, text = """v = "  padded  """"))

            buildConfig(Env.TEST_UNIT, files).getString("v") shouldBe "padded"
        }

        "fails fast on a config with unresolved substitutions" {
            val unresolved = ConfigFactory.parseString($$"""a = 1, b = ${a}""")

            shouldThrow<ConfigException.NotResolved> { unresolved.withTrimmedStrings() }
        }

        "trimmed values flow into extracted data classes" {
            val config = KtpConfig.create {
                setUnitTestEnv()
                overrideValue("app.name", "  padded \r\n")
            }

            config.data.app.name shouldBe "padded"
            config.config.getString("app.name") shouldBe "padded"
        }
    })
