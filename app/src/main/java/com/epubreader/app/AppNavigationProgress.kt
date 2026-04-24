package com.epubreader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.epubreader.Screen
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.EpubBook
import com.epubreader.data.settings.SettingsManager

internal fun shouldObserveLibraryProgress(currentScreen: Screen): Boolean {
    return currentScreen == Screen.Library
}

@Composable
internal fun rememberLibraryProgressByBookId(
    settingsManager: SettingsManager,
    currentScreen: Screen,
    libraryItems: List<EpubBook>,
    lastOpenedBook: EpubBook?,
): Map<String, BookProgress> {
    val progressTargets = remember(libraryItems, lastOpenedBook, currentScreen) {
        if (shouldObserveLibraryProgress(currentScreen)) {
            buildLibraryProgressTargets(
                libraryItems = libraryItems,
                lastOpenedBook = lastOpenedBook,
            )
        } else {
            emptyList()
        }
    }
    val progressByBookId by settingsManager.observeBookProgresses(progressTargets)
        .collectAsState(initial = emptyMap())
    return progressByBookId
}
