package net.ghue.ktp.gcp.cron

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.ZoneOffset
import net.ghue.ktp.ktor.openapi.excludeFromOpenApi
import net.ghue.ktp.log.log
import org.koin.ktor.ext.getKoin

/** Mounts the Cloud Scheduler routes; implement [CronHandler] and bind it in Koin (`@Single`). */
fun Route.installApiRoutesCron() {
    // Resolved at install so a missing or broken handler fails boot, not the first scheduled run.
    val handler: CronHandler =
        application.getKoin().getOrNull<CronHandler>()
            ?: error("No ${CronHandler::class.simpleName} is bound in Koin")
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
        .excludeFromOpenApi()
}

@Resource("/api")
class Api {
    @Resource("/cron")
    class Cron(val parent: Api = Api()) {
        @Resource("hourly") class Hourly(val parent: Cron = Cron())
    }
}
