plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    // GCP BOM for version management
    api(project(":libs:ktp-gcp"))

    api(project(":libs:ktp-ktor"))

    api(platform(libs.gcpBom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koinBom))

    // Ktor
    api(libs.ktor.sessions)
    api(libs.ktor.auth)

    // Firebase Admin SDK
    api(libs.firebaseAdmin)

    // Testing
    testImplementation(project(":libs:ktp-test"))
}
