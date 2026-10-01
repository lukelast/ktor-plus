package net.ghue.ktp.gradle.lukestack

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations

private const val GCLOUD_GROUP = "gcloud"
private const val SERVICE_ACCOUNT_NAME = "infra-manager"

/** Match the deployed environment, where the GCP project id is ambient. */
internal fun Project.configureGcpEnvironment() {
    val gcpProjectId = findProperty("gcp.projectId")?.toString() ?: rootProject.name
    tasks.withType<JavaExec>().configureEach { environment("GOOGLE_CLOUD_PROJECT", gcpProjectId) }
}

/**
 * Registers Google Cloud Infrastructure Manager tasks, driven by `gradle.properties`:
 * - `gcp.projectId`: GCP project id. Default: the root project name.
 * - `gcp.appName`: app name for the Infra Manager deployment. Default: the root project name.
 * - `gcp.region`: deployment region. Default: `us-central1`.
 * - `gcp.githubRepo`: passed to terraform. Default: the root project name.
 *
 * The GitHub owner is site identity, not build config: set `github_owner` in
 * `deploy/tf/site.auto.tfvars`. Terraform sources are expected at the `deploy/tf` convention
 * location.
 */
internal fun Project.registerGcloudTasks() {
    // Fail fast on dead keys: gcloud --input-values would silently outrank site.auto.tfvars.
    for (dead in listOf("gcp.github_owner", "gcp.githubOwner")) {
        check(findProperty(dead) == null) {
            "File 'gradle.properties', field '$dead', is no longer supported. " +
                "Set 'github_owner' in 'deploy/tf/site.auto.tfvars' instead."
        }
    }
    check(findProperty("gcp.github_repo") == null) {
        "File 'gradle.properties', field 'gcp.github_repo', is no longer supported. " +
            "Use 'gcp.githubRepo' instead."
    }
    val gcloudExecutable =
        if (System.getProperty("os.name").startsWith("Windows")) "gcloud.cmd" else "gcloud"
    val gcpProjectId = findProperty("gcp.projectId")?.toString() ?: rootProject.name
    val appName = findProperty("gcp.appName")?.toString() ?: rootProject.name
    val infraServiceAccount = "$SERVICE_ACCOUNT_NAME@$gcpProjectId.iam.gserviceaccount.com"

    tasks.register<GcloudInfraSetup>("gcloudInfraSetup") {
        group = GCLOUD_GROUP
        description =
            "Creates and authorizes the infra-manager service account and enables the " +
                "Infrastructure Manager APIs. Safe to run again."
        gcloudCommand.set(gcloudExecutable)
        projectId.set(gcpProjectId)
        serviceAccountEmail.set(infraServiceAccount)
    }

    tasks.register<Exec>("gcloudInfraDeploy") {
        group = GCLOUD_GROUP
        description = "Deploys infrastructure using Google Cloud Infrastructure Manager."
        workingDir = projectDir
        val region = findProperty("gcp.region")?.toString() ?: "us-central1"
        val inputValues =
            listOfNotNull(
                    "project_id=$gcpProjectId",
                    "app_name=$appName",
                    "region=$region",
                    "github_repo=${findProperty("gcp.githubRepo") ?: rootProject.name}",
                )
                .joinToString(",")
        commandLine(
            gcloudExecutable,
            "--project=$gcpProjectId",
            "infra-manager",
            "deployments",
            "apply",
            appName,
            "--location=$region",
            "--service-account=projects/$gcpProjectId/serviceAccounts/$infraServiceAccount",
            "--local-source=deploy/tf/.",
            "--input-values=$inputValues",
        )
        doFirst { logger.lifecycle("Executing command: " + commandLine.joinToString(" ")) }
    }
}

private const val BIND_ATTEMPTS = 6
private const val BIND_RETRY_MS = 5_000L

/** One task for the whole one-time setup; an `Exec` task can run only a single command. */
@UntrackedTask(because = "Its effects live in Google Cloud, so every run must talk to it.")
private abstract class GcloudInfraSetup : DefaultTask() {
    @get:Input abstract val gcloudCommand: Property<String>
    @get:Input abstract val projectId: Property<String>
    @get:Input abstract val serviceAccountEmail: Property<String>
    @get:Inject abstract val execOperations: ExecOperations

    @TaskAction
    fun run() {
        val account = serviceAccountEmail.get()
        runGcloudOrThrow(
            "services",
            "enable",
            "config.googleapis.com",
            "cloudresourcemanager.googleapis.com",
        )
        // `create` fails on an existing account, so probe first to make the task repeatable.
        if (gcloud(listOf("iam", "service-accounts", "describe", account), quiet = true) != 0) {
            runGcloudOrThrow("iam", "service-accounts", "create", SERVICE_ACCOUNT_NAME)
        }
        // A new account takes a few seconds to become bindable; IAM calls it missing until then.
        val bind =
            listOf(
                "projects",
                "add-iam-policy-binding",
                projectId.get(),
                "--member=serviceAccount:$account",
                "--role=roles/owner",
            )
        repeat(BIND_ATTEMPTS) { attempt ->
            if (gcloud(bind, quiet = false) == 0) return
            if (attempt < BIND_ATTEMPTS - 1) Thread.sleep(BIND_RETRY_MS)
        }
        throw GradleException("gcloud could not bind $account after $BIND_ATTEMPTS attempts.")
    }

    private fun runGcloudOrThrow(vararg args: String) {
        val exit = gcloud(args.toList(), quiet = false)
        if (exit != 0) {
            throw GradleException("gcloud ${args.joinToString(" ")} failed with exit code $exit.")
        }
    }

    /** One gcloud call under the task's project, returning its exit code; [quiet] hides a probe. */
    private fun gcloud(args: List<String>, quiet: Boolean): Int {
        val line = listOf(gcloudCommand.get(), "--project=${projectId.get()}") + args
        if (!quiet) logger.lifecycle("Executing command: " + line.joinToString(" "))
        val discard = ByteArrayOutputStream()
        return execOperations
            .exec {
                commandLine(line)
                isIgnoreExitValue = true
                if (quiet) {
                    standardOutput = discard
                    errorOutput = discard
                }
            }
            .exitValue
    }
}
