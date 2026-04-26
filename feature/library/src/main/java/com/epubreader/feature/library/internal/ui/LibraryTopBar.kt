package com.epubreader.feature.library.internal.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.feature.library.LibraryActionSlot
import com.epubreader.feature.library.internal.LibraryScreenActions
import com.epubreader.feature.library.internal.LibraryScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTopBar(
    state: LibraryScreenState,
    actions: LibraryScreenActions,
    extensionActions: List<LibraryActionSlot>,
) {
    val favoriteAccent = Color(
        themePaletteSeed(
            state.globalSettings.theme,
            state.globalSettings.customThemes,
        ).favoriteAccent,
    )
    if (state.selection.isBookSelectionMode) {
        TopAppBar(
            windowInsets = getStaticWindowInsets(),
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
            windowInsets = getStaticWindowInsets(),
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
                        tint = if (isFavorite) favoriteAccent else LocalContentColor.current,
                    )
                }
                IconButton(onClick = actions.onShowSortMenu) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                extensionActions.forEach { action ->
                    action.content()
                }
                IconButton(onClick = actions.onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
    }
}
