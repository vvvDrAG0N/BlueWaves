/** Central DataStore facade for settings, folders, themes, and book progress. */
package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.DarkThemeId
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.isBuiltInTheme
import com.epubreader.core.model.availableThemeOptions
import com.epubreader.core.model.normalizeThemeSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SettingsManager(private val context: Context) {
    val globalSettings: Flow<GlobalSettings> = context.settingsDataStore.data
        .map { preferences -> preferences.toGlobalSettingsSnapshot() }
        .distinctUntilChanged()
        .map { snapshot -> snapshot.toGlobalSettings() }

    suspend fun updateFolderOrder(order: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.folderOrder] = order.toJsonArray().toString()
        }
    }
    suspend fun createFolder(folderName: String, sort: String = DefaultLibrarySort) {
        context.settingsDataStore.edit { preferences ->
            val trimmedName = folderName.trim()
            if (trimmedName.isEmpty() || trimmedName == DefaultLibraryName) return@edit

            val folderSortsJson = safeJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            folderSortsJson.put(trimmedName, sort)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()

            val existingOrder = safeJsonArray(preferences[SettingsPreferenceKeys.folderOrder]).toStringList()
            if (!existingOrder.contains(trimmedName)) {
                preferences[SettingsPreferenceKeys.folderOrder] =
                    (existingOrder + trimmedName).toJsonArray().toString()
            }
        }
    }

    suspend fun setLibrarySort(folderName: String?, sort: String) {
        context.settingsDataStore.edit { preferences ->
            if (folderName == null || folderName == DefaultLibraryName) {
                preferences[SettingsPreferenceKeys.librarySort] = sort
            } else {
                val currentJson = safeJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
                currentJson.put(folderName, sort)
                preferences[SettingsPreferenceKeys.folderSorts] = currentJson.toString()
            }
        }
    }

    suspend fun setFavoriteLibrary(libraryName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.favoriteLibrary] = libraryName
        }
    }

    suspend fun updateBookGroup(bookId: String, groupName: String?) {
        context.settingsDataStore.edit { preferences ->
            val currentJson = safeJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            if (groupName == null || groupName == DefaultLibraryName) {
                currentJson.remove(bookId)
            } else {
                currentJson.put(bookId, groupName)
            }
            preferences[SettingsPreferenceKeys.bookGroups] = currentJson.toString()
        }
    }

    suspend fun updateBookGroups(bookIds: Set<String>, groupName: String?) {
        if (bookIds.isEmpty()) {
            return
        }
        context.settingsDataStore.edit { preferences ->
            val currentJson = safeJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            bookIds.forEach { bookId ->
                if (groupName == null || groupName == DefaultLibraryName) {
                    currentJson.remove(bookId)
                } else {
                    currentJson.put(bookId, groupName)
                }
            }
            preferences[SettingsPreferenceKeys.bookGroups] = currentJson.toString()
        }
    }

    suspend fun renameFolder(oldName: String, newName: String) {
        context.settingsDataStore.edit { preferences ->
            val trimmedOldName = oldName.trim()
            val trimmedNewName = newName.trim()
            if (
                trimmedOldName.isEmpty() ||
                trimmedOldName == DefaultLibraryName ||
                trimmedNewName.isEmpty() ||
                trimmedNewName == trimmedOldName ||
                trimmedNewName == DefaultLibraryName
            ) {
                return@edit
            }
            val folderSortsJson = strictJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            val folderOrderJson = strictJsonArray(preferences[SettingsPreferenceKeys.folderOrder])
            val bookGroupsJson = strictJsonObject(preferences[SettingsPreferenceKeys.bookGroups])

            val existingFolders = linkedSetOf<String>()
            val sortKeys = folderSortsJson.keys()
            while (sortKeys.hasNext()) {
                existingFolders.add(sortKeys.next())
            }
            for (index in 0 until folderOrderJson.length()) {
                existingFolders.add(folderOrderJson.getString(index))
            }
            val groupKeys = bookGroupsJson.keys()
            while (groupKeys.hasNext()) {
                val key = groupKeys.next()
                val groupName = bookGroupsJson.optString(key)
                if (groupName.isNotEmpty()) {
                    existingFolders.add(groupName)
                }
            }
            preferences[SettingsPreferenceKeys.favoriteLibrary]
                ?.takeIf { it.isNotEmpty() && it != DefaultLibraryName }
                ?.let(existingFolders::add)

            if (existingFolders.any { it == trimmedNewName && it != trimmedOldName }) {
                return@edit
            }

            folderSortsJson.renameStringEntry(trimmedOldName, trimmedNewName)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()
            preferences[SettingsPreferenceKeys.folderOrder] =
                folderOrderJson.replacingEntry(trimmedOldName, trimmedNewName).toString()
            bookGroupsJson.replaceStringValues(trimmedOldName, trimmedNewName)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()
            if (preferences[SettingsPreferenceKeys.favoriteLibrary] == trimmedOldName) {
                preferences[SettingsPreferenceKeys.favoriteLibrary] = trimmedNewName
            }
        }
    }

    suspend fun deleteFolder(folderName: String) {
        context.settingsDataStore.edit { preferences ->
            val folderSortsJson = strictJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            folderSortsJson.remove(folderName)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()
            val folderOrderJson = strictJsonArray(preferences[SettingsPreferenceKeys.folderOrder])
            preferences[SettingsPreferenceKeys.folderOrder] = folderOrderJson.withoutEntry(folderName).toString()
            val bookGroupsJson = strictJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            bookGroupsJson.removeEntriesWithStringValue(folderName)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()
            if (preferences[SettingsPreferenceKeys.favoriteLibrary] == folderName) {
                preferences[SettingsPreferenceKeys.favoriteLibrary] = DefaultLibraryName
            }
        }
    }

    suspend fun deleteFolders(folderNames: Set<String>) {
        val targets = folderNames
            .map(String::trim)
            .filter { it.isNotEmpty() && it != DefaultLibraryName }
            .toSet()
        if (targets.isEmpty()) {
            return
        }
        context.settingsDataStore.edit { preferences ->
            val folderSortsJson = strictJsonObject(preferences[SettingsPreferenceKeys.folderSorts])
            targets.forEach(folderSortsJson::remove)
            preferences[SettingsPreferenceKeys.folderSorts] = folderSortsJson.toString()

            val existingOrder = strictJsonArray(preferences[SettingsPreferenceKeys.folderOrder]).toStringList()
            preferences[SettingsPreferenceKeys.folderOrder] =
                existingOrder.filterNot(targets::contains).toJsonArray().toString()

            val bookGroupsJson = strictJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            targets.forEach(bookGroupsJson::removeEntriesWithStringValue)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()

            if (targets.contains(preferences[SettingsPreferenceKeys.favoriteLibrary])) {
                preferences[SettingsPreferenceKeys.favoriteLibrary] = DefaultLibraryName
            }
        }
    }

    suspend fun setFirstTime(firstTime: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.firstTime] = firstTime
        }
    }

    fun getLastSeenVersionCode(): Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsPreferenceKeys.lastSeenVersion] ?: 0
    }

    suspend fun setLastSeenVersionCode(version: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.lastSeenVersion] = version
        }
    }

    suspend fun updateGlobalSettings(transform: (GlobalSettings) -> GlobalSettings) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences.toGlobalSettings()
            val updated = transform(current)
            preferences[SettingsPreferenceKeys.fontSize] = updated.fontSize
            preferences[SettingsPreferenceKeys.fontType] = updated.fontType
            preferences[SettingsPreferenceKeys.theme] =
                normalizeThemeSelection(updated.theme, updated.customThemes)
            preferences[SettingsPreferenceKeys.customThemes] = updated.customThemes.toCustomThemesJson()
            preferences[SettingsPreferenceKeys.lineHeight] = updated.lineHeight
            preferences[SettingsPreferenceKeys.horizontalPadding] = updated.horizontalPadding
            preferences[SettingsPreferenceKeys.showScrubber] = updated.showScrubber
            preferences[SettingsPreferenceKeys.showSystemBar] = updated.showSystemBar
            preferences[SettingsPreferenceKeys.selectableText] = updated.selectableText
            preferences[SettingsPreferenceKeys.hapticFeedback] = updated.hapticFeedback
            preferences[SettingsPreferenceKeys.allowBlankCovers] = updated.allowBlankCovers
            preferences[SettingsPreferenceKeys.targetTranslationLanguage] = updated.targetTranslationLanguage
            preferences[SettingsPreferenceKeys.showScrollToTop] = updated.showScrollToTop
            preferences[SettingsPreferenceKeys.readerStatusEnabled] = updated.readerStatusUi.isEnabled
            preferences[SettingsPreferenceKeys.readerStatusShowClock] = updated.readerStatusUi.showClock
            preferences[SettingsPreferenceKeys.readerStatusShowBattery] = updated.readerStatusUi.showBattery
            preferences[SettingsPreferenceKeys.readerStatusShowChapterProgress] = updated.readerStatusUi.showChapterProgress
            preferences[SettingsPreferenceKeys.readerStatusShowChapterNumber] = updated.readerStatusUi.showChapterNumber
        }
    }

    suspend fun updateGlobalSettings(settings: GlobalSettings) {
        updateGlobalSettings { settings }
    }

    suspend fun setActiveTheme(themeId: String) {
        context.settingsDataStore.edit { preferences ->
            val customThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            preferences[SettingsPreferenceKeys.theme] = normalizeThemeSelection(themeId, customThemes)
        }
    }

    suspend fun saveCustomTheme(theme: CustomTheme, activate: Boolean = false) {
        context.settingsDataStore.edit { preferences ->
            val trimmedName = theme.name.trim()
            val trimmedId = theme.id.trim()
            if (trimmedName.isEmpty() || trimmedId.isEmpty() || isBuiltInTheme(trimmedId)) {
                return@edit
            }

            val existingThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            val normalizedTheme = theme.copy(id = trimmedId, name = trimmedName)
            val updatedThemes = if (existingThemes.any { it.id == normalizedTheme.id }) {
                existingThemes.map { existing ->
                    if (existing.id == normalizedTheme.id) normalizedTheme else existing
                }
            } else {
                existingThemes + normalizedTheme
            }

            preferences[SettingsPreferenceKeys.customThemes] = updatedThemes.toCustomThemesJson()
            if (activate || preferences[SettingsPreferenceKeys.theme] == normalizedTheme.id) {
                preferences[SettingsPreferenceKeys.theme] = normalizedTheme.id
            }
        }
    }

    suspend fun deleteCustomTheme(themeId: String) {
        deleteCustomThemes(setOf(themeId))
    }

    suspend fun deleteCustomThemes(themeIds: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            val existingThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            val customIds = existingThemes.map { it.id }.toSet()
            val targetsToDelete = themeIds.intersect(customIds)
            if (targetsToDelete.isEmpty()) return@edit

            val allOptionsBefore = availableThemeOptions(existingThemes)
            val currentActiveId = preferences[SettingsPreferenceKeys.theme] ?: LightThemeId
            val updatedCustomThemes = existingThemes.filterNot { it.id in targetsToDelete }
            preferences[SettingsPreferenceKeys.customThemes] = updatedCustomThemes.toCustomThemesJson()
            if (currentActiveId in targetsToDelete) {
                val activeIndexBefore = allOptionsBefore.indexOfFirst { it.id == currentActiveId }
                val allOptionsAfter = availableThemeOptions(updatedCustomThemes)
                val targetIndex = activeIndexBefore - 1
                val newThemeId = if (targetIndex >= 0 && targetIndex < allOptionsAfter.size) {
                    allOptionsAfter[targetIndex].id
                } else {
                    LightThemeId
                }
                preferences[SettingsPreferenceKeys.theme] = newThemeId
            }
        }
    }

    suspend fun toggleTheme() {
        context.settingsDataStore.edit { preferences ->
            val customThemes = parseCustomThemes(preferences[SettingsPreferenceKeys.customThemes])
            val current = normalizeThemeSelection(preferences[SettingsPreferenceKeys.theme], customThemes)
            preferences[SettingsPreferenceKeys.theme] = if (current == DarkThemeId) {
                LightThemeId
            } else {
                DarkThemeId
            }
        }
    }

    suspend fun toggleShowSystemBar() {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[SettingsPreferenceKeys.showSystemBar] ?: false
            preferences[SettingsPreferenceKeys.showSystemBar] = !current
        }
    }

    suspend fun toggleSelectableText() {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[SettingsPreferenceKeys.selectableText] ?: false
            preferences[SettingsPreferenceKeys.selectableText] = !current
        }
    }

    fun getBookProgress(
        bookId: String,
        representation: BookRepresentation = BookRepresentation.EPUB,
    ): Flow<BookProgress> = context.settingsDataStore.data.map { preferences ->
        preferences.toBookProgress(bookId, representation)
    }

    fun observeBookProgresses(
        books: List<com.epubreader.core.model.EpubBook>,
    ): Flow<Map<String, BookProgress>> {
        if (books.isEmpty()) {
            return flowOf(emptyMap())
        }
        val progressTargets = books.map { it.id to it.activeRepresentation }.distinct()
        return context.settingsDataStore.data
            .map { preferences ->
                buildMap(progressTargets.size) {
                    progressTargets.forEach { (bookId, representation) ->
                        put(bookId, preferences.toBookProgress(bookId, representation))
                    }
                }
            }
            .distinctUntilChanged()
    }

    suspend fun saveBookProgress(
        bookId: String,
        progress: BookProgress,
        representation: BookRepresentation = BookRepresentation.EPUB,
    ) {
        context.settingsDataStore.edit { preferences ->
            val keys = bookProgressPreferenceKeys(bookId, representation)
            preferences[keys.scrollIndex] = progress.scrollIndex
            preferences[keys.scrollOffset] = progress.scrollOffset
            val chapterHref = progress.lastChapterHref
            if (chapterHref == null) {
                preferences.remove(keys.chapter)
            } else {
                preferences[keys.chapter] = chapterHref
            }

            if (representation == BookRepresentation.EPUB) {
                val legacyKeys = legacyBookProgressPreferenceKeys(bookId)
                preferences[legacyKeys.scrollIndex] = progress.scrollIndex
                preferences[legacyKeys.scrollOffset] = progress.scrollOffset
                if (chapterHref == null) {
                    preferences.remove(legacyKeys.chapter)
                } else {
                    preferences[legacyKeys.chapter] = chapterHref
                }
            }
        }
    }

    suspend fun deleteBookData(bookId: String) {
        context.settingsDataStore.edit { preferences ->
            BookRepresentation.entries.forEach { representation ->
                val keys = bookProgressPreferenceKeys(bookId, representation)
                preferences.remove(keys.scrollIndex)
                preferences.remove(keys.scrollOffset)
                preferences.remove(keys.chapter)
            }
            val legacyKeys = legacyBookProgressPreferenceKeys(bookId)
            preferences.remove(legacyKeys.scrollIndex)
            preferences.remove(legacyKeys.scrollOffset)
            preferences.remove(legacyKeys.chapter)

            val bookGroupsJson = safeJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            bookGroupsJson.remove(bookId)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()
        }
    }

    suspend fun deleteBooksData(bookIds: Set<String>) {
        if (bookIds.isEmpty()) {
            return
        }
        context.settingsDataStore.edit { preferences ->
            bookIds.forEach { bookId ->
                BookRepresentation.entries.forEach { representation ->
                    val keys = bookProgressPreferenceKeys(bookId, representation)
                    preferences.remove(keys.scrollIndex)
                    preferences.remove(keys.scrollOffset)
                    preferences.remove(keys.chapter)
                }
                val legacyKeys = legacyBookProgressPreferenceKeys(bookId)
                preferences.remove(legacyKeys.scrollIndex)
                preferences.remove(legacyKeys.scrollOffset)
                preferences.remove(legacyKeys.chapter)
            }

            val bookGroupsJson = safeJsonObject(preferences[SettingsPreferenceKeys.bookGroups])
            bookIds.forEach(bookGroupsJson::remove)
            preferences[SettingsPreferenceKeys.bookGroups] = bookGroupsJson.toString()
        }
    }
    suspend fun updateReaderStatusEnabled(isEnabled: Boolean) =
        context.settingsDataStore.edit { it[SettingsPreferenceKeys.readerStatusEnabled] = isEnabled }
    suspend fun updateReaderStatusShowClock(show: Boolean) =
        context.settingsDataStore.edit { it[SettingsPreferenceKeys.readerStatusShowClock] = show }
    suspend fun updateReaderStatusShowBattery(show: Boolean) =
        context.settingsDataStore.edit { it[SettingsPreferenceKeys.readerStatusShowBattery] = show }
    suspend fun updateReaderStatusShowChapterProgress(show: Boolean) =
        context.settingsDataStore.edit { it[SettingsPreferenceKeys.readerStatusShowChapterProgress] = show }
    suspend fun updateReaderStatusShowChapterNumber(show: Boolean) =
        context.settingsDataStore.edit { it[SettingsPreferenceKeys.readerStatusShowChapterNumber] = show }
    suspend fun updateReaderStatusShowMaxChapter(show: Boolean) = Unit
}
