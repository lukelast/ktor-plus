package net.ghue.ktp.ktor.start

import net.ghue.ktp.config.KtpConfig
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class KtpStartTest {

    @Test
    fun `update should create new KtpAppBuilder with modifications applied`() {
        val originalBuilder = ktpAppCreate {
            addModule(module { })
            createConfigManager = { KtpConfig.createManagerForTest() }
        }
        
        val updatedBuilder = originalBuilder.update {
            addModule(module { })
            init { _ -> }
        }
        
        val originalApp = originalBuilder()
        val updatedApp = updatedBuilder()
        
        assertEquals(1, originalApp.modules.size)
        assertEquals(0, originalApp.appInits.size)
        assertEquals(2, updatedApp.modules.size)
        assertEquals(1, updatedApp.appInits.size)
        
        assertNotSame(originalBuilder, updatedBuilder)
    }

    @Test
    fun `update should preserve existing configuration`() {
        var customConfigCalled = false
        val customConfigManager = { 
            customConfigCalled = true
            KtpConfig.createManagerForTest()
        }
        
        val originalBuilder = ktpAppCreate {
            createConfigManager = customConfigManager
            addModule(module { })
        }
        
        val updatedBuilder = originalBuilder.update {
            addModule(module { })
        }
        
        val updatedApp = updatedBuilder()
        updatedApp.createConfigManager()
        
        assert(customConfigCalled) { "Custom config manager should be preserved" }
        assertEquals(2, updatedApp.modules.size)
    }

    @Test
    fun `update should allow chaining multiple updates`() {
        val originalBuilder = ktpAppCreate {
            createConfigManager = { KtpConfig.createManagerForTest() }
        }
        
        val firstUpdate = originalBuilder.update {
            addModule(module { })
        }
        
        val secondUpdate = firstUpdate.update {
            addModule(module { })
            init { _ -> }
        }
        
        val finalApp = secondUpdate()
        
        assertEquals(2, finalApp.modules.size)
        assertEquals(1, finalApp.appInits.size)
    }

    @Test
    fun `update should work with empty update block`() {
        val originalBuilder = ktpAppCreate {
            addModule(module { })
            createConfigManager = { KtpConfig.createManagerForTest() }
        }
        
        val originalApp = originalBuilder()
        val originalModulesCount = originalApp.modules.size
        
        val updatedBuilder = originalBuilder.update { }
        
        val updatedApp = updatedBuilder()
        
        assertEquals(originalModulesCount, updatedApp.modules.size)
        assertNotSame(originalBuilder, updatedBuilder)
    }

    @Test
    fun `update should return a new KtpAppBuilder that preserves modifications`() {
        val originalBuilder = ktpAppCreate {
            createConfigManager = { KtpConfig.createManagerForTest() }
        }
        
        val updatedBuilder = originalBuilder.update {
            addModule(module { })
            init { _ -> }
        }
        
        val firstCall = updatedBuilder()
        val secondCall = updatedBuilder()
        
        assertEquals(firstCall.modules.size, secondCall.modules.size)
        assertEquals(firstCall.appInits.size, secondCall.appInits.size)
        assertEquals(1, firstCall.modules.size)
        assertEquals(1, firstCall.appInits.size)
    }
}