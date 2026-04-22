package com.epubreader.feature.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.ReaderStatusUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun ReaderStatusOverlay(
    uiState: ReaderStatusUiState,
    isVisible: Boolean,
    themeColors: ReaderTheme,
    chapterIndex: Int,
    maxChapters: Int,
    progressPercentageState: State<Float>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val progressPercentage = progressPercentageState.value
    val overlayActive = uiState.isEnabled && isVisible

    val timeText by produceState(
        initialValue = "",
        overlayActive,
        uiState.showClock,
    ) {
        if (!overlayActive || !uiState.showClock) {
            value = ""
            return@produceState
        }

        while (true) {
            value = currentReaderTimeText()
            kotlinx.coroutines.delay(1000 * 30)
        }
    }
    val batteryText by produceState(
        initialValue = "",
        context,
        overlayActive,
        uiState.showBattery,
    ) {
        value = if (overlayActive && uiState.showBattery) {
            readBatteryText(context)
        } else {
            ""
        }
    }

    val chapterText = remember(uiState.showChapterNumber, chapterIndex) {
        if (uiState.showChapterNumber) "CH $chapterIndex" else ""
    }

    AnimatedVisibility(
        visible = overlayActive,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chapterText.isNotEmpty() || uiState.showChapterProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (chapterText.isNotEmpty()) {
                            Text(
                                text = chapterText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeColors.foreground.copy(alpha = 0.5f),
                                ),
                            )
                        }
                        if (uiState.showChapterProgress) {
                            Text(
                                text = "${(progressPercentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeColors.foreground.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (uiState.showClock) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeColors.foreground.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }

                if (uiState.showBattery && batteryText.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryFull,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = themeColors.foreground.copy(alpha = 0.5f),
                        )
                        Text(
                            text = batteryText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = themeColors.foreground.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun currentReaderTimeText(): String {
    val now = Calendar.getInstance()
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(now.time)
}

private fun readBatteryText(context: Context): String {
    val batteryStatus = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
    )
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level != -1 && scale != -1) {
        "${(level * 100 / scale.toFloat()).toInt()}%"
    } else {
        ""
    }
}
