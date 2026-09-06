# ktp-gradle-plugins
KTOR Plus Gradle Plugins: the settings plugin (`com.github.lukelast.ktor-plus`), the project
plugin (`.project`) and the lukestack plugin (`.lukestack`), all in one jar. What each one does,
the `ktp.plugin` / `ktp.mode` properties and the `KtpLibs` accessors are documented in the
[root README](../README.md#gradle-plugins); the `gradle.properties` keys the lukestack plugin
reads (`ktp.vite`, `ktp.vite.port`, `gcp.*`) are documented on `ViteDev.kt` and `Gcloud.kt`.

# Publishing

`./gradlew clean publish`

`./gradlew clean publishToMavenLocal`
