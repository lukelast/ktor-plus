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
plugins { id("com.github.lukelast.ktor-plus") version "VERSION" }

dependencies {
    // If using libs.versions.toml
    implementation(libs.ktp.ktor)
    testImplementation(libs.ktp.test)
    
    // Or directly
    implementation("com.github.lukelast.ktor-plus:ktp-ktor:VERSION")
    testImplementation("com.github.lukelast.ktor-plus:ktp-test:VERSION")
}
```

### Update libs.versions.toml if you use it
```toml
[versions]
ktp-version = "{{LATEST_VERSION}}"
[libraries]
ktp-ktor = { module = "com.github.lukelast.ktor-plus:ktp-ktor", version.ref = "ktp-version" }
ktp-test = { module = "com.github.lukelast.ktor-plus:ktp-test", version.ref = "ktp-version" }
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
- **Priority-Based Layering**: Files named `<priority>.<name>.<environment>.conf` with configurable precedence
- **Environment Detection**: Automatic detection via `KTP_ENV`, `ENV`, or Kubernetes namespace
- **Secret Masking**: Automatic sanitization of sensitive values in logs
- **Environment Variables**: Override any config value with `CONFIG_FORCE_` prefix
- **HOCON Injection**: Inject configuration via `KTP_CONFIG` environment variable
- **Testing Support**: Built-in helpers for unit and integration testing

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-config:VERSION")`

### ktp-ktor

Ktor-specific extensions and utilities for building production-ready microservices:

- **Debug Endpoints**: HTML index page, configuration viewer, GC logs, thread dumps, version info
- **ConfigDebugInfoPlugin**: Modern Ktor plugin with access control and configurable endpoints
- **Health Checks**: Built-in health endpoint for liveness/readiness probes
- **Vite Frontend**: Integration for serving Vite-built frontend applications
- **KtpStart**: Application startup utilities and configuration
- **Default Plugins**: Pre-configured Ktor plugins with sensible defaults
- **MDC Clearing**: Plugin for managing logging MDC context

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-ktor:VERSION")`

### ktp-gcp-auth

Firebase and Google Cloud Platform authentication for Ktor applications:

- **Firebase Authentication**: Verify Firebase ID tokens
- **Role-Based Access Control**: Protect routes with role requirements
- **Debug Routes**: Pre-configured authenticated debug endpoints
- **GCP Integration**: Seamless integration with Google Cloud services

**Dependency**: `implementation("com.github.lukelast.ktor-plus:ktp-gcp-auth:VERSION")`

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
    * `./gradlew ktfmtFormat check test`
* Publish to your local maven repository for testing locally in another project.
    * `./gradlew clean publishToMavenLocal`
* Format code
    * `./gradlew ktfmtFormat`
* Update gradle
  * `./gradlew wrapper --gradle-version latest`

## Gradle Plugins

The `ktp-gradle-plugins` composite child project builds a Gradle plugin Jar with two plugins.

### KTP Gradle Project Plugin

Plugin ID: `com.github.lukelast.ktor-plus`

This is a normal Gradle plugin and configures a project to follow the KTP Framework conventions.

## Releases

Releases and versioning happen automatically for each commit to the main branch.
