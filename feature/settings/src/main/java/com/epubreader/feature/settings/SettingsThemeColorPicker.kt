package com.epubreader.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.parseThemeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun ThemeColorPickerOverlay(
    label: String,
    initialValue: String,
    testTagPrefix: String? = null,
    isGuided: Boolean,
    onDismiss: () -> Unit,
    onPreviewValueChange: ((String) -> ThemeColorPickerPreviewResult)? = null,
    onValueChange: (String) -> ThemeEditorColorEditResult,
) {
    val initialColor = remember(initialValue) { parseThemeColorOrNull(initialValue) ?: DefaultPickerColor }
    val initialHex = remember(initialColor) { formatThemeColor(initialColor) }
    val initialHsv = remember(initialColor) { initialColor.toThemeColorPickerHsv() }
    val initialTextFields = remember(initialHex) { themeColorPickerTextFields(initialHex) }
    val guidedPreviewValueChange = if (isGuided) {
        requireNotNull(onPreviewValueChange) { "Guided color picker requires a preview callback" }
    } else {
        null
    }
    val safeZoneCache = remember(guidedPreviewValueChange) {
        guidedPreviewValueChange?.let { ThemeColorPickerSafeZoneCache() }
    }

    var pickerHue by remember { mutableFloatStateOf(initialHsv.hue) }
    var pickerSaturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var pickerValue by remember { mutableFloatStateOf(initialHsv.value) }
    var textFields by remember { mutableStateOf(themeColorPickerTextFields(initialHex)) }
    var wasAdjusted by remember { mutableStateOf(false) }
    var snapPulseToken by remember { mutableFloatStateOf(0f) }
    var showSnapHighlight by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val guidedSafeZone by produceState<ThemeColorPickerSafeZone?>(
        initialValue = safeZoneCache?.cachedZoneForHue(pickerHue),
        key1 = isGuided,
        key2 = guidedPreviewValueChange,
        key3 = pickerHue.toSafeZoneHueBucket(),
    ) {
        if (!isGuided || guidedPreviewValueChange == null || safeZoneCache == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            val currentContext = coroutineContext
            safeZoneCache.zoneForHue(pickerHue) { bucketedHue ->
                buildGuidedSafeZone(
                    hue = bucketedHue,
                    previewColor = guidedPreviewValueChange,
                    cancellationCheck = { currentContext.ensureActive() },
                )
            }
        }
    }

    LaunchedEffect(snapPulseToken) {
        if (snapPulseToken <= 0f) return@LaunchedEffect
        showSnapHighlight = true
        delay(650)
        showSnapHighlight = false
    }

    fun currentPreviewHex(): String {
        return formatThemeColor(
            ThemeColorPickerHsv(
                hue = pickerHue,
                saturation = pickerSaturation,
                value = pickerValue,
            ).toColorLong(),
        )
    }

    fun currentGuidedResolution(): ThemeColorPickerPreviewResult? {
        if (!isGuided || guidedPreviewValueChange == null) {
            return null
        }
        return guidedPreviewValueChange(currentPreviewHex())
    }

    fun isDirty(): Boolean {
        return currentPreviewHex() != initialHex ||
            textFields.hexText != initialTextFields.hexText ||
            textFields.rgbText != initialTextFields.rgbText
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

    fun syncTextFields(
        hex: String = currentPreviewHex(),
        activeInput: ThemeColorPickerActiveInput? = null,
        rgbOverride: ThemeColorPickerRgbText? = null,
    ) {
        textFields = themeColorPickerTextFields(
            hex = hex,
            rgbOverride = rgbOverride,
            activeInput = activeInput,
        )
    }

    fun applyPreviewHex(
        hex: String,
        adjusted: Boolean,
        activeInput: ThemeColorPickerActiveInput? = null,
        rgbOverride: ThemeColorPickerRgbText? = null,
    ) {
        val color = parseThemeColorOrNull(hex) ?: return
        val hsv = color.toThemeColorPickerHsv()
        setPickerColor(
            hue = hsv.hue,
            saturation = hsv.saturation,
            value = hsv.value,
        )
        syncTextFields(
            hex = formatThemeColor(color),
            activeInput = activeInput,
            rgbOverride = rgbOverride,
        )
        wasAdjusted = adjusted
        showSnapHighlight = false
    }

    fun applyPreviewPoint(
        hue: Float = pickerHue,
        saturation: Float = pickerSaturation,
        value: Float = pickerValue,
        preserveAdjustedState: Boolean = false,
    ) {
        setPickerColor(
            hue = hue,
            saturation = saturation.coerceIn(0f, 1f),
            value = value.coerceIn(0f, 1f),
        )
        syncTextFields()
        if (!preserveAdjustedState) {
            wasAdjusted = false
        }
        showSnapHighlight = false
    }

    LaunchedEffect(isGuided, guidedSafeZone, pickerHue) {
        if (!isGuided) return@LaunchedEffect
        val safeZone = guidedSafeZone ?: return@LaunchedEffect
        val currentResolution = currentGuidedResolution() ?: return@LaunchedEffect
        if (!currentResolution.wasAdjusted) {
            return@LaunchedEffect
        }
        val projected = safeZone.project(
            ThemeColorPickerPoint(
                saturation = pickerSaturation,
                value = pickerValue,
            ),
        )
        if (
            pickerSaturation.isApproximately(projected.saturation) &&
            pickerValue.isApproximately(projected.value)
        ) {
            return@LaunchedEffect
        }
        applyPreviewPoint(
            saturation = projected.saturation,
            value = projected.value,
            preserveAdjustedState = wasAdjusted,
        )
    }

    fun updatePickerColor(
        hue: Float = pickerHue,
        saturation: Float = pickerSaturation,
        value: Float = pickerValue,
    ) {
        val projectedPoint = if (isGuided && !hue.isApproximately(pickerHue)) {
            ThemeColorPickerPoint(
                saturation = saturation.coerceIn(0f, 1f),
                value = value.coerceIn(0f, 1f),
            )
        } else {
            guidedSafeZone?.project(
                ThemeColorPickerPoint(
                    saturation = saturation.coerceIn(0f, 1f),
                    value = value.coerceIn(0f, 1f),
                ),
            ) ?: ThemeColorPickerPoint(
                saturation = saturation.coerceIn(0f, 1f),
                value = value.coerceIn(0f, 1f),
            )
        }
        if (
            pickerHue.isApproximately(hue) &&
            pickerSaturation.isApproximately(projectedPoint.saturation) &&
            pickerValue.isApproximately(projectedPoint.value)
        ) {
            return
        }
        applyPreviewPoint(
            hue = hue,
            saturation = projectedPoint.saturation,
            value = projectedPoint.value,
        )
    }

    fun updateHexInput(nextHex: String) {
        val nextFields = textFields.withHexInput(nextHex)
        textFields = nextFields
        val resolvedHex = nextFields.tryResolveHex() ?: return
        if (isGuided && guidedPreviewValueChange != null) {
            val resolution = resolveGuidedTypedHex(
                rawHex = resolvedHex,
                previewColor = guidedPreviewValueChange,
            )
            applyPreviewHex(
                hex = resolution.resolvedHex,
                adjusted = resolution.wasAdjusted,
                activeInput = ThemeColorPickerActiveInput.HEX,
            )
        } else {
            applyPreviewHex(
                hex = resolvedHex,
                adjusted = false,
                activeInput = ThemeColorPickerActiveInput.HEX,
            )
        }
    }

    fun updateRgbInput(nextRgb: ThemeColorPickerRgbText) {
        val nextFields = textFields.withRgbInput(
            red = nextRgb.red,
            green = nextRgb.green,
            blue = nextRgb.blue,
        )
        textFields = nextFields
        val resolvedHex = nextFields.tryResolveHex() ?: return
        if (isGuided && guidedPreviewValueChange != null) {
            val resolution = resolveGuidedTypedHex(
                rawHex = resolvedHex,
                previewColor = guidedPreviewValueChange,
            )
            applyPreviewHex(
                hex = resolution.resolvedHex,
                adjusted = resolution.wasAdjusted,
                activeInput = ThemeColorPickerActiveInput.RGB,
            )
        } else {
            applyPreviewHex(
                hex = resolvedHex,
                adjusted = false,
                activeInput = ThemeColorPickerActiveInput.RGB,
                rgbOverride = nextFields.rgbText,
            )
        }
    }

    fun commitCurrentColor() {
        showExitDialog = false
        val result = onValueChange(currentPreviewHex())
        applyPreviewHex(
            hex = result.resolvedHex,
            adjusted = result.wasAdjusted,
        )
        if (result.wasAdjusted) {
            snapPulseToken += 1f
        }
        onDismiss()
    }

    fun requestDismiss() {
        if (isDirty()) {
            showExitDialog = true
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (showExitDialog) {
                showExitDialog = false
            } else {
                requestDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp - 32.dp
        val dialogScrollState = rememberScrollState()
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
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = maxDialogHeight)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            if (showExitDialog) {
                                showExitDialog = false
                            } else {
                                requestDismiss()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {})
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
                Column(
                    modifier = Modifier
                        .verticalScroll(dialogScrollState)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    ThemeColorPickerHeader(
                        label = label,
                        testTagPrefix = testTagPrefix,
                        onClose = ::requestDismiss,
                        onSave = ::commitCurrentColor,
                    )

                    ThemeColorPickerValueInputs(
                        textFields = textFields,
                        previewHex = currentPreviewHex(),
                        previewColor = Color(
                            ThemeColorPickerHsv(
                                hue = pickerHue,
                                saturation = pickerSaturation,
                                value = pickerValue,
                            ).toColorLong(),
                        ),
                        isGuided = isGuided,
                        wasAdjusted = wasAdjusted,
                        showSnapHighlight = showSnapHighlight,
                        testTagPrefix = testTagPrefix,
                        onHexInputChange = ::updateHexInput,
                        onRgbInputChange = ::updateRgbInput,
                        modifier = Modifier.padding(top = 16.dp),
                    )

                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    ThemeColorSpectrumField(
                        hue = pickerHue,
                        point = ThemeColorPickerPoint(
                            saturation = pickerSaturation,
                            value = pickerValue,
                        ),
                        safeZone = guidedSafeZone,
                        testTagPrefix = testTagPrefix,
                        onPointChange = { point ->
                            updatePickerColor(
                                saturation = point.saturation,
                                value = point.value,
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (testTagPrefix != null) {
                        CompatibilityProgressProxy(
                            tag = "${testTagPrefix}_picker_saturation",
                            value = pickerSaturation,
                            valueRange = 0f..1f,
                            onValueChange = { nextSaturation ->
                                updatePickerColor(saturation = nextSaturation)
                            },
                        )
                        CompatibilityProgressProxy(
                            tag = "${testTagPrefix}_picker_value",
                            value = pickerValue,
                            valueRange = 0f..1f,
                            onValueChange = { nextValue ->
                                updatePickerColor(value = nextValue)
                            },
                        )
                    }

                    Text(
                        text = "Hue",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Slider(
                        value = pickerHue,
                        onValueChange = { updatePickerColor(hue = it) },
                        onValueChangeFinished = {},
                        valueRange = 0f..360f,
                        modifier = if (testTagPrefix != null) {
                            Modifier
                                .padding(top = 2.dp)
                                .testTag("${testTagPrefix}_picker_hue")
                        } else {
                            Modifier.padding(top = 2.dp)
                        },
                    )
                }
            }

            if (showExitDialog) {
                ThemeColorPickerExitDialog(
                    testTagPrefix = testTagPrefix,
                    onSave = ::commitCurrentColor,
                    onDiscard = {
                        showExitDialog = false
                        onDismiss()
                    },
                    onKeepEditing = {
                        showExitDialog = false
                    },
                )
            }
        }
    }
}

private const val DefaultPickerColor = 0xFFFFFFFFL
