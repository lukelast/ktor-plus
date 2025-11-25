package net.ghue.ktp.ktor.plugin

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cachingheaders.*
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.core.removePrefix
import net.ghue.ktp.log.log
import org.koin.ktor.ext.inject

class ViteFrontendConfig {
    var vitePort: Int = 5173
    var indexFile: Path = Path("src", "index.html")
    /**
     * The URI path under which all static files are served. Should match the `base` setting in
     * vite.config.ts. No leading or trailing slashes.
     */
    var staticUri: String = "static"
    /** The directory on the production backend where static files are stored. */
    var staticDir: Path = Path(staticUri)
    var frontEndDist: Path = Path("frontend", "dist")
    var browserUriPathPrefix: String = "p"

    val indexFilePath: Path
        get() = staticDir.resolve(indexFile)

    /** The Ktor route that serves the frontend React pages. */
    val frontendRoute: String
        get() = "/${browserUriPathPrefix}/{...}"
}

val ViteFrontendPlugin =
    createApplicationPlugin(
        name = "ViteFrontendPlugin",
        createConfiguration = ::ViteFrontendConfig,
    ) {
        val config = pluginConfig
        val ktpConfig: KtpConfig by application.inject()

        if (ktpConfig.env.isLocalDev) {
            val viteDev = ServeViteDev(config)
            application.monitor.subscribe(ApplicationStopPreparing) { viteDev.close() }
            viteDev.init(application)
        } else {
            application.routing {
                staticResources("/${config.staticUri}", config.staticDir.toString()) {
                    cacheControl { listOf(cacheControlMaxAge(7.days)) }
                }
                get("/") { call.serveIndexHtml(config.indexFilePath) }
                get(config.frontendRoute) { call.serveIndexHtml(config.indexFilePath) }
            }
        }
    }

private suspend fun ApplicationCall.serveIndexHtml(path: Path) {
    try {
        caching = CachingOptions(cacheControl = cacheControlMaxAge(1.hours))
        resolveResource(path.toString())?.let { resource -> respond(resource) }
            ?: respond(HttpStatusCode.NotFound)
    } catch (ex: Exception) {
        log {}.warn(ex) { "Serving index html: $path" }
        respond(HttpStatusCode.NotFound)
    }
}

private class ServeViteDev(val config: ViteFrontendConfig) : Closeable {

    val frontEndFiles: Path =
        config.frontEndDist
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
            get("/") { call.serveDevRoute(config.indexFilePath) }
            get(config.frontendRoute) { call.serveDevRoute(config.indexFilePath) }
            get("/${config.staticUri}/{...}") { call.serveDevRoute(Path(call.request.path())) }
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
        val file = frontEndFiles.resolve(path.removePrefix(config.staticUri))
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
                        port = config.vitePort
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
