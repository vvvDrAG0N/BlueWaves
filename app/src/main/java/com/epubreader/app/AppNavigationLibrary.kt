package com.epubreader.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.epubreader.core.ui.BookItem
import com.epubreader.core.ui.RecentlyViewedStrip

/**
 * AI_LOAD_STRATEGY
 * - Read this file for library rendering only: drawer, top bar, grid, and bottom selection bar.
 * - Read `AppNavigationContracts.kt` if you need the state/action contract map.
 * - State mutation should usually remain in `AppNavigation.kt`; this file should stay mostly presentational.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
internal fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryScreenActions,
) {
    // This file renders the library shell from already-derived state.
    ModalNavigationDrawer(
        drawerState = state.drawerState,
        gesturesEnabled = !state.selection.isBookSelectionMode || state.drawerState.isOpen,
        drawerContent = {
            LibraryDrawerContent(
                state = state,
                actions = actions.folderDrawer,
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(state.snackbarHostState) },
            floatingActionButton = {
                if (!state.selection.isBookSelectionMode) {
                    FloatingActionButton(onClick = actions.onAddBookClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add Book")
                    }
                }
            },
            topBar = {
                LibraryTopBar(
                    state = state,
                    actions = actions,
                )
            },
        ) { padding ->
            val pullRefreshState = rememberPullRefreshState(
                refreshing = state.isLoading,
                onRefresh = actions.onRefreshLibrary,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pullRefresh(pullRefreshState),
            ) {
                LibraryBookGrid(
                    state = state,
                    actions = actions,
                )

                PullRefreshIndicator(
                    refreshing = state.isLoading,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryDrawerContent(
    state: LibraryScreenState,
    actions: FolderDrawerActions,
) {
    // Drawer concerns stay isolated here so agents can debug folder UI without loading screen routing.
    ModalDrawerSheet {
        val allSelectableFolders = state.folderDrawer.folders.filter { it != RootLibraryName }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.folderDrawer.isFolderSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.folderDrawer.foldersToDelete.isEmpty()) {
                            "Select Item"
                        } else {
                            "${state.folderDrawer.foldersToDelete.size} Selected"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.folderDrawer.foldersToDelete.isNotEmpty()) {
                            IconButton(onClick = actions.onRequestDeleteSelectedFolders) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (state.folderDrawer.foldersToDelete.size < allSelectableFolders.size) {
                            IconButton(onClick = actions.onSelectAllFolders) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            }
                        } else {
                            IconButton(onClick = actions.onClearFolderSelection) {
                                Icon(Icons.Default.Deselect, contentDescription = "Deselect All")
                            }
                        }

                        IconButton(onClick = actions.onExitFolderSelectionMode) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    }
                }
            } else {
                Text(
                    text = if (state.folderDrawer.isMovingMode) "Move To" else "Libraries",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (state.folderDrawer.isMovingMode) {
                    IconButton(onClick = actions.onCloseDrawer) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Move")
                    }
                } else {
                    Row {
                        IconButton(onClick = actions.onEnterFolderSelectionMode) {
                            Icon(Icons.Default.Checklist, contentDescription = "Selection Mode")
                        }
                        IconButton(onClick = actions.onShowCreateFolderDialog) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val itemHeightPx = with(density) { 60.dp.toPx() }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(state.folderDrawer.displayedFolders, key = { _, folderName -> folderName }) { _, folderName ->
                val isFavorite = state.globalSettings.favoriteLibrary == folderName
                val isDragged = folderName == state.folderDrawer.draggedFolderName
                val isSelected = state.folderDrawer.foldersToDelete.contains(folderName)
                val canDrag = folderName != RootLibraryName &&
                    !state.folderDrawer.isMovingMode &&
                    !state.folderDrawer.isFolderSelectionMode

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 10f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) state.folderDrawer.dragOffset else 0f
                        }
                        .then(
                            if (canDrag) {
                                Modifier.pointerInput(folderName) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { actions.onStartFolderDrag(folderName) },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            actions.onDragFolder(dragAmount.y, itemHeightPx)
                                        },
                                        onDragEnd = actions.onEndFolderDrag,
                                        onDragCancel = actions.onCancelFolderDrag,
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    NavigationDrawerItem(
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = folderName, modifier = Modifier.weight(1f))
                                if (isFavorite && !state.folderDrawer.isMovingMode && !state.folderDrawer.isFolderSelectionMode) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        selected = (state.selectedFolderName == folderName &&
                            !state.folderDrawer.isMovingMode &&
                            !state.folderDrawer.isFolderSelectionMode) ||
                            (state.folderDrawer.isFolderSelectionMode && isSelected),
                        onClick = {
                            when {
                                state.folderDrawer.isFolderSelectionMode -> actions.onToggleFolderSelection(folderName)
                                state.folderDrawer.isMovingMode -> actions.onMoveSelectedBooks(folderName)
                                else -> actions.onSelectFolder(folderName)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (folderName == RootLibraryName) {
                                    Icons.AutoMirrored.Filled.LibraryBooks
                                } else {
                                    Icons.Default.Folder
                                },
                                contentDescription = null,
                                tint = if (state.selectedFolderName == folderName &&
                                    !state.folderDrawer.isMovingMode &&
                                    !state.folderDrawer.isFolderSelectionMode
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = if (isDragged) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            } else {
                                Color.Transparent
                            },
                            selectedContainerColor = if (isDragged) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .height(56.dp)
                            .semantics { contentDescription = "Folder $folderName" }
                            .graphicsLayer {
                                val scale = if (isDragged) 1.05f else 1f
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (isDragged) 10f else 0f
                                shape = CircleShape
                                clip = isDragged
                            },
                        shape = CircleShape,
                        badge = {
                            if (folderName != RootLibraryName && !state.folderDrawer.isMovingMode) {
                                if (state.folderDrawer.isFolderSelectionMode) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                } else {
                                    Row {
                                        IconButton(
                                            onClick = { actions.onRequestRenameFolder(folderName) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                        Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename $folderName",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { actions.onRequestDeleteFolder(folderName) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete $folderName",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    state: LibraryScreenState,
    actions: LibraryScreenActions,
) {
    // Selection mode and normal mode share the same app-shell callbacks but render differently.
    if (state.selection.isBookSelectionMode) {
        TopAppBar(
            title = {
                Text(
                    text = if (state.selection.selectedBookIds.isEmpty()) {
                        "Select Item"
                    } else {
                        "${state.selection.selectedCount} Selected"
                    },
                )
            },
            actions = {
                val allIds = state.libraryItems.map { it.id }.toSet()
                if (state.selection.selectedCount < allIds.size) {
                    IconButton(onClick = actions.onSelectAllBooks) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                } else {
                    IconButton(onClick = actions.onClearBookSelection) {
                        Icon(Icons.Default.Deselect, contentDescription = "Deselect All")
                    }
                }
                IconButton(onClick = actions.onClearBookSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                }
            },
        )
    } else {
        TopAppBar(
            title = { Text(text = state.selectedFolderName) },
            navigationIcon = {
                IconButton(onClick = actions.onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = actions.onSetFavoriteFolder) {
                    val isFavorite = state.globalSettings.favoriteLibrary == state.selectedFolderName
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFFD700) else LocalContentColor.current,
                    )
                }
                IconButton(onClick = actions.onShowSortMenu) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                IconButton(onClick = actions.onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookGrid(
    state: LibraryScreenState,
    actions: LibraryScreenActions,
) {
    // Keep grid rendering dumb: it receives filtered/sorted books and emits user intent upward.
    val hasAnyBooks = state.books.isNotEmpty()

    if (!hasAnyBooks && !state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Add EPUB files to start reading",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.lastOpenedBook?.let { book ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecentlyViewedStrip(
                    book = book,
                    settingsManager = state.settingsManager,
                    globalSettings = state.globalSettings,
                    onOpen = actions.onOpenBook,
                )
            }
        }

        if (state.libraryItems.isEmpty() && !state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This folder is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.libraryItems, key = { it.id }) { book ->
            val isSelected = state.selection.selectedBookIds.contains(book.id)
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (state.selection.isBookSelectionMode) {
                            actions.onToggleBookSelection(book)
                        } else {
                            actions.onOpenBook(book)
                        }
                    },
                    onLongClick = {
                        if (!state.selection.isBookSelectionMode) {
                            actions.onStartBookSelection(book)
                        }
                    },
                ),
            ) {
                BookItem(book = book, settingsManager = state.settingsManager, isSelected = isSelected)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.BookSelectionActionBar(
    state: BookSelectionActionBarState,
    actions: BookSelectionActionBarActions,
) {
    // Bottom action bar is intentionally separate from the grid to reduce vertical scan cost.
    if (!state.visible) {
        return
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
    ) {
        Surface(
            color = if (state.theme == "oled") Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = if (state.theme == "oled") 0.dp else 8.dp,
            shadowElevation = 12.dp,
            border = if (state.theme == "oled") {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Surface(
                    onClick = actions.onDeleteSelectedBooks,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selected Books",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Surface(
                    onClick = actions.onMoveSelectedBooks,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = "Move Selected Books",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
