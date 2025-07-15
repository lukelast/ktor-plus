package net.ghue.ktp.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnvTest {

    @Test
    fun `test findEnvironment with KTP_ENV set`() {
        val envVar = "KTP_ENV"
        val value = "frank"
        System.setProperty(envVar, value)
        val env = findEnvironment()
        assertEquals(value, env.name)
        System.clearProperty(envVar)
    }

    @Test
    fun `test localDevEnv in config file`() {
        val env = findEnvironment()
        assertEquals("123", env.name)
    }

    @Test
    fun `local dev env `() {
        val env = Env(DEFAULT_ENV)
        assertEquals(true, env.isLocalDev)
    }

    @Test
    fun `local dev env is detected`() {
        val env = Env("$DEFAULT_ENV-xyz")
        assertEquals(true, env.isLocalDev)
    }

    @Test
    fun `unit test env`() {
        val env = Env.TEST_UNIT
        assertEquals(true, env.isCiTest)
        assertEquals(false, env.isLocalDev)
        assertEquals(false, env.isDefault)
    }
}
