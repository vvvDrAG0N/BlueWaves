package com.epubreader.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeColorPickerValueInputs(
    textFields: ThemeColorPickerTextFields,
    previewHex: String,
    previewColor: Color,
    isGuided: Boolean,
    wasAdjusted: Boolean,
    showSnapHighlight: Boolean,
    testTagPrefix: String?,
    onHexInputChange: (String) -> Unit,
    onRgbInputChange: (ThemeColorPickerRgbText) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewBorderColor by animateColorAsState(
        targetValue = if (showSnapHighlight) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 220),
        label = "theme_picker_preview_border_color",
    )
    val previewBorderWidth by animateDpAsState(
        targetValue = if (showSnapHighlight) 3.dp else 1.dp,
        animationSpec = tween(durationMillis = 220),
        label = "theme_picker_preview_border_width",
    )
    val previewBorderAlpha by animateFloatAsState(
        targetValue = if (showSnapHighlight) 1f else 0.85f,
        animationSpec = tween(durationMillis = 220),
        label = "theme_picker_preview_border_alpha",
    )
    val previewTag = testTagPrefix?.let { "${it}_picker_preview" }
    val guidedCueTag = testTagPrefix?.let { "${it}_picker_guided_status" }
    var inputMode by remember {
        mutableStateOf(
            when (textFields.preferredInput) {
                ThemeColorPickerActiveInput.RGB -> ThemeColorPickerInputMode.RGB
                ThemeColorPickerActiveInput.HEX, null -> ThemeColorPickerInputMode.HEX
            },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeColorInputModeButton(
                selectedMode = inputMode,
                testTagPrefix = testTagPrefix,
                onClick = {
                    inputMode = inputMode.toggle()
                },
            )

            when (inputMode) {
                ThemeColorPickerInputMode.HEX -> {
                    ThemeColorValueField(
                        label = null,
                        placeholder = "000000",
                        prefixText = "#",
                        value = textFields.hexText,
                        tag = testTagPrefix?.let { "${it}_picker_hex" },
                        singleLine = true,
                        onValueChange = onHexInputChange,
                        modifier = Modifier.weight(1f),
                    )
                }

                ThemeColorPickerInputMode.RGB -> {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ThemeColorValueField(
                            label = null,
                            placeholder = "000",
                            prefixText = "R",
                            value = textFields.rgbText.red,
                            tag = testTagPrefix?.let { "${it}_picker_rgb_red" },
                            keyboardType = KeyboardType.Number,
                            onValueChange = { onRgbInputChange(textFields.rgbText.copy(red = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemeColorValueField(
                            label = null,
                            placeholder = "000",
                            prefixText = "G",
                            value = textFields.rgbText.green,
                            tag = testTagPrefix?.let { "${it}_picker_rgb_green" },
                            keyboardType = KeyboardType.Number,
                            onValueChange = { onRgbInputChange(textFields.rgbText.copy(green = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemeColorValueField(
                            label = null,
                            placeholder = "000",
                            prefixText = "B",
                            value = textFields.rgbText.blue,
                            tag = testTagPrefix?.let { "${it}_picker_rgb_blue" },
                            keyboardType = KeyboardType.Number,
                            onValueChange = { onRgbInputChange(textFields.rgbText.copy(blue = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewColor)
                    .border(
                        width = previewBorderWidth,
                        color = previewBorderColor.copy(alpha = previewBorderAlpha),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .then(
                        if (previewTag != null) {
                            Modifier
                                .testTag(previewTag)
                                .semantics {
                                    contentDescription = previewHex
                                    stateDescription = if (showSnapHighlight) {
                                        "adjusted"
                                    } else {
                                        "default"
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        if (isGuided) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (guidedCueTag != null) {
                            Modifier
                                .testTag(guidedCueTag)
                                .semantics(mergeDescendants = true) {}
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (wasAdjusted) {
                        "Adjusted for readability"
                    } else {
                        "Guided mode keeps colors readable"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wasAdjusted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorValueField(
    label: String?,
    placeholder: String?,
    prefixText: String?,
    value: String,
    tag: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium,
        label = label?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        prefix = prefixText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun ThemeColorInputModeButton(
    selectedMode: ThemeColorPickerInputMode,
    testTagPrefix: String?,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 52.dp)
            .then(
                if (testTagPrefix != null) {
                    Modifier
                        .testTag("${testTagPrefix}_picker_mode_toggle")
                        .semantics {
                            stateDescription = selectedMode.name.lowercase()
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(selectedMode.label)
    }
}

private enum class ThemeColorPickerInputMode(val label: String) {
    HEX("HEX"),
    RGB("RGB"),
    ;

    fun toggle(): ThemeColorPickerInputMode {
        return when (this) {
            HEX -> RGB
            RGB -> HEX
        }
    }
}
