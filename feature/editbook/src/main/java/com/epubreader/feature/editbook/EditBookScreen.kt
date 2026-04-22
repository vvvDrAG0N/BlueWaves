package com.epubreader.feature.editbook

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BookCoverAction
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.EpubBook
import com.epubreader.core.ui.getStaticWindowInsets
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val SupportedCoverMimeTypes = arrayOf("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp")
internal val SupportedImportMimeTypes = arrayOf("text/html", "application/xhtml+xml", "application/xml", "text/xml", "text/plain")
internal val SupportedImportExtensions = setOf("html", "htm", "xhtml")

private enum class EditBookTab { DETAILS, CHAPTERS }
internal enum class ChapterSelectionMode { SPECIFIC, RANGE, OUTSIDE }
internal enum class ChapterDisplaySort { ASCENDING, DESCENDING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    book: EpubBook,
    allowBlankCovers: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onSave: (BookEditRequest) -> Unit,
) {
    val originalChapterItems = remember(book.id) { buildEditableChapterItems(book) }
    var selectedTab by rememberSaveable(book.id) { mutableStateOf(EditBookTab.DETAILS) }
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var author by rememberSaveable(book.id) { mutableStateOf(book.author) }
    var coverAction by remember(book.id) { mutableStateOf<BookCoverAction>(BookCoverAction.Keep) }
    var chapterItems by remember(book.id) { mutableStateOf(originalChapterItems) }
    var selectedChapterIds by remember(book.id) { mutableStateOf<Set<String>>(emptySet()) }
    var chapterQuery by rememberSaveable(book.id) { mutableStateOf("") }
    var chapterDisplaySort by rememberSaveable(book.id) { mutableStateOf(ChapterDisplaySort.ASCENDING) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showMoveSelectionDialog by remember { mutableStateOf(false) }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showGoToChapterDialog by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var pendingInsertPosition by remember { mutableIntStateOf(0) }
    var pendingGoToChapterIndex by remember { mutableIntStateOf(0) }
    var coverLoadInFlight by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val chapterListState = rememberLazyListState()
    val filteredChapterItems = remember(chapterItems, chapterQuery) {
        chapterItems.mapIndexedNotNull { index, item ->
            if (matchesChapterSearch(item, index + 1, chapterQuery)) IndexedValue(index, item) else null
        }
    }
    val displayedChapterItems = remember(filteredChapterItems, chapterDisplaySort) {
        if (chapterDisplaySort == ChapterDisplaySort.DESCENDING) filteredChapterItems.asReversed() else filteredChapterItems
    }
    val selectedChapterItems = remember(chapterItems, selectedChapterIds) {
        chapterItems.filter { it.id in selectedChapterIds }
    }
    val isDirty = title != book.title || author != book.author || coverAction != BookCoverAction.Keep || chapterItems != originalChapterItems
    val canSave = title.trim().isNotBlank() && chapterItems.isNotEmpty() && !isSaving
    val hasPersistedSelection = selectedChapterItems.any(EditableChapterItem::isPersisted)
    val storedCoverModel = remember(book, allowBlankCovers) {
        book.displayCoverPath(allowBlankCovers)?.takeIf { File(it).exists() }?.let(::File)
    }
    val removedCoverFallbackModel = remember(book) {
        sequenceOf(book.originalCoverPath, book.currentCoverPath, book.coverPath)
            .filterNotNull()
            .firstOrNull { path -> File(path).exists() }
            ?.let(::File)
    }
    val hasStoredCover = remember(book) {
        sequenceOf(book.currentCoverPath, book.originalCoverPath, book.coverPath).filterNotNull().any { path -> File(path).exists() }
    }
    val coverModel: Any? = when (val action = coverAction) {
        BookCoverAction.Keep -> storedCoverModel
        BookCoverAction.Remove -> if (allowBlankCovers) null else removedCoverFallbackModel
        is BookCoverAction.Replace -> action.cover.bytes
    }

    fun defaultInsertPosition(): Int {
        if (selectedChapterIds.isEmpty()) return chapterItems.size + 1
        val lastSelectedIndex = chapterItems.indexOfLast { it.id in selectedChapterIds }
        return if (lastSelectedIndex == -1) chapterItems.size + 1 else lastSelectedIndex + 2
    }

    fun requestExit() {
        if (isSaving) return
        if (isDirty) showUnsavedChangesDialog = true else onBack()
    }

    fun deleteSelection() {
        if (selectedChapterIds.isEmpty()) return
        val remainingItems = chapterItems.filterNot { it.id in selectedChapterIds }
        if (remainingItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("A book needs at least one chapter.") }
            return
        }
        chapterItems = remainingItems
        selectedChapterIds = emptySet()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }

    LaunchedEffect(pendingGoToChapterIndex, chapterQuery, displayedChapterItems) {
        if (pendingGoToChapterIndex > 0 && chapterQuery.isBlank()) {
            val targetListIndex = displayedChapterItems.indexOfFirst { indexedItem ->
                indexedItem.index == pendingGoToChapterIndex - 1
            }
            if (targetListIndex != -1) {
                chapterListState.animateScrollToItem(targetListIndex)
            }
            pendingGoToChapterIndex = 0
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            coverLoadInFlight = true
            val coverUpdate = withContext(Dispatchers.IO) { loadCoverUpdate(context, uri) }
            coverLoadInFlight = false
            if (coverUpdate != null) {
                coverAction = BookCoverAction.Replace(coverUpdate)
            } else {
                snackbarHostState.showSnackbar("Pick a PNG, JPG, GIF, or WEBP image.")
            }
        }
    }

    BackHandler(enabled = true, onBack = ::requestExit)

    Scaffold(
        contentWindowInsets = getStaticWindowInsets(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = getStaticWindowInsets(),
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(enabled = !isSaving, onClick = ::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
                        modifier = Modifier.testTag("edit-book-save"),
                        onClick = {
                            onSave(
                                BookEditRequest(
                                    title = title.trim(),
                                    author = author.trim(),
                                    coverAction = coverAction,
                                    chapters = chapterItems.map(EditableChapterItem::toChapterEdit),
                                ),
                            )
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(selected = selectedTab == EditBookTab.DETAILS, onClick = { selectedTab = EditBookTab.DETAILS }, text = { Text("Book Details") })
                Tab(selected = selectedTab == EditBookTab.CHAPTERS, onClick = { selectedTab = EditBookTab.CHAPTERS }, text = { Text("Chapters") })
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus(force = true) }) },
            ) {
                when (selectedTab) {
                    EditBookTab.DETAILS -> EditBookDetailsTab(
                        book = book,
                        title = title,
                        author = author,
                        coverModel = coverModel,
                        canRemoveCover = !isSaving && (coverAction != BookCoverAction.Remove) && (hasStoredCover || coverModel != null),
                        chapterCount = chapterItems.size,
                        isSaving = isSaving,
                        coverLoadInFlight = coverLoadInFlight,
                        onTitleChange = { title = it },
                        onAuthorChange = { author = it },
                        onPickCover = { coverPicker.launch(SupportedCoverMimeTypes) },
                        onRemoveCover = { coverAction = BookCoverAction.Remove },
                    )
                    EditBookTab.CHAPTERS -> EditBookChaptersTab(
                        chapterItems = chapterItems,
                        filteredChapterItems = displayedChapterItems,
                        selectedChapterIds = selectedChapterIds,
                        query = chapterQuery,
                        chapterDisplaySort = chapterDisplaySort,
                        isSaving = isSaving,
                        listState = chapterListState,
                        onQueryChange = { chapterQuery = it },
                        onToggleChapterSelection = { chapterId ->
                            selectedChapterIds = if (chapterId in selectedChapterIds) selectedChapterIds - chapterId else selectedChapterIds + chapterId
                        },
                        onToggleAllSelection = {
                            selectedChapterIds = if (selectedChapterIds.isEmpty()) {
                                chapterItems.mapTo(linkedSetOf(), EditableChapterItem::id)
                            } else {
                                emptySet()
                            }
                        },
                        onRequestAddText = {
                            pendingInsertPosition = defaultInsertPosition()
                            showAddTextDialog = true
                        },
                        onRequestRename = { renameTargetId = selectedChapterItems.singleOrNull()?.id },
                        onRequestDelete = {
                            if (selectedChapterIds.isEmpty()) return@EditBookChaptersTab
                            if (hasPersistedSelection) showDeleteSelectionDialog = true else deleteSelection()
                        },
                        onRequestSelectionDialog = { showSelectionDialog = true },
                        onRequestMoveSelection = { showMoveSelectionDialog = true },
                        onRequestGoToChapter = { showGoToChapterDialog = true },
                        onToggleSort = {
                            chapterDisplaySort = if (chapterDisplaySort == ChapterDisplaySort.ASCENDING) {
                                ChapterDisplaySort.DESCENDING
                            } else {
                                ChapterDisplaySort.ASCENDING
                            }
                        },
                    )
                }
            }

            if (isSaving) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Saving EPUB changes...")
                }
            }
        }
    }

    if (showUnsavedChangesDialog) {
        SimpleConfirmDialog(
            title = "Discard Changes?",
            message = "You have unsaved Edit Book changes.",
            confirmLabel = "Discard",
            dismissLabel = "Keep Editing",
            onDismiss = { showUnsavedChangesDialog = false },
            onConfirm = {
                showUnsavedChangesDialog = false
                onBack()
            },
        )
    }

    if (showDeleteSelectionDialog) {
        SimpleConfirmDialog(
            title = "Delete Selected Chapters?",
            message = "This will remove ${selectedChapterIds.size} selected chapter(s) from the EPUB.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onDismiss = { showDeleteSelectionDialog = false },
            onConfirm = {
                showDeleteSelectionDialog = false
                deleteSelection()
            },
        )
    }

    if (showSelectionDialog) {
        ChapterSelectionDialog(
            chapterCount = chapterItems.size,
            onDismiss = { showSelectionDialog = false },
            onConfirm = { mode, first, second ->
                selectedChapterIds = when (mode) {
                    ChapterSelectionMode.SPECIFIC -> selectSpecificChapter(chapterItems, first)
                    ChapterSelectionMode.RANGE -> selectChapterRange(chapterItems, first, second)
                    ChapterSelectionMode.OUTSIDE -> selectOutsideChapterRange(chapterItems, first, second)
                }
                showSelectionDialog = false
            },
        )
    }

    if (showAddTextDialog) {
        AddTextOrImportChapterDialog(
            chapterCount = chapterItems.size,
            defaultPosition = pendingInsertPosition.coerceAtLeast(1),
            onDismiss = { showAddTextDialog = false },
            onAddChapter = { chapter, insertPosition ->
                chapterItems = insertChapterItems(chapterItems, insertPosition, listOf(chapter))
                selectedTab = EditBookTab.CHAPTERS
                selectedChapterIds = linkedSetOf(chapter.id)
                showAddTextDialog = false
            },
        )
    }

    if (showGoToChapterDialog) {
        PositionDialog(
            title = "Go To Chapter",
            label = "Index",
            chapterCount = chapterItems.size,
            defaultPosition = 1,
            maxPosition = chapterItems.size,
            confirmLabel = "Go",
            onDismiss = { showGoToChapterDialog = false },
            onConfirm = { position ->
                pendingGoToChapterIndex = position
                chapterQuery = ""
                showGoToChapterDialog = false
            },
        )
    }

    if (showMoveSelectionDialog) {
        PositionDialog(
            title = "Move Selected Chapters",
            label = "New start position",
            chapterCount = chapterItems.size,
            defaultPosition = 1,
            confirmLabel = "Move",
            onDismiss = { showMoveSelectionDialog = false },
            onConfirm = { position ->
                chapterItems = moveSelectedChapterItems(chapterItems, selectedChapterIds, position)
                showMoveSelectionDialog = false
            },
        )
    }

    renameTargetId?.let { targetId ->
        chapterItems.firstOrNull { it.id == targetId }?.let { target ->
            RenameChapterDialog(
                currentTitle = target.title,
                onDismiss = { renameTargetId = null },
                onConfirm = { newTitle ->
                    chapterItems = chapterItems.map { item ->
                        if (item.id == targetId) item.copy(title = newTitle.trim()) else item
                    }
                    renameTargetId = null
                },
            )
        }
    }
}
