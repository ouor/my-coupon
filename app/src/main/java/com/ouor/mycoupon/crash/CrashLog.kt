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
class CrashLog @Inject constructor(@ApplicationContext context: Context) {
    val dir = File(context.filesDir, "crash").apply { mkdirs() }

    fun install() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(t.name, e) }
            prev?.uncaughtException(t, e)
        }
    }

    fun write(thread: String, e: Throwable): File {
        val now = LocalDateTime.now()
        val f = File(dir, "crash-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".txt")
        f.writeText(report(thread, e, now.toString()))
        files().drop(KEEP).forEach { it.delete() }
        return f
    }

    /** Newest first. */
    fun files(): List<File> = dir.listFiles { x -> x.name.startsWith("crash-") }.orEmpty().sortedByDescending { it.name }

    fun clear() = dir.listFiles().orEmpty().forEach { it.delete() }

    companion object {
        const val KEEP = 10

        fun report(thread: String, e: Throwable, time: String): String = buildString {
            appendLine("time: $time")
            appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
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

        /** Masks anything that could be coupon content in a message. */
        fun scrub(msg: String): String = msg
            .replace(Regex("\\d{4,}"), "#")
            .replace(Regex("[가-힣]+"), "*")
            .take(300)
    }
}
