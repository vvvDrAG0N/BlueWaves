package com.epubreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun TypographySettingsPanel(
    settings: GlobalSettings,
    currentFontSize: Int,
    currentLineHeight: Float,
    currentPadding: Int,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onPaddingChange: (Float) -> Unit,
    onCommitFontSize: () -> Unit,
    onCommitLineHeight: () -> Unit,
    onCommitPadding: () -> Unit,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    getSysFg: () -> Color,
    getPrimary: () -> Color,
) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Typography & Rhythm", style = MaterialTheme.typography.titleMedium, color = getSysFg())

        Column {
            val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = fonts.indexOf(settings.fontType).coerceAtLeast(0),
            )

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(fonts) { font ->
                    val isSelected = settings.fontType == font
                    FilterChip(
                        selected = isSelected,
                        onClick = { scope.launch { settingsManager.updateGlobalSettings { it.copy(fontType = font) } } },
                        label = { Text(font.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = getSysFg().copy(alpha = 0.5f),
                            selectedContainerColor = Color.Transparent,
                            selectedLabelColor = getPrimary(),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = getSysFg().copy(alpha = 0.1f),
                            selectedBorderColor = getPrimary(),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp,
                        ),
                    )
                }
            }
        }

        ControlSlider(
            label = "Font Size",
            value = currentFontSize.toFloat(),
            range = 12f..32f,
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            onValueChange = onFontSizeChange,
            onValueChangeFinished = onCommitFontSize,
        )

        ControlSlider(
            label = "Line Height",
            value = currentLineHeight,
            range = 1.0f..2.0f,
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            onValueChange = onLineHeightChange,
            onValueChangeFinished = onCommitLineHeight,
        )

        ControlSlider(
            label = "Padding",
            value = currentPadding.toFloat(),
            range = 0f..48f,
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            onValueChange = onPaddingChange,
            onValueChangeFinished = onCommitPadding,
        )
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    getSysFg: () -> Color,
    getPrimary: () -> Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = getSysFg().copy(alpha = 0.7f))
            Text(
                if (range.endInclusive > 5f) "${value.toInt()}" else String.format("%.2f", value),
                style = MaterialTheme.typography.labelMedium,
                color = getSysFg().copy(alpha = 0.5f),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = "$label Slider"
            },
            colors = SliderDefaults.colors(
                thumbColor = getPrimary(),
                activeTrackColor = getPrimary(),
                inactiveTrackColor = getSysFg().copy(alpha = 0.2f),
            ),
        )
    }
}
