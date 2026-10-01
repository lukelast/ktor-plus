@file:Suppress("MatchingDeclarationName")

package net.ghue.ktp.gcp.auth.firestore.test

import com.google.cloud.firestore.Firestore
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import net.ghue.ktp.gcp.auth.FirebaseAuthPlugin
import net.ghue.ktp.gcp.auth.authenticateFirebase
import net.ghue.ktp.gcp.auth.firebaseAuthModule
import net.ghue.ktp.gcp.auth.firestore.firestoreUserStoreModule
import net.ghue.ktp.gcp.auth.installDebugRoutes
import net.ghue.ktp.gcp.auth.userOrError
import net.ghue.ktp.gcp.cron.CronHandler
import net.ghue.ktp.gcp.cron.CronResult
import net.ghue.ktp.gcp.cron.installApiRoutesCron
import net.ghue.ktp.gcp.firestore.firestoreModule
import net.ghue.ktp.ktor.plugin.ViteFrontendPlugin
import net.ghue.ktp.ktor.plugin.installDefaultPlugins
import net.ghue.ktp.ktor.start.ktpAppCreate

@Serializable internal data class RpcMe(val email: String, val tenantId: String)

/** A job holding a GCP client, as real ones do, so resolving it at boot would build that client. */
private class FirestoreCron(@Suppress("unused") private val db: Firestore) : CronHandler {
    override suspend fun hourly(utcHour: Int) = CronResult()
}

/** Wired like a lukestack app's `Ktp.kt`, so these tests boot what the apps boot. */
internal val lukestackApp = ktpAppCreate {
    addModule(firebaseAuthModule())
    addModule(firestoreModule())
    addModule(firestoreUserStoreModule())
    addModule { single<CronHandler> { FirestoreCron(get()) } }
    addAppInit { config ->
        installDefaultPlugins(config)
        install(FirebaseAuthPlugin)
        routing {
            installApiRoutesCron()
            get("/api/hello") { call.respondText("hello") }
            authenticateFirebase {
                get("/api/me") {
                    val user = call.userOrError()
                    call.respond(RpcMe(user.email, user.tenantId.value))
                }
            }
        }
        installDebugRoutes()
        install(ViteFrontendPlugin)
    }
}
