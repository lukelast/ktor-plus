package net.ghue.ktp.gcp.auth.firestore.test

import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import net.ghue.ktp.gcp.cron.CronHandler
import net.ghue.ktp.ktor.start.update
import net.ghue.ktp.test.ktpTestApp
import org.koin.core.error.InstanceCreationException
import org.koin.ktor.ext.getKoin

/**
 * The image build's AOT training run and CI's smoke test start the app with no GCP credentials, so
 * boot and the public routes must never build a GCP client.
 */
class BootWithoutCredentialsTest :
    StringSpec({
        "a fully wired app boots and serves public routes without credentials" {
            ktpTestApp(lukestackApp) { publicRoutesRespond() }
        }

        "boot and the public routes resolve no GCP client" {
            // A Firestore client builds without credentials on a machine that has a GCP project,
            // so the missing credentials alone would let an eager one through.
            val tripwired = lukestackApp.update {
                addOverrideModule {
                    single<FirebaseApp> { error("FirebaseApp was built") }
                    single<FirebaseAuth> { error("FirebaseAuth was built") }
                    single<Firestore> { error("Firestore was built") }
                    single<CronHandler> { error("CronHandler was built") }
                }
            }

            ktpTestApp(tripwired) {
                publicRoutesRespond()
                // Each one now fails to resolve, which shows the tripwires replaced the real ones.
                val koin = application.getKoin()
                val tripwires =
                    listOf(
                        FirebaseApp::class,
                        FirebaseAuth::class,
                        Firestore::class,
                        CronHandler::class,
                    )
                for (type in tripwires) {
                    shouldThrow<InstanceCreationException> { koin.get<Any>(type) }
                }
            }
        }
    })

private suspend fun ApplicationTestBuilder.publicRoutesRespond() {
    client.get("/debug/version").status shouldBe HttpStatusCode.OK
    client.get("/api/hello").status shouldBe HttpStatusCode.OK
}
