package net.ghue.ktp.config

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KtpConfigManagerTest {

    private fun create() =
        KtpConfigManager(
            ConfigFactory.parseMap(
                mapOf(
                    "app.name" to "",
                    "app.nameShort" to "",
                    "app.secret" to "",
                    "app.version" to "",
                    "app.hostname" to "",
                )
            ),
            findEnvironment(),
        )

    private inline fun <reified T : Any> doSubConfigBadConstructor() {
        val ktp = create()

        val exceptionMsg = assertThrows(Exception::class.java) { ktp.get<T>() }.message!!

        assertTrue(exceptionMsg.contains("class must have a primary constructor"))
        assertTrue(exceptionMsg.contains("KtpConfig"))
    }

    class CorrectTestConfig(private val config: KtpConfigManager) {
        val msg = "hi"
    }

    @Test
    fun subConfig() {
        val config = create()
        assertEquals("hi", config.get<CorrectTestConfig>().msg)
    }

    @Test
    fun subConfigCache() {
        val config = create()
        val result1 = config.get<CorrectTestConfig>()
        val result2 = config.get<CorrectTestConfig>()
        assertSame(result1, result2)
    }

    @Test
    fun subConfigBadConstructorWrongType() {
        class TestConfig(private val config: String)
        doSubConfigBadConstructor<TestConfig>()
    }

    @Test
    fun subConfigBadConstructorTooMany() {
        class TestConfig(private val config: KtpConfigManager, val yo: String)
        doSubConfigBadConstructor<TestConfig>()
    }

    @Test
    fun getAllConfig() {
        val ktp = create()
        val allConfig = ktp.getAllConfig()
        assertEquals(
            mapOf(
                "app.name" to "",
                "app.nameShort" to "",
                "app.secret" to "0 chars",
                "app.version" to "",
                "app.hostname" to "",
            ),
            allConfig,
        )
    }

    @Test
    fun `logAllConfig test`() {
        val config = create()
        config.logAllConfig()
    }
}
