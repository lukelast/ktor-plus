plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(platform(libs.gcpBom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koinBom))

    api(libs.gcpCore)

    api(project(":libs:ktp-ktor"))

    // Provides TokenVerifier; version comes from gcpBom.
    api("com.google.auth:google-auth-library-oauth2-http")

    testImplementation(project(":libs:ktp-test"))
    testImplementation(libs.ktor.client.resources)
}
