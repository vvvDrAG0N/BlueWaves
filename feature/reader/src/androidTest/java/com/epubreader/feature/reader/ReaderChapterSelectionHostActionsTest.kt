package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChapterSelectionHostActionsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeSelection_tappingChapterContentClearsSelection() {
        val selectionActive = mutableStateOf(false)

        composeRule.setReaderSelectionContent(
            settings = GlobalSettings(selectableText = true),
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }

        composeRule.onNodeWithTag("selection_surface").performTouchInput {
            click(Offset(width - 10f, height - 10f))
        }

        composeRule.waitUntil(5_000) { !selectionActive.value }
        assertFalse(selectionActive.value)
    }

    @Test
    fun selectionActionBar_copyWritesSelectedTextToClipboard_andDismissesSelection() {
        val clipboardManager = composeRule.requireClipboardManager()
        val selectionActive = mutableStateOf(false)

        composeRule.setReaderSelectionContent(
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
        )
        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }
        composeRule.onNodeWithText("Copy", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString() == "Scholarship"
        }
        composeRule.waitUntil(5_000) { !selectionActive.value }

        assertEquals(
            "Scholarship",
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString(),
        )
        assertFalse(selectionActive.value)
    }

    @Test
    fun selectionActionBar_defineOpensLookupSheet_andDismissesSelection() {
        val selectionActive = mutableStateOf(false)

        composeRule.setReaderSelectionContent(
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }
        composeRule.onNodeWithText("Define", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) { !selectionActive.value }
        composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()
        assertFalse(selectionActive.value)
    }

    @Test
    fun selectionActionBar_translateOpensLookupSheet_andDismissesSelection() {
        val selectionActive = mutableStateOf(false)

        composeRule.setReaderSelectionContent(
            settings = GlobalSettings(
                selectableText = true,
                targetTranslationLanguage = "en",
            ),
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }
        composeRule.onNodeWithText("Translate", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) { !selectionActive.value }
        composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()
        assertFalse(selectionActive.value)
    }

    @Test
    fun selectionActionBar_defineLookupVisibilityCallbacks_openOnce_and_dismissOnce() {
        assertLookupVisibilityCallbackSequence(
            actionLabel = "Define",
            settings = GlobalSettings(selectableText = true),
        )
    }

    @Test
    fun selectionActionBar_translateLookupVisibilityCallbacks_openOnce_and_dismissOnce() {
        assertLookupVisibilityCallbackSequence(
            actionLabel = "Translate",
            settings = GlobalSettings(
                selectableText = true,
                targetTranslationLanguage = "en",
            ),
        )
    }

    @Test
    fun selectionActionBar_selectAllKeepsSelectionActive_withoutChangingViewportPosition() {
        val selectionActive = mutableStateOf(false)
        val listState = LazyListState()
        val chapterElements = longReaderSelectionChapterElements()

        composeRule.setReaderSelectionContent(
            settings = GlobalSettings(selectableText = true),
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
            listState = listState,
            chapterElements = chapterElements,
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }
        composeRule.waitUntil(5_000) { listState.layoutInfo.totalItemsCount > 1 }
        val initialFirstVisibleItemIndex = composeRule.runOnIdle { listState.firstVisibleItemIndex }

        composeRule.onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) {
            selectionActive.value
        }

        assertTrue(selectionActive.value)
        assertEquals(initialFirstVisibleItemIndex, listState.firstVisibleItemIndex)
    }

    @Test
    fun selectionActionBar_selectAllThenCopyWritesWholeChapterToClipboard_andDismissesSelection() {
        val clipboardManager = composeRule.requireClipboardManager()
        val selectionActive = mutableStateOf(false)
        val listState = LazyListState()
        val chapterElements = longReaderSelectionChapterElements()
        val expectedChapterText = selectionExpectedTextFor(chapterElements)

        composeRule.setReaderSelectionContent(
            settings = GlobalSettings(selectableText = true),
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
            listState = listState,
            chapterElements = chapterElements,
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }
        composeRule.waitUntil(5_000) { listState.layoutInfo.totalItemsCount > 1 }

        composeRule.onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("text_selection_action_copy", useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) {
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString() == expectedChapterText
        }
        composeRule.waitUntil(5_000) { !selectionActive.value }

        assertEquals(
            expectedChapterText,
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(composeRule.activity)
                ?.toString(),
        )
        assertFalse(selectionActive.value)
    }

    @Test
    fun selectionActionBar_selectAllDisablesDefineAndTranslate_butKeepsCopyEnabled() {
        val selectionActive = mutableStateOf(false)
        val listState = LazyListState()
        val chapterElements = longReaderSelectionChapterElements()

        composeRule.setReaderSelectionContent(
            settings = GlobalSettings(
                selectableText = true,
                targetTranslationLanguage = "en",
            ),
            onSelectionActiveChange = { _, isActive ->
                selectionActive.value = isActive
            },
            listState = listState,
            chapterElements = chapterElements,
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) { selectionActive.value }
        composeRule.waitUntil(5_000) { listState.layoutInfo.totalItemsCount > 1 }

        composeRule.onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true).performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_selection_action_copy", useUnmergedTree = true).assertIsEnabled()
        composeRule.onNodeWithTag("text_selection_action_translate", useUnmergedTree = true).assertIsNotEnabled()
        composeRule.onNodeWithTag("text_selection_action_define", useUnmergedTree = true).assertIsNotEnabled()
        assertTrue(selectionActive.value)
    }

    @Test
    fun selectionActionBar_buttonsRenderInCopySelectAllTranslateDefineOrder() {
        composeRule.setReaderSelectionContent(
            settings = GlobalSettings(
                selectableText = true,
                targetTranslationLanguage = "en",
            ),
        )

        composeRule.activateSelection()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val copyBounds = composeRule
            .onNodeWithTag("text_selection_action_copy", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val selectAllBounds = composeRule
            .onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val translateBounds = composeRule
            .onNodeWithTag("text_selection_action_translate", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val defineBounds = composeRule
            .onNodeWithTag("text_selection_action_define", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("text_selection_action_copy", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("text_selection_action_translate", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("text_selection_action_define", useUnmergedTree = true).assertExists()

        assertTrue(copyBounds.left < selectAllBounds.left)
        assertTrue(selectAllBounds.left < translateBounds.left)
        assertTrue(translateBounds.left < defineBounds.left)
    }

    @Test
    fun activeSelection_showsBothSelectionHandlesImmediately() {
        composeRule.setReaderSelectionContent()

        composeRule.activateSelection()

        composeRule.onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true).assertExists()
    }

    private fun assertLookupVisibilityCallbackSequence(
        actionLabel: String,
        settings: GlobalSettings,
    ) {
        val visibilityEvents = mutableListOf<Boolean>()
        var dismissCount = 0

        composeRule.setReaderSelectionContent(
            settings = settings,
            onLookupSheetVisibilityChange = { visibilityEvents += it },
            onLookupSheetDismissed = { dismissCount++ },
        )
        composeRule.waitForIdle()
        visibilityEvents.clear()
        dismissCount = 0

        composeRule.activateSelection()
        composeRule.onNodeWithText(actionLabel, useUnmergedTree = true).performClick()

        composeRule.waitUntil(5_000) { visibilityEvents.contains(true) }
        composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()
        assertEquals(listOf(true), visibilityEvents)
        assertEquals(0, dismissCount)

        pressBack()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("web_lookup_webview", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(5_000) { dismissCount == 1 }

        assertEquals(listOf(true), visibilityEvents)
        assertEquals(1, dismissCount)
    }
}
