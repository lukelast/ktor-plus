# KTOR Plus (ktp)

An opinionated microservice framework built on Ktor: dependency injection, configuration,
logging, auth, debug tools, and GCP deployment, batteries included.

## Using KTP via JitPack

[![](https://jitpack.io/v/lukelast/ktor-plus.svg)](https://jitpack.io/#lukelast/ktor-plus)

One version declaration rules everything KTP. Apply the settings plugin in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins { id("com.github.lukelast.ktor-plus") version "VERSION" }
```

Name the build in `gradle.properties`; the lukestack plugin is applied to every project unless
`ktp.plugin=ktp` asks for the generic project plugin alone:

```properties
rootProject.name=my-app
```

Optional libraries come from the generated `KtpLibs` object, pinned to the plugin's own version
(`ktp-ktor` and `ktp-test` are added automatically). Every Kotlin project also gets `ktp-bom`, so
Ktor, kotlinx, Koin, kotest and Google Cloud dependencies are written without a version:

```kotlin
import net.ghue.ktp.lib.KtpLibs

dependencies {
    implementation(KtpLibs.stripe)
    implementation("io.ktor:ktor-client-logging")
}
```

Upgrading from an older release? Delete the committed `gradle/ktp.versions.toml`; `KtpLibs`
replaced it.

## Wiring an app

```kotlin
fun main() = appFactory.start()

val appFactory = ktpAppCreate {
    addKoinConfig(koinConfiguration<MyApp>()) // the app's @Single/@Factory definitions
    addModule(firebaseAuthModule())           // library modules
    addAppInit { config ->
        installDefaultPlugins(config)
        install(FirebaseAuthPlugin)
        installApi()
        install(ViteFrontendPlugin)
    }
}
```

Koin definitions load in this order, later ones replacing earlier ones of the same type:
`addModule` modules, then `addKoinConfig` configs (so an app definition beats a library one), then
`addOverrideModule` modules. Tests build on the real app with `appFactory.update { ... }` and swap
a compiler-plugin `@Singleton` for a mock through `addOverrideModule`; an `addModule` definition
would lose to it. `ktpTestApp(appFactory) { client.get(...) }` from ktp-test runs the result in
Ktor's test host with the unit-test config env; for an app with Firebase auth,
`ktpAuthTestApp(appFactory.withGcpStubs())` from ktp-gcp-auth-firestore-test runs it signed in.

## Libraries

| Library                                     | `KtpLibs`          | What it gives you                                                                                                                                           |
|---------------------------------------------|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ktp-core                                    | `core`             | Structured logging, hashing, path/resource/string helpers, lazy properties                                                                                  |
| [ktp-config](libs/ktp-config/README.md)     | `config`           | HOCON config layered as `<priority>.<configName>.<env>.conf`; env from `KTP_ENV`/`ENV`; `CONFIG_FORCE_*` and `KTP_CONFIG` overrides; secret masking; test helpers |
| ktp-ktor                                    | `ktor` (automatic) | App startup, default plugins, health endpoint, debug endpoints (config, GC log, threads, version), Vite frontend serving (dev proxy, cache headers, root favicon.ico and robots.txt) |
| ktp-bom                                     | `bom` (automatic)  | The platform: Ktor, kotlinx.serialization, kotlinx.coroutines, Koin, kotest and Google Cloud BOMs, and every KTP library at this release                   |
| ktp-gcp                                     | `gcp`              | Project id and region detection, Cloud Run metadata                                                                                                         |
| [ktp-gcp-auth](libs/ktp-gcp-auth/README.md) | `gcpAuth`          | Firebase login, encrypted cookies with periodic account/role checks, RBAC, local-dev login                                                                  |
| ktp-gcp-auth-firestore                      | `gcpAuthFirestore` | Shared user records and typed tenant storage on Firestore ([details](libs/ktp-gcp-auth-firestore/README.md))                                         |
| ktp-gcp-firestore                           | `gcpFirestore`     | Firestore client plus typed collection, read/write, query, batch, and transaction helpers                                                                   |
| ktp-gcp-auth-firestore-test                 | `gcpAuthFirestoreTest` | Test harness for apps on ktp-gcp-auth-firestore: stubbed GCP clients, test sessions, a signed-in test app ([details](libs/ktp-gcp-auth-firestore-test/README.md)) |
| ktp-stripe                                  | `stripe`           | Stripe API and webhook helpers                                                                                                                              |
| ktp-test                                    | `test` (automatic) | Test app builder, config helpers, Kotest integration                                                                                                        |

## Gradle plugins

The `ktp-gradle-plugin` composite build ships three plugins in one jar:

- **Settings plugin** (`com.github.lukelast.ktor-plus`): the one versioned entry point (JitPack
  only serves markers whose group equals the repo group). Reads `rootProject.name` from
  `gradle.properties`, includes every subdirectory holding a `build.gradle.kts` or `package.json`,
  ships `KtpLibs`, applies the Foojay toolchain resolver, and auto-applies the lukestack plugin
  (`ktp.plugin=ktp` for the project plugin alone).
- **Project plugin** (`com.github.lukelast.ktor-plus.project`): KTP conventions per project. Mode
  is auto-detected or set with `ktp.mode`: `ktor` (default; adds ktp-ktor/ktp-test, formatting,
  detekt, tests, fat jar; compiles for and runs on JDK 25, which the Foojay resolver downloads
  when missing), `library` (published Java 21 library), `frontend` (a `package.json`; lifecycle
  tasks only), `kmp` (a `src/commonMain`: Kotlin Multiplatform library with serialization,
  formatting, detekt over `src/`, JVM targets at Java 21 bytecode, and `ktp-bom` on `commonMain`
  as `api`, so its consumers resolve the same versions; the build script declares the targets),
  `compose` (a `kmp` module with a `src/wasmJsMain/resources/index.html`: adds the
  Compose Multiplatform and Compose compiler plugins, pinned to the plugin's Kotlin, and the
  Compose detekt rules; `org.jetbrains.compose.*` dependencies written without a version get the
  plugin's Compose version), `root`. Every Kotlin project gets `ktp-bom`: on `implementation`
  (`api` for a published library), or on `commonMain` for multiplatform. A `detekt.yml` beside a project's build script is layered over
  KTP's rules. Every mode gets `check` (strict, what CI runs) and `verify` (format, then
  `check`); the root `verify` aggregates the subprojects that have one.
  `ktor` mode applies the Ktor Gradle plugin, so its extension is available as is: for
  example `ktor { openApi { enabled = true } }` turns on route metadata inference (Kotlin 2.4+),
  though KTP serves no spec endpoint yet.
- **Lukestack plugin** (`com.github.lukelast.ktor-plus.lukestack`): a personal stack on top: GCP
  deployment (Cloud Run + Infrastructure Manager), Docker tasks, and a frontend dev server that
  starts with `run` on a port of its own, passed to the backend as `KTP_VITE_PORT`, so several apps
  or worktrees run at once: bun/Vite for a `frontend/`, or a continuous child build serving a
  `compose` module's webpack dev server (a bare `run` then excludes that module's desktop `run`, as
  `-x` would). A backend port already in use fails local dev startup with the
  `0.local.localdev.conf` line that fixes it. Other stacks should layer on the project plugin
  instead. With
  `ktp.openapi=true` in `gradle.properties`, `:backend:openApiExport` runs the app's
  `OpenApiExportTest` to write the OpenAPI contract, and `:frontend:apiGenerate` turns it into
  `src/api/schema.d.ts`; `:frontend:apiCheck` (part of `check`) fails when that file is stale.

## Developing KTP

- `./gradlew ktfmtFormat check`: format and run every check.
- `./gradlew clean publishToMavenLocal`: install locally to test in another project.

Releases and versioning happen automatically for each commit to the main branch.
