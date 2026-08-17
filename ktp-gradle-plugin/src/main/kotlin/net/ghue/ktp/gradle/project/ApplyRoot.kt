package net.ghue.ktp.gradle.project

import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Configures the root project of a multi-project build with the standard lifecycle tasks
 * (`clean`, `build`, `check`). Stack plugins layer deployment tasks on top of this.
 */
fun Project.applyRoot() {
    pluginManager.apply(LifecycleBasePlugin::class.java)
    // `gradlew verify` reaches subprojects by task-name matching, but the qualified `:verify`
    // form and IDE task invocations need real task-graph edges to the subprojects.
    tasks.register("verify") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs verify in every subproject."
        dependsOn(subprojects.map { "${it.path}:verify" })
    }
}
