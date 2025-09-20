# Repository Guidelines

## Project Structure & Module Organization
Ktor Plus is a multi-project Gradle build. Core libraries live under `libs/` (for example `libs/ktp-ktor`, `libs/ktp-config`, `libs/ktp-core`, `libs/ktp-stripe`, `libs/ktp-test`), each using the standard `src/main/kotlin` and `src/test/kotlin` layout with resources alongside. The Gradle plugin code resides in `ktp-gradle-plugin/`, and a runnable reference app sits in `examples/ktp-example/`, which depends on the published libraries.

## Build, Test, and Development Commands
- `./gradlew clean check` – compile all modules and run every verification task.
- `./gradlew ktfmtFormat` – apply ktfmt formatting to Kotlin sources; run before committing.
- `./gradlew :examples:ktp-example:run` – launch the sample app using the configured main class.
- `./gradlew publishToMavenLocal` – install libraries and the plugin into your local Maven for downstream testing.
- `./gradlew publish` – trigger the release pipeline used for JitPack builds.

## Coding Style & Naming Conventions
Use four-space indentation and idiomatic Kotlin style. Keep packages under the `net.ghue.ktp` namespace and align filenames with public types. Prefer expressive function names; when scenario-driven, use backtick-quoted test-style names (see `ViteFrontendTest`). Let ktfmt settle import order and wrapping—avoid manual tweaks that fight the formatter.

## Testing Guidelines
Unit and integration tests rely on `kotlin.test` and Ktor's `testApplication` utilities. Name test files with the `*Test.kt` suffix and ensure each new feature has at least one covering test. Execute `./gradlew test` for module-level runs or `./gradlew check` for the full suite; investigate reports under `build/reports/tests/`. Aim to keep fast-running tests, mocking external services where needed.

## Commit & Pull Request Guidelines
Write concise, present-tense commit subjects (e.g. `Add Vite static routing helpers`). Squash fixups before pushing. Pull requests should summarize the change, link relevant issues, list manual/automated test results, and include screenshots or config snippets when altering HTTP surfaces or build outputs.

## Publishing & Release Notes
Main-branch commits automatically produce releases; verify version bumps in `gradle.properties` when cutting tagged builds. Update `README.md` and module-level docs when introducing new public APIs, and call out breaking changes in the PR description so the release notes stay accurate.
