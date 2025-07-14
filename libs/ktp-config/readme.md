# KTP Configuration Library

A configuration management library built on top of [Typesafe Config](https://github.com/lightbend/config) that provides a layered configuration system with environment-specific overrides.

## Config Files

* Config files must be placed in the `src/main/resources/ktp` directory
* Config files must end with `.conf` extension
* Config files must be in [HOCON format](https://github.com/lightbend/config/blob/main/HOCON.md)
* File names consist of up to 3 period-separated tokens: `<priority>.<name>.<environment>.conf`

### File Naming Convention

#### 1. Priority (Required)
The first token must be a number (0-9) that determines the configuration precedence:
- **Lower numbers = higher priority** (0 overrides 9)
- This allows for flexible layering of configuration files
- Example: `0.conf` has higher priority than `9.conf`

#### 2. Name (Optional)
The second token is an optional descriptive name for the config file:
- Can be any string that describes the purpose of the configuration
- Files with names take precedence over files without names at the same priority level
- Example: `5.database.conf`, `5.kafka.conf`

#### 3. Environment (Optional)
The third token is an optional environment specifier:
- When omitted, the config file applies to all environments
- When specified, the file only applies to that specific environment
- Environment-specific files take precedence over generic files at the same priority level
- Example: `5.app.prod.conf` overrides `5.app.conf` in the `prod` environment

### Configuration Precedence Order

When multiple config files exist, they are applied in the following order (highest to lowest precedence):

1. **Override map** (programmatically provided)
2. **System environment variables** (via `config.override_with_env_vars=true`)
3. **System properties**
4. **Config files** sorted by:
   - Priority number (ascending: 0, 1, 2, ...)
   - Environment name (files with env > files without)
   - Name (files with name > files without)
   - Alphabetical order within the same criteria

### Environment Detection

The current environment is determined by checking (in order):
1. `KTP_ENV` environment variable
2. `ENV` environment variable
3. `KUBERNETES_NAMESPACE` environment variable
4. `localDevEnv` value in `0.conf` file
5. Default: `dev`

#### Environment Name Validation

Environment names must:
- Not be blank
- Only contain lowercase letters, numbers, and dashes
- Match the regex pattern: `[a-z0-9-]+`

Valid examples: `dev`, `test`, `prod`, `staging-01`, `user-123`
Invalid examples: `DEV`, `test_env`, `prod.01`

### Custom Local Development Environment

For local development with a custom environment name, create a `0.conf` file:
```hocon
localDevEnv = "your-username"
```

This allows developers to have unique environment names to avoid conflicts when connecting to shared resources.

## Examples

* `9.defaults.conf` - Default values with lowest priority
* `5.app.conf` - Application-level configuration
* `5.app.prod.conf` - Production-specific application config
* `0.local.conf` - Local development overrides with highest priority
* `3.database.test.conf` - Test environment database configuration

## API Usage

### Creating a Configuration Manager

```kotlin
// For production use - auto-detects environment
val configManager = KtpConfig.createManager()

// For testing - always uses "test" environment
val testManager = KtpConfig.createManagerForTest()

// For testing with overrides
val testManager = KtpConfig.createManagerForTest(
    mapOf("app.name" to "test-app")
)

// For specific environment
val configManager = KtpConfig.createManagerForEnv(Env("staging"))
```

### Accessing Configuration Values

#### Built-in KtpConfigData

The library provides a built-in `KtpConfigData` class with common application settings:

```kotlin
val appName = configManager.data.app.name
val appVersion = configManager.data.app.version
val shortName = configManager.data.app.shortName()
```

Required fields in `app` configuration:
- `name`: Application name
- `nameShort`: Short name (optional, auto-generated if empty)
- `secret`: Application secret
- `version`: Application version (defaults to "local", overridden by `KUBE_APP_VERSION`)
- `hostname`: Hostname (defaults to "unknown", overridden by `USER`, `USERNAME`, or `HOSTNAME`)

#### Extracting Custom Data Classes

```kotlin
// Define your data class
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

// Extract from configuration (looks for "databaseConfig" path)
val dbConfig = configManager.extract<DatabaseConfig>()
```

#### Using Configuration Classes

For more complex configurations, create a class with a primary constructor that takes `KtpConfigManager`:

```kotlin
class MyServiceConfig(manager: KtpConfigManager) {
    val database = manager.extract<DatabaseConfig>()
    val kafka = manager.extract<KafkaConfig>()
    
    fun getDatabaseUrl() = "jdbc:postgresql://${database.host}:${database.port}/mydb"
}

// Usage
val serviceConfig = configManager.get<MyServiceConfig>()
```

### System Environment Variables

#### Direct Access to Environment Variables

All system environment variables are available under the `sysenv` prefix:

```hocon
myapp {
  apiKey = ${sysenv.MY_API_KEY}
  database {
    url = ${sysenv.DATABASE_URL}
  }
}
```

#### Environment Variable Overrides with CONFIG_FORCE_

The library enables `config.override_with_env_vars=true` by default, which allows you to override ANY configuration value using environment variables prefixed with `CONFIG_FORCE_`, even without explicit substitutions in your config files.

##### Naming Convention for CONFIG_FORCE_ Variables

The environment variable names are transformed as follows:
- The `CONFIG_FORCE_` prefix is stripped
- Single underscore (`_`) → dot (`.`)
- Double underscore (`__`) → dash (`-`)
- Triple underscore (`___`) → single underscore (`_`)

##### Examples

| Environment Variable | Config Path | Example Value |
|---------------------|-------------|---------------|
| `CONFIG_FORCE_app_name` | `app.name` | `"my-service"` |
| `CONFIG_FORCE_database_host` | `database.host` | `"prod-db.example.com"` |
| `CONFIG_FORCE_kafka_bootstrap__servers` | `kafka.bootstrap-servers` | `"kafka1:9092,kafka2:9092"` |
| `CONFIG_FORCE_service_api___key` | `service.api_key` | `"secret123"` |
| `CONFIG_FORCE_items_0` | `items[0]` | `"first-item"` |
| `CONFIG_FORCE_items_1` | `items[1]` | `"second-item"` |

##### Setting Array Values

To set array values via environment variables, specify the index:

```bash
# Sets servers = ["server1", "server2", "server3"]
export CONFIG_FORCE_servers_0="server1"
export CONFIG_FORCE_servers_1="server2"
export CONFIG_FORCE_servers_2="server3"
```

##### Precedence

`CONFIG_FORCE_` environment variables have very high precedence and will override:
- Values in config files
- System properties
- Any other configuration source except programmatic overrides

#### Optional Environment Variable Substitutions

You can also use optional substitutions in your config files that only apply if the environment variable exists:

```hocon
# Default value with optional override
basedir = "/app/data"
basedir = ${?CUSTOM_BASEDIR}  # Only overrides if CUSTOM_BASEDIR is set

# Multiple fallbacks
database {
  url = "jdbc:postgresql://localhost/mydb"
  url = ${?JDBC_DATABASE_URL}    # First priority if set
  url = ${?DATABASE_URL}          # Second priority if set
}
```

### Debugging Configuration

```kotlin
// Get all configuration values as a map
val allConfig = configManager.getAllConfig()

// Log all configuration values (secrets are masked)
configManager.logAllConfig()

// Access the underlying Typesafe Config object
val config: Config = configManager.config

// Check current environment
val env: Env = configManager.env
```

## Secrets

Fields that contain secrets need to be identified so that the values are not leaked.
We do this by adding a special keyword anywhere in the path.
Keywords are:
* secret
* password
* applicationKey

Example: `myapp.secretToken = "1234"`

When configuration values are logged or displayed, values in paths containing these keywords will be masked and show only the character count instead of the actual value.
