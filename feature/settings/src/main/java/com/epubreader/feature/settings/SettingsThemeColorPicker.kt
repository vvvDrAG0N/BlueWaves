package com.epubreader.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.parseThemeColorOrNull
import kotlinx.coroutines.delay

@Composable
internal fun ThemeColorPickerOverlay(
    label: String,
    initialValue: String,
    testTagPrefix: String? = null,
    isGuided: Boolean,
    onDismiss: () -> Unit,
    onValueChange: (String) -> ThemeEditorColorEditResult,
) {
    val parsedColor = remember(initialValue) { parseThemeColorOrNull(initialValue) }
    val initialHsv = remember(parsedColor) { (parsedColor ?: DefaultPickerColor).toHsvColor() }
    var pickerHue by remember { mutableFloatStateOf(initialHsv.hue) }
    var pickerSaturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var pickerValue by remember { mutableFloatStateOf(initialHsv.value) }
    var wasAdjusted by remember { mutableStateOf(false) }
    var hasPendingGuidedChange by remember { mutableStateOf(false) }
    var snapPulseToken by remember { mutableIntStateOf(0) }
    var showSnapHighlight by remember { mutableStateOf(false) }
    val adjustmentStatusTag = remember(testTagPrefix) {
        testTagPrefix?.let { "${it}_picker_guided_status" }
    }
    val previewTag = remember(testTagPrefix) {
        testTagPrefix?.let { "${it}_picker_preview" }
    }
    val statusState = remember(isGuided, wasAdjusted, showSnapHighlight) {
        ThemeColorPickerStatusState(
            isGuided = isGuided,
            wasAdjusted = wasAdjusted,
            showSnapHighlight = showSnapHighlight,
        )
    }
    val previewBorderColor by animateColorAsState(
        targetValue = if (statusState.showSnapHighlight) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 220),
        label = "theme_picker_preview_border_color",
    )
    val previewBorderWidth by animateDpAsState(
        targetValue = if (statusState.showSnapHighlight) 3.dp else 1.dp,
        animationSpec = tween(durationMillis = 220),
        label = "theme_picker_preview_border_width",
    )
    val previewBorderAlpha by animateFloatAsState(
        targetValue = if (statusState.showSnapHighlight) 1f else 0.85f,
        animationSpec = tween(durationMillis = 220),
        label = "theme_picker_preview_border_alpha",
    )

    LaunchedEffect(snapPulseToken) {
        if (snapPulseToken == 0) return@LaunchedEffect
        showSnapHighlight = true
        delay(650)
        showSnapHighlight = false
    }

    fun setPickerColor(
        hue: Float = pickerHue,
        saturation: Float = pickerSaturation,
        value: Float = pickerValue,
    ) {
        pickerHue = hue
        pickerSaturation = saturation
        pickerValue = value
    }

    fun commitPendingColor() {
        if (!isGuided || !hasPendingGuidedChange) return
        val rawColor = HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong()
        val result = onValueChange(formatThemeColor(rawColor))
        val resolvedColor = parseThemeColorOrNull(result.resolvedHex) ?: rawColor
        val resolvedHsv = resolvedColor.toHsvColor()
        setPickerColor(
            hue = resolvedHsv.hue,
            saturation = resolvedHsv.saturation,
            value = resolvedHsv.value,
        )
        hasPendingGuidedChange = false
        wasAdjusted = result.wasAdjusted
        if (result.wasAdjusted) {
            snapPulseToken += 1
        }
    }

    fun updatePickerColor(
        hue: Float = pickerHue,
        saturation: Float = pickerSaturation,
        value: Float = pickerValue,
    ) {
        if (isGuided) {
            if (
                pickerHue.isApproximately(hue) &&
                pickerSaturation.isApproximately(saturation) &&
                pickerValue.isApproximately(value)
            ) {
                return
            }
            setPickerColor(hue = hue, saturation = saturation, value = value)
            hasPendingGuidedChange = true
            wasAdjusted = false
            showSnapHighlight = false
            return
        }

        val rawColor = HsvColor(hue, saturation, value).toColorLong()
        val result = onValueChange(formatThemeColor(rawColor))
        val resolvedColor = parseThemeColorOrNull(result.resolvedHex) ?: rawColor
        val resolvedHsv = resolvedColor.toHsvColor()
        setPickerColor(
            hue = resolvedHsv.hue,
            saturation = resolvedHsv.saturation,
            value = resolvedHsv.value,
        )
        hasPendingGuidedChange = false
        wasAdjusted = false
        showSnapHighlight = false
    }

    val dismissPicker = onDismiss
    val confirmPicker = {
        commitPendingColor()
        onDismiss()
    }

    val backdropInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = dismissPicker,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("${testTagPrefix}_picker_backdrop")
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = backdropInteractionSource,
                        indication = null,
                        onClick = dismissPicker,
                    ),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {},
                        )
                    }
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("${testTagPrefix}_picker_dialog")
                        } else {
                            Modifier
                        },
                    ),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "$label Color",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Box(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong()))
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
                                            stateDescription = if (statusState.showSnapHighlight) {
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

                    if (statusState.isGuided) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(top = 8.dp)
                                .then(
                                    if (adjustmentStatusTag != null) {
                                        Modifier
                                            .testTag(adjustmentStatusTag)
                                            .semantics(mergeDescendants = true) {}
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = statusState.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusState.wasAdjusted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    Text("Hue", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pickerHue,
                        onValueChange = { updatePickerColor(hue = it) },
                        onValueChangeFinished = {},
                        valueRange = 0f..360f,
                        modifier = if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_hue") else Modifier,
                    )

                    Text("Saturation", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pickerSaturation,
                        onValueChange = { updatePickerColor(saturation = it) },
                        onValueChangeFinished = {},
                        valueRange = 0f..1f,
                        modifier = if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_saturation") else Modifier,
                    )

                    Text("Brightness", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pickerValue,
                        onValueChange = { updatePickerColor(value = it) },
                        onValueChangeFinished = {},
                        valueRange = 0f..1f,
                        modifier = if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_value") else Modifier,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = confirmPicker) { Text("Done") }
                    }
                }
            }
        }
    }
}

private data class ThemeColorPickerStatusState(
    val isGuided: Boolean,
    val wasAdjusted: Boolean,
    val showSnapHighlight: Boolean,
) {
    val statusText: String
        get() = if (wasAdjusted) {
            "Adjusted for readability"
        } else {
            "Guided mode keeps colors readable"
        }
}

private const val DefaultPickerColor = 0xFFFFFFFFL

private data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColorLong(): Long {
        val hsv = floatArrayOf(hue, saturation, value)
        return android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
    }
}

private fun Long.toHsvColor(): HsvColor {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toInt(), hsv)
    return HsvColor(hsv[0], hsv[1], hsv[2])
}

private fun Float.isApproximately(other: Float, epsilon: Float = 0.001f): Boolean {
    return kotlin.math.abs(this - other) <= epsilon
}
