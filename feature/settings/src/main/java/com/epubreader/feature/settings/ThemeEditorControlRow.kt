package com.epubreader.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeEditorControlsSection(
    mode: ThemeEditorMode,
    onModeChange: (ThemeEditorMode) -> Unit,
    onRebalance: () -> Unit,
) {
    val context = LocalContext.current
    val isRebalanceAvailable = mode != ThemeEditorMode.ADVANCED
    val rebalanceDescription = if (isRebalanceAvailable) {
        "Rebalance Derived Roles"
    } else {
        "Rebalance unavailable in Advanced mode"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeEditorModeRow(
            mode = mode,
            onModeChange = onModeChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = {
                if (isRebalanceAvailable) {
                    onRebalance()
                } else {
                    Toast.makeText(
                        context,
                        "Rebalance is only available in Basic and Extended",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier
                .testTag("theme_editor_rebalance_button")
                .semantics {
                    if (!isRebalanceAvailable) {
                        stateDescription = "Unavailable in Advanced"
                    }
                },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isRebalanceAvailable) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                },
                contentColor = if (isRebalanceAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Autorenew,
                contentDescription = rebalanceDescription,
            )
        }
    }
}

@Composable
private fun ThemeEditorModeRow(
    mode: ThemeEditorMode,
    onModeChange: (ThemeEditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeEditorMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChange(option) },
                label = {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("theme_editor_mode_${option.name.lowercase()}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
