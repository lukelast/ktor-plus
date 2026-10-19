plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    // Re-exported so auth consumers also get the shared GCP utilities.
    api(project(":libs:ktp-gcp"))

    api(libs.ktor.sessions)
    api(libs.ktor.auth)

    api(libs.firebaseAdmin)
    implementation(libs.identityToolkit)

    testImplementation(project(":libs:ktp-test"))
}
