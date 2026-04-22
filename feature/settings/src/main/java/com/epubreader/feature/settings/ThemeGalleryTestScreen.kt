package com.epubreader.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.launch
import kotlin.math.*

// Antigravity Design Tokens
private val DeepVoid = Color(0xFF010306)
private val CelestialCyan = Color(0xFF00E5FF)
private val StarViolet = Color(0xFF9D4EDD)
private val GlassBorder = Color(0x1AFFFFFF)
private val ObsidianRefraction = Color(0xCC05070A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryTestScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val settings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val allThemes = remember(settings.customThemes) {
        BuiltInThemeOptions.map { it.toCustomTheme() } + settings.customThemes
    }
    
    val selectedIndex = allThemes.indexOfFirst { it.id == settings.theme }.coerceAtLeast(0)
    
    // Pager state for the Orbital Carousel
    val pagerState = rememberPagerState(initialPage = selectedIndex) { allThemes.size }

    // Sync pager selection with settings
    LaunchedEffect(pagerState.currentPage) {
        val themeId = allThemes[pagerState.currentPage].id
        if (themeId != settings.theme) {
            settingsManager.setActiveTheme(themeId)
        }
    }

    val selectedTheme = allThemes[pagerState.currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepVoid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Header(onBack)

            Spacer(modifier = Modifier.height(40.dp))

            // 1. The Orbital Engine (The Circular Carousel)
            OrbitalCarousel(
                themes = allThemes,
                pagerState = pagerState,
                settings = settings
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Liquid Glass Console
            LiquidGlassConsole(
                theme = selectedTheme,
                index = pagerState.currentPage + 1,
                total = allThemes.size,
                settings = settings,
                onUpdate = { updated ->
                    scope.launch { settingsManager.updateGlobalSettings { updated } }
                }
            )
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun OrbitalCarousel(
    themes: List<CustomTheme>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    settings: GlobalSettings
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 100.dp),
            beyondViewportPageCount = 3
        ) { pageIndex ->
            // Calculate proximity to the "Zenith" (center)
            val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
            val absOffset = abs(pageOffset)
            
            // Focus amount: 1.0 at center, 0.0 at edges
            val focusAmount = (1f - absOffset).coerceIn(0f, 1f)
            
            // Orbital Transformation (Circular Path)
            val angle = pageOffset * 0.5f // Radian-ish
            val radius = 280f
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 3D Circular Orbit Emulation
                        // translationZ is not supported in graphicsLayer, using scale/alpha to simulate depth
                        translationX = -sin(angle) * radius
                        
                        val scale = lerp(0.7f, 1.35f, focusAmount)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.3f, 1f, focusAmount)
                        
                        // Apply perspective tilt
                        rotationX = 10f * (1f - focusAmount) 
                        cameraDistance = 1500f
                        
                        // Vertical "Float" drift for unselected
                        if (focusAmount < 0.9f) {
                            translationY = sin(System.currentTimeMillis() / 800f + pageIndex) * 8f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                OrbitalTomeArtifact(
                    theme = themes[pageIndex],
                    focusAmount = focusAmount,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun OrbitalTomeArtifact(
    theme: CustomTheme,
    focusAmount: Float,
    settings: GlobalSettings
) {
    // Dynamic Hinge Angle: -15deg (Closed) to -165deg (Open)
    // We only start opening once the focusAmount is > 0.5 for a "late bloom" effect
    val bloomProgress = ((focusAmount - 0.4f) / 0.6f).coerceIn(0f, 1f)
    val rotationAngle = lerp(-15f, -165f, bloomProgress)
    
    val palette = theme.palette
    val bg = Color(palette.readerBackground)
    val fg = Color(palette.readerForeground)
    val primary = Color(palette.primary)

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Proximity Aura
        Box(
            modifier = Modifier
                .fillMaxSize(1.4f)
                .drawBehind {
                    if (focusAmount > 0.1f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primary.copy(alpha = 0.25f * focusAmount), Color.Transparent)
                            ),
                            radius = size.width * (0.8f + focusAmount)
                        )
                    }
                }
        )

        // 2. The Internal Extruded Block
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp))
                .background(bg)
                .drawBehind {
                    // Precision Page Edges
                    for (i in 0..8) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.05f),
                            start = Offset(size.width - (i * 1.2).dp.toPx(), 0f),
                            end = Offset(size.width - (i * 1.2).dp.toPx(), size.height),
                            strokeWidth = 0.4.dp.toPx()
                        )
                    }
                    // Gutter Shadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.3f * bloomProgress), Color.Transparent),
                            startX = 0f,
                            endX = 42.dp.toPx()
                        ),
                        size = size.copy(width = 42.dp.toPx())
                    )
                }
                .padding(horizontal = (settings.horizontalPadding * 0.45f).dp, vertical = 24.dp)
        ) {
            if (bloomProgress > 0.1f) {
                Column(modifier = Modifier.alpha(bloomProgress)) {
                    Text(
                        text = "ORBITAL ZENITH",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                        color = fg.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Text("O", color = fg, style = MaterialTheme.typography.displaySmall.copy(fontSize = (settings.fontSize * 0.9f).sp, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Alignment achieved. The theme's frequency resonance is now at maximum amplitude...",
                            color = fg.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = (settings.fontSize * 0.35f).sp,
                                lineHeight = (settings.fontSize * settings.lineHeight * 0.35f).sp
                            )
                        )
                    }
                }
            }
        }

        // 3. The Responsive Cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotationAngle
                    cameraDistance = 1500f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        ) {
            val isInterior = rotationAngle < -90f
            
            if (!isInterior) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 1.dp, bottomStart = 1.dp))
                        .background(Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.85f))))
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 1.dp, bottomStart = 1.dp))
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(theme.name.uppercase(), color = Color.Black.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Icon(Icons.Default.Waves, null, modifier = Modifier.size(24.dp).alpha(0.04f), tint = Color.Black)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 1.dp, bottomEnd = 1.dp))
                        .background(bg.copy(alpha = 0.98f))
                        .border(0.5.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 1.dp, bottomEnd = 1.dp))
                )
            }
        }

        // 4. Spine
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .offset(x = (-6).dp)
                .background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.4f), primary.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.1f))),
                    RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                )
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .background(Color.White.copy(alpha = 0.02f), CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
        
        Text(
            text = "ORBITAL RESONANCE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraLight, letterSpacing = 8.sp),
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun LiquidGlassConsole(
    theme: CustomTheme,
    index: Int,
    total: Int,
    settings: GlobalSettings,
    onUpdate: (GlobalSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(ObsidianRefraction)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(32.dp))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = theme.name.uppercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 6.sp),
                    color = Color.White
                )
                Text(
                    text = "RESONANCE FREQUENCY $index / $total",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                    color = CelestialCyan.copy(alpha = 0.5f)
                )
            }
            
            Surface(
                onClick = { /* New */ },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, CelestialCyan.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CelestialCyan, StarViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = DeepVoid, modifier = Modifier.size(24.dp))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
            GlassInstrument("MAGNITUDE", settings.fontSize.toFloat(), 12f..32f, "${settings.fontSize}", Icons.Default.FormatSize) { onUpdate(settings.copy(fontSize = it.toInt())) }
            GlassInstrument("RHYTHM", settings.lineHeight, 1.0f..2.5f, String.format("%.1f", settings.lineHeight), Icons.Default.FormatLineSpacing) { onUpdate(settings.copy(lineHeight = it)) }
            GlassInstrument("BREATH", settings.horizontalPadding.toFloat(), 8f..48f, "${settings.horizontalPadding}", Icons.Default.FormatIndentIncrease) { onUpdate(settings.copy(horizontalPadding = it.toInt())) }
        }

        Button(
            onClick = { /* Edit */ },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.03f)),
            border = BorderStroke(0.5.dp, GlassBorder)
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = CelestialCyan)
            Spacer(Modifier.width(12.dp))
            Text("REFINE SURFACE PARAMETERS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun GlassInstrument(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, icon: ImageVector, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.08f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.3f), letterSpacing = 4.sp)
            }
            Text(display, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = CelestialCyan.copy(alpha = 0.4f),
                inactiveTrackColor = Color.White.copy(alpha = 0.05f)
            )
        )
    }
}

private fun com.epubreader.core.model.ThemeOption.toCustomTheme(): CustomTheme {
    return CustomTheme(id = this.id, name = this.name, palette = this.palette, isAdvanced = false)
}
