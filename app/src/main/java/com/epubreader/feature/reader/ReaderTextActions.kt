/**
 * AI_READ_AFTER: ReaderScreenControls.kt
 * AI_RELEVANT_TO: [Text Selection, Define, Translate, External App Intents]
 * PURPOSE: Intent dispatching helpers for Define and Translate actions on selected text.
 * AI_WARNING: External intent availability depends on user-installed apps.
 *
 * This file encapsulates all Android Intent construction and PackageManager
 * querying for the text selection menu's "Define" and "Translate" actions.
 * It is purely a helper layer with no Compose or UI state ownership.
 */
package com.epubreader.feature.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.epubreader.core.debug.AppLog

/**
 * Checks whether any app on the device can handle [Intent.ACTION_PROCESS_TEXT]
 * with the "define" or generic text-processing category.
 *
 * Returns true if at least one activity can handle the intent.
 */
internal fun canDefineText(context: Context): Boolean {
    val intent = buildProcessTextIntent("test", readOnly = true)
    return intent.resolveActivity(context.packageManager) != null
}

/**
 * Checks whether a translation app is available.
 * First tries ACTION_PROCESS_TEXT with specific translation packages,
 * then falls back to generic ACTION_PROCESS_TEXT availability.
 */
internal fun canTranslateText(context: Context): Boolean {
    // Try Google Translate's specific ACTION_PROCESS_TEXT first
    val processTextIntent = buildProcessTextIntent("test", readOnly = true)
    val activities = context.packageManager.queryIntentActivities(processTextIntent, 0)
    // Check if any translation-related app is available
    val hasTranslateApp = activities.any { resolveInfo ->
        val packageName = resolveInfo.activityInfo.packageName
        packageName.contains("translate", ignoreCase = true) ||
            packageName.contains("dictionary", ignoreCase = true)
    }
    if (hasTranslateApp) return true

    // Fallback: check if any app at all can handle text processing
    return activities.isNotEmpty()
}

/**
 * Launches the system "Define" action for the given [text].
 *
 * Strategy:
 * 1. Try ACTION_PROCESS_TEXT (Android 6.0+) — this is the standard floating
 *    toolbar action that system dictionaries register for.
 * 2. Fall back to an ACTION_VIEW web search as a last resort.
 *
 * Shows a Toast and returns false if no handler is available.
 */
internal fun launchDefineAction(context: Context, text: String): Boolean {
    // Try ACTION_PROCESS_TEXT first
    val processIntent = buildProcessTextIntent(text, readOnly = true)
    val activities = context.packageManager.queryIntentActivities(processIntent, 0)

    // Prefer dictionary/define-related activities
    val defineActivity = activities.firstOrNull { resolveInfo ->
        val name = resolveInfo.activityInfo.name.lowercase()
        val packageName = resolveInfo.activityInfo.packageName.lowercase()
        name.contains("define") || name.contains("dictionary") ||
            packageName.contains("dictionary") || packageName.contains("define")
    }

    if (defineActivity != null) {
        try {
            processIntent.setClassName(
                defineActivity.activityInfo.packageName,
                defineActivity.activityInfo.name
            )
            context.startActivity(processIntent)
            return true
        } catch (e: Exception) {
            AppLog.w(AppLog.READER, e) { "Failed to launch define activity" }
        }
    }

    // If no dictionary-specific handler, try generic ACTION_PROCESS_TEXT
    if (activities.isNotEmpty()) {
        try {
            // Use chooser so user can pick which text processing app
            val chooser = Intent.createChooser(processIntent, "Define")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            AppLog.w(AppLog.READER, e) { "Failed to launch process text chooser for define" }
        }
    }

    // Final fallback: web search
    return launchWebSearch(context, "define $text", "No dictionary app found")
}

/**
 * Launches the system "Translate" action for the given [text].
 *
 * Strategy:
 * 1. Try Google Translate intent directly.
 * 2. Try ACTION_PROCESS_TEXT and filter for translate-related activities.
 * 3. Fall back to Google Translate web as a last resort.
 *
 * Shows a Toast and returns false if no handler is available.
 */
internal fun launchTranslateAction(context: Context, text: String): Boolean {
    // Try Google Translate's direct intent
    val googleTranslateIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra("key_text_input", text)
        type = "text/plain"
        setPackage("com.google.android.apps.translate")
    }
    if (googleTranslateIntent.resolveActivity(context.packageManager) != null) {
        try {
            context.startActivity(googleTranslateIntent)
            return true
        } catch (e: Exception) {
            AppLog.w(AppLog.READER, e) { "Failed to launch Google Translate" }
        }
    }

    // Try ACTION_PROCESS_TEXT and prefer translate-related activities
    val processIntent = buildProcessTextIntent(text, readOnly = true)
    val activities = context.packageManager.queryIntentActivities(processIntent, 0)
    val translateActivity = activities.firstOrNull { resolveInfo ->
        val name = resolveInfo.activityInfo.name.lowercase()
        val packageName = resolveInfo.activityInfo.packageName.lowercase()
        name.contains("translate") || packageName.contains("translate")
    }

    if (translateActivity != null) {
        try {
            processIntent.setClassName(
                translateActivity.activityInfo.packageName,
                translateActivity.activityInfo.name
            )
            context.startActivity(processIntent)
            return true
        } catch (e: Exception) {
            AppLog.w(AppLog.READER, e) { "Failed to launch translate activity" }
        }
    }

    // Final fallback: Google Translate web
    return launchWebSearch(
        context,
        text,
        "No translation app found",
        "https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(text)}"
    )
}

/**
 * Builds an [Intent.ACTION_PROCESS_TEXT] intent for the given text.
 */
private fun buildProcessTextIntent(text: String, readOnly: Boolean): Intent {
    return Intent(Intent.ACTION_PROCESS_TEXT).apply {
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)
        type = "text/plain"
    }
}

/**
 * Fallback: opens a web search or URL.
 * Shows a Toast if even the browser cannot be opened.
 */
private fun launchWebSearch(
    context: Context,
    query: String,
    failureMessage: String,
    url: String = "https://www.google.com/search?q=${Uri.encode(query)}"
): Boolean {
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    return if (webIntent.resolveActivity(context.packageManager) != null) {
        try {
            context.startActivity(webIntent)
            true
        } catch (e: Exception) {
            AppLog.w(AppLog.READER, e) { "Failed to open web fallback" }
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
            false
        }
    } else {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        false
    }
}
