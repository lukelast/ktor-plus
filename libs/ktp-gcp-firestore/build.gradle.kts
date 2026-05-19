plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-gcp"))

    api(platform(libs.gcpBom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koinBom))

    api(libs.gcpFirestore)
    implementation(libs.kotlinReflect)

    testImplementation(project(":libs:ktp-test"))
}
