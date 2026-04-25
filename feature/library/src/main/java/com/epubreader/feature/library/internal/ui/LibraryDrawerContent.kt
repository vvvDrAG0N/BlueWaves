package com.epubreader.feature.library.internal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
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
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.feature.library.internal.FolderDrawerActions
import com.epubreader.feature.library.internal.LibraryScreenState
import com.epubreader.feature.library.internal.RootLibraryName

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryDrawerContent(
    state: LibraryScreenState,
    actions: FolderDrawerActions,
) {
    val favoriteAccent = Color(
        themePaletteSeed(
            state.globalSettings.theme,
            state.globalSettings.customThemes,
        ).favoriteAccent,
    )
    ModalDrawerSheet(windowInsets = getStaticWindowInsets()) {
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
                            },
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
                                        tint = favoriteAccent,
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
                                tint = if (
                                    state.selectedFolderName == folderName &&
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
