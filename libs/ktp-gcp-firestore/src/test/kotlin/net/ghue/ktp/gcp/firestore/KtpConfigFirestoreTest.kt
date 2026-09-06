package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.Firestore as GoogleCloudFirestore
import com.google.cloud.firestore.FirestoreOptions
import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class KtpConfigFirestoreTest :
    StringSpec({
        "google config extracts the firestore database id" {
            testConfig().google shouldBe Google(Google.Firestore(dbId = "test-db"))
        }

        "firestoreModule creates the configured Firestore service" {
            mockkStatic(FirestoreOptions::class)
            try {
                val builder = mockk<FirestoreOptions.Builder>()
                val options = mockk<FirestoreOptions>()
                val firestore = mockk<GoogleCloudFirestore>()
                every { FirestoreOptions.newBuilder() } returns builder
                every { builder.setDatabaseId("test-db") } returns builder
                every { builder.build() } returns options
                every { options.service } returns firestore
                justRun { firestore.close() }

                val config = testConfig()
                val koinApp = koinApplication {
                    modules(module { single { config } }, firestoreModule())
                }

                try {
                    koinApp.koin.get<GoogleCloudFirestore>() shouldBeSameInstanceAs firestore
                    verify(exactly = 1) { builder.setDatabaseId("test-db") }
                } finally {
                    koinApp.close()
                }
                verify(exactly = 1) { firestore.close() }
            } finally {
                unmockkStatic(FirestoreOptions::class)
            }
        }

        "createClient fails when the SDK does not create a service" {
            mockkStatic(FirestoreOptions::class)
            try {
                val builder = mockk<FirestoreOptions.Builder>()
                val options = mockk<FirestoreOptions>()
                every { FirestoreOptions.newBuilder() } returns builder
                every { builder.setDatabaseId("test-db") } returns builder
                every { builder.build() } returns options
                every { options.service } returns null

                val error =
                    shouldThrow<IllegalStateException> {
                        Google.Firestore("test-db").createClient()
                    }

                error.message shouldContain "error creating firestore"
            } finally {
                unmockkStatic(FirestoreOptions::class)
            }
        }
    })

private fun testConfig(): KtpConfig =
    KtpConfig(
        ConfigFactory.parseMap(
            mapOf(
                "app.name" to "test",
                "app.nameShort" to "test",
                "app.secret" to "secret",
                "app.version" to "1",
                "app.hostname" to "localhost",
                "app.server.port" to 0,
                "app.server.host" to "localhost",
                "google.firestore.dbId" to "test-db",
            )
        ),
        Env.TEST_UNIT,
    )
