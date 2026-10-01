package net.ghue.ktp.gcp.auth.firestore.test

import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import io.mockk.mockk
import net.ghue.ktp.ktor.start.KtpAppBuilderFactory
import net.ghue.ktp.ktor.start.update
import org.koin.core.module.Module
import org.koin.dsl.onClose

/**
 * The app with its GCP clients replaced, so it serves requests with no project or credentials.
 * [firebase] is strict: an account check the test did not script fails it. [firestore] is relaxed
 * because building the login hook already touches it. [overrides] swaps app services in the same
 * override module, so they beat the app's own definitions.
 */
fun KtpAppBuilderFactory.withGcpStubs(
    firebase: FirebaseAuth = mockk(),
    firestore: Firestore = mockk(relaxed = true),
    overrides: Module.() -> Unit = {},
): KtpAppBuilderFactory = update {
    addOverrideModule {
        // Strict, so `/auth/config` fails instead of loading whatever credentials the machine has.
        single<FirebaseApp> { mockk() }
        single<FirebaseAuth> { firebase }
        // The caller owns a client it passed in, so the app stopping leaves it open.
        single<Firestore> { firestore } onClose {}
        overrides()
    }
}
