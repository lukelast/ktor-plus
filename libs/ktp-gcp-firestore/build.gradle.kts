plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    api(project(":libs:ktp-gcp"))

    api(libs.gcp.firestore)

    testImplementation(project(":libs:ktp-test"))
}
