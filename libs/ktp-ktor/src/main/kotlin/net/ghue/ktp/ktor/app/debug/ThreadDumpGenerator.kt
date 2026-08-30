package net.ghue.ktp.ktor.app.debug

import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

/**
 * Dumps all threads via [HotSpotDiagnosticMXBean.dumpThreads] (JDK 21+) because
 * [java.lang.management.ThreadMXBean] omits the virtual threads that run every KTP request, at the
 * cost of the per-thread lock and CPU detail ThreadMXBean provides.
 */
fun generateThreadDump(): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
    val sb = StringBuilder()

    sb.appendLine("Full thread dump - $timestamp")
    sb.appendLine()

    val runtime = Runtime.getRuntime()
    sb.appendLine(
        "JVM: ${System.getProperty("java.vm.name")} (${System.getProperty("java.vm.version")})"
    )
    sb.appendLine("Kotlin: ${KotlinVersion.CURRENT}")
    sb.appendLine("Processors: ${runtime.availableProcessors()}")
    sb.appendLine()

    val allThreads = dumpAllThreads()
    sb.append(allThreads)

    sb.appendLine()
    sb.appendLine("Thread Summary:")
    sb.appendLine("  Total threads (including virtual): ${countThreadEntries(allThreads)}")
    sb.appendLine("  Platform threads: ${ManagementFactory.getThreadMXBean().threadCount}")

    collectCoroutineInfo()?.let {
        sb.appendLine()
        sb.appendLine("Coroutine Debug Info:")
        sb.appendLine(it)
    }

    return sb.toString()
}

/** Thread entries in the TEXT_PLAIN dump format each start a line with `#<tid>`. */
private val threadEntryRegex = Regex("""(?m)^#\d+ """)

private fun countThreadEntries(dump: String): Int = threadEntryRegex.findAll(dump).count()

/** [HotSpotDiagnosticMXBean.dumpThreads] refuses to overwrite files, hence the fresh temp dir. */
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

/** Unavailability yields an explanatory string, not null, so the dump says why it's missing. */
private fun collectCoroutineInfo(): String? {
    return try {
        val debugEnabled =
            System.getProperty("kotlinx.coroutines.debug")?.equals("on", ignoreCase = true) ?: false

        if (!debugEnabled) {
            return "Coroutine debugging not enabled. Enable with -Dkotlinx.coroutines.debug=on"
        }

        // Reflection avoids a hard dependency on kotlinx-coroutines-debug.
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
