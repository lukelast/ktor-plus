plugins { id("com.github.lukelast.ktor-plus.project") }

dependencies {
    // The stack under test and the harness this extends; both re-exported so an app's test
    // classpath needs this module alone.
    api(project(":libs:ktp-gcp-auth-firestore"))
    api(project(":libs:ktp-test"))
}

// Credential lookups find nothing on any machine, as in CI, so a boot that makes one fails here.
tasks.withType<Test>().configureEach {
    environment("GOOGLE_APPLICATION_CREDENTIALS", "no-such-credentials.json")
}
