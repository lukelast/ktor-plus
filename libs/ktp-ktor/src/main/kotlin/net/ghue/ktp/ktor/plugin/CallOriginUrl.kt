package net.ghue.ktp.ktor.plugin

import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin

fun ApplicationCall.originUrl(path: List<String> = emptyList()): Url = buildUrl {
    protocol = URLProtocol.createOrDefault(request.origin.scheme)
    host = request.origin.serverHost
    port = request.origin.serverPort
    pathSegments = path
}
