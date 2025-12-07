package net.ghue.ktp.gradle.project.mods

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import java.io.File
import org.gradle.api.Project

internal fun Project.applyDetekt() {
    plugins.apply(DetektPlugin::class.java)

    val configFile = layout.buildDirectory.file("detekt/detekt.yml")
    val generateConfig = tasks.register("generateDetektConfig") {
        outputs.file(configFile)
        doLast {
            val configResource = DetektPlugin::class.java.classLoader.getResourceAsStream("detekt.yml")
                ?: error("Could not find detekt.yml in resources")
            
            val file = configFile.get().asFile
            file.parentFile.mkdirs()
            file.writeBytes(configResource.readAllBytes())
        }
    }

    extensions.configure(DetektExtension::class.java) {
        buildUponDefaultConfig.set(true)
        config.setFrom(generateConfig)
    }
}
