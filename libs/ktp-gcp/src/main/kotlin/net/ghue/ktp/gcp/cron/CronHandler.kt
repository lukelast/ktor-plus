package net.ghue.ktp.gcp.cron

interface CronHandler {
    suspend fun hourly(hour: Int): Result
}

data class Result(val runAgain: Boolean = false)
