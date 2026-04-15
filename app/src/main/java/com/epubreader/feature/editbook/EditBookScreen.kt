package com.epubreader.feature.editbook

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.epubreader.core.model.BookCoverAction
import com.epubreader.core.model.BookCoverUpdate
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SupportedCoverMimeTypes = arrayOf("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp")
private val SupportedImportMimeTypes = arrayOf("text/html", "application/xhtml+xml", "application/xml", "text/xml", "text/plain")
private val SupportedImportExtensions = setOf("html", "htm", "xhtml")

private enum class EditBookTab { DETAILS, CHAPTERS }
private enum class ChapterSelectionMode { SPECIFIC, RANGE, OUTSIDE }
private enum class ChapterDisplaySort { ASCENDING, DESCENDING }

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
            if (matchesChapterSearch(item, index + 1, chapterQuery)) {
                IndexedValue(index, item)
            } else {
                null
            }
        }
    }
    val displayedChapterItems = remember(filteredChapterItems, chapterDisplaySort) {
        if (chapterDisplaySort == ChapterDisplaySort.DESCENDING) {
            filteredChapterItems.asReversed()
        } else {
            filteredChapterItems
        }
    }
    val selectedChapterItems = remember(chapterItems, selectedChapterIds) {
        chapterItems.filter { it.id in selectedChapterIds }
    }
    val isDirty = title != book.title ||
        author != book.author ||
        coverAction != BookCoverAction.Keep ||
        chapterItems != originalChapterItems
    val canSave = title.trim().isNotBlank() && chapterItems.isNotEmpty() && !isSaving
    val hasPersistedSelection = selectedChapterItems.any(EditableChapterItem::isPersisted)
    val storedCoverModel = remember(book, allowBlankCovers) {
        book.displayCoverPath(allowBlankCovers)
            ?.takeIf { File(it).exists() }
            ?.let(::File)
    }
    val removedCoverFallbackModel = remember(book) {
        sequenceOf(
            book.originalCoverPath,
            book.currentCoverPath,
            book.coverPath,
        )
            .filterNotNull()
            .firstOrNull { path -> File(path).exists() }
            ?.let(::File)
    }
    val hasStoredCover = remember(book) {
        sequenceOf(book.currentCoverPath, book.originalCoverPath, book.coverPath)
            .filterNotNull()
            .any { path -> File(path).exists() }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(enabled = !isSaving, onClick = ::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
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
                        modifier = Modifier.testTag("edit-book-save"),
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
                Tab(
                    selected = selectedTab == EditBookTab.DETAILS,
                    onClick = { selectedTab = EditBookTab.DETAILS },
                    text = { Text("Book Details") },
                    modifier = Modifier.testTag("edit-book-tab-details"),
                )
                Tab(
                    selected = selectedTab == EditBookTab.CHAPTERS,
                    onClick = { selectedTab = EditBookTab.CHAPTERS },
                    text = { Text("Chapters") },
                    modifier = Modifier.testTag("edit-book-tab-chapters"),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                    },
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
                            selectedChapterIds = if (chapterId in selectedChapterIds) {
                                selectedChapterIds - chapterId
                            } else {
                                selectedChapterIds + chapterId
                            }
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
                chapterItems = insertChapterItems(
                    chapterItems,
                    insertPosition,
                    listOf(chapter),
                )
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

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun EditBookDetailsTab(
    book: EpubBook,
    title: String,
    author: String,
    coverModel: Any?,
    canRemoveCover: Boolean,
    chapterCount: Int,
    isSaving: Boolean,
    coverLoadInFlight: Boolean,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useStackedLayout = maxWidth < 520.dp
                    if (useStackedLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoverPreviewPane(
                                coverModel = coverModel,
                                canRemoveCover = canRemoveCover,
                                isSaving = isSaving,
                                coverLoadInFlight = coverLoadInFlight,
                                onPickCover = onPickCover,
                                onRemoveCover = onRemoveCover,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DetailsFieldColumn(
                                title = title,
                                author = author,
                                isSaving = isSaving,
                                onTitleChange = onTitleChange,
                                onAuthorChange = onAuthorChange,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            CoverPreviewPane(
                                coverModel = coverModel,
                                canRemoveCover = canRemoveCover,
                                isSaving = isSaving,
                                coverLoadInFlight = coverLoadInFlight,
                                onPickCover = onPickCover,
                                onRemoveCover = onRemoveCover,
                                modifier = Modifier.weight(0.34f),
                            )
                            DetailsFieldColumn(
                                title = title,
                                author = author,
                                isSaving = isSaving,
                                onTitleChange = onTitleChange,
                                onAuthorChange = onAuthorChange,
                                modifier = Modifier.weight(0.66f),
                            )
                        }
                    }
                }

                HorizontalDivider()

                SummaryRow("Source", book.sourceFormat.name)
                SummaryRow("Total Chapters", chapterCount.toString())
                SummaryRow("Book ID", book.id, monospace = true)
            }
        }
    }
}

