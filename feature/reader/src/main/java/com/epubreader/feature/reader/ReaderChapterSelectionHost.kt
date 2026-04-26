package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.readerTapGesture
import com.epubreader.feature.reader.internal.logReaderSelectionTransition
import com.epubreader.feature.reader.internal.ui.TextSelectionActionBar
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Composable
internal fun ReaderChapterSelectionHost(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    selectionDocument: ReaderSelectionDocument,
    selectionSessionEpoch: Int,
    onSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetDismissed: () -> Unit = {},
    content: @Composable (ReaderSelectionController) -> Unit,
) {
    val overlayScrim = remember(settings.theme, settings.customThemes) {
        androidx.compose.ui.graphics.Color(themePaletteSeed(settings.theme, settings.customThemes).overlayScrim)
            .copy(alpha = 0.45f)
    }
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    var hostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var actionBarHeightPx by remember { mutableIntStateOf(0) }
    var pendingWebLookup by remember { mutableStateOf<WebLookupAction?>(null) }
    var rawDragPointerInHost by remember(selectionDocument) { mutableStateOf<Offset?>(null) }
    var resolvedHandleTarget by remember(selectionDocument) { mutableStateOf<ReaderResolvedSelectionPosition?>(null) }
    var isSelectionAutoScrollActive by remember(selectionDocument) { mutableStateOf(false) }

    val selectionState = remember(selectionDocument) { ReaderSelectionState() }
    val layoutRegistry = remember(selectionDocument) { ReaderSelectionLayoutRegistry() }

    fun clearSelection() {
        logReaderSelectionTransition {
            "selection.clear active=${selectionState.isActive} usable=${selectionState.hasUsableSelection(selectedTextLength = selectionDocument.extractSelectedText(selectionState.normalizedSelection).length)} epoch=$selectionSessionEpoch docLen=${selectionDocument.totalTextLength}"
        }
        selectionState.clear()
        rawDragPointerInHost = null; resolvedHandleTarget = null; isSelectionAutoScrollActive = false
    }

    fun buildDisabledController(): ReaderSelectionController {
        return ReaderSelectionController(
            selectionEnabled = false,
            selectionActive = false,
            selectionSessionPhase = ReaderSelectionSessionPhase.Idle,
            isHandleDragActive = false,
            hostOriginInRoot = hostOriginInRoot,
            highlightedRangesBySection = emptyMap(),
            startSelectionAt = { _, _ -> },
            updateSelectionGesture = { _, _ -> },
            finishSelectionGesture = {},
            updateSectionLayout = { _ -> },
            removeSectionLayout = { _ -> },
            clearSelection = {},
            startHandleDrag = { _, _ -> },
            updateHandleDrag = {},
            finishHandleDrag = {},
        )
    }

    fun selectionOffsetForResolvedPosition(
        position: ReaderResolvedSelectionPosition,
        handle: ReaderSelectionHandle,
    ): Int? {
        val section = selectionDocument.sectionById(position.sectionId) ?: return null
        return when (selectionState.dragSource) {
            ReaderSelectionDragSource.Handle -> position.documentOffset
            ReaderSelectionDragSource.SelectionGesture,
            null,
            -> {
                val snappedLocalOffset = snapReaderSelectionOffsetToWordBoundary(
                    text = section.text,
                    rawOffset = position.localOffset,
                    handle = handle,
                ).coerceIn(0, section.text.length)
                section.documentStart + snappedLocalOffset
            }
        }
    }

    fun constrainHandleDragPointer(pointerInHost: Offset, handle: ReaderSelectionHandle? = selectionState.draggedHandle): Offset {
        if (!shouldClampReaderSelectionDragPointer(selectionState.dragSource)) return pointerInHost
        val draggedHandle = handle ?: return pointerInHost
        val handleInsetPx = with(density) { 16.dp.toPx() }; val edgeZonePx = with(density) { 72.dp.toPx() }; val textClearancePx = with(density) { 4.dp.toPx() }
        val visualHeightPx = with(density) { 14.dp.toPx() } + with(density) { settings.fontSize.sp.toPx() * 0.5f }
        return clampReaderSelectionHandlePointerToVisibleSafeArea(
            pointerInHost = pointerInHost,
            hostWidth = hostSize.width.toFloat(),
            hostHeight = hostSize.height.toFloat(),
            handle = draggedHandle,
            handleInsetPx = handleInsetPx,
            safeVerticalInsetPx = edgeZonePx,
            visualHeightPx = visualHeightPx,
            textClearancePx = textClearancePx,
        )
    }

    fun resolveDraggedHandleTarget(pointerInHost: Offset): ReaderResolvedSelectionPosition? {
        val projectedPointer = resolveReaderSelectionTargetPointer(
            pointerInHost = pointerInHost,
            dragSource = selectionState.dragSource,
        )
        return layoutRegistry.resolvePositionInVisibleSections(projectedPointer)
    }

    fun startSelectionGesture(
        sectionId: String,
        localPositionInSection: Offset,
    ) {
        val resolvedPosition = layoutRegistry.resolvePositionFromSectionPointer(
            sectionId = sectionId,
            localPositionInSection = localPositionInSection,
        ) ?: return
        val section = selectionDocument.sectionById(resolvedPosition.sectionId) ?: return
        val wordBoundary = findReaderWordBoundary(
            text = section.text,
            rawOffset = resolvedPosition.localOffset,
        )
        selectionState.startSelectionGesture(
            TextRange(
                start = section.documentStart + wordBoundary.start,
                end = section.documentStart + wordBoundary.end,
            ),
        )
    }

    fun updateDraggedHandle(pointerInHost: Offset) {
        val draggedHandle = selectionState.draggedHandle ?: return
        val resolvedPosition = resolveDraggedHandleTarget(pointerInHost)
        resolvedHandleTarget = resolvedPosition
        if (resolvedPosition == null) return
        val snappedOffset = selectionOffsetForResolvedPosition(
            position = resolvedPosition,
            handle = draggedHandle,
        ) ?: return
        selectionState.updateDraggedHandle(snappedOffset)
    }

    fun continueHandleDrag(pointerInHost: Offset) {
        rawDragPointerInHost = pointerInHost
        val constrainedPointer = constrainHandleDragPointer(pointerInHost)
        selectionState.updateDragPointer(constrainedPointer)
        updateDraggedHandle(constrainedPointer)
    }

    fun updateSelectionGesture(
        sectionId: String,
        localPositionInSection: Offset,
    ) {
        val resolvedPosition = layoutRegistry.resolvePositionFromSectionPointer(
            sectionId = sectionId,
            localPositionInSection = localPositionInSection,
        ) ?: return
        if (!selectionState.isHandleDragActive) {
            selectionState.startDraggingHandle(
                handle = ReaderSelectionHandle.End,
                pointerInHost = resolvedPosition.positionInHost,
                source = ReaderSelectionDragSource.SelectionGesture,
            )
        }
        continueHandleDrag(resolvedPosition.positionInHost)
    }

    fun finishSelectionGesture() {
        selectionState.finishSelectionGesture()
        rawDragPointerInHost = null; resolvedHandleTarget = null; isSelectionAutoScrollActive = false
    }

    fun startHandleDrag(
        handle: ReaderSelectionHandle,
        pointerInHost: Offset,
    ) {
        rawDragPointerInHost = pointerInHost
        val constrainedPointer = constrainHandleDragPointer(pointerInHost, handle)
        selectionState.startDraggingHandle(
            handle = handle,
            pointerInHost = constrainedPointer,
            source = ReaderSelectionDragSource.Handle,
        )
        updateDraggedHandle(constrainedPointer)
        logReaderSelectionTransition {
            "selection.drag.start epoch=$selectionSessionEpoch raw=$rawDragPointerInHost pinned=${selectionState.dragPointerInHost} resolved=${resolvedHandleTarget?.documentOffset}"
        }
    }

    fun finishHandleDrag() {
        logReaderSelectionTransition {
            "selection.drag.end epoch=$selectionSessionEpoch raw=$rawDragPointerInHost pinned=${selectionState.dragPointerInHost} resolved=${resolvedHandleTarget?.documentOffset}"
        }
        selectionState.finishHandleDrag()
        rawDragPointerInHost = null; resolvedHandleTarget = null; isSelectionAutoScrollActive = false
    }

    val highlightedRangesBySection = remember(selectionDocument, selectionState.normalizedSelection) {
        buildMap {
            selectionDocument.sections.forEach { section ->
                selectionDocument.rangeInSection(
                    sectionId = section.sectionId,
                    selection = selectionState.normalizedSelection,
                )?.let { sectionRange ->
                    put(section.sectionId, sectionRange)
                }
            }
        }
    }
    val normalizedSelection = selectionState.normalizedSelection
    val selectedText = remember(selectionDocument, normalizedSelection) {
        selectionDocument.extractSelectedText(normalizedSelection)
    }
    val selectionSessionPhase = selectionState.resolveSessionPhase(selectedTextLength = selectedText.length)
    val hasUsableSelectionSession = selectionSessionPhase != ReaderSelectionSessionPhase.Idle

    val selectionController = remember(
        selectionDocument,
        layoutRegistry,
        settings.selectableText,
        hasUsableSelectionSession,
        selectionSessionPhase,
        selectionState.isHandleDragActive,
        hostOriginInRoot,
        highlightedRangesBySection,
    ) {
        if (!settings.selectableText) {
            buildDisabledController()
        } else {
            ReaderSelectionController(
                selectionEnabled = true,
                selectionActive = hasUsableSelectionSession,
                selectionSessionPhase = selectionSessionPhase,
                isHandleDragActive = selectionState.isHandleDragActive,
                hostOriginInRoot = hostOriginInRoot,
                highlightedRangesBySection = highlightedRangesBySection,
                startSelectionAt = ::startSelectionGesture,
                updateSelectionGesture = ::updateSelectionGesture,
                finishSelectionGesture = ::finishSelectionGesture,
                updateSectionLayout = layoutRegistry::update,
                removeSectionLayout = layoutRegistry::remove,
                clearSelection = ::clearSelection,
                startHandleDrag = ::startHandleDrag,
                updateHandleDrag = ::continueHandleDrag,
                finishHandleDrag = ::finishHandleDrag,
            )
        }
    }

    DisposableEffect(selectionSessionEpoch, selectionDocument) {
        logReaderSelectionTransition {
            "selection.host.start epoch=$selectionSessionEpoch sections=${selectionDocument.sections.size} docLen=${selectionDocument.totalTextLength}"
        }
        onDispose {
            logReaderSelectionTransition {
                "selection.host.dispose epoch=$selectionSessionEpoch sections=${selectionDocument.sections.size} docLen=${selectionDocument.totalTextLength}"
            }
        }
    }

    LaunchedEffect(selectionDocument, normalizedSelection, selectedText) {
        if (shouldClearReaderStaleSelection(
                normalizedSelection = normalizedSelection,
                selectedText = selectedText,
                isHandleDragActive = selectionState.isHandleDragActive,
            )
        ) {
            logReaderSelectionTransition {
                "selection.host.clearStaleSelection epoch=$selectionSessionEpoch active=${selectionState.isActive} docLen=${selectionDocument.totalTextLength}"
            }
            clearSelection()
        }
    }

    LaunchedEffect(selectionSessionEpoch, hasUsableSelectionSession, selectionSessionPhase) {
        logReaderSelectionTransition {
            "selection.host.active epoch=$selectionSessionEpoch usable=$hasUsableSelectionSession phase=$selectionSessionPhase raw=${selectionState.isActive} selectedLen=${selectedText.length}"
        }
        onSelectionActiveChange(selectionSessionEpoch, hasUsableSelectionSession)
    }

    LaunchedEffect(selectionSessionEpoch, selectionSessionPhase) {
        onSelectionHandleDragChange(
            selectionSessionEpoch,
            selectionSessionPhase == ReaderSelectionSessionPhase.HandleDragging,
        )
    }

    LaunchedEffect(selectionState.isHandleDragActive, hostSize) {
        if (!selectionState.isHandleDragActive || hostSize.height <= 0) {
            return@LaunchedEffect
        }

        val edgeZonePx = with(density) { 72.dp.toPx() }
        while (currentCoroutineContext().isActive && selectionState.isHandleDragActive) {
            val scrollDelta = resolveReaderSelectionAutoScrollDelta(
                pointerY = rawDragPointerInHost?.y,
                hostHeight = hostSize.height.toFloat(),
                edgeZonePx = edgeZonePx,
                hasValidResolvedTarget = resolvedHandleTarget != null,
            )
            val shouldAutoScroll = scrollDelta != 0f
            if (shouldAutoScroll != isSelectionAutoScrollActive) {
                isSelectionAutoScrollActive = shouldAutoScroll
                logReaderSelectionTransition {
                    "selection.drag.autoScroll epoch=$selectionSessionEpoch active=$isSelectionAutoScrollActive raw=$rawDragPointerInHost pinned=${selectionState.dragPointerInHost} resolved=${resolvedHandleTarget?.documentOffset}"
                }
            }

            if (scrollDelta != 0f) {
                val consumed = listState.scrollBy(scrollDelta)
                if (consumed == 0f) {
                    isSelectionAutoScrollActive = false
                }
                selectionState.dragPointerInHost?.let(::updateDraggedHandle)
            }

            androidx.compose.runtime.withFrameNanos { }
        }
    }

    val startHandle = normalizedSelection?.let { selection ->
        resolveVisibleReaderSelectionHandleAnchor(
            layoutRegistry.resolveHandleAnchor(
                offset = selection.start,
                affinity = ReaderSelectionOffsetAffinity.Downstream,
                document = selectionDocument,
            ),
            hostSize = hostSize,
        )?.let { anchor ->
            ReaderSelectionHandleUiState(
                handle = ReaderSelectionHandle.Start,
                anchorInHost = anchor,
            )
        }
    }
    val endHandle = normalizedSelection?.let { selection ->
        resolveVisibleReaderSelectionHandleAnchor(
            layoutRegistry.resolveHandleAnchor(
                offset = selection.end,
                affinity = ReaderSelectionOffsetAffinity.Upstream,
                document = selectionDocument,
            ),
            hostSize = hostSize,
        )?.let { anchor ->
            ReaderSelectionHandleUiState(
                handle = ReaderSelectionHandle.End,
                anchorInHost = anchor,
            )
        }
    }
    val shouldShowSelectionHandles = hasUsableSelectionSession &&
        (startHandle != null || endHandle != null || selectionState.isHandleDragActive)

    if (!settings.selectableText) {
        content(selectionController)
        return
    }

    val selectionActionBarBottomPadding =
        if (settings.readerStatusUi.isEnabled && !settings.showSystemBar) 40.dp else 24.dp
    val estimatedActionBarHeightPx =
        if (actionBarHeightPx > 0) actionBarHeightPx else with(density) { 72.dp.roundToPx() }
    val actionBarCollisionZonePx = estimatedActionBarHeightPx + with(density) {
        (selectionActionBarBottomPadding + 24.dp).roundToPx()
    }
    val actionBarReferenceY = listOfNotNull(
        startHandle?.anchorInHost?.y,
        endHandle?.anchorInHost?.y,
        selectionState.dragPointerInHost?.y,
    ).maxOrNull()
    val handleStemHeightPx = with(density) { settings.fontSize.sp.toPx() * 0.5f }
    val moveSelectionActionBarToTop = shouldMoveReaderSelectionActionBarToTop(
        actionBarReferenceY = actionBarReferenceY,
        hostHeight = hostSize.height,
        actionBarCollisionZonePx = actionBarCollisionZonePx,
    )
    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                hostOriginInRoot = coordinates.positionInRoot()
                hostSize = coordinates.size
            },
    ) {
        val contentTapDismissModifier =
            if (hasUsableSelectionSession) {
                Modifier.readerTapGesture(onTap = ::clearSelection)
            } else {
                Modifier
            }

        Box(modifier = contentTapDismissModifier) {
            content(selectionController)
        }
        if (shouldShowSelectionHandles) {
            ReaderSelectionHandleLayer(
                startHandle = startHandle,
                endHandle = endHandle,
                draggedHandle = selectionState.draggedHandle,
                dragPointerInHost = selectionState.dragPointerInHost,
                stemHeightPx = handleStemHeightPx,
                color = themeColors.primary,
                onHandleDragStart = selectionController::startHandleDrag,
                onHandleDrag = selectionController::updateHandleDrag,
                onHandleDragEnd = selectionController::finishHandleDrag,
            )
        }
        AnimatedVisibility(
            visible = hasUsableSelectionSession,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(if (moveSelectionActionBarToTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(
                    if (moveSelectionActionBarToTop) {
                        Modifier
                            .padding(top = 24.dp)
                            .statusBarsPadding()
                    } else {
                        Modifier
                            .padding(bottom = selectionActionBarBottomPadding)
                            .navigationBarsPadding()
                    },
                )
                .onGloballyPositioned { coordinates ->
                    actionBarHeightPx = coordinates.size.height
                },
        ) {
            TextSelectionActionBar(
                themeColors = themeColors,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(selectedText))
                    clearSelection()
                },
                onDefine = {
                    if (selectedText.isNotBlank()) {
                        pendingWebLookup = WebLookupAction.Define(selectedText)
                    }
                    clearSelection()
                },
                onTranslate = {
                    if (selectedText.isNotBlank()) {
                        pendingWebLookup = WebLookupAction.Translate(
                            text = selectedText,
                            targetLanguage = settings.targetTranslationLanguage,
                        )
                    }
                    clearSelection()
                },
            )
        }
    }
    pendingWebLookup?.let { action ->
        ReaderLookupWebViewBottomSheet(
            url = action.url,
            scrimColor = overlayScrim,
            onDismiss = { pendingWebLookup = null; onLookupSheetDismissed() },
        )
    }
}
internal fun shouldClampReaderSelectionDragPointer(dragSource: ReaderSelectionDragSource?): Boolean = dragSource != null
