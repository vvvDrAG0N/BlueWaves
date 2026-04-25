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

    val selectionState = remember(selectionDocument) { ReaderSelectionState() }
    val layoutRegistry = remember(selectionDocument) { ReaderSelectionLayoutRegistry() }

    fun clearSelection() {
        logReaderSelectionTransition {
            "selection.clear active=${selectionState.isActive} usable=${selectionState.hasUsableSelection(selectedTextLength = selectionDocument.extractSelectedText(selectionState.normalizedSelection).length)} epoch=$selectionSessionEpoch docLen=${selectionDocument.totalTextLength}"
        }
        selectionState.clear()
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
        val snappedLocalOffset = snapReaderSelectionOffsetToWordBoundary(
            text = section.text,
            rawOffset = position.localOffset,
            handle = handle,
        ).coerceIn(0, section.text.length)
        return section.documentStart + snappedLocalOffset
    }

    fun resolveVisibleHandleAnchor(anchorInHost: Offset?): Offset? {
        val resolvedAnchor = anchorInHost ?: return null
        if (hostSize.width <= 0 || hostSize.height <= 0) {
            return resolvedAnchor
        }
        val hostWidth = hostSize.width.toFloat()
        val hostHeight = hostSize.height.toFloat()
        return resolvedAnchor.takeIf { anchor ->
            anchor.x in 0f..hostWidth && anchor.y in 0f..hostHeight
        }
    }

    fun resolveSelectionTargetPointer(pointerInHost: Offset): Offset {
        val handleTextHitBiasPx = with(density) { 10.dp.toPx() }
        return resolveReaderSelectionTargetPointer(
            pointerInHost = pointerInHost,
            dragSource = selectionState.dragSource,
            handleTextHitBiasPx = handleTextHitBiasPx,
        )
    }

    fun constrainHandleDragPointer(pointerInHost: Offset): Offset {
        if (selectionState.dragSource != ReaderSelectionDragSource.Handle) {
            return pointerInHost
        }
        val handleInsetPx = with(density) { 16.dp.toPx() }
        return clampReaderSelectionHandlePointer(
            pointerInHost = pointerInHost,
            hostWidth = hostSize.width.toFloat(),
            hostHeight = hostSize.height.toFloat(),
            handleInsetPx = handleInsetPx,
        )
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
        val resolvedPosition = layoutRegistry.resolvePositionInVisibleSections(
            resolveSelectionTargetPointer(pointerInHost),
        ) ?: return
        val snappedOffset = selectionOffsetForResolvedPosition(
            position = resolvedPosition,
            handle = draggedHandle,
        ) ?: return
        selectionState.updateDraggedHandle(snappedOffset)
    }

    fun continueHandleDrag(pointerInHost: Offset) {
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
    }

    fun startHandleDrag(
        handle: ReaderSelectionHandle,
        pointerInHost: Offset,
    ) {
        val constrainedPointer = constrainHandleDragPointer(pointerInHost)
        selectionState.startDraggingHandle(
            handle = handle,
            pointerInHost = constrainedPointer,
            source = ReaderSelectionDragSource.Handle,
        )
        updateDraggedHandle(constrainedPointer)
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
                finishHandleDrag = selectionState::finishHandleDrag,
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
        if (normalizedSelection != null && normalizedSelection.collapsed.not() && selectedText.isBlank()) {
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
            val pointerInHost = selectionState.dragPointerInHost
            val scrollDelta = pointerInHost?.let {
                resolveSelectionAutoScrollDelta(
                    pointerY = it.y,
                    hostHeight = hostSize.height.toFloat(),
                    edgeZonePx = edgeZonePx,
                )
            } ?: 0f

            if (scrollDelta != 0f) {
                listState.scrollBy(scrollDelta)
                selectionState.dragPointerInHost?.let(::updateDraggedHandle)
            }

            androidx.compose.runtime.withFrameNanos { }
        }
    }

    val startHandle = normalizedSelection?.let { selection ->
        resolveVisibleHandleAnchor(
            layoutRegistry.resolveHandleAnchor(
                offset = selection.start,
                affinity = ReaderSelectionOffsetAffinity.Downstream,
                document = selectionDocument,
            ),
        )?.let { anchor ->
            ReaderSelectionHandleUiState(
                handle = ReaderSelectionHandle.Start,
                anchorInHost = anchor,
            )
        }
    }
    val endHandle = normalizedSelection?.let { selection ->
        resolveVisibleHandleAnchor(
            layoutRegistry.resolveHandleAnchor(
                offset = selection.end,
                affinity = ReaderSelectionOffsetAffinity.Upstream,
                document = selectionDocument,
            ),
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
    val moveSelectionActionBarToTop =
        actionBarReferenceY?.let { anchorY ->
            hostSize.height > 0 && anchorY >= (hostSize.height - actionBarCollisionZonePx)
        } == true

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
                },
                onDefine = {
                    if (selectedText.isNotBlank()) {
                        pendingWebLookup = WebLookupAction.Define(selectedText)
                    }
                },
                onTranslate = {
                    if (selectedText.isNotBlank()) {
                        pendingWebLookup = WebLookupAction.Translate(
                            text = selectedText,
                            targetLanguage = settings.targetTranslationLanguage,
                        )
                    }
                },
            )
        }
    }

    pendingWebLookup?.let { action ->
        ReaderLookupWebViewBottomSheet(
            url = action.url,
            scrimColor = overlayScrim,
            onDismiss = { pendingWebLookup = null },
        )
    }
}

private fun resolveSelectionAutoScrollDelta(
    pointerY: Float,
    hostHeight: Float,
    edgeZonePx: Float,
): Float {
    if (hostHeight <= 0f || edgeZonePx <= 0f) {
        return 0f
    }

    return when {
        pointerY < edgeZonePx -> {
            val strength = (1f - (pointerY / edgeZonePx)).coerceIn(0f, 1f)
            -36f * strength
        }

        pointerY > hostHeight - edgeZonePx -> {
            val distanceIntoEdge = pointerY - (hostHeight - edgeZonePx)
            val strength = (distanceIntoEdge / edgeZonePx).coerceIn(0f, 1f)
            36f * strength
        }

        else -> 0f
    }
}
