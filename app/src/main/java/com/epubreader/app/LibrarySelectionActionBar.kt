package com.epubreader.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.BookSelectionActionBar(
    state: BookSelectionActionBarState,
    actions: BookSelectionActionBarActions,
) {
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
            color = if (state.theme == "oled") Color.Black else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = if (state.theme == "oled") 0.dp else 8.dp,
            shadowElevation = 6.dp,
            border = if (state.theme == "oled") {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selected Books",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Surface(
                    onClick = actions.onMoveSelectedBooks,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
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

                SelectionActionButton(
                    enabled = state.canEditSelection,
                    onClick = actions.onEditSelectedBook,
                    contentColor = MaterialTheme.colorScheme.primary,
                    contentDescription = "Edit Selected Book",
                    icon = Icons.Default.Edit,
                )
            }
        }
    }
}

@Composable
private fun SelectionActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentColor: Color,
    contentDescription: String,
    icon: ImageVector,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            },
        ),
        shape = CircleShape,
        modifier = Modifier
            .size(56.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    contentColor
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
