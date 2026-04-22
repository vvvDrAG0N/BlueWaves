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
) {
    val readerStatusUi = settings.readerStatusUi
    val enabled = readerStatusUi.isEnabled
    val isInteractionDisabled = isSystemBarVisible
    val alpha = if (isInteractionDisabled) 0.3f else if (enabled) 1f else 0.5f

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
                )
                if (showHeader) {
                    Text(
                        text = "Minimal reading info overlay at the bottom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            FilterChip(
                selected = readerStatusUi.showClock,
                onClick = {
                    onUpdateSettings {
                        it.copy(readerStatusUi = it.readerStatusUi.copy(showClock = !readerStatusUi.showClock))
                    }
                },
                label = { Text("Time") },
                enabled = enabled && !isInteractionDisabled,
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
            )
        }
    }
}
