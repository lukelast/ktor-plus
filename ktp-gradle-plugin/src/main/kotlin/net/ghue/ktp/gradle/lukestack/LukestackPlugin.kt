package net.ghue.ktp.gradle.lukestack

import net.ghue.ktp.gradle.project.ProjectMode
import net.ghue.ktp.gradle.project.ProjectPlugin
import net.ghue.ktp.gradle.project.findProjectMode
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The lukestack Gradle plugin: the GCP + Docker + bun/Vite stack layered on top of the generic
 * ktor-plus plugin. Lukestack repos apply this plugin id (instead of the base ktp one) to every
 * project; it applies the ktp plugin itself and then adds the stack pieces for the project's
 * mode. Other stacks built on ktor-plus apply the base plugin and never see any of this.
 */
class LukestackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(ProjectPlugin::class.java)
        when (project.findProjectMode()) {
            ProjectMode.ROOT -> {
                project.registerDockerTasks()
                project.registerGcloudTasks()
            }
            ProjectMode.KTOR -> {
                project.configureGcpEnvironment()
                project.configureTesting()
                project.applyViteDev()
            }
            ProjectMode.FRONTEND -> project.registerBunTasks()
            ProjectMode.LIBRARY -> {}
        }
    }
}
