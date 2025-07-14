package net.ghue.ktp.ktor.app

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthKtTest {

    @Test
    fun healthOk() = testApplication {
        application { installK8sHealthCheck() }
        with(client.get(K8S_LIVENESS)) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Alive", bodyAsText())
        }
    }

    @Test
    fun readinessOk() = testApplication {
        application { installK8sHealthCheck() }
        val response = client.get(K8S_READINESS)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ready", response.bodyAsText())
    }

    @Test
    fun healthNotOk() = testApplication {
        application { installK8sHealthCheck(healthCheck = { false }) }
        val response = client.get(K8S_LIVENESS)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("Not Alive", response.bodyAsText())
    }

    @Test
    fun readinessNotOk() = testApplication {
        application { installK8sHealthCheck(readinessCheck = { false }) }
        val response = client.get(K8S_READINESS)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("Not Ready", response.bodyAsText())
    }
}
