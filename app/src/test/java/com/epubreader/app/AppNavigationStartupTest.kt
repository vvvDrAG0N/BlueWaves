package com.epubreader.app

import com.epubreader.core.model.GlobalSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppNavigationStartupTest {

    @Test
    fun firstLaunchWithoutLibrary_showsWelcome() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        clearBooksDir(context)

        val decision = evaluateAppShellStartup(
            context = context,
            globalSettings = GlobalSettings(firstTime = true, lastSeenVersionCode = 0),
        )

        assertTrue(decision.showFirstTimeNote)
        assertFalse(decision.shouldClearFirstTime)
        assertTrue(decision.changelogEntries.isEmpty())
        assertNull(decision.versionCodeToMarkSeen)
    }

    @Test
    fun existingLibrary_clearsFirstTimeAndSkipsWelcome() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        createBookDir(context, "book-1")

        val decision = evaluateAppShellStartup(
            context = context,
            globalSettings = GlobalSettings(firstTime = true, lastSeenVersionCode = 0),
        )

        assertFalse(decision.showFirstTimeNote)
        assertTrue(decision.shouldClearFirstTime)
        assertTrue(decision.changelogEntries.isNotEmpty())
        assertNull(decision.versionCodeToMarkSeen)
    }

    @Test
    fun versionBump_returnsPendingChangelogEntries() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        clearBooksDir(context)

        val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        val decision = evaluateAppShellStartup(
            context = context,
            globalSettings = GlobalSettings(firstTime = false, lastSeenVersionCode = currentVersion - 2),
        )

        assertFalse(decision.showFirstTimeNote)
        assertFalse(decision.shouldClearFirstTime)
        assertTrue(decision.changelogEntries.isNotEmpty())
        assertEquals(currentVersion, decision.detectedVersionCode)
        assertNull(decision.versionCodeToMarkSeen)
    }

    @Test
    fun noMatchingChangelog_marksVersionSeen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        clearBooksDir(context)
        val latestChangelogVersion = readHighestChangelogVersion(context)
        val missingChangelogVersion = latestChangelogVersion + 1
        setVersionCode(context, missingChangelogVersion)

        val decision = evaluateAppShellStartup(
            context = context,
            globalSettings = GlobalSettings(firstTime = false, lastSeenVersionCode = latestChangelogVersion),
        )

        assertFalse(decision.showFirstTimeNote)
        assertTrue(decision.changelogEntries.isEmpty())
        assertEquals(missingChangelogVersion, decision.detectedVersionCode)
        assertEquals(missingChangelogVersion, decision.versionCodeToMarkSeen)
    }

    @Test
    fun upToDateInstall_skipsDialogsAndVersionWrites() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        clearBooksDir(context)

        val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        val decision = evaluateAppShellStartup(
            context = context,
            globalSettings = GlobalSettings(firstTime = false, lastSeenVersionCode = currentVersion),
        )

        assertFalse(decision.showFirstTimeNote)
        assertFalse(decision.shouldClearFirstTime)
        assertTrue(decision.changelogEntries.isEmpty())
        assertEquals(currentVersion, decision.detectedVersionCode)
        assertNull(decision.versionCodeToMarkSeen)
    }

    private fun clearBooksDir(context: android.content.Context) {
        context.cacheDir.resolve("books").deleteRecursively()
    }

    private fun createBookDir(context: android.content.Context, name: String) {
        val booksDir = context.cacheDir.resolve("books")
        booksDir.mkdirs()
        booksDir.resolve(name).mkdirs()
    }

    private fun readHighestChangelogVersion(context: android.content.Context): Int {
        val changelog = org.json.JSONArray(
            context.assets.open("changelog.json").bufferedReader().use { it.readText() }
        )
        var highestVersion = 0
        for (index in 0 until changelog.length()) {
            highestVersion = maxOf(highestVersion, changelog.getJSONObject(index).getInt("versionCode"))
        }
        return highestVersion
    }

    @Suppress("DEPRECATION")
    private fun setVersionCode(context: android.content.Context, versionCode: Int) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionCode = versionCode
        packageInfo.longVersionCode = versionCode.toLong()
        shadowOf(context.packageManager).installPackage(packageInfo)
    }
}
