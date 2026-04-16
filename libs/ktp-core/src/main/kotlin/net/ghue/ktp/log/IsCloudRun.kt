package net.ghue.ktp.log

import ch.qos.logback.core.boolex.PropertyConditionBase

class IsCloudRun : PropertyConditionBase() {
    init {
        installSlf4jBridge()
    }

    override fun evaluate(): Boolean {
        val isCloudRun =
            System.getenv("K_SERVICE") != null || System.getProperty("K_SERVICE") != null
        if (isCloudRun) {
            log {}.debug { "Determined to be running in Google Cloud Run" }
        }
        return isCloudRun
    }
}
