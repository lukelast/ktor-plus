plugins { id("com.github.lukelast.ktor-plus") }

group = "ktp.example"

version = "0.0.1"

application { mainClass.set("ktp.example.KtpKt") }

dependencies {
    implementation(project(":libs:ktp-ktor"))

    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-compression-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-gson-jvm")
}
