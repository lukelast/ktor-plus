package net.ghue.ktp.ktor.app

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.app.debug.installConfigDebugInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebugEndpointsTest {

    @Test
    fun `check version`() = testApplication {
        application {
            installConfigDebugInfo(KtpConfig.createManagerForTest(mapOf("app.version" to "69")))
        }
        with(client.get("/debug/version")) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("69", bodyAsText())
        }
    }
}
