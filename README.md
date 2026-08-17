# KTOR Plus (ktp)

An opinionated microservice framework built on ktor.
The objective of KTP is to turn Ktor into a batteries-included framework for building microservices that includes dependency injection,
configuration management, logging, metrics, health checks, debug tools, and more.

## Using KTP via JitPack

KTP libraries and Gradle plugins are available via JitPack:

[![](https://jitpack.io/v/lukelast/ktor-plus.svg)](https://jitpack.io/#lukelast/ktor-plus)

https://jitpack.io/#lukelast/ktor-plus

### Add Jitpack to settings.gradle.kts

```kotlin
// In your settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Update build.gradle.kts

```kotlin
// If using libs.versions.toml
plugins { alias(libs.plugins.ktp) }

// Or directly
plugins { id("com.github.lukelast.ktor-plus.project") version "VERSION" }

// The plugin adds the ktp-ktor and ktp-test dependencies automatically.
// Only the optional KTP libraries need declaring:
dependencies {
    implementation("com.github.lukelast.ktor-plus:ktp-stripe:VERSION")
}
```

### Update libs.versions.toml if you use it
```toml
[versions]
ktp-version = "{{LATEST_VERSION}}"
[libraries]
# Only optional KTP libraries need entries; ktp-ktor and ktp-test come from the plugin.
ktp-stripe = { module = "com.github.lukelast.ktor-plus:ktp-stripe", version.ref = "ktp-version" }
[plugins]
ktp = { id = "com.github.lukelast.ktor-plus", version.ref = "ktp-version" }
```

## KTP Libraries

### ktp-core

Core utilities with minimal dependencies, providing essential building blocks for KTP applications:

- **Logging**: Structured logging with `KtpLog` and Google Cloud Run detection
- **Path Utilities**: Path manipulation and file operations
- **Hash Functions**: Hashing utilities for strings and data
- **Resource Loading**: Loading resources from classpath
- **String Extensions**: Common string manipulation helpers
- **Lazy Properties**: Utilities for lazy initialization
- **Enum Utilities**: Helper functions for working with enums

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-core:VERSION")`

### [ktp-config](libs%2Fktp-config%2Freadme.md)

Configuration management built on Typesafe Config with layered, environment-specific overrides:

- **HOCON Format**: Human-optimized configuration using HOCON syntax
- **Priority-Based Layering**: Files named `<priority>.<configName>.<environment>.conf` with configurable precedence
- **Environment Detection**: Automatic detection via `KTP_ENV`, `ENV`, or Kubernetes namespace
- **Secret Masking**: Automatic sanitization of sensitive values in logs
- **Environment Variables**: Override any config value with `CONFIG_FORCE_` prefix
- **HOCON Injection**: Inject configuration via `KTP_CONFIG` environment variable
- **Testing Support**: Built-in helpers for unit and integration testing

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-config:VERSION")`

### ktp-ktor

Ktor-specific extensions and utilities for building production-ready microservices:

- **Debug Endpoints**: HTML index page, configuration viewer, GC logs, thread dumps, version info
- **DebugEndpointsPlugin**: Modern Ktor plugin with access control and configurable endpoints
- **Health Checks**: Built-in health endpoint for liveness/readiness probes
- **Vite Frontend**: Integration for serving Vite-built frontend applications
- **KtpStart**: Application startup utilities and configuration
- **Default Plugins**: Pre-configured Ktor plugins with sensible defaults
- **MDC Clearing**: Plugin for managing logging MDC context

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-ktor:VERSION")`

### ktp-gcp

Google Cloud Platform BOM and utilities for GCP integration:

- **GCP BOM**: Provides the Google Cloud Platform Bill of Materials for consistent dependency version management
- **Project ID Detection**: Get GCP project ID from application default credentials
- **Cloud Run Detection**: Detect if running on Google Cloud Run and access Cloud Run metadata
- **Environment Detection**: Check if running on any GCP platform
- **Region Detection**: Get GCP region from environment variables
- **Metadata Utilities**: Access Cloud Run service name, revision, and configuration

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-gcp:VERSION")`

### ktp-gcp-auth

Firebase and Google Cloud Platform authentication for Ktor applications:

- **Firebase Authentication**: Verify Firebase ID tokens
- **Role-Based Access Control**: Protect routes with role requirements
- **Debug Routes**: Pre-configured authenticated debug endpoints
- **GCP Integration**: Seamless integration with Google Cloud services

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-gcp-auth:VERSION")`

### ktp-gcp-firestore

Google Cloud Firestore integration utilities:

- **Google Cloud Firestore**: Native Google Cloud Firestore client.
- **Firebase Admin SDK**: Support for Firebase Admin SDK.

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-gcp-firestore:VERSION")`

### ktp-stripe

Stripe payment integration utilities for Ktor applications:

- Stripe API integration helpers
- Payment processing utilities
- Webhook handling

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-stripe:VERSION")`

### ktp-test

Testing utilities and helpers for KTP applications:

- Test configuration helpers
- Common test fixtures
- Testing utilities for Ktor applications
- Kotest integration

**Dependency**: `testImplementation("com.github.lukelast.ktor-plus:ktp-test:VERSION")`


## Developing KTP

* Test everything
    * `./gradlew ktfmtFormat check`
* Publish to your local maven repository for testing locally in another project.
    * `./gradlew clean publishToMavenLocal`
* Format code
    * `./gradlew ktfmtFormat`

## Gradle Plugins

The `ktp-gradle-plugin` composite child project builds a Gradle plugin Jar with three plugins.

### KTP Gradle Project Plugin

Plugin ID: `com.github.lukelast.ktor-plus.project`

Configures a project to follow the KTP Framework conventions. The project mode is auto-detected,
with the `ktp.mode` Gradle property as the explicit override:

- `ktor` (default): a Ktor application. Adds the `ktp-ktor`/`ktp-test` dependencies and
  configures formatting, detekt, testing, and the fat jar. The build script sets
  `application { mainClass.set(...) }`.
- `library`: a published JVM library targeting Java 21.
- `frontend`: auto-detected by a `package.json` in the project directory. Lifecycle tasks only;
  toolchain-specific tasks come from a stack plugin layered on top.
- `root`: auto-detected as the root of a multi-project build.

All modes get `check` (strict verification, what CI runs — fails on unformatted code) and
`verify` (format the code, then run the full `check`; the local dev loop).

### KTP Settings Plugin

Plugin ID: `com.github.lukelast.ktor-plus` — the settings plugin owns the bare repo-group id
because it is the entry point consumers resolve by plugin marker, and JitPack can only serve
markers whose group equals the repo group.

Applied in `settings.gradle.kts`, which then needs nothing else:

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

Features:

- **Root project name** comes from a `rootProject.name` entry in `gradle.properties`, so it
  survives checkouts into differently named directories (Docker build stages).
- **Project auto-include**: every direct subdirectory containing a `build.gradle.kts` or a
  `package.json` becomes a project. A conventional frontend needs no Gradle file at all.
- **Version catalog**: a `ktp` catalog with every KTP library (`ktp.ktor`, `ktp.stripe`, ...)
  and plugin (`ktp.plugins.lukestack`, ...), pinned to the settings plugin's own version — one
  version declaration rules everything KTP, and library versions can never drift from the
  plugin. The catalog is materialized as `gradle/ktp.versions.toml` (commit it) so the IDE can
  resolve the accessors; the consumer's own `libs.versions.toml` stays untouched.
- **Toolchain resolver**: the Foojay resolver is applied, so a missing JDK downloads on demand.
- **Project plugin auto-apply**: `ktp.plugin=ktp|lukestack` in `gradle.properties` applies that
  plugin to every project — no per-project `plugins {}` blocks needed anywhere.

### Lukestack Plugin

Plugin ID: `com.github.lukelast.ktor-plus.lukestack`

An opinionated personal stack layered on the base plugin: GCP deployment (Cloud Run +
Infrastructure Manager), Docker image tasks, and a bun/Vite frontend whose dev server starts
alongside `run`. Lukestack repos apply this ID instead of the base one in every project; other
stacks should apply the base plugin and build their own layer.

## Releases

Releases and versioning happen automatically for each commit to the main branch.
