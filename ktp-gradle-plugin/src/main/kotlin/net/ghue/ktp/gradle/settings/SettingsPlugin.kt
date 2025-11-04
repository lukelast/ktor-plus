package net.ghue.ktp.gradle.settings

import net.ghue.ktp.gradle.common.Version
import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class SettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        println("Starting KTP Settings Plugin")

        settings.dependencyResolutionManagement {
            versionCatalogs {
                create("libs") {
                    library("koin.bom", "io.insert-koin", "koin-bom").version(Version.KOIN)

                    val ktpVersion = version("ktp", KtpVersion.VERSION)
                    KtpVersion.libs.forEach {
                        library(it.replace("-", "."), "com.github.lukelast.ktor-plus", it).versionRef(ktpVersion)
                    }
                }
            }
        }

        // Auto apply to all projects?
        // settings.gradle.allprojects { pluginManager.apply(ProjectPlugin::class.java) }
    }
}
