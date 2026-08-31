plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    // Re-exported so auth consumers also get the shared GCP utilities.
    api(project(":libs:ktp-gcp"))

    api(project(":libs:ktp-ktor"))

    // Not inherited from ktp-gcp because JitPack builds could not resolve them transitively.
    api(platform(libs.gcp.bom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koin.bom))

    api(libs.ktor.sessions)
    api(libs.ktor.auth)

    api(libs.firebaseAdmin)
    implementation(libs.identityToolkit)

    testImplementation(project(":libs:ktp-test"))
}