@Composable
private fun EditBookChaptersTab(
    chapterItems: List<EditableChapterItem>,
    filteredChapterItems: List<IndexedValue<EditableChapterItem>>,
    selectedChapterIds: Set<String>,
    query: String,
    chapterDisplaySort: ChapterDisplaySort,
    isSaving: Boolean,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onToggleChapterSelection: (String) -> Unit,
    onToggleAllSelection: () -> Unit,
    onRequestAddText: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestSelectionDialog: () -> Unit,
    onRequestMoveSelection: () -> Unit,
    onRequestGoToChapter: () -> Unit,
    onToggleSort: () -> Unit,
) {
    val selectedCount = selectedChapterIds.size
    val toggleSelectionDescription = if (selectedCount == 0) {
        "Select all chapters"
    } else {
        "Unselect all chapters"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).testTag("edit-book-search"),
                label = { Text("Search") },
                placeholder = { Text("Index, title, or href") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                enabled = !isSaving,
            )
            ChapterActionButton(
                enabled = !isSaving && chapterItems.isNotEmpty(),
                onClick = onRequestGoToChapter,
                contentDescription = "Go to chapter",
                testTag = "edit-book-action-go-to",
                icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterStatChip(
                icon = Icons.Default.Checklist,
                value = chapterItems.size.toString(),
                label = "Total",
                modifier = Modifier.weight(1f),
            )
            ChapterStatChip(
                icon = Icons.Default.CheckCircle,
                value = selectedCount.toString(),
                label = "Selected",
                modifier = Modifier.weight(1f),
            )
        }

        ActionStrip {
            ActionGroup {
                ChapterActionButton(
                    enabled = !isSaving,
                    onClick = onRequestAddText,
                    contentDescription = "Add or import chapter",
                    testTag = "edit-book-action-add-text",
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                )
            }
            ActionGroup {
                ChapterActionButton(
                    enabled = !isSaving,
                    onClick = onRequestSelectionDialog,
                    contentDescription = "Selection tools",
                    testTag = "edit-book-action-select",
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving,
                    onClick = onToggleAllSelection,
                    contentDescription = toggleSelectionDescription,
                    testTag = "edit-book-action-toggle-all",
                    icon = {
                        Icon(
                            imageVector = if (selectedCount == 0) Icons.Default.SelectAll else Icons.Default.Deselect,
                            contentDescription = null,
                        )
                    },
                )
                ChapterActionButton(
                    enabled = !isSaving && selectedCount > 0,
                    onClick = onRequestMoveSelection,
                    contentDescription = "Move selected chapters",
                    testTag = "edit-book-action-move",
                    icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving && selectedCount == 1,
                    onClick = onRequestRename,
                    contentDescription = "Rename selected chapter",
                    testTag = "edit-book-action-rename",
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving && selectedCount > 0,
                    onClick = onRequestDelete,
                    contentDescription = "Delete selected chapters",
                    testTag = "edit-book-action-delete",
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving && chapterItems.isNotEmpty(),
                    onClick = onToggleSort,
                    contentDescription = if (chapterDisplaySort == ChapterDisplaySort.ASCENDING) {
                        "Sort chapters descending"
                    } else {
                        "Sort chapters ascending"
                    },
                    testTag = "edit-book-action-sort",
                    icon = {
                        Icon(
                            imageVector = if (chapterDisplaySort == ChapterDisplaySort.ASCENDING) {
                                Icons.Default.ArrowDownward
                            } else {
                                Icons.Default.ArrowUpward
                            },
                            contentDescription = null,
                        )
                    },
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f),
        ) {
            if (filteredChapterItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No chapters match this search.")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(filteredChapterItems, key = { it.value.id }) { indexedItem ->
                        ChapterRow(
                            position = indexedItem.index + 1,
                            chapter = indexedItem.value,
                            selected = indexedItem.value.id in selectedChapterIds,
                            onToggleSelection = { onToggleChapterSelection(indexedItem.value.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    position: Int,
    chapter: EditableChapterItem,
    selected: Boolean,
    onToggleSelection: () -> Unit,
) {
    Surface(
        onClick = onToggleSelection,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleSelection,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) "Deselect" else "Select",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$position.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = when (chapter.source) {
                        EditableChapterSource.EXISTING -> chapter.href.orEmpty()
                        EditableChapterSource.NEW_TEXT -> "Draft text chapter"
                        EditableChapterSource.IMPORTED_HTML -> "Imported HTML/XHTML"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CoverPreviewPane(
    coverModel: Any?,
    canRemoveCover: Boolean,
    isSaving: Boolean,
    coverLoadInFlight: Boolean,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = "Cover preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text("No cover", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoverActionButton(
                enabled = !isSaving && !coverLoadInFlight && canRemoveCover,
                onClick = onRemoveCover,
                contentDescription = "Remove current cover",
                testTag = "edit-book-cover-remove",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            CoverActionButton(
                enabled = !isSaving && !coverLoadInFlight,
                onClick = onPickCover,
                contentDescription = "Replace current cover",
                testTag = "edit-book-cover-pick",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                if (coverLoadInFlight) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsFieldColumn(
    title: String,
    author: String,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth().testTag("edit-book-title"),
            label = { Text("Title") },
            singleLine = true,
            enabled = !isSaving,
        )
        OutlinedTextField(
            value = author,
            onValueChange = onAuthorChange,
            modifier = Modifier.fillMaxWidth().testTag("edit-book-author"),
            label = { Text("Author") },
            singleLine = true,
            enabled = !isSaving,
        )
    }
}

@Composable
private fun CoverActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (enabled) {
            containerColor
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (enabled) {
            contentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(36.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun ChapterStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("$value $label", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActionGroup(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ChapterActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
private fun AddTextOrImportChapterDialog(
    chapterCount: Int,
    defaultPosition: Int,
    onDismiss: () -> Unit,
    onAddChapter: (EditableChapterItem, Int) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf(defaultPosition.toString()) }
    var importedMarkup by rememberSaveable { mutableStateOf<String?>(null) }
    var importedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var importLoadInFlight by remember { mutableStateOf(false) }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val parsedPosition = position.toIntOrNull()?.coerceIn(1, chapterCount + 1)
    val isImportedMode = importedMarkup != null
    val canAdd = title.trim().isNotBlank() &&
        parsedPosition != null &&
        (isImportedMode || body.trim().isNotBlank())

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            importLoadInFlight = true
            val importedChapter = withContext(Dispatchers.IO) { loadImportedChapterDraft(context, uri) }
            importLoadInFlight = false
            val htmlContent = importedChapter?.content as? com.epubreader.core.model.BookNewChapterContent.HtmlDocument
            if (importedChapter != null && htmlContent != null) {
                title = importedChapter.title
                importedMarkup = htmlContent.markup
                importedFileName = htmlContent.fileNameHint
                body = ""
                importError = null
            } else {
                importError = "Pick an HTML or XHTML file."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Add Chapter") },
        text = {
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        IconButton(
                            enabled = !importLoadInFlight,
                            onClick = { importPicker.launch(SupportedImportMimeTypes) },
                            modifier = Modifier.testTag("edit-book-action-import-html"),
                        ) {
                            if (importLoadInFlight) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Language, contentDescription = "Import HTML XHTML")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("edit-book-add-title"),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    enabled = !isImportedMode,
                    label = { Text("Text") },
                    placeholder = {
                        Text(
                            if (isImportedMode) {
                                "HTML/XHTML imported. Text is disabled."
                            } else {
                                "Write chapter text"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("edit-book-add-body"),
                )
                importError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it.filter(Char::isDigit) },
                    label = { Text("Insert index") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit-book-add-position"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    parsedPosition?.let { insertPosition ->
                        val chapter = if (isImportedMode) {
                            buildHtmlDraftChapter(
                                title = title,
                                markup = importedMarkup.orEmpty(),
                                fileNameHint = importedFileName,
                            )
                        } else {
                            buildTextDraftChapter(title, body)
                        }
                        onAddChapter(chapter, insertPosition)
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChapterSelectionDialog(
    chapterCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (ChapterSelectionMode, Int, Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var mode by rememberSaveable { mutableStateOf(ChapterSelectionMode.SPECIFIC) }
    var first by rememberSaveable { mutableStateOf("1") }
    var second by rememberSaveable { mutableStateOf(if (chapterCount > 0) chapterCount.toString() else "1") }
    val parsedFirst = if (chapterCount > 0) first.toIntOrNull()?.coerceIn(1, chapterCount) else null
    val parsedSecond = if (chapterCount > 0) second.toIntOrNull()?.coerceIn(1, chapterCount) else null
    val needsSecondIndex = mode != ChapterSelectionMode.SPECIFIC
    val canConfirm = chapterCount > 0 && parsedFirst != null && (!needsSecondIndex || parsedSecond != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Selection") },
        text = {
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectionModeCard(
                        text = "●",
                        selected = mode == ChapterSelectionMode.SPECIFIC,
                        onClick = { mode = ChapterSelectionMode.SPECIFIC },
                        modifier = Modifier.weight(1f),
                    )
                    SelectionModeCard(
                        text = "●────●",
                        selected = mode == ChapterSelectionMode.RANGE,
                        onClick = { mode = ChapterSelectionMode.RANGE },
                        modifier = Modifier.weight(1f),
                    )
                    SelectionModeCard(
                        text = "─●  ●─",
                        selected = mode == ChapterSelectionMode.OUTSIDE,
                        onClick = { mode = ChapterSelectionMode.OUTSIDE },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it.filter(Char::isDigit) },
                        label = { Text("Index 1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    if (needsSecondIndex) {
                        OutlinedTextField(
                            value = second,
                            onValueChange = { second = it.filter(Char::isDigit) },
                            label = { Text("Index 2") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (parsedFirst != null) {
                        onConfirm(mode, parsedFirst, parsedSecond ?: parsedFirst)
                    }
                },
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text("Cancel Select")
            }
        },
    )
}

@Composable
private fun SelectionModeCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (selected) 6.dp else 0.dp,
        shadowElevation = if (selected) 10.dp else 0.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PositionDialog(
    title: String,
    label: String,
    chapterCount: Int,
    defaultPosition: Int,
    maxPosition: Int = chapterCount + 1,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var position by rememberSaveable { mutableStateOf(defaultPosition.toString()) }
    val parsedPosition = position.toIntOrNull()?.coerceIn(1, maxPosition.coerceAtLeast(1))

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                },
            ) {
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it.filter(Char::isDigit) },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedPosition != null,
                onClick = {
                    parsedPosition?.let(onConfirm)
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RenameChapterDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var title by rememberSaveable(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Rename Chapter") },
        text = {
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                },
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotBlank(),
                onClick = { onConfirm(title) },
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SimpleConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val isDestructive = confirmLabel == "Delete" || confirmLabel == "Discard"
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text(dismissLabel)
            }
        },
    )
}

private fun loadCoverUpdate(context: Context, uri: Uri): BookCoverUpdate? {
    val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    if (mimeType !in SupportedCoverMimeTypes) return null
    val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "cover"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return BookCoverUpdate(fileName = fileName, mimeType = mimeType, bytes = bytes)
}

private fun loadImportedChapterDraft(context: Context, uri: Uri): EditableChapterItem? {
    val fileName = queryDisplayName(context, uri)
    val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (mimeType !in SupportedImportMimeTypes && extension !in SupportedImportExtensions) {
        return null
    }
    val markup = context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    } ?: return null
    return buildHtmlDraftChapter(
        title = inferImportedChapterTitle(fileName, markup),
        markup = markup,
        fileNameHint = fileName,
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex != -1) cursor.getString(columnIndex) else null
        } else {
            null
        }
    }
}
