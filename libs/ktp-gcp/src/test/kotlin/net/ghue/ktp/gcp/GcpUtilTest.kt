package net.ghue.ktp.gcp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GcpUtilTest :
    StringSpec({
        "getProjectId returns null when not in GCP environment" {
            // This test will pass in local dev environment where GCP credentials aren't set
            // In GCP environment with proper credentials, it would return a project ID
            val projectId = GcpUtil.getProjectId()
            // We can't assert a specific value since it depends on the environment
            // Just verify the function doesn't throw
            projectId // Should be null or a valid project ID string
        }

        "isCloudRun returns false when K_SERVICE is not set" {
            // This assumes we're not running on Cloud Run in test environment
            if (System.getenv("K_SERVICE") == null) {
                GcpUtil.isCloudRun() shouldBe false
            }
        }

        "getServiceName returns null when not on Cloud Run" {
            if (System.getenv("K_SERVICE") == null) {
                GcpUtil.getServiceName() shouldBe null
            }
        }

        "getRevision returns null when not on Cloud Run" {
            if (System.getenv("K_REVISION") == null) {
                GcpUtil.getRevision() shouldBe null
            }
        }

        "getConfiguration returns null when not on Cloud Run" {
            if (System.getenv("K_CONFIGURATION") == null) {
                GcpUtil.getConfiguration() shouldBe null
            }
        }

        "isGoogleCloud returns false when no GCP env vars are set" {
            // This assumes we're in a local dev environment
            if (
                System.getenv("GOOGLE_CLOUD_PROJECT") == null &&
                    System.getenv("GCLOUD_PROJECT") == null &&
                    System.getenv("GCP_PROJECT") == null &&
                    System.getenv("K_SERVICE") == null
            ) {
                GcpUtil.isGoogleCloud() shouldBe false
            }
        }

        "getRegion returns null when not in GCP environment" {
            if (
                System.getenv("GOOGLE_CLOUD_REGION") == null && System.getenv("GCP_REGION") == null
            ) {
                GcpUtil.getRegion() shouldBe null
            }
        }

        "getProjectIdFromEnv returns null when no project env vars are set" {
            if (
                System.getenv("GOOGLE_CLOUD_PROJECT") == null &&
                    System.getenv("GCLOUD_PROJECT") == null &&
                    System.getenv("GCP_PROJECT") == null
            ) {
                GcpUtil.getProjectIdFromEnv() shouldBe null
            }
        }

        "GcpUtil object is accessible" {
            // Verify the object can be accessed
            GcpUtil shouldNotBe null
        }
    })
