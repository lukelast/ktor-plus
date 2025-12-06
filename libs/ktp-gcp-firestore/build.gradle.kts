plugins { id("com.github.lukelast.ktor-plus") }

dependencies {
    api(project(":libs:ktp-gcp"))

    api(libs.gcpFirestore)
    implementation(libs.kotlinReflect)

    testImplementation(project(":libs:ktp-test"))
}
