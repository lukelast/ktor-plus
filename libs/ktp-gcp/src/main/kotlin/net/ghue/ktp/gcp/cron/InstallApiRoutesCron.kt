package net.ghue.ktp.gcp.cron

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.ZoneOffset
import net.ghue.ktp.log.log
import org.koin.ktor.ext.getKoin

/**
 * Mounts `GET|POST /api/cron/hourly` for Cloud Scheduler. Implement [CronHandler] and bind it in
 * Koin (`@Single`), or the first hit fails with an error naming the missing binding.
 */
fun Route.installApiRoutesCron() {
    val handler: CronHandler by lazy {
        application.getKoin().getOrNull<CronHandler>()
            ?: error("You must implement ${CronHandler::class.simpleName}")
    }
    suspend fun RoutingContext.handle() {
        log {}.info { "Doing hourly cron job" }
        val utcHour = Instant.now().atOffset(ZoneOffset.UTC).hour
        val result = handler.hourly(utcHour)
        if (result.runAgain) {
            // Any non-2xx makes Cloud Scheduler retry; 429 is the closest semantic fit.
            call.respond(HttpStatusCode.TooManyRequests)
        } else {
            call.respond(HttpStatusCode.OK)
        }
    }
    authenticateGcpCron {
        resource<Api.Cron.Hourly> {
            get { handle() }
            post { handle() }
        }
    }
}

@Resource("/api")
class Api {
    @Resource("/cron")
    class Cron(val parent: Api = Api()) {
        @Resource("hourly") class Hourly(val parent: Cron = Cron())
    }
}
