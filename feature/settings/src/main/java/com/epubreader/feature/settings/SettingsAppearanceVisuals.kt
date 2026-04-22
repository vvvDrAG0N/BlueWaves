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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.ThemePalette
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class SpecimenGeometry(
    val lineHeight: Dp,
    val spacing: Dp,
    val padding: Dp,
    val fontSize: TextUnit,
    val scale: Float,
)

@Composable
internal fun ThemeControlHub(
    currentTheme: CustomTheme?,
    getSysFg: () -> Color,
    getPrimary: () -> Color,
    onCreate: () -> Unit,
    onData: () -> Unit,
    onModify: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onGallery: () -> Unit,
) {
    val isCustom = currentTheme?.id?.startsWith(CustomThemeIdPrefix) == true
    var showDataMenu = remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HubButton(
            icon = Icons.Default.Add,
            label = "Create",
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            testTag = "create_custom_theme_button",
        ) {
            onCreate()
        }

        Box {
            HubButton(Icons.Default.DataUsage, "Data", getSysFg, getPrimary) {
                showDataMenu.value = true
            }
            DropdownMenu(expanded = showDataMenu.value, onDismissRequest = { showDataMenu.value = false }) {
                DropdownMenuItem(
                    text = { Text("Import Themes") },
                    onClick = { showDataMenu.value = false; onData() },
                    leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                )
                if (isCustom) {
                    DropdownMenuItem(
                        text = { Text("Export This Theme") },
                        onClick = { showDataMenu.value = false; onExport() },
                        leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                    )
                }
            }
        }

        HubButton(
            icon = Icons.Default.Edit,
            label = "Modify",
            getSysFg = getSysFg,
            getPrimary = getPrimary,
            enabled = isCustom,
        ) {
            onModify()
        }

        HubButton(Icons.Default.GridView, "Gallery", getSysFg, getPrimary) { onGallery() }
    }
}

@Composable
private fun HubButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    getSysFg: () -> Color,
    getPrimary: () -> Color,
    enabled: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 0.8f else 0.2f
    val labelAlpha = if (enabled) 0.4f else 0.15f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
                .drawBehind {
                    drawCircle(color = getSysFg().copy(alpha = 0.05f))
                },
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = getSysFg().copy(alpha = alpha),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = getSysFg().copy(alpha = labelAlpha),
            fontSize = 10.sp,
        )
    }
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
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        color = Color(p.readerBackground),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    )
                }
                .border(
                    width = if (isActive) 3.dp else 1.dp,
                    color = if (isActive) Color(p.primary) else Color(p.outline).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
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
                    fontSize = if (isMini) 8.sp else 12.sp,
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
                    val primaryColor = Color(p.primary)

                    drawRoundRect(
                        color = readerFg,
                        size = androidx.compose.ui.geometry.Size(size.width * 0.9f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )

                    val y2 = h + s
                    drawRoundRect(
                        color = readerFg.copy(alpha = 0.5f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y2),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.3f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.3f + 4.dp.toPx(), y2),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.4f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )

                    val y3 = (h + s) * 2
                    drawRoundRect(
                        color = readerFg.copy(alpha = 0.5f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y3),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.8f, h),
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
