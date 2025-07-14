# KTOR Plus (ktp)

A microservice framework built on ktor.

## Running stuff

* Test everything
    * `./gradlew clean check`
* Publish to your local maven repository for testing locally in another project.
    * `./gradlew clean publishToMavenLocal`
* Publish
    * `./gradlew publish`
* Format code
  * `./gradlew ktfmtFormat`

## Gradle Plugins

The `ktp-gradle-plugins` composite child project builds a Gradle plugin Jar with two plugins.

### KTP Gradle Settings Plugin

Plugin ID: `net.ghue.ktp.gradle.settings`

This is a Gradle settings plugin and runs first.

### KTP Gradle Project Plugin

Plugin ID: `net.ghue.ktp.gradle`

This is a normal Gradle plugin and configures a project to follow the KTP Framework conventions.

## KTP Libraries

### core
This is for utility code that has minimal dependencies.

### [config](libs%2Fktp-config%2Freadme.md)
Manage all the configuration settings of your application.

### ktor
Library for Ktor.


## Using KTP via JitPack

KTP libraries and Gradle plugins are available via JitPack:

### Libraries

```kotlin
// In your build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.yourusername.ktp:ktp-config:TAG")
    implementation("com.github.yourusername.ktp:ktp-core:TAG")
    implementation("com.github.yourusername.ktp:ktp-ktor:TAG")
}
```

### Gradle Plugins

```kotlin
// In your settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

// In your build.gradle.kts
plugins {
    id("com.github.yourusername.ktp.ktp-gradle-plugin") version "TAG"
}
```

Replace `yourusername` with your GitHub username and `TAG` with a git tag, commit hash, or branch name.

## Releases

Releases and versioning happen automatically for each commit to the main branch.


### Update Gradle
`./gradlew wrapper --gradle-version latest`
