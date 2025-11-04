plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-ktor"))

    // Ktor Sessions
    api(libs.ktor.sessions)

    // Firebase Admin SDK
    api(libs.firebaseAdmin)

    // Testing
    testImplementation(libs.koinTest)
}
