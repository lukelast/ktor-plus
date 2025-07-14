package net.ghue.ktp.ktor.app

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val K8S_LIVENESS = "/healthz"
const val K8S_READINESS = "/readyz"

/** Add Kubernetes health checks to the application. */
fun Application.installK8sHealthCheck(
    /** If this returns false, K8s will restart the pod. */
    healthCheck: () -> Boolean = { true },
    /** If this returns false, K8s will not send traffic to the pod. */
    readinessCheck: () -> Boolean = { true },
) {
    routing {
        get(K8S_LIVENESS) {
            if (healthCheck()) {
                call.respondText("Alive", status = HttpStatusCode.OK)
            } else {
                call.respondText("Not Alive", status = HttpStatusCode.ServiceUnavailable)
            }
        }
        get(K8S_READINESS) {
            if (readinessCheck()) {
                call.respondText("Ready", status = HttpStatusCode.OK)
            } else {
                call.respondText("Not Ready", status = HttpStatusCode.ServiceUnavailable)
            }
        }
    }
}
