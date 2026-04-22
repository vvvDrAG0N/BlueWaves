package com.epubreader.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.GlobalSettings

@Composable
fun ReaderStatusSettingsRow(
    settings: GlobalSettings,
    onUpdateSettings: (GlobalSettingsTransform) -> Unit,
    modifier: Modifier = Modifier,
    isReaderUI: Boolean = false,
    isSystemBarVisible: Boolean = false,
    showHeader: Boolean = true,
    primaryColor: androidx.compose.ui.graphics.Color? = null,
    onSurfaceColor: androidx.compose.ui.graphics.Color? = null,
) {
    val readerStatusUi = settings.readerStatusUi
    val enabled = readerStatusUi.isEnabled
    val isInteractionDisabled = isSystemBarVisible
    val alpha = if (isInteractionDisabled) 0.3f else if (enabled) 1f else 0.5f

    val effectivePrimary = primaryColor ?: MaterialTheme.colorScheme.primary
    val effectiveOnSurface = onSurfaceColor ?: MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reading Info",
                    style = if (isReaderUI) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = effectiveOnSurface,
                )
                if (showHeader) {
                    Text(
                        text = "Minimal reading info overlay at the bottom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = effectiveOnSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    onUpdateSettings {
                        it.copy(readerStatusUi = it.readerStatusUi.copy(isEnabled = checked))
                    }
                },
                enabled = !isInteractionDisabled,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = effectivePrimary,
                    uncheckedThumbColor = effectiveOnSurface.copy(alpha = 0.4f),
                    uncheckedTrackColor = effectiveOnSurface.copy(alpha = 0.12f),
                    uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            )
        }

        Spacer(modifier = Modifier.height(0.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled && !isInteractionDisabled) 1f else 0.3f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val chipColors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                labelColor = effectiveOnSurface.copy(alpha = 0.5f),
                selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                selectedLabelColor = effectivePrimary,
            )
            val chipBorder = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                enabled = enabled && !isInteractionDisabled,
                selected = true,
                borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                selectedBorderColor = effectivePrimary,
                borderWidth = 1.dp,
                selectedBorderWidth = 2.dp,
            )

            FilterChip(
                selected = readerStatusUi.showClock,
                onClick = {
                    onUpdateSettings {
                        it.copy(readerStatusUi = it.readerStatusUi.copy(showClock = !readerStatusUi.showClock))
                    }
                },
                label = { Text("Time") },
                enabled = enabled && !isInteractionDisabled,
                colors = chipColors,
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = enabled && !isInteractionDisabled,
                    selected = readerStatusUi.showClock,
                    borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                    selectedBorderColor = effectivePrimary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 2.dp,
                ),
            )

            FilterChip(
                selected = readerStatusUi.showBattery,
                onClick = {
                    onUpdateSettings {
                        it.copy(readerStatusUi = it.readerStatusUi.copy(showBattery = !readerStatusUi.showBattery))
                    }
                },
                label = { Text("Battery") },
                enabled = enabled && !isInteractionDisabled,
                colors = chipColors,
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = enabled && !isInteractionDisabled,
                    selected = readerStatusUi.showBattery,
                    borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                    selectedBorderColor = effectivePrimary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 2.dp,
                ),
            )

            FilterChip(
                selected = readerStatusUi.showChapterNumber,
                onClick = {
                    onUpdateSettings {
                        it.copy(
                            readerStatusUi = it.readerStatusUi.copy(
                                showChapterNumber = !readerStatusUi.showChapterNumber,
                            ),
                        )
                    }
                },
                label = { Text("Chapter") },
                enabled = enabled && !isInteractionDisabled,
                colors = chipColors,
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = enabled && !isInteractionDisabled,
                    selected = readerStatusUi.showChapterNumber,
                    borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                    selectedBorderColor = effectivePrimary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 2.dp,
                ),
            )

            FilterChip(
                selected = readerStatusUi.showChapterProgress,
                onClick = {
                    onUpdateSettings {
                        it.copy(
                            readerStatusUi = it.readerStatusUi.copy(
                                showChapterProgress = !readerStatusUi.showChapterProgress,
                            ),
                        )
                    }
                },
                label = { Text("Progress") },
                enabled = enabled && !isInteractionDisabled,
                colors = chipColors,
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = enabled && !isInteractionDisabled,
                    selected = readerStatusUi.showChapterProgress,
                    borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                    selectedBorderColor = effectivePrimary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 2.dp,
                ),
            )
        }
    }
}
