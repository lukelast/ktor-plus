package net.ghue.ktp.gcp.cron

interface CronHandler {
    /** Runs the hourly job; [hour] is the current UTC hour of day, 0-23. */
    suspend fun hourly(hour: Int): Result
}

data class Result(
    /** True when work remains; the endpoint responds non-2xx so Cloud Scheduler retries early. */
    val runAgain: Boolean = false
)
