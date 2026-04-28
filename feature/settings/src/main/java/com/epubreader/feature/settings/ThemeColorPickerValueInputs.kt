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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
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

            ThemeColorValueField(
                label = "HEX",
                value = textFields.hexText,
                tag = testTagPrefix?.let { "${it}_picker_hex" },
                singleLine = true,
                onValueChange = onHexInputChange,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeColorValueField(
                label = "R",
                value = textFields.rgbText.red,
                tag = testTagPrefix?.let { "${it}_picker_rgb_red" },
                keyboardType = KeyboardType.Number,
                onValueChange = { onRgbInputChange(textFields.rgbText.copy(red = it)) },
                modifier = Modifier.weight(1f),
            )
            ThemeColorValueField(
                label = "G",
                value = textFields.rgbText.green,
                tag = testTagPrefix?.let { "${it}_picker_rgb_green" },
                keyboardType = KeyboardType.Number,
                onValueChange = { onRgbInputChange(textFields.rgbText.copy(green = it)) },
                modifier = Modifier.weight(1f),
            )
            ThemeColorValueField(
                label = "B",
                value = textFields.rgbText.blue,
                tag = testTagPrefix?.let { "${it}_picker_rgb_blue" },
                keyboardType = KeyboardType.Number,
                onValueChange = { onRgbInputChange(textFields.rgbText.copy(blue = it)) },
                modifier = Modifier.weight(1f),
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
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun ThemeColorValueField(
    label: String,
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
            .height(64.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
