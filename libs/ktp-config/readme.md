# KTP Configuration Library

Configuration management built on [Typesafe Config](https://github.com/lightbend/config) with layered, environment-specific overrides.

## Quick Start

```kotlin
// Production - auto-detects environment
val config = KtpConfig.create()
val appName = config.data.app.name

// Testing with overrides
val config = KtpConfig.create {
    setUnitTestEnv()
    overrideValue("app.name", "test-app")
}
```

## Config Files

Place `.conf` files in `src/main/resources/ktp/` using [HOCON format](https://github.com/lightbend/config/blob/main/HOCON.md).

### File Naming: `<priority>.<configName>.<environment>.conf`

- **Priority** (required): Number 0-9. Lower = higher priority (0 overrides 9)
- **Config name** (optional): Descriptive identifier (e.g., `database`, `kafka`)
- **Environment** (optional): Applies only to specific env (e.g., `prod`, `test`)

**Examples:**
- `0.local.conf` - Highest priority local overrides
- `5.app.conf` - App config for all environments
- `5.app.prod.conf` - Production-only app config
- `9.defaults.conf` - Lowest priority defaults

### Configuration Precedence (highest to lowest)

1. Override map (`overrideValue()`)
2. `CONFIG_FORCE_` environment variables
3. `KTP_CONFIG` environment variable
4. Config files (by priority, env, config name, alphabetically)

Override keys are paths relative to the config root (e.g. `app.secret`) and must match a path that
already exists in the merged config. `create()` fails fast on unknown keys because an override that
nothing reads would be a silent no-op. The `env` path cannot be overridden; set `env` on the builder
instead.

String values are trimmed of leading/trailing ASCII whitespace (the `java.lang.String.trim` set)
when the config is built, so padded values injected by secret managers or CI just work; trimmed
paths are logged. Padding can still survive inside a `${}` concatenation when the padded value
comes from a config file, `KTP_CONFIG`, or a plain `${?ENV_VAR}` substitution.

## Environment Detection

Checks in order: `KTP_ENV` → `ENV` → `KUBERNETES_NAMESPACE` (each as an environment variable, then as a
system property) → `localDevEnv` in `0.conf` → default: `localdev`

**Custom dev environment:** Create `0.conf` with `localDevEnv = "your-username"`

**Valid names:** lowercase letters, numbers, dashes only (`[a-z0-9-]+`)

A `localDevEnv` key that is present but invalid fails fast rather than silently falling back to
`localdev`; only a missing `0.conf` or missing key falls back.

## Creating Configuration

```kotlin
// Auto-detect environment
val config = KtpConfig.create()

// Unit testing
val config = KtpConfig.create { setUnitTestEnv() }

// Integration testing
val config = KtpConfig.create { setIntegrationTestEnv() }

// With overrides
val config = KtpConfig.create {
    setUnitTestEnv()
    overrideValue("app.name", "test-app")
    overrideValue("database.host", "localhost")
}

// Specific environment
val config = KtpConfig.create { env = Env("staging") }
```

## Accessing Configuration

`extractChild<T>()` reads from the config object whose name matches `T` with the first letter lowercased.

```kotlin
// Built-in data
val name = config.data.app.name
val version = config.data.app.version

// Custom data classes
// DatabaseConfig maps to the "databaseConfig" config object.
data class DatabaseConfig(val host: String, val port: Int)
val dbConfig = config.extractChild<DatabaseConfig>()

// Configuration classes
class MyServiceConfig(config: KtpConfig) {
    val db = config.extractChild<DatabaseConfig>()
}
val serviceConfig = config.get<MyServiceConfig>()

// Debugging
val allConfig = config.getAllConfigMasked()  // Secrets masked
config.logAllConfig()
val env = config.env
```

## Environment Variables

### KTP_CONFIG - Inject HOCON via Environment

Useful for containers, CI/CD, and cloud deployments:

```bash
# Simple
export KTP_CONFIG='app.name = "my-service"'

# Nested
export KTP_CONFIG='database { host = "prod-db.example.com", port = 5432 }'

# Arrays
export KTP_CONFIG='servers = ["server1", "server2"]'

# With substitutions
export KTP_CONFIG='baseUrl = ${BASE_URL}, apiUrl = ${baseUrl}"/api"'
```

Invalid syntax is logged and ignored.

### CONFIG_FORCE_ - Override Any Config Value

Prefix environment variables with `CONFIG_FORCE_` to override config:

**Naming rules:**
- `_` → `.` (dot)
- `__` → `-` (dash)
- `___` → `_` (underscore)

**Examples:**

| Environment Variable | Config Path |
|---------------------|-------------|
| `CONFIG_FORCE_app_name` | `app.name` |
| `CONFIG_FORCE_database_host` | `database.host` |
| `CONFIG_FORCE_kafka_bootstrap__servers` | `kafka.bootstrap-servers` |
| `CONFIG_FORCE_service_api___key` | `service.api_key` |
| `CONFIG_FORCE_servers_0` | `servers[0]` |

### Optional Substitutions in Config Files

```hocon
# Override if env var exists
basedir = "/app/data"
basedir = ${?CUSTOM_BASEDIR}

# Multiple fallbacks
database.url = "jdbc:postgresql://localhost/mydb"
database.url = ${?JDBC_DATABASE_URL}
database.url = ${?DATABASE_URL}
```

## Secrets

Values with `secret`, `password`, or `applicationKey` in the path are automatically masked in logs:

```hocon
myapp.secretToken = "1234"  # Logged as "4 chars"
```
