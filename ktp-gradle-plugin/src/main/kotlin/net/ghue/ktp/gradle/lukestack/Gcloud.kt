package net.ghue.ktp.gradle.lukestack

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

private const val GCLOUD_GROUP = "gcloud"
private const val SERVICE_ACCOUNT_NAME = "infra-manager"

/** Match the deployed environment, where the GCP project id is ambient. */
internal fun Project.configureGcpEnvironment() {
    val gcpProjectId = findProperty("gcp.projectId")?.toString() ?: rootProject.name
    tasks.withType<JavaExec>().configureEach {
        environment("GOOGLE_CLOUD_PROJECT", gcpProjectId)
    }
}

/**
 * Registers Google Cloud Infrastructure Manager tasks, driven by `gradle.properties`:
 * - `gcp.projectId`: GCP project id. Default: the root project name.
 * - `gcp.appName`: app name for the Infra Manager deployment. Default: the root project name.
 * - `gcp.region`: deployment region. Default: `us-central1`.
 * - `gcp.github_owner`: passed to terraform when set, omitted otherwise.
 * - `gcp.github_repo`: passed to terraform. Default: the root project name.
 *
 * Terraform sources are expected at the `deploy/tf` convention location.
 */
internal fun Project.registerGcloudTasks() {
    val gcloudCommand =
        if (System.getProperty("os.name").startsWith("Windows")) "gcloud.cmd" else "gcloud"
    val gcpProjectId = findProperty("gcp.projectId")?.toString() ?: rootProject.name
    val appName = findProperty("gcp.appName")?.toString() ?: rootProject.name
    val serviceAccountEmail = "$SERVICE_ACCOUNT_NAME@$gcpProjectId.iam.gserviceaccount.com"

    fun Exec.runGcloud(vararg args: String) {
        commandLine(gcloudCommand, "--project=$gcpProjectId", *args)
        doFirst { logger.lifecycle("Executing command: " + commandLine.joinToString(" ")) }
    }

    tasks.register<Exec>("gcloudInfraIam") {
        group = GCLOUD_GROUP
        description = "Create and configure the GCP service account for infra manager to use."
        runGcloud("iam", "service-accounts", "create", SERVICE_ACCOUNT_NAME)
    }

    tasks.register<Exec>("gcloudInfraBind") {
        group = GCLOUD_GROUP
        description = "Grant the infra manager service account permission to manage the project."
        runGcloud(
            "projects",
            "add-iam-policy-binding",
            gcpProjectId,
            // No shell is involved, so the values must be bare: quote characters here would be
            // passed through to gcloud verbatim on Linux and rejected as a malformed member.
            "--member=serviceAccount:$serviceAccountEmail",
            "--role=roles/owner",
        )
    }

    tasks.register<Exec>("gcloudInfraEnable") {
        group = GCLOUD_GROUP
        description = "Enable the GCP services required by infrastructure manager."
        runGcloud(
            "services",
            "enable",
            "config.googleapis.com",
            "cloudresourcemanager.googleapis.com",
        )
    }

    tasks.register<Exec>("gcloudDeployInfra") {
        group = GCLOUD_GROUP
        description = "Deploys infrastructure using Google Cloud Infrastructure Manager."
        workingDir = projectDir
        val region = findProperty("gcp.region")?.toString() ?: "us-central1"
        val inputValues =
            listOfNotNull(
                    "project_id=$gcpProjectId",
                    "app_name=$appName",
                    "region=$region",
                    findProperty("gcp.github_owner")?.let { "github_owner=$it" },
                    "github_repo=${findProperty("gcp.github_repo") ?: rootProject.name}",
                )
                .joinToString(",")
        runGcloud(
            "infra-manager",
            "deployments",
            "apply",
            appName,
            "--location=$region",
            "--service-account=projects/$gcpProjectId/serviceAccounts/$serviceAccountEmail",
            "--local-source=deploy/tf/.",
            "--input-values=$inputValues",
        )
    }
}
