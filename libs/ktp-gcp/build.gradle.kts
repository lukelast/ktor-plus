plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(platform(libs.gcpBom))

    api(libs.gcpCore)

    // Ktor dependency for plugin functionality
    api(project(":libs:ktp-ktor"))

    // Google auth library for TokenVerifier (managed by gcpBom)
    api("com.google.auth:google-auth-library-oauth2-http")

    // Testing
    testImplementation(project(":libs:ktp-test"))
    testImplementation(libs.ktor.client.resources)
}
