package net.ghue.ktp.gradle.project.mods

import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers `verify`, the local dev loop: format the code, then run the full `check` (ktfmt
 * validation, detekt, tests). CI runs plain `check`, which never formats and fails on
 * unformatted code; `verify` is the local command that fixes instead of failing.
 */
fun Project.registerVerifyTask() {
    tasks.register("verify") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Formats the code, then runs all checks."
        dependsOn(tasks.withType<KtfmtFormatTask>())
        dependsOn(LifecycleBasePlugin.CHECK_TASK_NAME)
    }
}
