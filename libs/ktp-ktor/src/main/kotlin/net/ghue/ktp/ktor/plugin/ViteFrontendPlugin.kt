package net.ghue.ktp.ktor.plugin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.io.File
import java.net.ConnectException
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.core.Resource
import net.ghue.ktp.core.removeFirstFolder
import net.ghue.ktp.core.sha256
import net.ghue.ktp.ktor.http.connectTimeout
import net.ghue.ktp.ktor.http.createKtorHttpClient
import net.ghue.ktp.ktor.http.requestTimeout
import net.ghue.ktp.ktor.http.useHttp1
import net.ghue.ktp.log.log
import org.koin.ktor.ext.inject

private const val DEFAULT_VITE_PORT = 5173

/**
 * In local dev every frontend request is proxied to the dev server on [vitePort], falling back to
 * [frontendDist] when it is down. Elsewhere the bundle is served from [staticDir] (a directory next
 * to the jar, else classpath resources) with the SPA fallback on `/` and [frontendRoute].
 */
class ViteFrontendConfig {
    var vitePort: Int = DEFAULT_VITE_PORT

    /**
     * The SPA entry, relative to [staticDir], as the bundler writes it. Vite keeps the source
     * layout so the default is `src/index.html`; a bundler that emits it at the root (webpack,
     * Compose for Web) sets `Path("index.html")`. Production also tries the root form when the
     * `src/` one is missing, but local dev proxies exactly this path, so set it to match the dev
     * server.
     */
    var indexFile: Path = Path("src", "index.html")
    /**
     * URI segment static files are served under; no slashes, must match `base` in vite.config.ts.
     */
    var staticPathSegment: String = "static"
    /** The directory on the production backend where static files are stored. */
    var staticDir: Path = Path(staticPathSegment)

    /**
     * Where the frontend's production bundle lands, for the local-dev fallback when the dev server
     * is down; resolved from the working directory and then from its parent, since Gradle runs the
     * backend in its own subproject directory.
     */
    var frontendDist: Path = Path("frontend", "dist")
    var frontendPathSegment: String = "p"

    /**
     * Files served at the site root as well as under [staticDir], because browsers and crawlers
     * request them by fixed name regardless of what index.html links: `/favicon.ico` for any tab
     * without an icon link (JSON responses, error pages) and `/robots.txt`. Each is looked up as
     * `staticDir/<name>`; a missing `favicon.ico` answers 204 rather than 404 so the noise stays
     * out of error counts, anything else missing is 404. Vite copies `frontend/public/` to the root
     * of the bundle, so that is where an app drops them.
     */
    var rootFiles: Set<String> = setOf("favicon.ico", "robots.txt")

    /**
     * Subdirectory of [staticDir] holding content-hashed files, which get a one-year `immutable`
     * cache. Everything else under [staticDir] keeps a plain URL and gets [staticMaxAge]. Matches
     * Vite's default `build.assetsDir`.
     */
    var hashedAssetsDir: String = "assets"

    /** Browser cache lifetime of un-hashed static files (favicon, manifest, robots). */
    var staticMaxAge: Duration = 1.hours

    /**
     * Browser cache lifetime of index.html. Bounds how long a fresh navigation after a deploy can
     * pick up an HTML page whose entry script has since been replaced; conditional requests then
     * revalidate against the ETag, so a 304 is the common case.
     */
    var indexMaxAge: Duration = 10.minutes

    val staticRootPath: String = "/${staticPathSegment}"

    val indexFilePath: Path
        get() = staticDir.resolve(indexFile)

    val indexFileText: String by lazy {
        val altPath = staticDir.resolve(indexFile.removeFirstFolder("src"))
        if (indexFilePath.isReadable()) {
            return@lazy indexFilePath.readText()
        } else if (altPath.isReadable()) {
            return@lazy altPath.readText()
        }
        Resource.readOrNull(indexFilePath.toString())
            ?: Resource.readOrNull(altPath.toString())
            ?: error("$indexFile not found")
    }

    /**
     * ETag value (unquoted) of [indexFileText]: a cached copy revalidates to a 304 until a deploy.
     */
    val indexFileETag: String by lazy {
        indexFileText.sha256().take(16).joinToString("") { "%02x".format(it) }
    }

    /** Catch-all under [frontendPathSegment] so client-side routes load on direct navigation. */
    val frontendRoute: String
        get() = "/${frontendPathSegment}/{...}"
}

