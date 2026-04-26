package com.epubreader.feature.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun activeSelection_showsBothSelectionHandlesImmediately() {
        composeRule.setReaderSelectionContent()

        composeRule.activateSelection()

        composeRule.onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true).assertExists()
    }
}
