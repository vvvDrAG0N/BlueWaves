package com.epubreader.feature.reader.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.GlobalSettingsTransform
import com.epubreader.feature.reader.ReaderTheme

private const val ReaderControlsDismissVelocityThreshold = 1_600f

@Composable
fun ReaderControls(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    onPreviewSettingsChange: ((GlobalSettingsTransform) -> Unit)? = null,
    onPersistSettingsChange: ((GlobalSettingsTransform) -> Unit)? = null,
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
    progressPercentageState: State<Float>,
    onDismiss: () -> Unit,
    isVisible: Boolean = true,
) {
    val previewSettingsChange = onPreviewSettingsChange ?: onSettingsChange
    val persistSettingsChange = onPersistSettingsChange ?: onSettingsChange

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        val density = LocalDensity.current
        val initialSheetHeightPx = with(density) { maxHeight.toPx() * 0.5f }
        val minSheetHeightPx = with(density) { 190.dp.toPx() }
        val dismissThresholdPx = with(density) { 120.dp.toPx() }
        val maxSheetHeightPx = with(density) {
            val topBarHeightPx = 64.dp.toPx()
            val statusBarHeightPx = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
            maxHeight.toPx() - topBarHeightPx - statusBarHeightPx
        }

        var desiredSheetHeightPx by remember(maxHeight) { mutableFloatStateOf(initialSheetHeightPx) }

        LaunchedEffect(isVisible) {
            if (isVisible) {
                desiredSheetHeightPx = initialSheetHeightPx
            }
        }

        var sheetChromeHeightPx by remember { mutableIntStateOf(0) }
        val contentMaxHeightDp = with(density) {
            (desiredSheetHeightPx - sheetChromeHeightPx)
                .coerceAtLeast(0f)
                .toDp()
        }
        val contentScrollState = rememberScrollState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { desiredSheetHeightPx.toDp() })
                .testTag("reader_controls_sheet"),
            shape = RectangleShape,
            colors = CardDefaults.cardColors(
                containerColor = themeColors.background.copy(alpha = 0.98f),
                contentColor = themeColors.foreground,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = themeColors.foreground.copy(alpha = 0.15f),
                )
                Column(
                    modifier = Modifier.testTag("reader_controls_chrome")
                        .padding(0.dp),
                ) {
                    ReaderControlsDragHandle(
                        themeColors = themeColors,
                        onDragDelta = { delta ->
                            desiredSheetHeightPx =
                                (desiredSheetHeightPx - delta).coerceIn(0f, maxSheetHeightPx)
                        },
                        onDragStopped = { velocity ->
                            val dismissByHeight = desiredSheetHeightPx < dismissThresholdPx
                            val dismissByFling =
                                velocity > ReaderControlsDismissVelocityThreshold &&
                                    desiredSheetHeightPx <= initialSheetHeightPx

                            if (dismissByHeight || dismissByFling) {
                                onDismiss()
                            } else if (desiredSheetHeightPx < minSheetHeightPx) {
                                desiredSheetHeightPx = minSheetHeightPx
                            }
                        },
                    )

                    HorizontalDivider(
                        color = themeColors.foreground.copy(alpha = 0.2f),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = contentMaxHeightDp)
                        .verticalScroll(contentScrollState)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ReaderChapterControlsSection(
                        themeColors = themeColors,
                        onNavigatePrev = onNavigatePrev,
                        onNavigateNext = onNavigateNext,
                        listState = listState,
                        itemCount = itemCount,
                        currentChapterIndex = currentChapterIndex,
                        totalChapters = totalChapters,
                        sectionLabel = sectionLabel,
                        progressPercentageState = progressPercentageState,
                    )
                    HorizontalDivider(color = themeColors.foreground.copy(alpha = 0.08f))
                    ReaderThemeControlsSection(
                        settings = settings,
                        onSettingsChange = persistSettingsChange,
                        isVisible = isVisible,
                        themeColors = themeColors,
                    )
                    HorizontalDivider(color = themeColors.foreground.copy(alpha = 0.08f))
                    ReaderFontControlsSection(
                        settings = settings,
                        onPreviewSettingsChange = previewSettingsChange,
                        onPersistSettingsChange = persistSettingsChange,
                        isVisible = isVisible,
                        themeColors = themeColors,
                    )
                    HorizontalDivider(color = themeColors.foreground.copy(alpha = 0.08f))
                    ReaderReadingControlsSection(
                        settings = settings,
                        onSettingsChange = persistSettingsChange,
                        themeColors = themeColors,
                    )
                    HorizontalDivider(color = themeColors.foreground.copy(alpha = 0.08f))
                    ReaderOtherControlsSection(
                        settings = settings,
                        onSettingsChange = persistSettingsChange,
                        isVisible = isVisible,
                        themeColors = themeColors,
                    )
                }
            }
        }
    }
}