val ViteFrontendPlugin =
    createApplicationPlugin(
        name = "ViteFrontendPlugin",
        createConfiguration = ::ViteFrontendConfig,
    ) {
        val config = pluginConfig
        val ktpConfig: KtpConfig by application.inject()

        if (ktpConfig.env.isLocalDev) {
            val viteDev = ViteDevProxy(config)
            application.monitor.subscribe(ApplicationStopPreparing) { viteDev.close() }
            viteDev.registerRoutes(application)
        } else {
            application.routing {
                fun StaticContentConfig<*>.configCache() {
                    cacheControl { resource ->
                        val path = resource.toString().replace(File.separatorChar, '/')
                        val hashed = path.contains("/${config.hashedAssetsDir}/")
                        listOf(
                            if (hashed) cacheControlImmutable(365.days)
                            else cacheControlMaxAge(config.staticMaxAge)
                        )
                    }
                }
                if (config.staticDir.isDirectory()) {
                    staticFiles(config.staticRootPath, config.staticDir.toFile()) { configCache() }
                } else {
                    staticResources(config.staticRootPath, config.staticDir.toString()) {
                        configCache()
                    }
                }
                get("/") { call.serveIndexHtml(config) }
                get(config.frontendRoute) { call.serveIndexHtml(config) }
                for (name in config.rootFiles) {
                    get("/$name") { call.serveRootFile(config, name) }
                }
            }
        }
    }

private suspend fun ApplicationCall.serveIndexHtml(config: ViteFrontendConfig) {
    val html =
        try {
            config.indexFileText
        } catch (ex: IllegalStateException) {
            log {}.warn(ex) { "Serving index html: ${config.indexFile}" }
            respond(HttpStatusCode.NotFound)
            return
        }
    response.cacheControl(cacheControlMaxAge(config.indexMaxAge))
    respond(
        TextContent(html, ContentType.Text.Html.withCharset(Charsets.UTF_8)).apply {
            versions += EntityTagVersion(config.indexFileETag)
        }
    )
}

/**
 * `/favicon.ico` and friends: the same file from [ViteFrontendConfig.staticDir], see `rootFiles`.
 */
private suspend fun ApplicationCall.serveRootFile(config: ViteFrontendConfig, name: String) {
    val contentType = ContentType.defaultForFilePath(name)
    val file = config.staticDir.resolve(name)
    if (file.isReadable()) {
        response.cacheControl(cacheControlMaxAge(config.staticMaxAge))
        respond(LocalPathContent(file, contentType))
        return
    }
    val resource = Resource.urlOrNull(config.staticDir.resolve(name).joinToString("/"))
    if (resource != null) {
        response.cacheControl(cacheControlMaxAge(config.staticMaxAge))
        respond(URIFileContent(resource, contentType))
        return
    }
    respond(if (name == "favicon.ico") HttpStatusCode.NoContent else HttpStatusCode.NotFound)
}

private class ViteDevProxy(val config: ViteFrontendConfig) : Closeable {
    val client = createKtorHttpClient {
        // Default HTTP_2 sends `Connection: Upgrade`; Vite routes that to its HMR WebSocket
        // handler, which never responds and hangs the request.
        useHttp1()
        // Short so requests fall back to built files quickly when Vite is down.
        connectTimeout(500.milliseconds)
        // Bounds the whole fetch including the body; Vite stalls requests while it pre-bundles
        // dependencies on a cold start, which can take well over ten seconds.
        requestTimeout(1.minutes)
    }

    override fun close() {
        client.close()
    }

    fun registerRoutes(app: Application) {
        app.routing {
            get("/") { call.serveDevRoute(config.indexFilePath) }
            get(config.frontendRoute) { call.serveDevRoute(config.indexFilePath) }
            get("${config.staticRootPath}/{...}") { call.serveDevRoute(Path(call.request.path())) }
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
            serveFromFrontendDist(path)
        }
    }

    private suspend fun ApplicationCall.serveFromFrontendDist(path: Path) {
        val frontendDistDir = frontendDistDirOrNull()
        if (frontendDistDir == null) {
            log {}.warn { "Frontend dist directory not found for fallback: ${config.frontendDist}" }
            respond(HttpStatusCode.NotFound)
            return
        }
        val file = frontendDistDir.resolve(path.removeFirstFolder(config.staticPathSegment))
        if (file.isReadable()) {
            log {}.info { "Serving: $file" }
            respondPath(file)
        } else {
            log {}.warn { "File not found: $file" }
            respond(HttpStatusCode.NotFound)
        }
    }

    // Under Gradle the working dir is the server subproject, so the frontend may be a sibling.
    private fun frontendDistDirOrNull(): Path? =
        listOf(config.frontendDist, Path("..").resolve(config.frontendDist))
            .map { it.normalize() }
            .firstOrNull { it.isDirectory() }

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
