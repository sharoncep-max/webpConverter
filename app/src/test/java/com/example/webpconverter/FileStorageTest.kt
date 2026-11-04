package com.example.webpconverter

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.webpconverter.data.Batch
import com.example.webpconverter.data.BatchEntry
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.BatchStore
import com.example.webpconverter.data.DocumentStore
import com.example.webpconverter.data.OutputFormat
import com.example.webpconverter.data.SourceFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileStorageTest {
    @Test fun byteCopyVerificationAndChangedOutputDetection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "copy-source-${UUID.randomUUID()}.webp")
        val output = File(context.cacheDir, "copy-output-${UUID.randomUUID()}.webp")
        try {
            val bytes = ByteArray(150_000) { (it % 251).toByte() }
            source.writeBytes(bytes)
            val documents = DocumentStore(context.contentResolver)
            documents.copyAndVerify(Uri.fromFile(source), Uri.fromFile(output))
            assertArrayEquals(bytes, output.readBytes())
            output.appendBytes(byteArrayOf(99))
            try {
                documents.verifyCopy(Uri.fromFile(source), Uri.fromFile(output))
                fail("Expected verification failure")
            } catch (_: IOException) {
                assertArrayEquals(bytes, source.readBytes())
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test fun durableMoveCheckpointRoundTripsAndOldConversionReportLoads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val legacyId = UUID.randomUUID().toString()
        val store = BatchStore(context)
        val entry = BatchEntry(
            SourceFile("source", "one.pdf", 100, "application/pdf"), "output",
            copyVerified = true, deletionStarted = true
        )
        val batch = Batch("destination", OutputFormat.PNG, listOf(entry), BatchOperation.MOVE, "input")
        try {
            store.save(id, batch)
            assertEquals(batch, store.load(id))
            File(context.filesDir, "batches/$legacyId.json").writeText(
                """{"destination":"dest","format":"PNG","entries":[{"uri":"src","name":"one.webp","size":100,"output":null,"finished":false,"error":null}]}"""
            )
            val legacy = store.load(legacyId)
            assertEquals(BatchOperation.CONVERT, legacy.operation)
            assertFalse(legacy.entries.single().copyVerified)
            assertNull(legacy.entries.single().source.mimeType)
            assertNull(legacy.inputFolder)
        } finally {
            File(context.filesDir, "batches/$id.json").delete()
            File(context.filesDir, "batches/$legacyId.json").delete()
        }
    }

    @Test fun folderIdentityUsesProviderAndTreeDocumentId() {
        val documents = DocumentStore(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        )
        assertTrue(documents.sameFolder(
            Uri.parse("content://provider/tree/primary%3APictures"),
            Uri.parse("content://provider/tree/primary%3APictures/document/primary%3APictures")
        ))
        assertFalse(documents.sameFolder(
            Uri.parse("content://provider/tree/primary%3APictures"),
            Uri.parse("content://provider/tree/primary%3AOther")
        ))
    }
}
