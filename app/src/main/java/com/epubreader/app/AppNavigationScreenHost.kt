package com.epubreader.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.epubreader.Screen
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.editbook.EditBookScreen
import com.epubreader.feature.reader.ReaderScreen
import com.epubreader.feature.settings.SettingsScreen

@Composable
internal fun AppNavigationScreenHost(
    currentScreen: Screen,
    editingBook: EpubBook?,
    selectedBook: EpubBook?,
    settingsManager: SettingsManager,
    globalSettings: GlobalSettings,
    parser: EpubParser,
    libraryScreenState: LibraryScreenState,
    libraryScreenActions: LibraryScreenActions,
    selectionBarState: BookSelectionActionBarState,
    selectionBarActions: BookSelectionActionBarActions,
    dialogState: LibraryDialogState,
    dialogActions: LibraryDialogActions,
    editBookSaveInFlight: Boolean,
    editBookErrorMessage: String?,
    onEditBookErrorDismiss: () -> Unit,
    onExitEditBook: () -> Unit,
    onSaveEditBook: (EpubBook) -> ((com.epubreader.core.model.BookEditRequest) -> Unit),
    onGoToLibrary: () -> Unit,
    onClearFolderSelection: () -> Unit,
    onCloseDrawer: () -> Unit,
    onClearBookSelection: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "ScreenTransition",
        ) { screen ->
            when (screen) {
                Screen.Settings -> SettingsScreen(
                    settingsManager = settingsManager,
                    onBack = onGoToLibrary,
                )
                Screen.EditBook -> editingBook?.let { book ->
                    EditBookScreen(
                        book = book,
                        allowBlankCovers = globalSettings.allowBlankCovers,
                        isSaving = editBookSaveInFlight,
                        errorMessage = editBookErrorMessage,
                        onDismissError = onEditBookErrorDismiss,
                        onBack = onExitEditBook,
                        onSave = onSaveEditBook(book),
                    )
                }
                Screen.Reader -> selectedBook?.let { book ->
                    ReaderScreen(
                        book = book,
                        settingsManager = settingsManager,
                        parser = parser,
                        onBack = onGoToLibrary,
                    )
                }
                Screen.Library -> LibraryScreen(
                    state = libraryScreenState,
                    actions = libraryScreenActions,
                )
            }
        }

        BookSelectionActionBar(
            state = selectionBarState,
            actions = selectionBarActions,
        )

        LibraryDialogHost(
            state = dialogState,
            actions = dialogActions,
        )
    }

    val shellBackAction = resolveShellBackAction(
        currentScreen = currentScreen,
        isDrawerOpen = libraryScreenState.drawerState.isOpen,
        isFolderSelectionMode = libraryScreenState.folderDrawer.isFolderSelectionMode,
        isBookSelectionMode = libraryScreenState.selection.isBookSelectionMode,
    )

    BackHandler(enabled = shellBackAction != null) {
        when (shellBackAction) {
            ShellBackAction.ClearFolderSelection -> onClearFolderSelection()
            ShellBackAction.CloseDrawer -> onCloseDrawer()
            ShellBackAction.ClearBookSelection -> onClearBookSelection()
            ShellBackAction.GoToLibrary -> onGoToLibrary()
            null -> Unit
        }
    }
}
