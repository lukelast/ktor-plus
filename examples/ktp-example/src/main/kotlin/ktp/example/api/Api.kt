package ktp.example.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.ghue.ktp.ktor.error.KtpRspExNotFound
import net.ghue.ktp.ktor.error.ktpRspError

fun Application.installApi() {
    routing {
        get("/") { call.respondText("KTP is running!") }
        get("/error") {
            ktpRspError {
                internalMessage = "Secret error data"
                status = HttpStatusCode.UnprocessableEntity
                title = "bad thing happened"
                detail = "something bad happened when you called /error"
                extra("foo", "bar")
            }
        }
        get("/error2") { throw KtpRspExNotFound(name = "User", id = "user1234") }
    }
}
