// https://stackoverflow.com/questions/75673923/build-config-for-developing-a-gradle-plugin-written-in-kotlin
plugins {
    `kotlin-dsl`
    `maven-publish`
}

tasks.validatePlugins { enableStricterValidation.set(true) }

group = "com.github.lukelast.ktor-plus"

version = System.getenv("VERSION") ?: "0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradleKotlin)
    implementation(libs.gradleKtor)
    implementation(libs.gradleKotlinSerialization)
    implementation(libs.gradleKtfmt)
    implementation(libs.gradleDetekt)
    implementation(libs.gradleKoinCompiler)
    implementation(libs.gradleFoojay)
    // Must be on the runtime classpath (not compileOnly) to win over the older Shadow the
    // Ktor plugin pulls in: 9.1.0 has service-file merge regressions (GradleUp/shadow#1348).
    implementation(libs.gradleShadow)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

kotlin { jvmToolchain(21) }

java { withSourcesJar() }

gradlePlugin {
    plugins {
        // The settings plugin owns the bare repo-group id: it is the entry point consumers
        // resolve by marker, and JitPack only serves markers whose group equals the repo group.
        // The project plugins are normally auto-applied by class and rarely resolved by id.
        create("ktpGradleSettingsPlugin") {
            id = group.toString()
            implementationClass = "net.ghue.ktp.gradle.settings.SettingsPlugin"
        }
        create("ktpGradleProjectPlugin") {
            id = "$group.project"
            implementationClass = "net.ghue.ktp.gradle.project.ProjectPlugin"
        }
        create("lukestackGradlePlugin") {
            id = "$group.lukestack"
            implementationClass = "net.ghue.ktp.gradle.lukestack.LukestackPlugin"
        }
    }
}

tasks.named<Copy>("processResources") { from("../gradle/libs.versions.toml") { into("") } }

val sourceGenDir = "generated/version"
val versionGenTask =
    tasks.register("generateVersionFile") {
        val outputFile = layout.buildDirectory.file("$sourceGenDir/net/ghue/ktp/lib/Version.kt")
        val projectGroup = project.group.toString()
        val projectVersion = project.version.toString()
        val koinVersion = libs.versions.koin.get()
        val kotestVersion = libs.versions.kotest.get()
        val byteBuddyVersion = libs.versions.byteBuddy.get()
        val ktfmtVersion = libs.versions.ktfmt.get()

        // Only real library projects; generated directories like libs/build must not leak in.
        val libraryNames =
            rootDir
                .resolve("../libs")
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.resolve("build.gradle.kts").exists() }
                .map { it.name }
                .sorted()

        inputs.property("group", projectGroup)
        inputs.property("version", projectVersion)
        inputs.property("koinVersion", koinVersion)
        inputs.property("kotestVersion", kotestVersion)
        inputs.property("byteBuddyVersion", byteBuddyVersion)
        inputs.property("ktfmtVersion", ktfmtVersion)
        inputs.property("libraryNames", libraryNames)
        outputs.file(outputFile)

        doLast {
            outputFile.get().asFile.parentFile.mkdirs()
            outputFile
                .get()
                .asFile
                .writeText(
                    """
            package net.ghue.ktp.lib

            /** Generated from `gradle/libs.versions.toml`; do not edit by hand. */
            object KtpVersion {
                /** Maven group all ktor-plus artifacts are published under. */
                const val GROUP = "$projectGroup"

                /** ktor-plus's own published version. */
                const val VERSION = "$projectVersion"

                /** koin BOM version this plugin injects into consumer builds. */
                const val KOIN = "$koinVersion"

                /** kotest BOM version this plugin injects into consumer builds. */
                const val KOTEST = "$kotestVersion"

                /** Byte Buddy agent version this plugin preloads for MockK-friendly tests. */
                const val BYTE_BUDDY = "$byteBuddyVersion"

                /** ktfmt engine version used by the formatting tasks. */
                const val KTFMT = "$ktfmtVersion"

                /** Names of the ktor-plus library modules this plugin publishes. */
                val libs = listOf(${libraryNames.joinToString { "\"$it\"" }})
            }
        """
                        .trimIndent()
                )
        }
    }

sourceSets["main"].java { srcDir(layout.buildDirectory.dir(sourceGenDir)) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { dependsOn(versionGenTask) }

tasks.named("sourcesJar") { dependsOn(versionGenTask) }
