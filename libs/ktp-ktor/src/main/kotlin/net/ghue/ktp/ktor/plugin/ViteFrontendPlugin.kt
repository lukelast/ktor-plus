package net.ghue.ktp.ktor.plugin

import io.ktor.client.*
import io.ktor.client.engine.java.*
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
import kotlin.io.path.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.core.Resource
import net.ghue.ktp.core.removePrefix
import net.ghue.ktp.log.log
import org.koin.ktor.ext.inject

private const val DEFAULT_VITE_PORT = 5173

class ViteFrontendConfig {
    var vitePort: Int = DEFAULT_VITE_PORT
    var indexFile: Path = Path("src", "index.html")
    /**
     * The URI path under which all static files are served. Should match the `base` setting in
     * vite.config.ts. No leading or trailing slashes.
     */
    var staticUri: String = "static"
    /** The directory on the production backend where static files are stored. */
    var staticDir: Path = Path(staticUri)

    /** Where the frontend files are built during development. */
    var frontEndDist: Path = Path("frontend", "dist")
    var browserUriPathPrefix: String = "p"

    val staticRootUri: String = "/${staticUri}"

    val indexFilePath: Path
        get() = staticDir.resolve(indexFile)

    val indexFileText: String by lazy {
        val altPath = staticDir.resolve(indexFile.removePrefix("src"))
        if (indexFilePath.isReadable()) {
            return@lazy indexFilePath.readText()
        } else if (altPath.isReadable()) {
            return@lazy altPath.readText()
        }
        Resource.readOrNull(indexFilePath.toString())
            ?: Resource.readOrNull(altPath.toString())
            ?: error("$indexFile not found")
    }

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
                fun StaticContentConfig<*>.configCache() {
                    cacheControl { listOf(cacheControlMaxAge(7.days)) }
                }
                if (config.staticDir.isDirectory()) {
                    staticFiles(config.staticRootUri, config.staticDir.toFile()) { configCache() }
                } else {
                    staticResources(config.staticRootUri, config.staticDir.toString()) {
                        configCache()
                    }
                }
                get("/") { call.serveIndexHtml(config) }
                get(config.frontendRoute) { call.serveIndexHtml(config) }
            }
        }
    }

private suspend fun ApplicationCall.serveIndexHtml(config: ViteFrontendConfig) {
    try {
        caching = CachingOptions(cacheControl = cacheControlMaxAge(1.hours))
        respondText(config.indexFileText, contentType = ContentType.Text.Html)
    } catch (ex: IllegalStateException) {
        log {}.warn(ex) { "Serving index html: ${config.indexFile}" }
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
        HttpClient(Java) { engine { config { connectTimeout(500.milliseconds.toJavaDuration()) } } }

    override fun close() {
        client.close()
    }

    fun init(app: Application) {
        app.routing {
            get("/") { call.serveDevRoute(config.indexFilePath) }
            get(config.frontendRoute) { call.serveDevRoute(config.indexFilePath) }
            get("${config.staticRootUri}/{...}") { call.serveDevRoute(Path(call.request.path())) }
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
