package com.example.webpconverter

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.webpconverter.data.SourceFile
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.ui.ConverterScreen
import com.example.webpconverter.ui.ConverterState
import com.example.webpconverter.ui.WebpConverterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConverterScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionAndDestinationGateConversion() {
        var state by mutableStateOf(ConverterState(
            restoring = false, input = "content://input/tree/images",
            files = listOf(SourceFile("content://input/1", "one.webp", 100))
        ))
        var conversions = 0
        compose.setContent {
            WebpConverterTheme {
                ConverterScreen(
                    state, onInput = {}, onOutput = {
                        state = state.copy(output = "content://output/tree/images")
                    }, onRefresh = {},
                    onToggle = { state = state.copy(selected = setOf(it)) },
                    onSelectAll = { state = state.copy(selected = setOf("content://input/1")) },
                    onFormat = { state = state.copy(format = it) },
                    onConvert = { conversions++ }, onCancel = {}, onDismissError = {}
                )
            }
        }
        compose.onNodeWithText("Convert 0 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("one.webp"))
        compose.onNodeWithText("one.webp").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("1 of 1 selected"))
        compose.onNodeWithText("1 of 1 selected").assertExists()
        // The Convert button lives in the pinned bottom bar, outside the scrolling list.
        compose.onNodeWithText("Convert 1 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Choose destination folder"))
        compose.onNodeWithText("Choose destination folder").performClick()
        compose.onNodeWithText("Convert 1 selected files").assertIsEnabled().performClick()
        assertEquals(1, conversions)
    }

    @Test fun tabsShareSelectionAndDeleteRequiresConfirmationWithoutDestination() {
        var state by mutableStateOf(ConverterState(
            restoring = false, input = "content://input/tree/images",
            files = listOf(SourceFile("one", "one.webp", 100), SourceFile("two", "two.webp", 200)),
            selected = setOf("one"), fileAction = BatchOperation.DELETE
        ))
        var operations = 0
        compose.setContent {
            WebpConverterTheme {
                ConverterScreen(
                    state, onInput = {}, onOutput = {}, onRefresh = {}, onToggle = {},
                    onSelectAll = { state = state.copy(selected = state.files.map { it.uri }.toSet()) },
                    onFormat = {}, onConvert = { operations++ }, onCancel = {}, onDismissError = {},
                    onManagement = { state = state.copy(management = it) },
                    onFileAction = { state = state.copy(fileAction = it) }
                )
            }
        }
        compose.onNodeWithText("Manage files").performClick()
        compose.onNodeWithText("Delete 1 selected files").assertIsEnabled().performClick()
        compose.onNodeWithText("Delete permanently").assertExists()
        assertEquals(0, operations)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, operations)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Select all"))
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Delete 2 selected files").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        assertEquals(1, operations)
        compose.onNodeWithText("Convert", substring = false).performClick()
        compose.onNodeWithText("Convert 2 selected files").assertIsNotEnabled()
    }

    @Test fun moveRequiresConfirmationAndCopyDoesNot() {
        var state by mutableStateOf(ConverterState(
            restoring = false, input = "content://input/tree/images",
            output = "content://output/tree/other", management = true, fileAction = BatchOperation.MOVE,
            files = listOf(SourceFile("one", "one.webp", 100)), selected = setOf("one")
        ))
        var operations = 0
        compose.setContent {
            WebpConverterTheme {
                ConverterScreen(
                    state, onInput = {}, onOutput = {}, onRefresh = {}, onToggle = {},
                    onSelectAll = {}, onFormat = {}, onConvert = { operations++ },
                    onCancel = {}, onDismissError = {},
                    onFileAction = { state = state.copy(fileAction = it) }
                )
            }
        }
        compose.onNodeWithText("Move 1 selected files").performClick()
        assertEquals(0, operations)
        compose.onNodeWithText("Move files").performClick()
        assertEquals(1, operations)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Copy", substring = false))
        compose.onNodeWithText("Copy", substring = false).performClick()
        compose.onNodeWithText("Copy 1 selected files").performClick()
        assertEquals(2, operations)
    }

    @Test fun otherFilesSelectionCannotLeakIntoConversion() {
        val allFiles = listOf(
            SourceFile("webp", "one.webp", 100),
            SourceFile("text", "notes.txt", 200),
            SourceFile("noext", "README", 300)
        )
        var state by mutableStateOf(ConverterState(
            restoring = false, input = "content://input/tree/images",
            output = "content://output/tree/other", management = true, fileAction = BatchOperation.DELETE,
            files = allFiles.filter { FileTypeFilter.WEBP.matches(it.name) }, selected = setOf("webp")
        ))
        fun updateView(management: Boolean, filter: FileTypeFilter) {
            val visibleType = if (management) filter else FileTypeFilter.WEBP
            val visible = allFiles.filter { visibleType.matches(it.name) }
            state = state.copy(
                management = management, fileType = filter, files = visible,
                selected = state.selected.intersect(visible.map { it.uri }.toSet())
            )
        }
        compose.setContent {
            WebpConverterTheme {
                ConverterScreen(
                    state, onInput = {}, onOutput = {}, onRefresh = {}, onToggle = {},
                    onSelectAll = { state = state.copy(selected = state.files.map { it.uri }.toSet()) },
                    onFormat = {}, onConvert = {}, onCancel = {}, onDismissError = {},
                    onManagement = { updateView(it, state.fileType) },
                    onFileType = { updateView(state.management, it) }
                )
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Other files"))
        compose.onNodeWithText("Other files").performClick()
        compose.onNodeWithText("Delete 0 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Select all"))
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Delete 2 selected files").performClick()
        compose.onNodeWithText("File type: Other files (not .webp)").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Convert", substring = false).performClick()
        compose.onNodeWithText("Convert 0 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("one.webp"))
        compose.onNodeWithText("one.webp").assertExists()
        compose.onNodeWithText("notes.txt").assertDoesNotExist()
    }
}
