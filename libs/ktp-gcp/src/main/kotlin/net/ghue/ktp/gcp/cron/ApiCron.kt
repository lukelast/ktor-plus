package net.ghue.ktp.gcp.cron

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.resources.resource
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.log.log
import org.koin.ktor.ext.getKoin

@Resource("/api")
class Api {
    @Resource("/cron")
    class Cron(val parent: Api = Api()) {
        @Resource("hourly") class Hourly(val parent: Cron = Cron())
    }
}

/** Make sure you implement [CronHandler] and annotate it with [org.koin.core.annotation.Factory] */
fun Route.installApiCronRoutes() {
    val handler: CronHandler by lazy {
        application.getKoin().getOrNull<CronHandler>()
            ?: error("You must implement ${CronHandler::class.simpleName}")
    }
    suspend fun RoutingContext.handle() {
        log {}.info { "Doing hourly cron job" }
        val result = handler.hourly()
        if (result.runAgain) {
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
