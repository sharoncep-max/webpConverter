package com.example.webpconverter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.data.SourceFile
import com.example.webpconverter.ui.ConverterScreen
import com.example.webpconverter.ui.ConverterState
import com.example.webpconverter.ui.WebpConverterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileManagementUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            // The release APK deliberately does not declare the Compose test-host activity.
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = ComponentActivity::class.java.name
                packageName = context.packageName
                exported = true
                theme = android.R.style.Theme_Material_Light_NoActionBar
            })
        }
    }).around(compose)
    private val availableFiles = listOf(
        SourceFile("one", "landscape.webp", 421_000),
        SourceFile("two", "portrait.webp", 186_000),
        SourceFile("three", "notes.txt", 100),
        SourceFile("four", "report.pdf", 200)
    )
    private var state by mutableStateOf(ConverterState(
        restoring = false, input = "content://provider/tree/primary%3APictures%2FWebP",
        output = "content://provider/tree/primary%3APictures%2FOutput",
        files = availableFiles.filter { FileTypeFilter.WEBP.matches(it.name) },
        selected = setOf("one"), management = true
    ))
    private var operations = 0

    private fun changeView(management: Boolean, filter: FileTypeFilter = state.fileType) {
        val visibleType = if (management) filter else FileTypeFilter.WEBP
        val files = if (state.input == null) emptyList()
            else availableFiles.filter { visibleType.matches(it.name) }
        state = state.copy(
            management = management, fileType = filter, files = files,
            selected = state.selected.intersect(files.map { it.uri }.toSet())
        )
    }

    private fun render() {
        compose.setContent {
            WebpConverterTheme {
                ConverterScreen(
                    state, onInput = {}, onOutput = {}, onRefresh = {},
                    onToggle = { uri -> state = state.copy(
                        selected = if (uri in state.selected) state.selected - uri else state.selected + uri
                    ) },
                    onSelectAll = { state = state.copy(
                        selected = if (state.selected.size == state.files.size) emptySet()
                            else state.files.map { it.uri }.toSet()
                    ) },
                    onFormat = { state = state.copy(format = it) },
                    onConvert = { operations++ }, onCancel = {}, onDismissError = {},
                    onManagement = { changeView(it) },
                    onFileAction = { state = state.copy(fileAction = it) },
                    onFileType = { changeView(state.management, it) },
                    onReset = { state = ConverterState(restoring = false, management = state.management) }
                )
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val directory = File("build/previews").apply { check(isDirectory || mkdirs()) }
                File(directory, "$name.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun copyAndMoveRequireDestinationAndMoveConfirmation() {
        state = state.copy(output = null)
        render()
        compose.onNodeWithText("Copy 1 selected files").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(output = "content://provider/tree/primary%3APictures%2FOutput") }
        compose.onNodeWithText("Copy 1 selected files").assertIsEnabled().performClick()
        assertEquals(1, operations)
        screenshot("screen-manage")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Move", substring = false))
        compose.onNodeWithText("Move", substring = false).performClick()
        compose.onNodeWithText("Move 1 selected files").performClick()
        assertEquals(1, operations)
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertEquals(1, operations)
        compose.onNodeWithText("Move 1 selected files").performClick()
        compose.onNodeWithText("Move files").performClick()
        assertEquals(2, operations)
    }

    @Test fun deleteRequiresConfirmationAndSelectionIsSharedAcrossTabs() {
        state = state.copy(output = null, fileAction = BatchOperation.DELETE)
        render()
        compose.onNodeWithText("Delete 1 selected files").assertIsEnabled().performClick()
        assertEquals(0, operations)
        screenshot("screen-delete")
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Select all", substring = false))
        compose.onNodeWithText("Select all", substring = false).performClick()
        compose.onNodeWithText("Delete 2 selected files").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        assertEquals(1, operations)
        compose.onNodeWithText("Convert", substring = false).performClick()
        compose.onNodeWithText("Convert 2 selected files").assertIsNotEnabled()
        compose.onNodeWithText("Manage files", substring = false).performClick()
        compose.onNodeWithText("Delete 2 selected files").assertIsEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Select all", substring = false))
        compose.onNodeWithText("Select all", substring = false).performClick()
        compose.onNodeWithText("Delete 0 selected files").assertIsNotEnabled()
    }

    @Test fun conversionTabStillWorks() {
        state = state.copy(management = false)
        render()
        compose.onNodeWithText("Convert 1 selected files").assertIsEnabled().performClick()
        assertEquals(1, operations)
        screenshot("screen-light")
    }

    @Test fun otherFilesFilterLimitsSelectionAndDestructiveConfirmation() {
        state = state.copy(fileAction = BatchOperation.DELETE)
        render()
        compose.onNodeWithText("WebP only").assertIsSelected()
        compose.onNodeWithText("Other files").performClick()
        compose.onNodeWithText("Other files").assertIsSelected()
        screenshot("screen-other-files")
        compose.onNodeWithText("Delete 0 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("notes.txt"))
        compose.onNodeWithText("notes.txt").assertExists()
        compose.onNodeWithText("landscape.webp").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Select all", substring = false))
        compose.onNodeWithText("Select all", substring = false).performClick()
        compose.onNodeWithText("Delete 2 selected files").performClick()
        compose.onNodeWithText("File type: Other files (not .webp)").assertExists()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertEquals(0, operations)

        compose.onNodeWithText("Convert", substring = false).performClick()
        compose.onNodeWithText("Convert 0 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("landscape.webp"))
        compose.onNodeWithText("landscape.webp").assertExists()
        compose.onNodeWithText("notes.txt").assertDoesNotExist()
        compose.onNodeWithText("Manage files", substring = false).performClick()
        compose.onNodeWithText("Delete 0 selected files").assertIsNotEnabled()
    }

    @Test fun resetClearsSharedFieldsAndSelectionOnBothTabs() {
        state = state.copy(fileAction = BatchOperation.DELETE)
        render()
        compose.onNodeWithText("Reset").assertIsEnabled().performClick()
        compose.onNodeWithText("Copy 0 selected files").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Choose an input folder to begin."))
        compose.onNodeWithText("Choose an input folder to begin.").assertExists()
        compose.onNodeWithText("landscape.webp").assertDoesNotExist()
        compose.onNodeWithText("Convert", substring = false).performClick()
        compose.onNodeWithText("Convert 0 selected files").assertIsNotEnabled()
        assertEquals(null, state.input)
        assertEquals(null, state.output)
        assertEquals(0, operations)
    }

    @Test fun resetIsDisabledWhileSubmitting() {
        state = state.copy(submitting = true)
        render()
        compose.onNodeWithText("Reset").assertIsNotEnabled()
        compose.onNodeWithText("Other files").assertIsNotEnabled()
        assertEquals(2, state.files.size)
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-night-xxhdpi")
    fun darkModeKeepsFileOperationsAvailable() {
        render()
        compose.onNodeWithText("Copy 1 selected files").assertIsEnabled()
        screenshot("screen-dark")
    }
}
