package com.epubreader.feature.settings

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.ThemePalette

internal data class SpecimenGeometry(
    val lineHeight: Dp,
    val spacing: Dp,
    val padding: Dp,
    val fontSize: TextUnit,
    val scale: Float,
)

internal fun buildSpecimenGeometry(
    fontSize: Int,
    lineHeight: Float,
    horizontalPadding: Int,
    scale: Float,
): SpecimenGeometry {
    val baseLineHeight = (4.dp * (fontSize.toFloat() / 18f)) * scale
    val spacingBetweenLines = (baseLineHeight * (lineHeight - 1f) + 4.dp) * scale
    val internalPadding = (16.dp * (horizontalPadding.toFloat() / 16f)) * scale
    val constrainedPadding = if (internalPadding > 32.dp * scale) 32.dp * scale else internalPadding
    return SpecimenGeometry(
        lineHeight = baseLineHeight,
        spacing = spacingBetweenLines,
        padding = constrainedPadding,
        fontSize = fontSize.sp,
        scale = scale,
    )
}

@Composable
internal fun LandscapeSpecimenCard(
    theme: CustomTheme,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    isActive: Boolean = false,
    isMarqueeActive: () -> Boolean,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        val p = theme.palette
        ThemeSpecimenContent(
            theme = theme,
            fontFamily = fontFamily,
            geometry = geometry,
            modifier = Modifier
                .testTag("appearance_theme_card_${theme.id}")
                .semantics { selected = isActive }
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        color = Color(p.readerBackground),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    )
                }
                .border(
                    width = 1.dp,
                    color = if (isActive) {
                        Color(p.primary).copy(alpha = 0.4f)
                    } else {
                        Color(p.primary).copy(alpha = 0.18f)
                    },
                    shape = RoundedCornerShape(16.dp),
                ),
            isMini = false,
            isMarqueeActive = isMarqueeActive,
        )
    }
}

@Composable
internal fun ThemeSpecimenContent(
    theme: CustomTheme,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    modifier: Modifier = Modifier,
    isMini: Boolean = false,
    isMarqueeActive: () -> Boolean = { true },
) {
    val p = theme.palette
    val scaleFactor = geometry.scale

    Column(modifier = modifier.fillMaxSize()) {
        SystemBarMock(p, isMini)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
            horizontalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .padding(start = geometry.padding),
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(p.readerForeground),
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    softWrap = false,
                    fontSize = if (isMini) 11.sp else 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = if (isMarqueeActive()) Int.MAX_VALUE else 0,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ),
                )

                Spacer(Modifier.height(8.dp * scaleFactor))

                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val h = geometry.lineHeight.toPx()
                    val s = geometry.spacing.toPx()
                    val r = h / 2
                    val readerFg = Color(p.readerForeground)
                    val primaryColor = Color(p.readerAccent)

                    val lineH = h * 0.6f
                    val yOffset = (h - lineH) / 2

                    drawRoundRect(
                        color = readerFg,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, yOffset),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.9f, lineH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )

                    val y2Base = h + s
                    drawRoundRect(
                        color = readerFg.copy(alpha = 0.5f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y2Base + yOffset),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.3f, lineH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            size.width * 0.3f + 4.dp.toPx(),
                            y2Base + yOffset - 1.dp.toPx(),
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.4f, lineH + 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )

                    val y3Base = (h + s) * 2
                    drawRoundRect(
                        color = readerFg.copy(alpha = 0.5f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y3Base + yOffset),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.8f, lineH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).align(Alignment.Bottom)) {
                SurfaceMock(p, isMini)
            }
        }
    }
}

@Composable
private fun SystemBarMock(p: ThemePalette, isMini: Boolean = false) {
    val scaleFactor = if (isMini) 0.6f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 6.dp * scaleFactor),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("12:00", fontSize = (8 * scaleFactor).sp, color = Color(p.systemForeground).copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)) {
            Box(modifier = Modifier.size(8.dp * scaleFactor, 4.dp * scaleFactor).background(Color(p.systemForeground).copy(alpha = 0.2f)))
            Box(modifier = Modifier.size(8.dp * scaleFactor, 4.dp * scaleFactor).background(Color(p.systemForeground).copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun SurfaceMock(p: ThemePalette, isMini: Boolean = false) {
    val scaleFactor = if (isMini) 0.6f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp * scaleFactor))
            .background(Color(p.surface))
            .border(1.dp * scaleFactor, Color(p.outline).copy(alpha = 0.1f), RoundedCornerShape(12.dp * scaleFactor))
            .padding(8.dp * scaleFactor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp * scaleFactor)
                .clip(RoundedCornerShape(4.dp * scaleFactor))
                .background(Color(p.surfaceVariant)),
        )

        Spacer(modifier = Modifier.height(8.dp * scaleFactor))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp * scaleFactor, 12.dp * scaleFactor)
                    .clip(RoundedCornerShape(2.dp * scaleFactor))
                    .border(0.5.dp * scaleFactor, Color(p.secondary), RoundedCornerShape(2.dp * scaleFactor)),
            )
            Spacer(modifier = Modifier.width(4.dp * scaleFactor))
            Box(
                modifier = Modifier
                    .size(32.dp * scaleFactor, 12.dp * scaleFactor)
                    .clip(RoundedCornerShape(2.dp * scaleFactor))
                    .background(Color(p.primary)),
            )
        }
    }
}
