package net.ghue.ktp.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueType
import net.ghue.ktp.log.log

/**
 * Strips leading/trailing ASCII whitespace (code points at or below U+0020, matching
 * `java.lang.String.trim`) from every string value, including strings inside lists. HOCON already
 * trims unquoted strings, but quoted values, env-var overrides, and `${?VAR}` substitution results
 * arrive verbatim, so a secret with a trailing newline would otherwise fail far from its source.
 * Unicode spaces such as NBSP are deliberately preserved. Trimmed paths and their origins are
 * logged. Returns this same instance when nothing needs trimming.
 *
 * Must not be called on a config with unresolved `${}` substitutions; a resolved config or one
 * parsed from a plain string map is safe.
 */
internal fun Config.withTrimmedStrings(): Config {
    val (transformed, trimmedPaths) = trimStringsWithReport()
    if (trimmedPaths.isNotEmpty()) {
        log {}.info { "Trimmed whitespace from config value(s): $trimmedPaths" }
    }
    return transformed
}

/** Returns the trimmed config and the sorted `path (origin)` entries that needed trimming. */
internal fun Config.trimStringsWithReport(): Pair<Config, List<String>> {
    val trimmedPaths = mutableListOf<String>()
    val transformed = root().trimStrings(path = "", trimmedPaths)
    val config = if (transformed === root()) this else (transformed as ConfigObject).toConfig()
    return config to trimmedPaths.sorted()
}

/** Returns the receiver itself when nothing in the subtree needed trimming. */
private fun ConfigValue.trimStrings(path: String, trimmedPaths: MutableList<String>): ConfigValue =
    when (valueType()) {
        ConfigValueType.OBJECT -> {
            val original = this as ConfigObject
            val transformed = original.mapValues { (key, value) ->
                value.trimStrings(appendKey(path, key), trimmedPaths)
            }
            if (transformed.all { (key, value) -> value === original[key] }) {
                this
            } else {
                ConfigValueFactory.fromMap(transformed).withOrigin(origin())
            }
        }
        ConfigValueType.LIST -> {
            val original = this as ConfigList
            val transformed = original.mapIndexed { index, value ->
                value.trimStrings("$path[$index]", trimmedPaths)
            }
            if (transformed.indices.all { transformed[it] === original[it] }) {
                this
            } else {
                ConfigValueFactory.fromIterable(transformed).withOrigin(origin())
            }
        }
        ConfigValueType.STRING -> {
            val raw = unwrapped() as String
            // Kotlin's trim() would also strip NBSP and other Unicode spaces, which can be
            // intentional trailing characters in display strings.
            val trimmed = raw.trim { it.code <= ' '.code }
            if (trimmed == raw) {
                this
            } else {
                trimmedPaths += "$path (${origin().description()})"
                ConfigValueFactory.fromAnyRef(trimmed).withOrigin(origin())
            }
        }
        else -> this
    }

/** Builds a log-friendly dotted path, quoting keys that themselves contain dots. */
private fun appendKey(path: String, key: String): String {
    val safeKey = if ('.' in key) "\"$key\"" else key
    return if (path.isEmpty()) safeKey else "$path.$safeKey"
}
