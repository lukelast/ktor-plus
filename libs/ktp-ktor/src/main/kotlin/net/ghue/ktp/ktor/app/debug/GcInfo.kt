package net.ghue.ktp.ktor.app.debug

import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.milliseconds
import net.ghue.ktp.config.ConfigRecord

fun collectGcInfo(): ConfigRecord {
    val gcInfo = ManagementFactory.getGarbageCollectorMXBeans()
    val gcNames = gcInfo.map { it.name }
    val gcCounts = gcInfo.map { it.collectionCount }
    val gcTimes = gcInfo.map { it.collectionTime.milliseconds }
    val gcInfoMap = gcNames.zip(gcCounts.zip(gcTimes)).toMap()
    return ConfigRecord(
        path = "jvm gc info",
        source = "jvm",
        value =
            gcInfoMap
                .map { "${it.key}, ${it.value.first} collections, ${it.value.second}" }
                .joinToString("; "),
    )
}

fun collectMaxGcHeapMib(): ConfigRecord {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory()
    return ConfigRecord(
        path = "jvm max heap (MiB)",
        source = "jvm",
        value = "${maxMemory / (1024 * 1024)}",
    )
}

fun collectCpuCores(): ConfigRecord {
    return ConfigRecord(
        path = "cpu virtual threads",
        source = "jvm",
        value = Runtime.getRuntime().availableProcessors().toString(),
    )
}
