package com.example.webpconverter

import com.example.webpconverter.data.BatchEntry
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.FileOperationAccess
import com.example.webpconverter.data.FileOperationProcessor
import com.example.webpconverter.data.SourceFile
import com.example.webpconverter.data.reserveFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FileOperationTest {
    private val source = BatchEntry(SourceFile("source", "image.webp", 4))

    private class Files : FileOperationAccess {
        val files = mutableMapOf("source" to byteArrayOf(1, 2, 3, 4), "unselected" to byteArrayOf(5))
        val events = mutableListOf<String>()
        var failCopy = false
        var failDelete = false
        var failVerification = false
        override suspend fun create(source: SourceFile): String {
            events += "create"
            files["output"] = byteArrayOf()
            return "output"
        }
        override suspend fun copyAndVerify(source: String, output: String) {
            events += "copy"
            if (failCopy) throw IOException("Storage full")
            files[output] = files.getValue(source).copyOf()
            verifyCopy(source, output)
        }
        override suspend fun verifyCopy(source: String, output: String) {
            events += "verify"
            if (failVerification || !files.getValue(source).contentEquals(files.getValue(output))) {
                throw IOException("Verification failed")
            }
        }
        override fun exists(uri: String): Boolean = uri in files
        override fun delete(uri: String) {
            events += "delete:$uri"
            if (failDelete) throw IOException("Deletion denied")
            check(files.remove(uri) != null)
        }
    }

    @Test fun copyKeepsSourceAndCopiesExactBytes() = runBlocking {
        val files = Files()
        val result = FileOperationProcessor(files).process(source, BatchOperation.COPY) {}
        assertTrue(result.finished && result.copyVerified)
        assertArrayEquals(files.files["source"], files.files["output"])
        assertTrue("unselected" in files.files)
        assertFalse(files.events.any { it.startsWith("delete:") })
    }

    @Test fun moveVerifiesAndCheckpointsBeforeDeletingSource() = runBlocking {
        val files = Files()
        val checkpoints = mutableListOf<BatchEntry>()
        val result = FileOperationProcessor(files).process(source, BatchOperation.MOVE) {
            checkpoints += it
            if (it.deletionStarted && !it.finished) {
                assertTrue(it.copyVerified)
                assertTrue("source" in files.files)
            }
        }
        assertTrue(result.finished)
        assertFalse("source" in files.files)
        assertTrue("output" in files.files)
        assertTrue("unselected" in files.files)
        assertTrue(checkpoints.any { it.copyVerified && !it.deletionStarted })
        assertTrue(files.events.indexOf("verify") < files.events.indexOf("delete:source"))
    }

    @Test fun deleteDoesNotCreateOutputOrTouchUnselectedFile() = runBlocking {
        val files = Files()
        val result = FileOperationProcessor(files).process(source, BatchOperation.DELETE) {}
        assertTrue(result.finished)
        assertFalse("source" in files.files)
        assertTrue("unselected" in files.files)
        assertEquals(listOf("delete:source"), files.events)
    }

    @Test fun failedCopyNeverDeletesSource() = runBlocking {
        val files = Files().apply { failCopy = true }
        try {
            FileOperationProcessor(files).process(source, BatchOperation.MOVE) {}
            fail("Expected a copy error")
        } catch (expected: IOException) {
            assertEquals("Storage full", expected.message)
        }
        assertTrue("source" in files.files)
        assertFalse("delete:source" in files.events)
    }

    @Test fun verificationFailureNeverDeletesSource() = runBlocking {
        val files = Files().apply { failVerification = true }
        try {
            FileOperationProcessor(files).process(source, BatchOperation.MOVE) {}
            fail("Expected verification error")
        } catch (expected: IOException) {
            assertEquals("Verification failed", expected.message)
        }
        assertTrue("source" in files.files)
        assertFalse("delete:source" in files.events)
    }

    @Test fun failedSourceDeletionKeepsVerifiedCopyAndSource() = runBlocking {
        val files = Files().apply { failDelete = true }
        var saved = source
        try {
            FileOperationProcessor(files).process(source, BatchOperation.MOVE) { saved = it }
            fail("Expected deletion error")
        } catch (expected: IOException) {
            assertEquals("Deletion denied", expected.message)
        }
        assertTrue(saved.copyVerified)
        assertArrayEquals(files.files["source"], files.files["output"])
    }

    @Test fun resumedMoveDoesNotCopyAgain() = runBlocking {
        val files = Files().apply { files["output"] = files.getValue("source").copyOf() }
        val saved = source.copy(outputUri = "output", copyVerified = true)
        val result = FileOperationProcessor(files).process(saved, BatchOperation.MOVE) {}
        assertTrue(result.finished)
        assertFalse("create" in files.events)
        assertFalse("copy" in files.events)
        assertFalse("source" in files.files)
    }

    @Test fun resumedMoveRechecksBytesBeforeDeletion() = runBlocking {
        val files = Files().apply { files["output"] = byteArrayOf(99) }
        try {
            FileOperationProcessor(files).process(
                source.copy(outputUri = "output", copyVerified = true), BatchOperation.MOVE
            ) {}
            fail("Expected changed-copy error")
        } catch (_: IOException) {
            assertTrue("source" in files.files)
        }
    }

    @Test fun resumedMoveAfterDeletionIsIdempotent() = runBlocking {
        val files = Files().apply { files["output"] = files.remove("source")!! }
        val saved = source.copy(outputUri = "output", copyVerified = true, deletionStarted = true)
        val result = FileOperationProcessor(files).process(saved, BatchOperation.MOVE) {}
        assertTrue(result.finished)
        assertTrue(files.events.isEmpty())
    }

    @Test fun missingVerifiedOutputDoesNotDeleteSource() = runBlocking {
        val files = Files()
        try {
            FileOperationProcessor(files).process(
                source.copy(outputUri = "missing", copyVerified = true), BatchOperation.MOVE
            ) {}
            fail("Expected missing-output error")
        } catch (_: IOException) {
            assertTrue("source" in files.files)
        }
    }

    @Test fun resumedDeleteAfterDeletionIsIdempotent() = runBlocking {
        val files = Files().apply { files.remove("source") }
        val result = FileOperationProcessor(files).process(
            source.copy(deletionStarted = true), BatchOperation.DELETE
        ) {}
        assertTrue(result.finished)
        assertTrue(files.events.isEmpty())
    }

    @Test fun missingSourceIsNotReportedAsSuccessfulNewDeletion() = runBlocking {
        val files = Files().apply { files.remove("source") }
        try {
            FileOperationProcessor(files).process(source, BatchOperation.DELETE) {}
            fail("Expected missing-source error")
        } catch (_: IOException) {
            assertTrue(files.events.isEmpty())
        }
    }

    @Test fun cancellationBeforeSourceDeletionKeepsSourceAndVerifiedCopy() = runBlocking {
        val files = Files()
        try {
            FileOperationProcessor(files).process(source, BatchOperation.MOVE) {
                if (it.copyVerified) throw CancellationException("Cancelled")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertArrayEquals(files.files["source"], files.files["output"])
            assertFalse("delete:source" in files.events)
        }
    }

    @Test fun interruptedPartialCopyIsRemovedBeforeRestart() = runBlocking {
        val files = Files().apply { files["partial"] = byteArrayOf(1) }
        val result = FileOperationProcessor(files).process(
            source.copy(outputUri = "partial"), BatchOperation.COPY
        ) {}
        assertTrue(result.finished)
        assertFalse("partial" in files.files)
        assertEquals("delete:partial", files.events.first())
        assertArrayEquals(files.files["source"], files.files["output"])
    }

    @Test fun completedEntryIsNotProcessedAgain() = runBlocking {
        val files = Files()
        FileOperationProcessor(files).process(source.copy(finished = true), BatchOperation.DELETE) {
            fail("Should not checkpoint a finished entry")
        }
        assertTrue(files.events.isEmpty())
    }

    @Test fun webpNamesKeepExtensionAndAvoidCollisions() {
        assertEquals("Photo (2).WEBP",
            reserveFileName("Photo.WEBP", mutableSetOf("photo.webp", "photo (1).webp")))
        assertEquals("bad_name.webp", reserveFileName("bad/name.webp", mutableSetOf()))
    }
}
