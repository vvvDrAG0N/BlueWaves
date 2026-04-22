package com.epubreader.feature.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.*
import com.epubreader.core.ui.KarlaFont
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.launch

// Performance Tokens: Pure OLED Efficiency
private val PureBlack = Color(0xFF000000)
private const val InfinitePageCount = 10000

@Composable
fun ThemeStudioScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val settings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    
    val allThemes = remember(settings.customThemes) {
        val builtIn = BuiltInThemeOptions.map { opt ->
            CustomTheme(id = opt.id, name = opt.name, palette = opt.palette)
        }
        builtIn + settings.customThemes
    }
    
    val initialPage = remember(allThemes) {
        val startIndex = allThemes.indexOfFirst { it.id == settings.theme }.coerceAtLeast(0)
        (InfinitePageCount / 2) - ((InfinitePageCount / 2) % allThemes.size) + startIndex
    }
    
    val pagerState = rememberPagerState(initialPage = initialPage) { InfinitePageCount }
    
    LaunchedEffect(pagerState.currentPage) {
        val actualIndex = pagerState.currentPage % allThemes.size
        val themeId = allThemes[actualIndex].id
        if (themeId != settings.theme) {
            settingsManager.setActiveTheme(themeId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioHeader(onBack)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 40.dp),
                pageSpacing = 20.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val actualIndex = page % allThemes.size
                ContextualSpecimenCard(
                    theme = allThemes[actualIndex],
                    fontSize = settings.fontSize,
                    lineHeight = settings.lineHeight,
                    horizontalPadding = settings.horizontalPadding
                )
            }

            ThemeIndicators(
                count = allThemes.size,
                currentIndex = pagerState.currentPage % allThemes.size
            )
            
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun ContextualSpecimenCard(
    theme: CustomTheme,
    fontSize: Int,
    lineHeight: Float,
    horizontalPadding: Int
) {
    val p = theme.palette
    
    // Scale settings for the large specimen card context
    val baseLineHeight = 8.dp * (fontSize.toFloat() / 18f)
    val spacingBetweenLines = baseLineHeight * (lineHeight - 1f) + 8.dp
    val internalPadding = 24.dp * (horizontalPadding.toFloat() / 16f)
    val constrainedPadding = if (internalPadding > 40.dp) 40.dp else internalPadding

    // Outer Container representing the 'Background' token (Stable UI Zone)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(p.background))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Inner "App" Simulation
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(p.readerBackground))
                .border(1.dp, Color(p.outline).copy(alpha = 0.1f), RoundedCornerShape(24.dp))
        ) {
            // 1. System Representation (Stable UI Zone)
            SystemBarMock(p)

            Column(
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .padding(horizontal = (constrainedPadding + 24.dp)) // Dynamic Padding
            ) {
                // 2. Reader Representation (Affected by reader settings)
                
                // Stable Title
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(p.readerForeground),
                    fontWeight = FontWeight.Bold,
                    fontFamily = KarlaFont
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Reactive Reader Content
                Column(verticalArrangement = Arrangement.spacedBy(spacingBetweenLines)) {
                    SkeletonLine(
                        color = Color(p.readerForeground), 
                        widthPercent = 0.8f,
                        height = baseLineHeight
                    )
                    // Highlighting a part of the "text" with Primary
                    Row(modifier = Modifier.fillMaxWidth(1.0f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonLine(
                            color = Color(p.readerForeground).copy(alpha = 0.5f), 
                            widthPercent = 0.4f,
                            height = baseLineHeight
                        )
                        SkeletonLine(
                            color = Color(p.primary), 
                            widthPercent = 0.3f,
                            height = baseLineHeight
                        )
                        SkeletonLine(
                            color = Color(p.readerForeground).copy(alpha = 0.5f), 
                            widthPercent = 0.2f,
                            height = baseLineHeight
                        )
                    }
                    SkeletonLine(
                        color = Color(p.readerForeground).copy(alpha = 0.5f), 
                        widthPercent = 0.9f,
                        height = baseLineHeight
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // 3. UI Layer Representation (Stable UI Zone)
                SurfaceMock(p)
            }
        }
    }
}

@Composable
private fun SystemBarMock(p: ThemePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("12:00", fontSize = 10.sp, color = Color(p.systemForeground).copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(10.dp, 6.dp).background(Color(p.systemForeground).copy(alpha = 0.3f)))
            Box(modifier = Modifier.size(10.dp, 6.dp).background(Color(p.systemForeground).copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun SurfaceMock(p: ThemePalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(p.surface))
            .border(1.dp, Color(p.outline).copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(p.surfaceVariant))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("UI COMPONENT", fontSize = 8.sp, color = Color(p.systemForeground).copy(alpha = 0.4f))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            // Secondary Action
            Box(
                modifier = Modifier
                    .size(60.dp, 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(p.secondary), RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Primary Action
            Box(
                modifier = Modifier
                    .size(60.dp, 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(p.primary))
            )
        }
    }
}

@Composable
private fun SkeletonLine(color: Color, widthPercent: Float, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthPercent)
            .height(height)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
    )
}

@Composable
private fun ThemeIndicators(count: Int, currentIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { index ->
            val isSelected = index == currentIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 8.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun StudioHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = "Contextual Specimen",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Normal
        )
    }
}
