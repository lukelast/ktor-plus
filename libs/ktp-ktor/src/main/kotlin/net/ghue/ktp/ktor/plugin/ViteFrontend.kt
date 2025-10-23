package net.ghue.ktp.ktor.plugin

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.net.ConnectException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isReadable
import kotlin.io.path.notExists
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.core.removePrefix
import net.ghue.ktp.log.log

data class ViteConfig(
    val vitePort: Int = 5173,
    val indexFile: Path = Path("src", "index.html"),
    /**
     * The URI path under which all static files are served. Should match the `base` setting in
     * vite.config.ts. No leading or trailing slashes.
     */
    val staticUri: String = "static",
    /** The directory on the production backend where static files are stored. */
    val staticDir: Path = Path(staticUri),
    val frontEndDist: Path = Path("frontend", "dist"),
    val browserUriPathPrefix: String = "p",
) {
    val indexFilePath: Path = staticDir.resolve(indexFile)

    /** The Ktor route that serves the frontend React pages. */
    val frontendRoute: String = "/${browserUriPathPrefix}/{...}"
}

fun Application.installViteFrontend(config: KtpConfig, viteConfig: ViteConfig = ViteConfig()) {
    if (config.env.isLocalDev) {
        val viteDev = ServeViteDev(viteConfig)
        monitor.subscribe(ApplicationStopPreparing) { viteDev.close() }
        viteDev.init(this)
    } else {
        routing {
            staticResources("/${viteConfig.staticUri}", viteConfig.staticDir.toString())
            get("/") { call.serveIndexHtml(viteConfig.indexFilePath) }
            get(viteConfig.frontendRoute) { call.serveIndexHtml(viteConfig.indexFilePath) }
        }
    }
}

private suspend fun ApplicationCall.serveIndexHtml(path: Path) {
    try {
        resolveResource(path.toString())?.let { resource -> respond(resource) }
            ?: respond(HttpStatusCode.NotFound)
    } catch (e: Exception) {
        respond(HttpStatusCode.NotFound)
    }
}

private class ServeViteDev(val viteConfig: ViteConfig) : Closeable {

    val frontEndFiles: Path =
        viteConfig.frontEndDist
            .let {
                if (it.notExists()) {
                    Path("..").resolve(it)
                } else {
                    it
                }
            }
            .toRealPath()

    // Create HTTP client with short timeout
    val client =
        HttpClient(Apache) {
            engine {
                connectTimeout = 500
                socketTimeout = 1000
            }
        }

    override fun close() {
        client.close()
    }

    fun init(app: Application) {
        app.routing {
            get("/") { call.serveDevRoute(viteConfig.indexFilePath) }
            get(viteConfig.frontendRoute) { call.serveDevRoute(viteConfig.indexFilePath) }
            get("/${viteConfig.staticUri}/{...}") { call.serveDevRoute(Path(call.request.path())) }
        }
    }

    private suspend fun ApplicationCall.serveDevRoute(path: Path) {
        val viteResponse = fetchFromViteDevServer(path)
        if (viteResponse != null) {
            respondBytes(
                status = viteResponse.status,
                contentType = viteResponse.contentType,
                bytes = viteResponse.body,
            )
        } else {
            serveFromFrontendFiles(path)
        }
    }

    private suspend fun ApplicationCall.serveFromFrontendFiles(path: Path) {
        val file = frontEndFiles.resolve(path.removePrefix(viteConfig.staticUri))
        if (file.isReadable()) {
            log {}.info { "Serving: $file" }
            respondPath(file)
        } else {
            log {}.warn { "File not found: $file" }
            respond(HttpStatusCode.NotFound)
        }
    }

    private suspend fun ApplicationCall.fetchFromViteDevServer(path: Path): ViteDevResponse? {
        try {
            val proxyUri =
                URLBuilder()
                    .apply {
                        protocol = URLProtocol.HTTP
                        host = "localhost"
                        port = viteConfig.vitePort
                        pathSegments = path.toList().map { it.toString() }
                        parameters.appendAll(request.queryParameters)
                    }
                    .buildString()
            val response = client.get(proxyUri)
            log {}.info { "Fetched from Vite: $proxyUri" }
            val contentType =
                response.headers["Content-Type"]?.let { ContentType.parse(it) }
                    ?: ContentType.Text.Plain
            val responseBytes = response.bodyAsChannel().toByteArray()
            return ViteDevResponse(
                status = response.status,
                contentType = contentType,
                body = responseBytes,
            )
        } catch (_: ConnectException) {
            log {}.info { "Vite dev server offline" }
            return null
        }
    }

    private class ViteDevResponse(
        val status: HttpStatusCode,
        val contentType: ContentType,
        val body: ByteArray,
    )
}
