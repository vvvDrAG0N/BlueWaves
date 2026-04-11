package com.epubreader.core.model

/**
 * Data class representing the global state of the application.
 * Adding new fields here requires updating SettingsManager.updateGlobalSettings
 * and the SettingsManager.globalSettings mapping logic.
 */
data class GlobalSettings(
    val fontSize: Int = 18,
    val fontType: String = "serif",
    val theme: String = "light",
    val lineHeight: Float = 1.6f,
    val horizontalPadding: Int = 16,
    val firstTime: Boolean = true,
    val lastSeenVersionCode: Int = 0,
    val showScrubber: Boolean = false,
    val librarySort: String = "added_desc",
    val favoriteLibrary: String = "My Library",
    val bookGroups: String = "{}",
    val folderSorts: String = "{}",
    val folderOrder: String = "[]"
)

/**
 * Data class for per-book reading progress.
 * scrollIndex refers to the first visible item in the reader LazyColumn.
 */
data class BookProgress(
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastChapterHref: String? = null
)
