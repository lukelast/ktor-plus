plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    // The auth hook this module implements and the storage it implements it on; both re-exported
    // so an app depending on this module alone gets the whole login stack.
    api(project(":libs:ktp-gcp-auth"))
    api(project(":libs:ktp-gcp-firestore"))

    // Not inherited transitively because JitPack builds could not resolve them that way.
    api(platform(libs.gcp.bom))
    api(platform(libs.ktor.bom))
    api(platform(libs.koin.bom))

    testImplementation(project(":libs:ktp-test"))
}
