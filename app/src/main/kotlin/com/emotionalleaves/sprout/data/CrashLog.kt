package com.emotionalleaves.sprout.data

import java.io.File
import java.time.Instant

/**
 * A local crash log. When Sprout crashes, a report is written to app-private storage; on the next start the user
 * can share it (their choice, through Android's share sheet) or dismiss it. Nothing is ever sent automatically.
 *
 * Reports hold only exception class names and stack frames. Exception messages are dropped entirely, because a
 * message could contain a transaction description or other personal data.
 */
class CrashLog(private val file: File) {

    fun install(version: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(format(error, version, Instant.now().epochSecond, thread.name)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() { runCatching { file.delete() } }

    companion object {
        private const val MAX_FRAMES = 40
        private const val MAX_CAUSES = 5

        fun format(error: Throwable, version: String, epochSeconds: Long, threadName: String): String = buildString {
            appendLine("Sprout $version crash report")
            appendLine("Time: ${Instant.ofEpochSecond(epochSeconds)}")
            appendLine("Thread: ${threadName.take(60)}")
            appendLine("Android: ${runCatching { android.os.Build.VERSION.SDK_INT }.getOrDefault(0)}")
            var t: Throwable? = error
            var depth = 0
            val seen = HashSet<Throwable>()
            while (t != null && depth < MAX_CAUSES && seen.add(t)) {
                appendLine(if (depth == 0) t.javaClass.name else "Caused by: ${t.javaClass.name}")
                t.stackTrace.take(MAX_FRAMES).forEach { appendLine("    at $it") }
                t = t.cause
                depth++
            }
        }
    }
}
