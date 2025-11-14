package net.ghue.ktp.log

import ch.qos.logback.core.boolex.PropertyConditionBase

class IsGcloudRun : PropertyConditionBase() {
    override fun evaluate(): Boolean {
        val isGcloudRun =
            System.getenv("K_SERVICE") != null || System.getProperty("K_SERVICE") != null
        if (isGcloudRun) {
            log {}.debug { "Determined to be running in Google Cloud Run" }
        }
        return isGcloudRun
    }
}
