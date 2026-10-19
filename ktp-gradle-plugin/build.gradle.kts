// https://stackoverflow.com/questions/75673923/build-config-for-developing-a-gradle-plugin-written-in-kotlin
import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask

plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.ktfmt)
}

// The same formatting the plugin gives consumers (features/Ktfmt.kt); this build is not a consumer.
ktfmt { kotlinLangStyle() }

configurations.named("ktfmt") {
    resolutionStrategy.force("com.facebook:ktfmt:${libs.versions.ktfmt.get()}")
}

// ktfmt's forked JVM is the daemon's; JDK 23+ warns about its compiler's Unsafe use unless told so.
if (JavaVersion.current().majorVersion.toInt() >= 23) {
    tasks.withType<KtfmtBaseTask>().configureEach {
        processIsolationJvmArgs.add("--sun-misc-unsafe-memory-access=allow")
    }
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
    implementation(libs.gradleCompose)
    implementation(libs.gradleComposeCompiler)
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
        // The other two plugins are normally auto-applied by class and rarely resolved by id.
        create("ktpGradleSettingsPlugin") {
            id = group.toString()
            implementationClass = "net.ghue.ktp.gradle.settings.SettingsPlugin"
        }
        create("ktpGradleProjectPlugin") {
            id = "$group.project"
            implementationClass = "net.ghue.ktp.gradle.project.ProjectPlugin"
        }
        create("ktpGradleLukestackPlugin") {
            id = "$group.lukestack"
            implementationClass = "net.ghue.ktp.gradle.lukestack.LukestackPlugin"
        }
    }
}

val sourceGenDir = "generated/version"
val versionGenTask =
    tasks.register("generateVersionFile") {
        val outputFile = layout.buildDirectory.file("$sourceGenDir/net/ghue/ktp/lib/Version.kt")
        val projectGroup = project.group.toString()
        val projectVersion = project.version.toString()
        val byteBuddyVersion = libs.versions.byteBuddy.get()
        val ktfmtVersion = libs.versions.ktfmt.get()
        val composeVersion = libs.versions.compose.get()
        val composeRulesVersion = libs.versions.composeRules.get()

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
        inputs.property("byteBuddyVersion", byteBuddyVersion)
        inputs.property("ktfmtVersion", ktfmtVersion)
        inputs.property("composeVersion", composeVersion)
        inputs.property("composeRulesVersion", composeRulesVersion)
        inputs.property("libraryNames", libraryNames)
        outputs.file(outputFile)

        doLast {
            outputFile.get().asFile.parentFile.mkdirs()
            val libAccessors =
                libraryNames.joinToString("\n\n") { name ->
                    val alias =
                        name
                            .removePrefix("ktp-")
                            .split('-')
                            .mapIndexed { i, part ->
                                if (i == 0) part else part.replaceFirstChar { it.uppercaseChar() }
                            }
                            .joinToString("")
                    "    /** `$projectGroup:$name` */\n" +
                        "    const val $alias = \"$projectGroup:$name:$projectVersion\""
                }
            outputFile
                .get()
                .asFile
                .writeText(
                    """
            package net.ghue.ktp.lib

            /** Generated from `gradle/libs.versions.toml`; do not edit by hand. */
            internal object KtpVersion {
                /** ktor-plus's own published version. */
                const val VERSION = "$projectVersion"

                /** Byte Buddy agent version this plugin preloads for MockK-friendly tests. */
                const val BYTE_BUDDY = "$byteBuddyVersion"

                /** ktfmt engine version used by the formatting tasks. */
                const val KTFMT = "$ktfmtVersion"

                /** Compose Multiplatform version `compose` mode gives versionless coordinates. */
                const val COMPOSE = "$composeVersion"

                /** Compose detekt rule set (io.nlopez.compose.rules) `compose` mode adds. */
                const val COMPOSE_RULES = "$composeRulesVersion"
            }

            /**
             * Dependency coordinates for every KTP library, pinned to this
             * plugin's own release so they can never drift from it.
             * Usable from any build script of a build that applies a KTP plugin:
             * `implementation(KtpLibs.stripe)`.
             */
            object KtpLibs {
            %LIBS%
            }
        """
                        // %LIBS% is spliced after trimIndent because the accessor block's
                        // 4-space lines would otherwise corrupt the common-indent computation.
                        .trimIndent()
                        .replace("%LIBS%", libAccessors)
                )
        }
    }

sourceSets["main"].java { srcDir(layout.buildDirectory.dir(sourceGenDir)) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { dependsOn(versionGenTask) }

tasks.named("sourcesJar") { dependsOn(versionGenTask) }

// The Compose detekt rule set is compiled against one detekt release; fail when the catalog pairs
// it with another, instead of at lint time in an app.
val checkComposeRulesDetekt =
    tasks.register("checkComposeRulesDetekt") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks that the Compose detekt rules are built against KTP's detekt."
        val detektVersion = libs.versions.detekt.get()
        val rulesVersion = libs.versions.composeRules.get()
        val rules =
            configurations.detachedConfiguration(
                dependencies.create("io.nlopez.compose.rules:detekt:$rulesVersion")
            )
        val resolved = rules.incoming.resolutionResult.rootComponent
        doLast {
            val wanted =
                resolved
                    .get()
                    .dependencies
                    .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                    .flatMap { it.selected.dependencies }
                    .map { it.requested }
                    .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentSelector>()
                    .firstOrNull {
                        it.group == "dev.detekt" && it.module in setOf("detekt-core", "detekt-api")
                    }
                    ?.version
                    ?: error("Compose rules $rulesVersion no longer declare a detekt dependency")
            check(wanted == detektVersion) {
                "Compose rules $rulesVersion are built against detekt $wanted, but the catalog " +
                    "has detekt $detektVersion: pick the composeRules release built for it."
            }
        }
    }

tasks.check { dependsOn(checkComposeRulesDetekt) }
