package net.ghue.ktp.gradle.project

import io.ktor.plugin.*
import net.ghue.ktp.gradle.project.mods.*
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

fun Project.applyKtor() {
    applyKotlin()

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

    dependencies {
        //        add("implementation", "com.github.lukelast.ktor-plus:ktp-ktor:${KtpVersion.VERSION}")

//        add("testImplementation", project(":libs:ktp-test"))
    }
}
