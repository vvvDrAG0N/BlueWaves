package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandle
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandleLayer
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandleSemanticsKey
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionHandleUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionHandleVisibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startHandle_keepsItsNodeButHidesItsVisualsWhileDragged() {
        val draggedHandle = mutableStateOf<ReaderSelectionHandle?>(null)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ReaderSelectionHandleLayer(
                            startHandle = ReaderSelectionHandleUiState(
                                handle = ReaderSelectionHandle.Start,
                                anchorInHost = Offset(120f, 160f),
                            ),
                            endHandle = ReaderSelectionHandleUiState(
                                handle = ReaderSelectionHandle.End,
                                anchorInHost = Offset(220f, 160f),
                            ),
                            draggedHandle = draggedHandle.value,
                            dragPointerInHost = Offset(120f, 160f),
                            stemHeightPx = 32f,
                            color = Color.Red,
                            onHandleDragStart = { _, _ -> },
                            onHandleDrag = {},
                            onHandleDragEnd = {},
                        )
                    }
                }
            }
        }

        assertHandleHidden("reader_selection_handle_start", expectedHidden = false)
        assertHandleHidden("reader_selection_handle_end", expectedHidden = false)

        composeRule.runOnUiThread {
            draggedHandle.value = ReaderSelectionHandle.Start
        }
        composeRule.waitForIdle()

        assertHandleHidden("reader_selection_handle_start", expectedHidden = true)
        assertHandleHidden("reader_selection_handle_end", expectedHidden = false)

        composeRule.runOnUiThread {
            draggedHandle.value = null
        }
        composeRule.waitForIdle()

        assertHandleHidden("reader_selection_handle_start", expectedHidden = false)
        assertHandleHidden("reader_selection_handle_end", expectedHidden = false)
    }

    @Test
    fun endHandle_keepsItsNodeButHidesItsVisualsWhileDragged() {
        val draggedHandle = mutableStateOf<ReaderSelectionHandle?>(null)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ReaderSelectionHandleLayer(
                            startHandle = ReaderSelectionHandleUiState(
                                handle = ReaderSelectionHandle.Start,
                                anchorInHost = Offset(120f, 160f),
                            ),
                            endHandle = ReaderSelectionHandleUiState(
                                handle = ReaderSelectionHandle.End,
                                anchorInHost = Offset(220f, 160f),
                            ),
                            draggedHandle = draggedHandle.value,
                            dragPointerInHost = Offset(220f, 160f),
                            stemHeightPx = 32f,
                            color = Color.Red,
                            onHandleDragStart = { _, _ -> },
                            onHandleDrag = {},
                            onHandleDragEnd = {},
                        )
                    }
                }
            }
        }

        assertHandleHidden("reader_selection_handle_start", expectedHidden = false)
        assertHandleHidden("reader_selection_handle_end", expectedHidden = false)

        composeRule.runOnUiThread {
            draggedHandle.value = ReaderSelectionHandle.End
        }
        composeRule.waitForIdle()

        assertHandleHidden("reader_selection_handle_end", expectedHidden = true)
        assertHandleHidden("reader_selection_handle_start", expectedHidden = false)

        composeRule.runOnUiThread {
            draggedHandle.value = null
        }
        composeRule.waitForIdle()

        assertHandleHidden("reader_selection_handle_start", expectedHidden = false)
        assertHandleHidden("reader_selection_handle_end", expectedHidden = false)
    }

    private fun assertHandleHidden(
        tag: String,
        expectedHidden: Boolean,
    ) {
        val semantics = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[ReaderSelectionHandleSemanticsKey]
        if (expectedHidden) {
            assertTrue("Expected $tag to hide its visuals, but was $semantics", semantics.isHidden)
        } else {
            assertFalse("Expected $tag to stay visible, but was $semantics", semantics.isHidden)
        }
    }
}
