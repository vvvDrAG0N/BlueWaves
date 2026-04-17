package com.epubreader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * AI_LOAD_STRATEGY
 * - Read this file only when working on sort-sheet or dialog copy/layout/confirm flows.
 * - Read `AppNavigationContracts.kt` if you need the dialog state/action contract.
 * - Dialog side effects should still be traced back to `AppNavigation.kt`.
 */
@Composable
internal fun LibraryDialogHost(
    state: LibraryDialogState,
    actions: LibraryDialogActions,
) {
    LibrarySortSheet(
        visible = state.showSortMenu,
        currentSort = state.currentSort,
        onDismiss = actions.onDismissSortMenu,
        onSelectSort = actions.onSelectSort,
    )

    CreateFolderDialog(
        visible = state.showCreateFolderDialog,
        existingFolders = state.existingFolders,
        onDismiss = actions.onDismissCreateFolder,
        onCreateFolder = actions.onCreateFolder,
    )

    RenameFolderDialog(
        folderName = state.folderToRename,
        existingFolders = state.existingFolders,
        onDismiss = actions.onDismissRenameFolder,
        onRenameFolder = actions.onRenameFolder,
    )

    DeleteFolderDialog(
        folderName = state.folderToDelete,
        onDismiss = actions.onDismissDeleteFolder,
        onDeleteFolder = actions.onDeleteFolder,
    )

    DeleteBooksDialog(
        visible = state.showBulkBookDeleteConfirm,
        selectedBookCount = state.selectedBookCount,
        onDismiss = actions.onDismissDeleteBooks,
        onDeleteBooks = actions.onDeleteBooks,
    )

    DeleteFoldersDialog(
        visible = state.showBulkFolderDeleteConfirm,
        selectedFolderCount = state.selectedFolderCount,
        onDismiss = actions.onDismissDeleteFolders,
        onDeleteFolders = actions.onDeleteFolders,
    )

    WelcomeDialog(
        visible = state.showFirstTimeNote,
        onDismiss = actions.onDismissWelcome,
    )

    ChangelogDialog(
        changelogEntries = state.changelogEntries,
        onDismiss = actions.onDismissChangelog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySortSheet(
    visible: Boolean,
    currentSort: String,
    onDismiss: () -> Unit,
    onSelectSort: (String) -> Unit,
) {
    // Sort-order toggling is preserved exactly; this file only renders the chooser surface.
    if (!visible) {
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text("Sort By", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(16.dp))

            val sortParts = currentSort.split("_")
            val currentType = sortParts.getOrNull(0) ?: "added"
            val currentOrder = sortParts.getOrNull(1) ?: "desc"

            LibrarySortOptions.forEach { (key, label) ->
                Surface(
                    onClick = {
                        val newOrder = if (currentType == key) {
                            if (currentOrder == "asc") "desc" else "asc"
                        } else if (key == "recent" || key == "added") {
                            "desc"
                        } else {
                            "asc"
                        }
                        onSelectSort("${key}_$newOrder")
                    },
                    color = if (currentType == key) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = label, modifier = Modifier.weight(1f))
                        if (currentType == key) {
                            Icon(
                                imageVector = if (currentOrder == "asc") {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateFolderDialog(
    visible: Boolean,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    if (!visible) {
        return
    }

    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder") },
        text = {
            TextField(
                value = folderName,
                onValueChange = { folderName = it },
                placeholder = { Text("Folder Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newFolderName = folderName.trim()
                    if (newFolderName.isNotBlank() && !existingFolders.contains(newFolderName)) {
                        onCreateFolder(newFolderName)
                    }
                    onDismiss()
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
internal fun RenameFolderDialog(
    folderName: String?,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onRenameFolder: (String, String) -> Unit,
) {
    folderName ?: return

    var newName by remember(folderName) { mutableStateOf(folderName) }
    val trimmedNewName = newName.trim()
    val canRename = trimmedNewName.isNotBlank() &&
        trimmedNewName != folderName &&
        trimmedNewName != RootLibraryName &&
        existingFolders.none { it == trimmedNewName && it != folderName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Folder") },
        text = {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = canRename,
                onClick = {
                    if (canRename) {
                        onRenameFolder(folderName, trimmedNewName)
                        onDismiss()
                    }
                },
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
internal fun DeleteFolderDialog(
    folderName: String?,
    onDismiss: () -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    folderName ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Folder") },
        text = { Text("Delete \"$folderName\"? Books inside will move to My Library.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteFolder(folderName)
                    onDismiss()
                },
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
internal fun DeleteBooksDialog(
    visible: Boolean,
    selectedBookCount: Int,
    onDismiss: () -> Unit,
    onDeleteBooks: () -> Unit,
) {
    if (!visible) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Books") },
        text = { Text("Remove $selectedBookCount books from library?") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteBooks()
                    onDismiss()
                },
            ) {
                Text("Delete All", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
internal fun DeleteFoldersDialog(
    visible: Boolean,
    selectedFolderCount: Int,
    onDismiss: () -> Unit,
    onDeleteFolders: () -> Unit,
) {
    if (!visible) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Folders") },
        text = { Text("Delete $selectedFolderCount folders? Books inside will move to My Library.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteFolders()
                    onDismiss()
                },
            ) {
                Text("Delete All", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
internal fun WelcomeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to Blue Waves") },
        text = { Text("Native high-performance EPUB reader.") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Start")
            }
        },
    )
}

@Composable
internal fun ChangelogDialog(
    changelogEntries: List<JSONObject>,
    onDismiss: () -> Unit,
) {
    if (changelogEntries.isEmpty()) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's New") },
        text = {
            Column {
                changelogEntries.forEach { entry ->
                    Text(
                        text = entry.optString("versionName", "Update"),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val changes = entry.optJSONArray("changes")
                    if (changes != null) {
                        for (index in 0 until changes.length()) {
                            Text("\u2022 ${changes.getString(index)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}
