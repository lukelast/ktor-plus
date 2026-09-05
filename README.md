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

Pick the project plugin in `gradle.properties`:

```properties
rootProject.name=my-app
# Auto-applied to every project: 'ktp', or 'lukestack' for the full stack.
ktp.plugin=ktp
```

Optional libraries come from the generated `KtpLibs` object, pinned to the plugin's own version
(`ktp-ktor` and `ktp-test` are added automatically):

```kotlin
import net.ghue.ktp.lib.KtpLibs

dependencies { implementation(KtpLibs.stripe) }
```

Upgrading from an older release? Delete the committed `gradle/ktp.versions.toml`; `KtpLibs`
replaced it.

## Libraries

| Library                                     | `KtpLibs`          | What it gives you                                                                                                                                           |
|---------------------------------------------|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ktp-core                                    | `core`             | Structured logging, hashing, path/resource/string helpers, lazy properties                                                                                  |
| [ktp-config](libs/ktp-config/readme.md)     | `config`           | HOCON config layered as `<priority>.<name>.<env>.conf`; env from `KTP_ENV`/`ENV`; `CONFIG_FORCE_*` and `KTP_CONFIG` overrides; secret masking; test helpers |
| ktp-ktor                                    | `ktor` (automatic) | App startup, default plugins, health endpoint, debug endpoints (config, GC log, threads, version), Vite frontend serving with a dev proxy                   |
| ktp-gcp                                     | `gcp`              | GCP BOM, project id and region detection, Cloud Run metadata                                                                                                |
| [ktp-gcp-auth](libs/ktp-gcp-auth/README.md) | `gcpAuth`          | Firebase login, encrypted cookies with periodic account/role checks, RBAC, local-dev login                                                                  |
| ktp-gcp-auth-firestore                      | `gcpAuthFirestore` | User records on Firestore for ktp-gcp-auth ([details](libs/ktp-gcp-auth/README.md#user-records-ktp-gcp-auth-firestore))                                    |
| ktp-gcp-firestore                           | `gcpFirestore`     | Firestore client plus typed collection, read/write, query, batch, and transaction helpers                                                                   |
| ktp-stripe                                  | `stripe`           | Stripe API and webhook helpers                                                                                                                              |
| ktp-test                                    | `test` (automatic) | Test app builder, config helpers, Kotest integration                                                                                                        |

## Gradle plugins

The `ktp-gradle-plugin` composite build ships three plugins in one jar:

- **Settings plugin** (`com.github.lukelast.ktor-plus`): the one versioned entry point (JitPack
  only serves markers whose group equals the repo group). Reads `rootProject.name` from
  `gradle.properties`, includes every subdirectory holding a `build.gradle.kts` or `package.json`,
  ships `KtpLibs`, applies the Foojay toolchain resolver, and auto-applies the project plugin named
  by `ktp.plugin`.
- **Project plugin** (`com.github.lukelast.ktor-plus.project`): KTP conventions per project. Mode
  is auto-detected or set with `ktp.mode`: `ktor` (default; adds ktp-ktor/ktp-test, formatting,
  detekt, tests, fat jar), `library` (published Java 21 library), `frontend` (a `package.json`;
  lifecycle tasks only), `root`. Every mode gets `check` (strict, what CI runs) and `verify`
  (format, then `check`).
- **Lukestack plugin** (`com.github.lukelast.ktor-plus.lukestack`): a personal stack on top: GCP
  deployment (Cloud Run + Infrastructure Manager), Docker tasks, and a bun/Vite frontend whose dev
  server starts with `run`. Other stacks should layer on the base plugin instead.

## Developing KTP

- `./gradlew ktfmtFormat check`: format and run every check.
- `./gradlew clean publishToMavenLocal`: install locally to test in another project.

Releases and versioning happen automatically for each commit to the main branch.
