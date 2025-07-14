plugins {
    id("net.ghue.ktp.gradle")
}

dependencies {
    api(project(":libs:ktp-core"))
    api(libs.typesafeConfig)
    api(libs.config4k)
    implementation(libs.classgraph)
}
