package com.ouor.mycoupon.crash

import android.content.Context
import android.os.Build
import com.ouor.mycoupon.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F19: crash reports as files inside the app only (latest [KEEP]). Nothing is sent; the user shares a file
 * through the system sheet. Reports carry no coupon data: exception messages are scrubbed of digit runs and
 * Hangul (numbers, product names) since a message can quote parsed input.
 */
@Singleton
class CrashLog @Inject constructor(@ApplicationContext private val context: Context) {
    val dir = File(context.filesDir, "crash").apply { mkdirs() }

    fun install() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(t.name, e) }
            // the system handler logs the message to the crash log buffer: hand it the scrubbed copy (QA R1-19)
            prev?.uncaughtException(t, runCatching { scrubbed(e) }.getOrDefault(e))
        }
    }

    fun write(thread: String, e: Throwable): File = save(LocalDateTime.now(), report(thread, e, LocalDateTime.now().toString()))

    private fun save(at: LocalDateTime, text: String): File {
        val f = File(dir, "crash-" + at.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".txt")
        f.writeText(text)
        files().drop(KEEP).forEach { it.delete() }
        return f
    }

    /**
     * Native crashes (ONNX Runtime, ZXing has none) kill the process before any Java handler runs. On the next start,
     * Android 11+ reports them through ApplicationExitInfo; each new one becomes a crash file (header + system
     * description only: the tombstone holds raw memory, which could contain coupon numbers, so it is not copied).
     */
    fun recordNativeCrashes() {
        if (Build.VERSION.SDK_INT < 30) return
        val seen = File(dir, "native-seen")
        val last = seen.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
        val exits = context.getSystemService(android.app.ActivityManager::class.java)
            .getHistoricalProcessExitReasons(null, 0, 0)
            .filter { it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE && it.timestamp > last }
        for (x in exits) {
            val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(x.timestamp), java.time.ZoneId.systemDefault())
            save(at, header(at.toString()) + "native crash (process ${x.processName})\n" + (x.description?.let { "description: " + scrub(it) + "\n" } ?: ""))
        }
        exits.maxOfOrNull { it.timestamp }?.let { seen.writeText(it.toString()) }
    }

    /** Newest first. */
    fun files(): List<File> = dir.listFiles { x -> x.name.startsWith("crash-") }.orEmpty().sortedByDescending { it.name }

    /** Keeps the native-crash cursor so wiped reports do not come back on the next start. */
    fun clear() = files().forEach { it.delete() }

    companion object {
        const val KEEP = 10

        fun header(time: String): String = buildString {
            appendLine("time: $time")
            appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        }

        fun report(thread: String, e: Throwable, time: String): String = buildString {
            append(header(time))
            appendLine("thread: $thread")
            var c: Throwable? = e
            var first = true
            while (c != null) {
                appendLine((if (first) "" else "Caused by: ") + c.javaClass.name + (c.message?.let { ": " + scrub(it) } ?: ""))
                c.stackTrace.forEach { appendLine("\tat $it") }
                first = false
                c = c.cause.takeIf { it !== c }
            }
        }

        /** Same class name, stack and causes as [e], messages scrubbed. */
        fun scrubbed(e: Throwable): Throwable = Scrubbed(e.javaClass.name, e.message?.let(::scrub), e.cause?.takeIf { it !== e }?.let(::scrubbed))
            .apply { stackTrace = e.stackTrace }

        private class Scrubbed(val name: String, msg: String?, cause: Throwable?) : RuntimeException(msg, cause) {
            override fun toString() = if (message == null) name else "$name: $message"
        }

        /** Masks anything that could be coupon content in a message. */
        fun scrub(msg: String): String = msg
            .replace(Regex("\\d{4,}"), "#")
            .replace(Regex("[가-힣]+"), "*")
            .take(300)
    }
}
