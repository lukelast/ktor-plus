plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(project(":libs:ktp-gcp"))

    api(platform(libs.gcp.bom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koin.bom))

    api(libs.gcp.firestore)
    implementation(libs.kotlinReflect)

    testImplementation(project(":libs:ktp-test"))
}
