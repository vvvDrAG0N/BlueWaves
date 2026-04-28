package com.epubreader.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeColorSpectrumField(
    hue: Float,
    point: ThemeColorPickerPoint,
    safeZone: ThemeColorPickerSafeZone?,
    testTagPrefix: String?,
    onPointChange: (ThemeColorPickerPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .then(
                if (testTagPrefix != null) {
                    Modifier.testTag("${testTagPrefix}_picker_spectrum")
                } else {
                    Modifier
                },
            )
            .pointerInput(hue, safeZone) {
                detectTapGestures { offset ->
                    onPointChange(
                        offset.toSpectrumPoint(
                            size = Size(size.width.toFloat(), size.height.toFloat()),
                            safeZone = safeZone,
                        ),
                    )
                }
            }
            .pointerInput(hue, safeZone) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onPointChange(
                            offset.toSpectrumPoint(
                                size = Size(size.width.toFloat(), size.height.toFloat()),
                                safeZone = safeZone,
                            ),
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onPointChange(
                            change.position.toSpectrumPoint(
                                size = Size(size.width.toFloat(), size.height.toFloat()),
                                safeZone = safeZone,
                            ),
                        )
                    },
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color(ThemeColorPickerHsv(hue, 1f, 1f).toColorLong())),
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                ),
            )
        }

        safeZone?.let { zone ->
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("${testTagPrefix}_picker_safe_zone")
                        } else {
                            Modifier
                        },
                    ),
            ) {
                drawSafeZoneVeil(zone)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val handleCenter = point.toOffset(size)
            val handleColor = Color(ThemeColorPickerHsv(hue, point.saturation, point.value).toColorLong())
            drawCircle(
                color = handleColor,
                radius = 10.dp.toPx(),
                center = handleCenter,
            )
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = handleCenter,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = 13.dp.toPx(),
                center = handleCenter,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

private fun Offset.toSpectrumPoint(
    size: Size,
    safeZone: ThemeColorPickerSafeZone?,
): ThemeColorPickerPoint {
    val point = ThemeColorPickerPoint(
        saturation = if (size.width <= 0f) 0f else (x / size.width).coerceIn(0f, 1f),
        value = if (size.height <= 0f) 1f else (1f - (y / size.height)).coerceIn(0f, 1f),
    )
    return safeZone?.project(point) ?: point
}

private fun ThemeColorPickerPoint.toOffset(size: Size): Offset {
    return Offset(
        x = saturation.coerceIn(0f, 1f) * size.width,
        y = (1f - value.coerceIn(0f, 1f)) * size.height,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSafeZoneVeil(
    safeZone: ThemeColorPickerSafeZone,
) {
    val veilColor = Color.Black.copy(alpha = SafeZoneVeilAlpha)
    if (safeZone.rows.isEmpty()) {
        drawRect(veilColor)
        return
    }

    val rowCenters = safeZone.rows.map { (1f - it.value.coerceIn(0f, 1f)) * size.height }
    safeZone.rows.forEachIndexed { index, row ->
        val centerY = rowCenters[index]
        val top = if (index == 0) {
            0f
        } else {
            (rowCenters[index - 1] + centerY) / 2f
        }
        val bottom = if (index == safeZone.rows.lastIndex) {
            size.height
        } else {
            (centerY + rowCenters[index + 1]) / 2f
        }
        val bandHeight = (bottom - top).coerceAtLeast(1f)
        var veilStart = 0f
        row.spans.forEach { span ->
            val left = span.start.coerceIn(0f, 1f) * size.width
            val right = span.endInclusive.coerceIn(0f, 1f) * size.width
            if (left > veilStart) {
                drawRect(
                    color = veilColor,
                    topLeft = Offset(x = veilStart, y = top),
                    size = Size(width = left - veilStart, height = bandHeight),
                )
            }
            veilStart = maxOf(veilStart, right)
        }
        if (veilStart < size.width) {
            drawRect(
                color = veilColor,
                topLeft = Offset(x = veilStart, y = top),
                size = Size(width = size.width - veilStart, height = bandHeight),
            )
        }
    }
}

@Composable
internal fun CompatibilityProgressProxy(
    tag: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .testTag(tag)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange,
                )
                setProgress { targetValue ->
                    onValueChange(targetValue.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            },
    )
}

internal data class ThemeColorPickerStatusState(
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

internal fun Float.isApproximately(other: Float, epsilon: Float = 0.001f): Boolean {
    return kotlin.math.abs(this - other) <= epsilon
}

private const val SafeZoneVeilAlpha = 0.22f
