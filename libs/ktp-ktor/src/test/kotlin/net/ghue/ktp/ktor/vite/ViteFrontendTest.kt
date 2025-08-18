package net.ghue.ktp.ktor.vite

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Path
import kotlin.io.path.Path
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.plugin.ViteConfig
import net.ghue.ktp.ktor.plugin.installViteFrontend
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ViteFrontendTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `production mode serves index for missing specific resource`() = testApplication {
        val viteConfig = ViteConfig(indexFile = Path("nonexistent.html"))
        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "false"))

        application { installViteFrontend(config, viteConfig) }

        // Test root path returns 404 when index.html doesn't exist in resources
        with(client.get("/")) { assertEquals(HttpStatusCode.NotFound, status) }

        // Test frontend route returns 404 when index.html doesn't exist in resources
        with(client.get("/p/some/page")) { assertEquals(HttpStatusCode.NotFound, status) }
    }

    @Test
    fun `production mode serves non-existent static resources as 404`() = testApplication {
        val viteConfig = ViteConfig()
        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "false"))

        application { installViteFrontend(config, viteConfig) }

        // Test that non-existent static resources return 404
        with(client.get("/static/nonexistent.css")) {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `ViteConfig has correct default values`() {
        val config = ViteConfig()

        assertEquals(5173, config.vitePort)
        assertEquals(Path("src", "index.html"), config.indexFile)
        assertEquals("static", config.staticUri)
        assertEquals(Path("static"), config.staticDir)
        assertEquals(Path("frontend", "dist"), config.frontEndDist)
        assertEquals("p", config.browserUriPathPrefix)
        assertEquals("/p/{...}", config.frontendRoute)
        assertEquals(Path("static").resolve(Path("src", "index.html")), config.indexFilePath)
    }

    @Test
    fun `ViteConfig custom values work correctly`() {
        val config =
            ViteConfig(
                vitePort = 3000,
                indexFile = Path("custom.html"),
                staticUri = "assets",
                staticDir = Path("public"),
                frontEndDist = Path("build"),
                browserUriPathPrefix = "app",
            )

        assertEquals(3000, config.vitePort)
        assertEquals(Path("custom.html"), config.indexFile)
        assertEquals("assets", config.staticUri)
        assertEquals(Path("public"), config.staticDir)
        assertEquals(Path("build"), config.frontEndDist)
        assertEquals("app", config.browserUriPathPrefix)
        assertEquals("/app/{...}", config.frontendRoute)
        assertEquals(Path("public").resolve(Path("custom.html")), config.indexFilePath)
    }

    @Test
    fun `dev mode is enabled when localDev is true`() = testApplication {
        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "true"))
        val viteConfig = ViteConfig()

        application { installViteFrontend(config, viteConfig) }

        // In dev mode, should attempt to proxy to Vite dev server
        // Since no Vite server is running, this will fall back to serving files
        // Since we have an index.html resource, it should serve that
        with(client.get("/")) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Test Index Page"))
        }

        // Test that the frontend route also works in dev mode
        with(client.get("/p/some/page")) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Test Index Page"))
        }
    }

    @Test
    fun `production mode serves static resources correctly`() = testApplication {
        val viteConfig = ViteConfig()
        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "false"))

        application { installViteFrontend(config, viteConfig) }

        // Test that static CSS resource is served correctly
        with(client.get("/static/app.css")) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("color: blue"))
        }

        // Test that non-existent resources return 404
        with(client.get("/static/nonexistent.css")) {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `production mode serves index HTML correctly`() = testApplication {
        val viteConfig = ViteConfig()
        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "false"))

        application { installViteFrontend(config, viteConfig) }

        // Test root path serves index HTML from resources
        with(client.get("/")) {
            assertEquals(HttpStatusCode.OK, status)
            // Resource content type detection varies, but it should serve the content
            assertTrue(bodyAsText().contains("Test Index Page"))
        }

        // Test frontend route serves index HTML from resources
        with(client.get("/p/some/page")) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Test Index Page"))
        }
    }

    @Test
    fun `custom static URI routes are configured correctly`() = testApplication {
        val viteConfig = ViteConfig(staticUri = "assets")

        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "false"))

        application { installViteFrontend(config, viteConfig) }

        // Test that custom static URI route is configured
        with(client.get("/assets/nonexistent.js")) { assertEquals(HttpStatusCode.NotFound, status) }
    }

    @Test
    fun `custom browser URI path prefix routes are configured correctly`() = testApplication {
        val viteConfig = ViteConfig(browserUriPathPrefix = "app")

        val config = KtpConfig.createManagerForTest(mapOf("env.localDev" to "false"))

        application { installViteFrontend(config, viteConfig) }

        // Test that custom browser URI prefix routes serve the index file
        with(client.get("/app/dashboard")) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Test Index Page"))
        }

        with(client.get("/app/settings/profile")) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Test Index Page"))
        }
    }
}
