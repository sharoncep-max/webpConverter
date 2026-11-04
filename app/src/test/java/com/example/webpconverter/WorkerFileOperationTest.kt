package com.example.webpconverter

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.webpconverter.conversion.ConversionWorker
import com.example.webpconverter.data.Batch
import com.example.webpconverter.data.BatchEntry
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.BatchStore
import com.example.webpconverter.data.DocumentStore
import com.example.webpconverter.data.OutputFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * File-backed provider speaking the DocumentsContract URI/call protocol. A plain ContentProvider is used because
 * Robolectric invokes the pre-O query overload, which DocumentsProvider makes final and unsupported.
 */
class TestDocumentsProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(id: String) = File(root, id)

    private fun mime(file: File): String? = when (file.extension.lowercase()) {
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "custom" -> "application/x-test"
        else -> null
    }

    private fun row(cursor: MatrixCursor, id: String) {
        val f = file(id)
        cursor.addRow(arrayOf<Any?>(id, f.name,
            if (f.isDirectory) Document.MIME_TYPE_DIR else mime(f), if (f.isDirectory) null else f.length()))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val id = DocumentsContract.getDocumentId(uri)
        val cursor = MatrixCursor(COLUMNS)
        if (uri.lastPathSegment == "children") {
            file(id).listFiles().orEmpty().forEach { row(cursor, "$id/${it.name}") }
        } else {
            if (!file(id).exists()) throw FileNotFoundException(id)
            row(cursor, id)
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.parseMode(mode))

    override fun call(authority: String, method: String, arg: String?, extras: Bundle?): Bundle? =
        call(method, arg, extras)

    @Suppress("DEPRECATION")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val target = extras!!.getParcelable<Uri>("uri")!!
        val id = DocumentsContract.getDocumentId(target)
        return when (method) {
            "android:createDocument" -> {
                val childId = "$id/${extras.getString(Document.COLUMN_DISPLAY_NAME)}"
                if (!file(childId).createNewFile()) throw IllegalStateException("Exists: $childId")
                createdMimeTypes[childId] = requireNotNull(extras.getString(Document.COLUMN_MIME_TYPE))
                Bundle().apply {
                    putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(target, childId))
                }
            }
            "android:deleteDocument" -> {
                if (!file(id).delete()) throw IllegalStateException("Cannot delete $id")
                Bundle()
            }
            else -> throw UnsupportedOperationException(method)
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.example.webpconverter.test.documents"
        lateinit var root: File
        val createdMimeTypes = mutableMapOf<String, String>()
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkerFileOperationTest {
    private lateinit var context: Context
    private lateinit var input: File
    private lateinit var output: File
    private val bytes = mapOf(
        "a.webp" to ByteArray(70_000) { (it % 251).toByte() },
        "b.webp" to ByteArray(10) { 7 },
        "c.webp" to ByteArray(20) { 3 }
    )

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        TestDocumentsProvider.createdMimeTypes.clear()
        TestDocumentsProvider.root = File(context.cacheDir, "docs-${UUID.randomUUID()}").apply { mkdirs() }
        input = File(TestDocumentsProvider.root, "in").apply { mkdirs() }
        output = File(TestDocumentsProvider.root, "out").apply { mkdirs() }
        bytes.forEach { (name, data) -> File(input, name).writeBytes(data) }
        File(output, "a.webp").writeBytes(byteArrayOf(1, 2, 3))
        Robolectric.buildContentProvider(TestDocumentsProvider::class.java).create(
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                grantUriPermissions = true
                exported = true
            }
        )
    }

    private fun tree(id: String): Uri = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, id)

    private fun run(
        operation: BatchOperation, names: List<String>, omitMimeTypes: Boolean = false
    ): ListenableWorker.Result = runBlocking {
        val inTree = tree("in")
        val available = DocumentStore(context.contentResolver).scan(inTree)
        val entries = names.map { name ->
            val source = available.single { it.name == name }
            BatchEntry(if (omitMimeTypes) source.copy(mimeType = null) else source)
        }
        val id = UUID.randomUUID().toString()
        val store = BatchStore(context)
        store.save(id, Batch(tree("out").toString(), OutputFormat.PNG, entries, operation, inTree.toString()))
        val worker = TestListenableWorkerBuilder<ConversionWorker>(context)
            .setInputData(workDataOf(ConversionWorker.BATCH_ID to id))
            .build()
        worker.doWork()
    }

    // Assert on the worker's output: batch-file rewrites cannot replace files on a Windows test host.
    private fun assertSucceeded(result: ListenableWorker.Result, count: Int) {
        assertTrue("$result", result is ListenableWorker.Result.Success)
        assertEquals("$result", count, result.outputData.getInt(ConversionWorker.SUCCEEDED, -1))
        assertEquals("$result", 0, result.outputData.getInt(ConversionWorker.FAILED, -1))
    }

    @Test fun copyKeepsSourcesAndNeverOverwritesExistingFiles() {
        val result = run(BatchOperation.COPY, listOf("a.webp", "b.webp"))
        assertSucceeded(result, 2)
        assertTrue(File(input, "a.webp").exists())
        assertTrue(File(input, "b.webp").exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(output, "a.webp").readBytes())
        assertArrayEquals(bytes["a.webp"], File(output, "a (1).webp").readBytes())
        assertArrayEquals(bytes["b.webp"], File(output, "b.webp").readBytes())
        assertFalse(File(output, "c.webp").exists())
    }

    @Test fun moveCopiesSelectedFilesThenRemovesOnlyTheirSources() {
        val result = run(BatchOperation.MOVE, listOf("b.webp", "c.webp"))
        assertSucceeded(result, 2)
        assertFalse(File(input, "b.webp").exists())
        assertFalse(File(input, "c.webp").exists())
        assertTrue(File(input, "a.webp").exists())
        assertArrayEquals(bytes["b.webp"], File(output, "b.webp").readBytes())
        assertArrayEquals(bytes["c.webp"], File(output, "c.webp").readBytes())
    }

    @Test fun deleteRemovesOnlySelectedFilesFromInputFolder() {
        val result = run(BatchOperation.DELETE, listOf("a.webp", "c.webp"))
        assertSucceeded(result, 2)
        assertFalse(File(input, "a.webp").exists())
        assertFalse(File(input, "c.webp").exists())
        assertTrue(File(input, "b.webp").exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(output, "a.webp").readBytes())
        assertEquals(setOf("a.webp"), output.list()!!.toSet())
    }

    @Test fun copyOtherFilesPreservesBytesMimeAndCollisionSafeNames() {
        val text = "Important text".toByteArray()
        val custom = byteArrayOf(12, 34, 56)
        val noExtension = byteArrayOf(9, 8, 7)
        File(input, "notes.txt").writeBytes(text)
        File(input, "data.custom").writeBytes(custom)
        File(input, "README").writeBytes(noExtension)
        File(output, "notes.txt").writeText("Existing")

        assertSucceeded(run(BatchOperation.COPY, listOf("notes.txt", "data.custom", "README")), 3)
        assertArrayEquals(text, File(output, "notes (1).txt").readBytes())
        assertArrayEquals(custom, File(output, "data.custom").readBytes())
        assertArrayEquals(noExtension, File(output, "README").readBytes())
        assertEquals("Existing", File(output, "notes.txt").readText())
        assertTrue(File(input, "notes.txt").exists())
        assertTrue(File(input, "a.webp").exists())
        assertEquals("text/plain", TestDocumentsProvider.createdMimeTypes["out/notes (1).txt"])
        assertEquals("application/x-test", TestDocumentsProvider.createdMimeTypes["out/data.custom"])
        assertEquals("application/octet-stream", TestDocumentsProvider.createdMimeTypes["out/README"])
    }

    @Test fun moveOtherFilesPreservesBytesAndLeavesWebpFilesAlone() {
        val image = byteArrayOf(11, 22, 33)
        val document = byteArrayOf(44, 55)
        File(input, "image.png").writeBytes(image)
        File(input, "document.pdf").writeBytes(document)

        assertSucceeded(run(BatchOperation.MOVE, listOf("image.png", "document.pdf")), 2)
        assertFalse(File(input, "image.png").exists())
        assertFalse(File(input, "document.pdf").exists())
        assertTrue(File(input, "a.webp").exists())
        assertArrayEquals(image, File(output, "image.png").readBytes())
        assertArrayEquals(document, File(output, "document.pdf").readBytes())
        assertEquals("image/png", TestDocumentsProvider.createdMimeTypes["out/image.png"])
        assertEquals("application/pdf", TestDocumentsProvider.createdMimeTypes["out/document.pdf"])
    }

    @Test fun missingMimeUsesWebpOrExtensionFallback() {
        File(input, "notes.txt").writeText("text")

        assertSucceeded(run(BatchOperation.COPY, listOf("b.webp", "notes.txt"), omitMimeTypes = true), 2)
        assertEquals("image/webp", TestDocumentsProvider.createdMimeTypes["out/b.webp"])
        assertEquals("text/plain", TestDocumentsProvider.createdMimeTypes["out/notes.txt"])
    }

    @Test fun deleteOtherFilesRemovesOnlySelectedFiles() {
        File(input, "notes.txt").writeText("selected")
        File(input, "document.pdf").writeText("unselected")

        assertSucceeded(run(BatchOperation.DELETE, listOf("notes.txt")), 1)
        assertFalse(File(input, "notes.txt").exists())
        assertTrue(File(input, "document.pdf").exists())
        assertTrue(File(input, "a.webp").exists())
        assertEquals(setOf("a.webp"), output.list()!!.toSet())
    }

    @Test fun conversionRejectsNonWebpEntriesBeforeCreatingOutput() {
        File(input, "notes.txt").writeText("not an image")

        val result = run(BatchOperation.CONVERT, listOf("a.webp", "notes.txt"))
        assertTrue("$result", result is ListenableWorker.Result.Failure)
        assertEquals("Conversion only accepts files ending in .webp.",
            result.outputData.getString(ConversionWorker.ERROR))
        assertTrue(File(input, "notes.txt").exists())
        assertTrue(File(input, "a.webp").exists())
        assertEquals(setOf("a.webp"), output.list()!!.toSet())
    }
}
