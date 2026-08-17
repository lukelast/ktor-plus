package net.ghue.ktp.gradle.project

import io.ktor.plugin.*
import net.ghue.ktp.gradle.project.mods.*
import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** The JDK floor for apps; the virtual-thread-per-request model needs JEP 491 (24+). */
private const val MIN_JAVA_VERSION = 25

fun Project.applyKtor() {
    applyDetekt()
    applyKotlin()

    // Toolchain specs cannot express "25 or newer", so when the JVM running the build is newer
    // than the floor, the spec is raised to match it and that JVM is used directly. Release
    // semantics keep the API surface and bytecode at the floor either way, so a newer local
    // JDK cannot produce code that CI or the Docker image build (both on the floor) would
    // reject. Libraries instead target 21 so published jars stay broadly consumable.
    val toolchainVersion = maxOf(MIN_JAVA_VERSION, JavaVersion.current().majorVersion.toInt())
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(toolchainVersion)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(MIN_JAVA_VERSION.toString()))
            freeCompilerArgs.add("-Xjdk-release=$MIN_JAVA_VERSION")
        }
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(MIN_JAVA_VERSION) }

    // https://github.com/ktorio/ktor-build-plugins/blob/a9d5b3c836e65cb11f41fdbc0431b2a692d6ccf3/plugin/build.gradle.kts#L52
    pluginManager.apply(KtorGradlePlugin::class.java)

    tasks.withType<JavaExec>().configureEach {
        // Avoid native access warnings from Jansi loading terminal support on JDK 24+.
        jvmArgumentProviders.add(NativeAccessArgumentProvider(javaLauncher))
        // Avoid JDK 23+ warnings for terminally deprecated sun.misc.Unsafe memory access.
        jvmArgumentProviders.add(SunMiscUnsafeMemoryAccessArgumentProvider(javaLauncher))
    }

    applyKtfmt()
    applyKoinCompilerPlugin()
    configureShadow()
    installKotest()
    registerVerifyTask()

    // Every ktor-plus app uses the runtime library and the test harness. Always the external
    // coordinates: the ktor-plus repo itself rewires these to its local subprojects with a
    // dependency substitution in its root build script.
    dependencies {
        add("implementation", "${KtpVersion.GROUP}:ktp-ktor:${KtpVersion.VERSION}")
        add("testImplementation", "${KtpVersion.GROUP}:ktp-test:${KtpVersion.VERSION}")
    }
}
