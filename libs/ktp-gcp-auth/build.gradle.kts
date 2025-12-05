plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    // GCP BOM for version management
    api(project(":libs:ktp-gcp"))

    api(project(":libs:ktp-ktor"))

    // Ktor
    api(libs.ktor.sessions)
    api(libs.ktor.auth)

    // Firebase Admin SDK
    api(libs.firebaseAdmin)

    // Testing
    testImplementation(project(":libs:ktp-test"))
}
