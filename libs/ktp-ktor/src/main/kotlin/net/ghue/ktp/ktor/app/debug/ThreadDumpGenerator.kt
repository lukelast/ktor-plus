package net.ghue.ktp.ktor.app.debug

import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

/**
 * Generates a thread dump of all threads, including virtual threads.
 *
 * [java.lang.management.ThreadMXBean] only reports platform threads, which would hide every request
 * handler now that KTP runs each request on its own virtual thread.
 * [HotSpotDiagnosticMXBean.dumpThreads] (JDK 21+) enumerates all threads, at the cost of the
 * per-thread lock and CPU detail the old ThreadMXBean-based dump provided.
 */
fun generateThreadDump(): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
    val sb = StringBuilder()

    // Header
    sb.appendLine("Full thread dump - $timestamp")
    sb.appendLine()

    // Runtime info
    val runtime = Runtime.getRuntime()
    sb.appendLine(
        "JVM: ${System.getProperty("java.vm.name")} (${System.getProperty("java.vm.version")})"
    )
    sb.appendLine("Kotlin: ${KotlinVersion.CURRENT}")
    sb.appendLine("Processors: ${runtime.availableProcessors()}")
    sb.appendLine()

    val allThreads = dumpAllThreads()
    sb.append(allThreads)

    // Summary statistics
    sb.appendLine()
    sb.appendLine("Thread Summary:")
    sb.appendLine("  Total threads (including virtual): ${countThreadEntries(allThreads)}")
    sb.appendLine("  Platform threads: ${ManagementFactory.getThreadMXBean().threadCount}")

    // Coroutine info (experimental)
    collectCoroutineInfo()?.let {
        sb.appendLine()
        sb.appendLine("Coroutine Debug Info:")
        sb.appendLine(it)
    }

    return sb.toString()
}

/**
 * Each thread entry in the [HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN] format starts a
 * line with `#<tid>`.
 */
private val threadEntryRegex = Regex("""(?m)^#\d+ """)

private fun countThreadEntries(dump: String): Int = threadEntryRegex.findAll(dump).count()

/**
 * [HotSpotDiagnosticMXBean.dumpThreads] refuses to overwrite an existing file, so dump into a fresh
 * temp directory and clean it up after reading.
 */
private fun dumpAllThreads(): String {
    val diagnostic = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
    val dir = Files.createTempDirectory("ktp-thread-dump")
    val file = dir.resolve("threads.txt")
    return try {
        diagnostic.dumpThreads(
            file.toAbsolutePath().toString(),
            HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN,
        )
        file.readText()
    } finally {
        file.deleteIfExists()
        dir.deleteIfExists()
    }
}

/**
 * Attempts to collect Kotlin coroutine debug information. This is experimental and depends on
 * kotlinx-coroutines-debug being available and coroutine debugging being enabled via system
 * property.
 *
 * Returns null if coroutine debug info is not available.
 */
private fun collectCoroutineInfo(): String? {
    return try {
        // Check if coroutine debugging is enabled
        val debugEnabled =
            System.getProperty("kotlinx.coroutines.debug")?.equals("on", ignoreCase = true) ?: false

        if (!debugEnabled) {
            return "Coroutine debugging not enabled. Enable with -Dkotlinx.coroutines.debug=on"
        }

        // Attempt to access coroutine debug info via reflection
        // This avoids hard dependency on kotlinx-coroutines-debug
        val debugClass = Class.forName("kotlinx.coroutines.debug.DebugProbes")
        val dumpCoroutinesMethod = debugClass.getMethod("dumpCoroutines")
        val coroutineInfo = dumpCoroutinesMethod.invoke(null)
        coroutineInfo?.toString()
    } catch (_: ClassNotFoundException) {
        "Coroutine debug info not available (kotlinx-coroutines-debug not in classpath)"
    } catch (e: Exception) {
        "Failed to collect coroutine info: ${e.message}"
    }
}
