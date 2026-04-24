package com.epubreader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.CustomTheme

@Composable
internal fun ThemeEditorPreviewCard(
    theme: CustomTheme,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    modifier: Modifier = Modifier,
) {
    val palette = theme.palette
    Card(
        modifier = modifier
            .semantics {
                contentDescription = "Theme ${theme.name}"
                selected = true
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        ThemeSpecimenContent(
            theme = theme,
            fontFamily = fontFamily,
            geometry = geometry,
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        color = Color(palette.readerBackground),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    )
                }
                .background(Color.Transparent)
                .border(
                    width = 1.dp,
                    color = Color(palette.primary).copy(alpha = 0.32f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ),
            isMini = false,
            isMarqueeActive = { false },
        )
    }
}
