package com.example

import android.app.Application
import java.io.File

/**
 * Prega AI — Application entry point, existing for exactly one reason:
 * the crash flight recorder.
 *
 * Context: the app hit an opens-and-instantly-closes crash loop on a real
 * device that survived a clean reinstall, and this project has no device
 * access, no adb, and (deliberately, after its own incident) no Crashlytics
 * yet. Debugging by hypothesis was the only option, and it is a bad option.
 *
 * This handler ends that: any uncaught exception from Application.onCreate
 * onward is written to files/last_crash.txt before the process dies. On the
 * NEXT launch, MainActivity sees the file and — instead of running the app
 * straight into the same crash — shows the stack trace full-screen with a
 * copy button, so the exact failing line can be reported in a screenshot.
 *
 * What it cannot catch, by Android design: crashes inside library
 * ContentProvider initializers (e.g. Firebase's), which run BEFORE
 * Application.onCreate. That blindness is itself a signal — if the app
 * still dies instantly and NO crash screen appears on relaunch, the fault
 * is provider-level, which narrows it to the Firebase SDK set.
 */
class PregaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(filesDir, CRASH_FILE).writeText(render(throwable))
            } catch (_: Exception) {
                // If we can't record it, still crash honestly below.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun render(e: Throwable): String = buildString {
        appendLine("Prega AI crash report")
        appendLine("Time: ${System.currentTimeMillis()}")
        appendLine()
        appendLine(e.toString())
        e.stackTrace.take(30).forEach { appendLine("    at $it") }
        var cause = e.cause
        var depth = 0
        while (cause != null && depth < 4) {
            appendLine("Caused by: $cause")
            cause.stackTrace.take(15).forEach { appendLine("    at $it") }
            cause = cause.cause
            depth++
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
