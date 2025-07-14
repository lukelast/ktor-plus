# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Essential Commands

### Build & Test
- `./gradlew clean check` - Run all tests and verification tasks on all projects
- `./gradlew clean publishToMavenLocal` - Build and publish to local Maven repository for testing
- `./gradlew publish` - Publish libraries and plugins to Nexus
- `./gradlew ktfmtFormat` - Format all Kotlin code using ktfmt

### Gradle Plugin Commands
- `./gradlew clean check` includes validation of the ktp-gradle-plugin
- `./gradlew validatePlugins` - Validate the Gradle plugins specifically

### Single Test Execution
- Use standard Gradle test task patterns: `./gradlew :libs:ktp-config:test`
- For specific test classes: `./gradlew :libs:ktp-config:test --tests "KtpConfigTest"`

## Architecture Overview

### Multi-Project Structure
- **Root Project**: `ktp` - KTOR Plus microservice framework
- **Composite Build**: `ktp-gradle-plugin` - Contains two Gradle plugins for KTP conventions
- **Libraries**: Located in `libs/` directory, each auto-included as subproject
- **Examples**: `examples/ktp-demo` - Demonstrates framework usage

### Key Libraries
- **ktp-core**: Core utilities with minimal dependencies, including logging (KtpLog)
- **ktp-config**: Configuration management using HOCON format with priority-based layering
- **ktp-ktor**: Ktor-specific extensions and utilities (Health endpoints, debug info)

### Gradle Plugin System
Two plugins work together:
1. **Settings Plugin** (`net.ghue.ktp.gradle.settings`): Runs first during settings phase
2. **Project Plugin** (`net.ghue.ktp.gradle`): Configures projects with KTP conventions

### Configuration System (ktp-config)
Uses a sophisticated file naming convention for config layering:
- Format: `{priority}.{name}.{environment}.conf`
- Priority groups: `local` > `app` > `lib` > `default`
- Environment-specific configs override general ones
- Files placed in `src/main/resources/ktp/`
- Secrets identified by keywords: `secret`, `password`, `applicationKey`

### Technology Stack
- **Kotlin**: 2.2.0 with coroutines
- **Ktor**: 3.2.1 for HTTP server functionality
- **Koin**: Dependency injection
- **Logback**: Logging with SLF4J
- **HOCON**: Configuration format via Typesafe Config and config4k
- **JUnit 5**: Testing framework

### Project Conventions
- Group ID for libraries: `net.ghue.ktp.lib`
- Uses Kotlin DSL for all Gradle files
- Ktfmt for code formatting
- Detekt for static analysis
- Version catalogs in `gradle/libs.versions.toml`

## Development Notes

### Working with Libraries
- Each directory in `libs/` is automatically included as a subproject
- Libraries should have minimal dependencies (especially ktp-core)
- Use the KTP Gradle plugin for consistent project configuration

### Configuration Development
- Test configs use priority `0` and environment `test`
- Local development configs use priority `local`
- Never commit local development configs to version control
- Use `KTP_DEV_ENV` environment variable for custom dev environment names

### Testing
- Unit tests should not pick up `local.conf` files
- Use `local.{name}.test.conf` for test-specific local overrides
- Test resources follow same config naming conventions

### Releases
- Automated releases trigger on commits to main branch
- No manual version management required