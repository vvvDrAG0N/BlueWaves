package com.epubreader.core.debug

import android.util.Log

/**
 * Small centralized logging wrapper for high-signal app diagnostics.
 *
 * Debug/info logs are debug-build only to keep release output quiet.
 * Warning/error logs stay available for real fallback and failure paths.
 */
object AppLog {
    private val isDebugBuild by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            Class.forName("com.epubreader.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        }.getOrDefault(false)
    }

    const val APP_SHELL = "AppShell"
    const val LIBRARY = "LibraryScreen"
    const val PARSER = "EpubParser"
    const val READER = "ReaderScreen"
    const val SETTINGS = "SettingsManager"

    fun d(tag: String, message: () -> String) {
        if (isDebugBuild) {
            runLogging { Log.d(tag, message()) }
        }
    }

    fun i(tag: String, message: () -> String) {
        if (isDebugBuild) {
            runLogging { Log.i(tag, message()) }
        }
    }

    fun w(tag: String, message: () -> String) {
        runLogging { Log.w(tag, message()) }
    }

    fun w(tag: String, throwable: Throwable, message: () -> String) {
        runLogging { Log.w(tag, message(), throwable) }
    }

    fun e(tag: String, message: () -> String) {
        runLogging { Log.e(tag, message()) }
    }

    fun e(tag: String, throwable: Throwable, message: () -> String) {
        runLogging { Log.e(tag, message(), throwable) }
    }

    private inline fun runLogging(block: () -> Unit) {
        runCatching(block)
    }
}
