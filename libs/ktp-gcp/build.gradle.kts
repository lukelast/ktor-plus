plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(platform(libs.gcp.bom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koin.bom))

    api(libs.gcp.core)

    api(project(":libs:ktp-ktor"))

    // Provides TokenVerifier; version comes from gcp-bom.
    api("com.google.auth:google-auth-library-oauth2-http")

    testImplementation(project(":libs:ktp-test"))
    testImplementation(libs.ktor.client.resources)
}
