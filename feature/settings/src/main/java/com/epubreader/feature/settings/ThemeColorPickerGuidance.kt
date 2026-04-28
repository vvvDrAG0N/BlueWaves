package com.epubreader.feature.settings

import com.epubreader.core.model.formatThemeColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ThemeColorPickerPoint(
    val saturation: Float,
    val value: Float,
)

internal data class ThemeColorPickerSafeZoneRow(
    val value: Float,
    val spans: List<ClosedFloatingPointRange<Float>>,
)

internal data class ThemeColorPickerSafeZone(
    val rows: List<ThemeColorPickerSafeZoneRow>,
) {
    fun contains(point: ThemeColorPickerPoint): Boolean {
        val sampledRow = rows.firstOrNull { row ->
            abs(row.value - point.value) <= SafeZoneRowValueEpsilon
        } ?: return false
        return sampledRow.spans.any { span ->
            point.saturation >= span.start && point.saturation <= span.endInclusive
        }
    }

    fun project(point: ThemeColorPickerPoint): ThemeColorPickerPoint {
        if (contains(point)) {
            return point
        }

        return rows
            .flatMap { row ->
                row.spans.map { span ->
                    ThemeColorPickerPoint(
                        saturation = point.saturation.coerceIn(span.start, span.endInclusive),
                        value = row.value,
                    )
                }
            }
            .minByOrNull { candidate -> candidate.distanceSquaredTo(point) }
            ?: point
    }
}

internal data class ThemeColorPickerPreviewResult(
    val resolvedHex: String,
    val wasAdjusted: Boolean,
)

internal class ThemeColorPickerSafeZoneCache {
    private val zonesByHueBucket = ConcurrentHashMap<Int, ThemeColorPickerSafeZone>()

    fun cachedZoneForHue(hue: Float): ThemeColorPickerSafeZone? {
        return zonesByHueBucket[hue.toSafeZoneHueBucket()]
    }

    fun zoneForHue(
        hue: Float,
        buildZone: (Float) -> ThemeColorPickerSafeZone,
    ): ThemeColorPickerSafeZone {
        val bucket = hue.toSafeZoneHueBucket()
        return zonesByHueBucket.computeIfAbsent(bucket) { bucketedHue ->
            buildZone(bucketedHue.toSafeZoneHue())
        }
    }
}

internal fun buildGuidedSafeZone(
    hue: Float,
    previewColor: (String) -> ThemeColorPickerPreviewResult,
    rows: Int = 36,
    columns: Int = 48,
    cancellationCheck: (() -> Unit)? = null,
): ThemeColorPickerSafeZone {
    val rowCount = rows.coerceAtLeast(2)
    val columnCount = columns.coerceAtLeast(2)
    val maxRowIndex = rowCount - 1
    val maxColumnIndex = columnCount - 1
    val saturationStep = 1f / maxColumnIndex.toFloat()
    val sampledRows = buildList {
        repeat(rowCount) { rowIndex ->
            cancellationCheck?.invoke()
            val value = 1f - rowIndex.toFloat() / maxRowIndex.toFloat()
            val validSaturations = buildList {
                repeat(columnCount) { columnIndex ->
                    if (columnIndex % 8 == 0) {
                        cancellationCheck?.invoke()
                    }
                    val saturation = columnIndex.toFloat() / maxColumnIndex.toFloat()
                    val rawHex = formatThemeColor(
                        ThemeColorPickerHsv(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                        ).toColorLong(),
                    )
                    val preview = previewColor(rawHex)
                    if (!preview.wasAdjusted) {
                        add(saturation)
                    }
                }
            }
            val spans = validSaturations.toSafeZoneSpans(saturationStep)
            if (spans.isNotEmpty()) {
                add(
                    ThemeColorPickerSafeZoneRow(
                        value = value,
                        spans = spans,
                    ),
                )
            }
        }
    }

    return ThemeColorPickerSafeZone(rows = sampledRows)
}

internal data class ThemeColorPickerHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColorLong(): Long {
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        val clampedSaturation = saturation.coerceIn(0f, 1f)
        val clampedValue = value.coerceIn(0f, 1f)
        val chroma = clampedValue * clampedSaturation
        val secondary = chroma * (1f - abs((normalizedHue / 60f) % 2f - 1f))
        val match = clampedValue - chroma
        val (red, green, blue) = when ((normalizedHue / 60f).toInt()) {
            0 -> Triple(chroma, secondary, 0f)
            1 -> Triple(secondary, chroma, 0f)
            2 -> Triple(0f, chroma, secondary)
            3 -> Triple(0f, secondary, chroma)
            4 -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }

        val r = ((red + match) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((green + match) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((blue + match) * 255f).roundToInt().coerceIn(0, 255)
        return 0xFF000000L or
            (r.toLong() shl 16) or
            (g.toLong() shl 8) or
            b.toLong()
    }
}

internal fun Long.toThemeColorPickerHsv(): ThemeColorPickerHsv {
    val red = ((this shr 16) and 0xFF).toFloat() / 255f
    val green = ((this shr 8) and 0xFF).toFloat() / 255f
    val blue = (this and 0xFF).toFloat() / 255f
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    val delta = maxChannel - minChannel

    val hue = when {
        delta == 0f -> 0f
        maxChannel == red -> 60f * (((green - blue) / delta).mod(6f))
        maxChannel == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    val saturation = if (maxChannel == 0f) 0f else delta / maxChannel

    return ThemeColorPickerHsv(
        hue = if (hue.isNaN()) 0f else hue,
        saturation = saturation,
        value = maxChannel,
    )
}

private fun ThemeColorPickerPoint.distanceSquaredTo(other: ThemeColorPickerPoint): Float {
    val saturationDelta = saturation - other.saturation
    val valueDelta = value - other.value
    return saturationDelta * saturationDelta + valueDelta * valueDelta
}

private fun List<Float>.toSafeZoneSpans(step: Float): List<ClosedFloatingPointRange<Float>> {
    if (isEmpty()) {
        return emptyList()
    }

    val spans = mutableListOf<ClosedFloatingPointRange<Float>>()
    var spanStart = first()
    var spanEnd = first()
    val gapThreshold = step + 0.0001f

    for (index in 1 until size) {
        val saturation = this[index]
        if (saturation - spanEnd <= gapThreshold) {
            spanEnd = saturation
        } else {
            spans += spanStart..spanEnd
            spanStart = saturation
            spanEnd = saturation
        }
    }

    spans += spanStart..spanEnd
    return spans
}

internal fun Float.toSafeZoneHueBucket(): Int {
    val normalizedHue = ((this % 360f) + 360f) % 360f
    return normalizedHue.roundToInt() % 360
}

private fun Int.toSafeZoneHue(): Float {
    return (((this % 360) + 360) % 360).toFloat()
}

private const val SafeZoneRowValueEpsilon = 0.0001f
