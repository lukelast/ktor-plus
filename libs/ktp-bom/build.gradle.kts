// Every version KTP chooses for apps; the KTP plugin adds this platform to each Kotlin project.
plugins {
    `java-platform`
    `maven-publish`
}

version = System.getenv("VERSION") ?: "0-SNAPSHOT"

repositories { mavenCentral() }

// Upstream BOMs are imported rather than copied, which a platform allows only when told to.
javaPlatform { allowDependencies() }

dependencies {
    api(platform(libs.ktor.bom))
    api(platform(libs.kotlinx.serialization.bom))
    api(platform(libs.kotlinx.coroutines.bom))
    api(platform(libs.koin.bom))
    api(platform(libs.kotest.bom))
    api(platform(libs.gcp.bom))
    constraints {
        // A versionless KTP lib next to a KtpLibs one cannot split releases.
        rootProject.subprojects
            .filter { it.parent?.name == "libs" && it != project }
            .forEach { api(it) }
    }
}

publishing { publications { create<MavenPublication>("mavenJava") { from(components["javaPlatform"]) } } }

// JitPack rewrites .module files, so there the platform ships as a POM only, like the other libs.
if (System.getenv("JITPACK") != null) {
    tasks.withType<GenerateModuleMetadata>().configureEach { enabled = false }
}
