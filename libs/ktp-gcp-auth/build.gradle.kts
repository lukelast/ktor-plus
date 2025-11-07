plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-ktor"))

    // Ktor
    api(libs.ktor.sessions)
    api(libs.ktor.auth)

    // Firebase Admin SDK
    api(libs.firebaseAdmin)

    // Testing
    testImplementation(libs.ktor.test)
    testImplementation(libs.koinTest)
}
