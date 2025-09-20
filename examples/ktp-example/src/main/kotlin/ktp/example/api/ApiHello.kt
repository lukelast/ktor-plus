package ktp.example.api

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.installApiHello() {
    routing { get("/") { call.respondText("KTP is running!") } }
}
