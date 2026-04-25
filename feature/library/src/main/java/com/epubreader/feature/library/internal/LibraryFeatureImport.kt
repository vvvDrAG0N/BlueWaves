package com.epubreader.feature.library.internal

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun rememberBookImportLauncher(
    books: List<EpubBook>,
    context: Context,
    parser: EpubParser,
    settingsManager: SettingsManager,
    selectedFolderName: String,
    bookGroups: Map<String, String>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onImportStateChange: (Boolean) -> Unit,
    onBookImported: (EpubBook) -> Unit,
): (Array<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                onImportStateChange(true)
                try {
                    when (
                        val result = importBookIntoLibrary(
                            books = books,
                            context = context,
                            uri = selectedUri,
                            parser = parser,
                            settingsManager = settingsManager,
                            selectedFolderName = selectedFolderName,
                            bookGroups = bookGroups,
                        )
                    ) {
                        is ImportBookResult.Duplicate -> {
                            snackbarHostState.showSnackbar(
                                "This book is already in your library (${result.folderName}).",
                            )
                        }
                        is ImportBookResult.Imported -> {
                            onBookImported(result.book)
                        }
                        is ImportBookResult.Failed -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) {
                        throw error
                    }
                    snackbarHostState.showSnackbar("Couldn't import this book.")
                } finally {
                    onImportStateChange(false)
                }
            }
        }
    }

    return remember(launcher) { { mimeTypes -> launcher.launch(mimeTypes) } }
}
