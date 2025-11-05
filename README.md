# KTOR Plus (ktp)

A microservice framework built on ktor.

## Running stuff

* Test everything
    * `./gradlew ktfmtFormat check test`
* Publish to your local maven repository for testing locally in another project.
    * `./gradlew clean publishToMavenLocal`
* Publish
    * `./gradlew publish`
* Format code
  * `./gradlew ktfmtFormat`

## Gradle Plugins

The `ktp-gradle-plugins` composite child project builds a Gradle plugin Jar with two plugins.

### KTP Gradle Project Plugin

Plugin ID: `com.github.lukelast.ktor-plus`

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

https://jitpack.io/#lukelast/ktor-plus

### Libraries

```kotlin
// In your build.gradle.kts
dependencies {
    implementation("com.github.lukelast.ktor-plus:ktp-ktor:VERSION")
}
```

### Gradle Plugins

```kotlin
// In your settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// In your build.gradle.kts
plugins { id("com.github.lukelast.ktor-plus") version "VERSION" }
```


## Releases

Releases and versioning happen automatically for each commit to the main branch.


### Update Gradle
`./gradlew wrapper --gradle-version latest`
