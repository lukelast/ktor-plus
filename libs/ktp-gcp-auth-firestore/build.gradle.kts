plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    // The auth hook this module implements and the storage it implements it on; both re-exported
    // so an app depending on this module alone gets the whole login stack.
    api(project(":libs:ktp-gcp-auth"))
    api(project(":libs:ktp-gcp-firestore"))

    testImplementation(project(":libs:ktp-test"))
}
