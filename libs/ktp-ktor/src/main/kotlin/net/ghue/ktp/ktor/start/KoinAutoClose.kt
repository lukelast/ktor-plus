package net.ghue.ktp.ktor.start

import net.ghue.ktp.log.log
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Callbacks

/**
 * Closes every created [AutoCloseable] instance when this Koin closes, so services need no
 * `onClose` of their own. A definition that declares `onClose` keeps it. Call after all modules are
 * loaded; Koin only exposes the definitions through its internal registry.
 */
@OptIn(KoinInternalApi::class)
fun Koin.autoCloseInstances() {
    instanceRegistry.instances.values
        .map { it.beanDefinition }
        .filter { it.callbacks.onClose == null }
        .forEach { definition ->
            @Suppress("UNCHECKED_CAST")
            (definition as BeanDefinition<Any?>).callbacks = Callbacks(::closeIfCloseable)
        }
}

// Koin drops instances in a plain loop, so a throwing close must not stop the others.
private fun closeIfCloseable(instance: Any?) {
    if (instance !is AutoCloseable) return
    try {
        instance.close()
    } catch (ex: Exception) {
        log {}.warn(ex) { "Failed to close ${instance::class.qualifiedName}" }
    }
}
