package net.ghue.ktp.log

import org.junit.jupiter.api.Test

class KtpLogTest {
    @Test
    fun log() {
        log {}.info { "Hi" }
    }

    @Test
    fun `test maybeAdjustLoggerForDevelopment`() {
        maybeAdjustLoggerForDevelopment()
        log {}.info { "yo" }
    }
}
