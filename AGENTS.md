# Repository Guidelines

## Project Structure & Module Organization
Ktor Plus is a multi-project Gradle build. Core libraries live under `libs/` (for example `libs/ktp-ktor`, `libs/ktp-config`, `libs/ktp-core`, `libs/ktp-stripe`, `libs/ktp-test`), each using the standard `src/main/kotlin` and `src/test/kotlin` layout with resources alongside. The Gradle plugin code resides in `ktp-gradle-plugin/`, and a runnable reference app sits in `examples/ktp-example/`, which depends on the published libraries.

## Build, Test, and Development Commands
- `./gradlew clean check` – compile all modules and run every verification task.
- `./gradlew ktfmtFormat` – apply ktfmt formatting to Kotlin sources; run before committing.
- `./gradlew :examples:ktp-example:run` – launch the sample app using the configured main class.
- `./gradlew publishToMavenLocal` – install libraries and the plugin into your local Maven for downstream testing.
- `./gradlew publish` – trigger the release pipeline used for JitPack builds.

## Naming & Comments
Formatting is ktfmt's job (`./gradlew ktfmtFormat`); nothing here is about layout. Keep packages under the `net.ghue.ktp` namespace and align filenames with public types. Test names are scenario sentences in kotest `StringSpec`.
Comments are one line: say why, next to the code it explains; never restate what the code or a referenced constant already shows.

## Testing Guidelines
Tests are kotest `StringSpec` specs using Ktor's `testApplication` utilities. Name test files with the `*Test.kt` suffix and ensure each new feature has at least one covering test. Execute `./gradlew test` for module-level runs or `./gradlew check` for the full suite; investigate reports under `build/reports/tests/`. Aim to keep fast-running tests, mocking external services where needed.

## Commit & Pull Request Guidelines
Do nothing in git (stage, commit, push, reset) without asking Luke first.

## Publishing & Release Notes
Every main-branch push is a release: `build.yml` sets the version to `0.1.<run_number>.<run_attempt>`, tags it, and JitPack builds from the tag; a local `publishToMavenLocal` is `0-SNAPSHOT`. Update `README.md` and module-level docs when introducing new public APIs, and call out breaking changes in the PR description so the release notes stay accurate.
