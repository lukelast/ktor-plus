package net.ghue.ktp.gradle.project

import io.ktor.plugin.*
import net.ghue.ktp.gradle.project.mods.*
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.applyKtor() {
    applyKotlin()

    // https://github.com/ktorio/ktor-build-plugins/blob/a9d5b3c836e65cb11f41fdbc0431b2a692d6ccf3/plugin/build.gradle.kts#L52
    pluginManager.apply(KtorGradlePlugin::class.java)

    applyKtfmt()
    applyKspGradlePlugin()
    applyKoinKspCompiler()
    installKotest()

    dependencies {
        //        add("implementation", "net.ghue.ktp.lib:ktp-ktor:${KtpVersion.VERSION}")

//        add("testImplementation", project(":libs:ktp-test"))
    }
}
