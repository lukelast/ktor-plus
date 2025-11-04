package net.ghue.ktp.gcp.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.ServiceOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

fun firebaseAuthModule() = module {
    single {
        FirebaseApp.initializeApp(
            FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(ServiceOptions.getDefaultProjectId())
                .build()
        )
    }

    single { FirebaseAuth.getInstance(get()) }

    factoryOf(::FirebaseAuthService)
}
