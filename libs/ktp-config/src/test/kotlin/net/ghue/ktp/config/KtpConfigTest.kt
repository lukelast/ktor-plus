package net.ghue.ktp.config

import net.ghue.ktp.config.KtpConfig.buildConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KtpConfigTest {

    @Test
    fun `check env`() {
        assertEquals(Env.TEST_UNIT, KtpConfig.createManagerForTest().env)
    }

    @Test
    fun `create manager test overrides`() {
        assertEquals("", KtpConfig.createManagerForTest().data.app.name)
        assertEquals(
            "blah",
            KtpConfig.createManagerForTest(mapOf("app.name" to "blah")).data.app.name,
        )
    }

    @Test
    fun `build config priority`() {
        val files =
            listOf(fakeConfig(9, text = """v=default"""), fakeConfig(5, text = """v=app"""))
                .shuffled()
        val config = buildConfig(Env.TEST_UNIT, files)
        val value = config.getValue("v")
        assertEquals("app", value.unwrapped())
        assertEquals("5.conf: 1", value.origin().description())
    }

    @Test
    fun `build config priority name`() {
        val files =
            listOf(
                    fakeConfig(0, name = "a", text = """v=a"""),
                    fakeConfig(0, name = "b", text = """v=b"""),
                    fakeConfig(0, name = "c", text = """v=c"""),
                )
                .shuffled()
        val config = buildConfig(Env("env"), files)
        assertEquals("a", config.getValue("v").unwrapped())
    }

    @Test
    fun `config file sorting - priority takes precedence`() {
        val files =
            listOf(
                    fakeConfig(9, name = "z", text = """v=9"""),
                    fakeConfig(5, name = "a", text = """v=5"""),
                    fakeConfig(1, name = "m", text = """v=1"""),
                )
                .shuffled()

        val sorted = files.sorted()
        assertEquals(listOf(1, 5, 9), sorted.map { it.priority })
    }

    @Test
    fun `config file sorting - env vs no env at same priority`() {
        val files =
            listOf(
                    fakeConfig(5, name = "config", env = "", text = """v=no_env"""),
                    fakeConfig(5, name = "config", env = "prod", text = """v=prod"""),
                )
                .shuffled()

        val sorted = files.sorted()
        assertEquals("prod", sorted[0].envName)
        assertEquals("", sorted[1].envName)
    }

    @Test
    fun `config file sorting - name vs no name at same priority`() {
        val files =
            listOf(
                    fakeConfig(5, name = "", env = "", text = """v=no_name"""),
                    fakeConfig(5, name = "config", env = "", text = """v=with_name"""),
                )
                .shuffled()

        val sorted = files.sorted()
        assertEquals("config", sorted[0].name)
        assertEquals("", sorted[1].name)
    }

    @Test
    fun `config file sorting - alphabetical name order at same priority`() {
        val files =
            listOf(
                    fakeConfig(5, name = "zebra", env = "", text = """v=zebra"""),
                    fakeConfig(5, name = "apple", env = "", text = """v=apple"""),
                    fakeConfig(5, name = "banana", env = "", text = """v=banana"""),
                )
                .shuffled()

        val sorted = files.sorted()
        assertEquals(listOf("apple", "banana", "zebra"), sorted.map { it.name })
    }

    @Test
    fun `config file sorting - complex priority and env combinations`() {
        val files =
            listOf(
                    fakeConfig(8, name = "config", env = "dev", text = """v=8_config_dev"""),
                    fakeConfig(8, name = "config", env = "", text = """v=8_config_all"""),
                    fakeConfig(3, name = "config", env = "dev", text = """v=3_config_dev"""),
                    fakeConfig(3, name = "config", env = "", text = """v=3_config_all"""),
                    fakeConfig(3, name = "", env = "", text = """v=3_no_name"""),
                )
                .shuffled()

        val sorted = files.sorted()

        // Expected order: priority first, then env presence, then name, then path
        assertEquals(3, sorted[0].priority)
        assertEquals("dev", sorted[0].envName)
        assertEquals("config", sorted[0].name)

        assertEquals(3, sorted[1].priority)
        assertEquals("", sorted[1].envName)
        assertEquals("config", sorted[1].name)

        assertEquals(3, sorted[2].priority)
        assertEquals("", sorted[2].envName)
        assertEquals("", sorted[2].name)
    }

    @Test
    fun `config file sorting - env name alphabetical order`() {
        val files =
            listOf(
                    fakeConfig(5, name = "config", env = "zebra", text = """v=zebra"""),
                    fakeConfig(5, name = "config", env = "apple", text = """v=apple"""),
                    fakeConfig(5, name = "config", env = "banana", text = """v=banana"""),
                )
                .shuffled()

        val sorted = files.sorted()
        assertEquals(listOf("apple", "banana", "zebra"), sorted.map { it.envName })
    }

    @Test
    fun `config file sorting - comprehensive test with all sorting criteria`() {
        val files =
            listOf(
                    fakeConfig(9, name = "z", env = "prod", text = """v=9_z_prod"""),
                    fakeConfig(2, name = "a", env = "dev", text = """v=2_a_dev"""),
                    fakeConfig(2, name = "a", env = "", text = """v=2_a_all"""),
                    fakeConfig(2, name = "", env = "test", text = """v=2_no_name_test"""),
                    fakeConfig(2, name = "", env = "", text = """v=2_no_name_all"""),
                    fakeConfig(6, name = "b", env = "staging", text = """v=6_b_staging"""),
                )
                .shuffled()

        val sorted = files.sorted()

        // Verify complete sort order
        assertEquals(2, sorted[0].priority)
        assertEquals("dev", sorted[0].envName)
        assertEquals("a", sorted[0].name)

        assertEquals(2, sorted[1].priority)
        assertEquals("test", sorted[1].envName)
        assertEquals("", sorted[1].name)

        assertEquals(2, sorted[2].priority)
        assertEquals("", sorted[2].envName)
        assertEquals("a", sorted[2].name)

        assertEquals(2, sorted[3].priority)
        assertEquals("", sorted[3].envName)
        assertEquals("", sorted[3].name)

        assertEquals(6, sorted[4].priority)
        assertEquals("staging", sorted[4].envName)
        assertEquals("b", sorted[4].name)

        assertEquals(9, sorted[5].priority)
        assertEquals("prod", sorted[5].envName)
        assertEquals("z", sorted[5].name)
    }

    @Test
    fun `config file name sorting`() {
        val expected =
            listOf(
                "0.app.dev.conf",
                "0.database.dev.conf",
                "0.local.dev.conf",
                "0.app.prod.conf",
                "0.database.prod.conf",
                "0.local.prod.conf",
                "0.app.test.conf",
                "0.app.conf",
                "0.database.conf",
                "0.local.conf",
                "0.conf",
                "1.app.dev.conf",
                "1.cache.dev.conf",
                "1.app.prod.conf",
                "1.cache.prod.conf",
                "1.app.staging.conf",
                "1.app.conf",
                "1.cache.conf",
                "1.conf",
                "5.feature.dev.conf",
                "5.feature.prod.conf",
                "5.feature.test.conf",
                "5.feature.conf",
                "5.conf",
                "9.final.dev.conf",
                "9.final.prod.conf",
                "9.final.staging.conf",
                "9.final.conf",
                "9.conf",
            )

        val files = expected.shuffled().map { ConfigFile.create(it, "") }.sorted()
        assertEquals(expected, files.map { it.fileName })
    }
}
