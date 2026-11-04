package com.example.webpconverter

import android.app.Application
import android.content.pm.ProviderInfo
import android.os.Looper
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.ui.ConverterState
import com.example.webpconverter.ui.ConverterViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManageFileFilterTest {
    private lateinit var app: Application
    private lateinit var input: File

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        TestDocumentsProvider.root = File(app.cacheDir, "filter-${UUID.randomUUID()}").apply { mkdirs() }
        input = File(TestDocumentsProvider.root, "input").apply { mkdirs() }
        listOf("a.WEBP", "b.webp", "document.pdf", "photo.PNG", "README").forEach {
            File(input, it).writeText(it)
        }
        File(input, "nested.webp").apply { mkdirs() }
            .resolve("hidden.txt").writeText("not a direct child")
        Robolectric.buildContentProvider(TestDocumentsProvider::class.java).create(
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                grantUriPermissions = true
                exported = true
            }
        )
        val tree = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "input")
        app.getSharedPreferences("folders", 0).edit().clear()
            .putString("input", tree.toString()).commit()
    }

    private fun await(model: ConverterViewModel, predicate: (ConverterState) -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate(model.state.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("State: ${model.state.value}", predicate(model.state.value))
    }

    @Test fun changingFiltersScopesSelectionAndConversionRemainsWebpOnly() {
        val models = ViewModelStore()
        try {
            val model = ConverterViewModel(app)
            models.put("model", model)
            await(model) { !it.restoring && !it.scanning && it.files.size == 2 }
            assertEquals(listOf("a.WEBP", "b.webp"), model.state.value.files.map { it.name })
            val webp = model.state.value.files.first().uri
            model.toggle(webp)
            model.management(true)
            assertEquals(setOf(webp), model.state.value.selected)

            model.fileType(FileTypeFilter.OTHER)
            assertEquals(listOf("document.pdf", "photo.PNG", "README"), model.state.value.files.map { it.name })
            assertTrue(model.state.value.selected.isEmpty())
            model.toggle(webp)
            assertTrue(model.state.value.selected.isEmpty())
            model.selectAll()
            assertEquals(model.state.value.files.map { it.uri }.toSet(), model.state.value.selected)
            model.fileAction(BatchOperation.DELETE)
            assertTrue(model.state.value.canRun)

            model.management(false)
            assertEquals(listOf("a.WEBP", "b.webp"), model.state.value.files.map { it.name })
            assertTrue(model.state.value.selected.isEmpty())
            assertFalse(model.state.value.canRun)
            model.management(true)
            assertEquals(FileTypeFilter.OTHER, model.state.value.fileType)
            assertEquals(3, model.state.value.files.size)
            assertTrue(model.state.value.selected.isEmpty())

            File(input, "new.txt").writeText("new")
            model.refresh()
            await(model) { !it.scanning && it.files.any { file -> file.name == "new.txt" } }
            assertEquals(4, model.state.value.files.size)
        } finally {
            models.clear()
        }
    }

    @Test fun fileTypePersistsButResetRestoresWebpDefault() {
        val models = ViewModelStore()
        try {
            val model = ConverterViewModel(app)
            models.put("first", model)
            await(model) { !it.restoring && !it.scanning && it.files.size == 2 }
            model.management(true)
            model.fileType(FileTypeFilter.OTHER)
            assertEquals("OTHER", app.getSharedPreferences("folders", 0).getString("fileType", null))

            val recreated = ConverterViewModel(app)
            models.put("second", recreated)
            await(recreated) { !it.restoring && !it.scanning && it.files.size == 2 }
            assertEquals(FileTypeFilter.OTHER, recreated.state.value.fileType)
            recreated.management(true)
            assertEquals(3, recreated.state.value.files.size)
            recreated.reset()
            assertEquals(FileTypeFilter.WEBP, recreated.state.value.fileType)
            assertTrue(recreated.state.value.files.isEmpty())
            assertFalse(app.getSharedPreferences("folders", 0).contains("fileType"))
        } finally {
            models.clear()
        }
    }
}
