package net.ghue.ktp.ktor.app.debug

import java.lang.management.LockInfo
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Generates a comprehensive thread dump in JVM standard format.
 *
 * This includes:
 * - Thread state and priority
 * - Stack traces
 * - Lock and monitor information
 * - CPU time statistics
 * - Coroutine debug info (if available)
 */
fun generateThreadDump(): String {
    val threadMXBean = ManagementFactory.getThreadMXBean()
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

    // Get all thread info with lock information
    val threadInfos = threadMXBean.dumpAllThreads(true, true)

    // Sort by thread ID for consistency
    threadInfos.sortBy { it.threadId }

    // Format each thread
    for (threadInfo in threadInfos) {
        sb.append(formatThreadInfo(threadInfo, threadMXBean))
        sb.appendLine()
    }

    // Summary statistics
    sb.appendLine("Thread Summary:")
    sb.appendLine("  Total threads: ${threadInfos.size}")
    val stateGroups = threadInfos.groupBy { it.threadState }
    for ((state, threads) in stateGroups.entries.sortedByDescending { it.value.size }) {
        sb.appendLine("  ${state.name}: ${threads.size}")
    }

    // Coroutine info (experimental)
    collectCoroutineInfo()?.let {
        sb.appendLine()
        sb.appendLine("Coroutine Debug Info:")
        sb.appendLine(it)
    }

    return sb.toString()
}

private const val MS_PER_SECOND = 1_000_000

@Suppress("complexity")
private fun formatThreadInfo(
    threadInfo: ThreadInfo,
    threadMXBean: java.lang.management.ThreadMXBean,
): String {
    val sb = StringBuilder()

    // Thread header: "thread-name" #id daemon? prio=X
    sb.append("\"${threadInfo.threadName}\" #${threadInfo.threadId}")
    if (threadInfo.isDaemon) {
        sb.append(" daemon")
    }
    sb.append(" prio=${threadInfo.priority}")
    sb.appendLine()

    // Thread state
    sb.append("   java.lang.Thread.State: ${threadInfo.threadState}")

    // Lock info if waiting/blocked
    threadInfo.lockInfo?.let { lockInfo ->
        sb.appendLine()
        when (threadInfo.threadState) {
            Thread.State.BLOCKED -> sb.append("   - waiting to lock ${formatLockInfo(lockInfo)}")
            Thread.State.WAITING,
            Thread.State.TIMED_WAITING -> sb.append("   - waiting on ${formatLockInfo(lockInfo)}")
            else -> {}
        }

        threadInfo.lockOwnerName?.let { owner ->
            sb.appendLine()
            sb.append("   owned by \"$owner\" #${threadInfo.lockOwnerId}")
        }
    }
    sb.appendLine()

    // CPU time statistics
    if (threadMXBean.isThreadCpuTimeEnabled) {
        val cpuTime = threadMXBean.getThreadCpuTime(threadInfo.threadId)
        val userTime = threadMXBean.getThreadUserTime(threadInfo.threadId)
        if (cpuTime >= 0) {
            sb.appendLine(
                "   CPU time: ${cpuTime / MS_PER_SECOND}ms (user: ${userTime / MS_PER_SECOND}ms)"
            )
        }
    }

    // Stack trace
    val stackTrace = threadInfo.stackTrace
    if (stackTrace.isNotEmpty()) {
        for (element in stackTrace) {
            sb.appendLine("        at $element")

            // Show locks held at this frame
            for (monitor in threadInfo.lockedMonitors) {
                if (monitor.lockedStackDepth == stackTrace.indexOf(element)) {
                    sb.appendLine("        - locked ${formatLockInfo(monitor)}")
                }
            }
        }
    } else {
        sb.appendLine("   (no stack trace available)")
    }

    // Locked synchronizers
    val lockedSynchronizers = threadInfo.lockedSynchronizers
    if (lockedSynchronizers.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("   Locked ownable synchronizers:")
        for (sync in lockedSynchronizers) {
            sb.appendLine("        - ${formatLockInfo(sync)}")
        }
    }

    return sb.toString()
}

private fun formatLockInfo(lockInfo: LockInfo): String {
    @Suppress("MagicNumber")
    return "<${lockInfo.identityHashCode.toString(16)}> (a ${lockInfo.className})"
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
