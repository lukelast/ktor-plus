package net.ghue.ktp.ktor.app

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class HealthTest :
    StringSpec({
        "health endpoint returns Alive when healthy" {
            testApplication {
                application { installK8sHealthCheck() }
                with(client.get(K8S_LIVENESS_PATH)) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "Alive"
                }
            }
        }

        "readiness endpoint returns Ready when healthy" {
            testApplication {
                application { installK8sHealthCheck() }
                val response = client.get(K8S_READINESS_PATH)
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "Ready"
            }
        }

        "health endpoint returns ServiceUnavailable when unhealthy" {
            testApplication {
                application { installK8sHealthCheck(healthCheck = { false }) }
                val response = client.get(K8S_LIVENESS_PATH)
                response.status shouldBe HttpStatusCode.ServiceUnavailable
                response.bodyAsText() shouldBe "Not Alive"
            }
        }

        "readiness endpoint returns ServiceUnavailable when not ready" {
            testApplication {
                application { installK8sHealthCheck(readinessCheck = { false }) }
                val response = client.get(K8S_READINESS_PATH)
                response.status shouldBe HttpStatusCode.ServiceUnavailable
                response.bodyAsText() shouldBe "Not Ready"
            }
        }
    })
