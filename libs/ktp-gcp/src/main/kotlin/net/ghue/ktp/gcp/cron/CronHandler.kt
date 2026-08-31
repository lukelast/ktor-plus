package net.ghue.ktp.gcp.cron

interface CronHandler {
    /** Runs the hourly job; [utcHour] is the current UTC hour of day, 0-23. */
    suspend fun hourly(utcHour: Int): CronResult
}

data class CronResult(
    /** True when work remains; the endpoint responds non-2xx so Cloud Scheduler retries early. */
    val runAgain: Boolean = false
)
