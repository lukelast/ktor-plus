package net.ghue.ktp.gradle.project

import net.ghue.ktp.gradle.project.features.VERIFY_TASK
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Configures the root project of a multi-project build with the standard lifecycle tasks (`clean`,
 * `build`, `check`). Stack plugins layer deployment tasks on top of this.
 */
internal fun Project.applyRoot() {
    pluginManager.apply(LifecycleBasePlugin::class.java)
    // `gradlew verify` reaches subprojects by task-name matching, but the qualified `:verify`
    // form and IDE task invocations need real task-graph edges to the subprojects.
    tasks.register(VERIFY_TASK) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs verify in every subproject that has one."
        // Resolved when the task graph is built, after every subproject is configured. A module
        // KTP does not manage registers no verify task and must not break the aggregate; nor
        // must it be forced to define a stub.
        dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName(VERIFY_TASK) } })
    }
}
