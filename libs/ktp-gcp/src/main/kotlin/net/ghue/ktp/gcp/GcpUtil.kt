package net.ghue.ktp.gcp

import com.google.cloud.ServiceOptions

/** Utilities for working with Google Cloud Platform. */
object GcpUtil {
    /**
     * Get the current GCP project ID from application default credentials. Returns null if not
     * running in GCP or credentials are not configured.
     */
    fun getProjectId(): String? =
        try {
            ServiceOptions.getDefaultProjectId()
        } catch (e: Exception) {
            null
        }

    /**
     * Check if the application is running on Google Cloud Run. Uses the K_SERVICE environment
     * variable which is set by Cloud Run.
     */
    fun isCloudRun(): Boolean = System.getenv("K_SERVICE") != null

    /**
     * Get the Cloud Run service name if running on Cloud Run. Returns null if not running on Cloud
     * Run.
     */
    fun getServiceName(): String? = System.getenv("K_SERVICE")

    /**
     * Get the Cloud Run revision if running on Cloud Run. Returns null if not running on Cloud Run.
     */
    fun getRevision(): String? = System.getenv("K_REVISION")

    /**
     * Get the Cloud Run configuration name if running on Cloud Run. Returns null if not running on
     * Cloud Run.
     */
    fun getConfiguration(): String? = System.getenv("K_CONFIGURATION")

    /**
     * Check if the application is running on any GCP platform. Checks for GCP-specific environment
     * variables.
     */
    fun isGoogleCloud(): Boolean =
        System.getenv("GOOGLE_CLOUD_PROJECT") != null ||
            System.getenv("GCLOUD_PROJECT") != null ||
            System.getenv("GCP_PROJECT") != null ||
            isCloudRun()

    /**
     * Get the GCP region from environment variables if available. Common in Cloud Run and other GCP
     * services. Returns null if not running on GCP or region is not set.
     */
    fun getRegion(): String? = System.getenv("GOOGLE_CLOUD_REGION") ?: System.getenv("GCP_REGION")

    /**
     * Get the GCP project ID from environment variables. Checks multiple common environment
     * variable names. Returns null if not found.
     */
    fun getProjectIdFromEnv(): String? =
        System.getenv("GOOGLE_CLOUD_PROJECT")
            ?: System.getenv("GCLOUD_PROJECT")
            ?: System.getenv("GCP_PROJECT")
}
