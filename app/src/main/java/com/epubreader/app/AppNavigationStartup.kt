/**
 * AI_READ_AFTER: AppNavigation.kt
 * AI_RELEVANT_TO: [Startup Checks, First-Run Dialog, Changelog Dialog]
 * PURPOSE: Package-local helpers for app-shell startup/version evaluation.
 * AI_WARNING: Keep version and first-run behavior stable; this only computes decisions.
 */
package com.epubreader.app

import android.content.Context
import com.epubreader.core.model.GlobalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class AppShellStartupDecision(
    val detectedVersionCode: Int,
    val shouldClearFirstTime: Boolean,
    val showFirstTimeNote: Boolean,
    val changelogEntries: List<JSONObject>,
    val versionCodeToMarkSeen: Int?,
)

/**
 * Evaluates the startup state of the app shell to decide which initial UI to show.
 *
 * [AI_NOTE] Precedence Logic:
 * 1. Fresh Install (firstTime == true && no existing books): Show Welcome Note.
 * 2. Upgrade (versionCode > lastSeen): Show Changelog if entries exist.
 * 3. Normal Startup: Go straight to library.
 *
 * This function is pure and IO-bound; it does not perform writes itself.
 */
internal suspend fun evaluateAppShellStartup(
    context: Context,
    globalSettings: GlobalSettings,
): AppShellStartupDecision = withContext(Dispatchers.IO) {
    val currentVersion = readCurrentVersionCode(context)
    val hasExistingData = hasExistingLibraryData(context)

    if (globalSettings.firstTime && !hasExistingData) {
        return@withContext AppShellStartupDecision(
            detectedVersionCode = currentVersion,
            shouldClearFirstTime = false,
            showFirstTimeNote = true,
            changelogEntries = emptyList(),
            versionCodeToMarkSeen = null,
        )
    }

    if (currentVersion <= 0) {
        return@withContext AppShellStartupDecision(
            detectedVersionCode = currentVersion,
            shouldClearFirstTime = false,
            showFirstTimeNote = false,
            changelogEntries = emptyList(),
            versionCodeToMarkSeen = null,
        )
    }

    var lastSeen = globalSettings.lastSeenVersionCode
    var shouldClearFirstTime = false
    if (globalSettings.firstTime && hasExistingData) {
        shouldClearFirstTime = true
        if (lastSeen == 0) {
            lastSeen = 1
        }
    }

    if (lastSeen < currentVersion) {
        val newChanges = readPendingChangelogEntries(context, lastSeen)
        if (newChanges.isNotEmpty()) {
            return@withContext AppShellStartupDecision(
                detectedVersionCode = currentVersion,
                shouldClearFirstTime = shouldClearFirstTime,
                showFirstTimeNote = false,
                changelogEntries = newChanges,
                versionCodeToMarkSeen = null,
            )
        }

        return@withContext AppShellStartupDecision(
            detectedVersionCode = currentVersion,
            shouldClearFirstTime = shouldClearFirstTime,
            showFirstTimeNote = false,
            changelogEntries = emptyList(),
            versionCodeToMarkSeen = currentVersion,
        )
    }

    AppShellStartupDecision(
        detectedVersionCode = currentVersion,
        shouldClearFirstTime = shouldClearFirstTime,
        showFirstTimeNote = false,
        changelogEntries = emptyList(),
        versionCodeToMarkSeen = null,
    )
}

private fun readCurrentVersionCode(context: Context): Int {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    } catch (_: Exception) {
        0
    }
}

private fun hasExistingLibraryData(context: Context): Boolean {
    val booksDir = File(context.cacheDir, "books")
    return booksDir.exists() && (booksDir.listFiles()?.any { it.isDirectory } == true)
}

private fun readPendingChangelogEntries(context: Context, lastSeenVersion: Int): List<JSONObject> {
    return try {
        val jsonString = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
        val fullChangelog = JSONArray(jsonString)
        if (fullChangelog.length() > 0) {
            val latestEntry = fullChangelog.getJSONObject(0)
            if (latestEntry.getInt("versionCode") > lastSeenVersion) {
                listOf(latestEntry)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}
