package net.ghue.ktp.gcp.cron

interface CronHandler {
    suspend fun hourly(): Result
}

data class Result(val runAgain: Boolean = false)
