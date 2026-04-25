package com.epubreader.feature.editbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.ui.FeatureLegoPlugin
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditBookRoute(
    val bookId: String,
)

data class EditBookDependencies(
    val parser: EpubParser,
    val settingsManager: SettingsManager,
)

sealed interface EditBookEvent {
    data object Back : EditBookEvent
    data class Saved(val bookId: String) : EditBookEvent
}

object EditBookLegoPlugin : FeatureLegoPlugin<EditBookRoute, EditBookDependencies, EditBookEvent> {
    @Composable
    override fun Render(
        route: EditBookRoute,
        dependencies: EditBookDependencies,
        onEvent: (EditBookEvent) -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        val globalSettings by dependencies.settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
        var screenState by remember(route.bookId) { mutableStateOf<EditBookScreenState>(EditBookScreenState.Loading) }
        var isSaving by remember(route.bookId) { mutableStateOf(false) }
        var errorMessage by remember(route.bookId) { mutableStateOf<String?>(null) }

        LaunchedEffect(route.bookId) {
            screenState = loadEditBookScreenState(route, dependencies)
            isSaving = false
            errorMessage = null
        }

        when (val state = screenState) {
            EditBookScreenState.Loading -> {
                EditBookPluginStatusScreen(
                    title = "Edit Book",
                    message = "Loading book...",
                    showProgress = true,
                    onBack = { onEvent(EditBookEvent.Back) },
                )
            }

            is EditBookScreenState.Unavailable -> {
                EditBookPluginStatusScreen(
                    title = "Edit Book",
                    message = state.message,
                    showProgress = false,
                    onBack = { onEvent(EditBookEvent.Back) },
                )
            }

            is EditBookScreenState.Ready -> {
                EditBookScreen(
                    book = state.book,
                    allowBlankCovers = globalSettings.allowBlankCovers,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                    onDismissError = { errorMessage = null },
                    onBack = { onEvent(EditBookEvent.Back) },
                    onSave = onSave@{ request ->
                        if (isSaving) {
                            return@onSave
                        }
                        scope.launch {
                            isSaving = true
                            when (val result = saveEditBookChanges(dependencies, state.book, request)) {
                                is EditBookSaveResult.Failed -> {
                                    errorMessage = result.message
                                    isSaving = false
                                }

                                is EditBookSaveResult.Saved -> {
                                    screenState = EditBookScreenState.Ready(result.book)
                                    errorMessage = null
                                    isSaving = false
                                    onEvent(EditBookEvent.Saved(result.book.id))
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

private sealed interface EditBookScreenState {
    data object Loading : EditBookScreenState
    data class Ready(val book: EpubBook) : EditBookScreenState
    data class Unavailable(val message: String) : EditBookScreenState
}

private sealed interface EditBookSaveResult {
    data class Saved(val book: EpubBook) : EditBookSaveResult
    data class Failed(val message: String) : EditBookSaveResult
}

private suspend fun loadEditBookScreenState(
    route: EditBookRoute,
    dependencies: EditBookDependencies,
): EditBookScreenState {
    val book = withContext(Dispatchers.IO) {
        dependencies.parser.loadBookById(route.bookId)
    } ?: return EditBookScreenState.Unavailable("This book is no longer available.")

    if (book.sourceFormat != BookFormat.EPUB) {
        return EditBookScreenState.Unavailable("Only EPUB books can be edited right now.")
    }

    return EditBookScreenState.Ready(book)
}

private suspend fun saveEditBookChanges(
    dependencies: EditBookDependencies,
    book: EpubBook,
    request: BookEditRequest,
): EditBookSaveResult {
    if (book.sourceFormat != BookFormat.EPUB) {
        return EditBookSaveResult.Failed("Only EPUB books can be edited right now.")
    }

    return try {
        val existingProgress = dependencies.settingsManager
            .getBookProgress(book.id, BookRepresentation.EPUB)
            .first()
        val updatedBook = withContext(Dispatchers.IO) {
            dependencies.parser.editBook(book, request)
        } ?: return EditBookSaveResult.Failed("Couldn't save changes to this book.")

        val repairedProgress = repairEditBookProgress(
            previousProgress = existingProgress,
            originalSpineHrefs = book.spineHrefs,
            updatedSpineHrefs = updatedBook.spineHrefs,
        )
        dependencies.settingsManager.saveBookProgress(
            bookId = book.id,
            progress = repairedProgress,
            representation = BookRepresentation.EPUB,
        )
        EditBookSaveResult.Saved(updatedBook)
    } catch (error: Exception) {
        if (error is CancellationException) {
            throw error
        }
        EditBookSaveResult.Failed("Couldn't save changes to this book.")
    }
}

internal fun repairEditBookProgress(
    previousProgress: BookProgress,
    originalSpineHrefs: List<String>,
    updatedSpineHrefs: List<String>,
): BookProgress {
    val updatedHrefs = updatedSpineHrefs
        .map(::normalizeEditBookProgressHref)
        .filter(String::isNotBlank)
    if (updatedHrefs.isEmpty()) {
        return BookProgress()
    }

    val previousHref = normalizeEditBookProgressHref(previousProgress.lastChapterHref)
    if (previousHref.isBlank()) {
        return previousProgress
    }

    val existingMatch = updatedHrefs.firstOrNull { it == previousHref }
    if (existingMatch != null) {
        return previousProgress.copy(lastChapterHref = existingMatch)
    }

    val originalHrefs = originalSpineHrefs
        .map(::normalizeEditBookProgressHref)
        .filter(String::isNotBlank)
    val originalIndex = originalHrefs.indexOf(previousHref)

    if (originalIndex != -1) {
        for (index in originalIndex until originalHrefs.size) {
            val candidate = originalHrefs[index]
            if (candidate in updatedHrefs) {
                return BookProgress(lastChapterHref = candidate)
            }
        }
        for (index in (originalIndex - 1) downTo 0) {
            val candidate = originalHrefs[index]
            if (candidate in updatedHrefs) {
                return BookProgress(lastChapterHref = candidate)
            }
        }
    }

    return BookProgress(lastChapterHref = updatedHrefs.first())
}

private fun normalizeEditBookProgressHref(rawHref: String?): String {
    return rawHref
        .orEmpty()
        .substringBefore("#")
        .removePrefix("/")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBookPluginStatusScreen(
    title: String,
    message: String,
    showProgress: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = getStaticWindowInsets(),
        topBar = {
            TopAppBar(
                windowInsets = getStaticWindowInsets(),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showProgress) {
                    CircularProgressIndicator()
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (!showProgress) {
                    TextButton(onClick = onBack) {
                        Text("Back to Library")
                    }
                }
            }
        }
    }
}
