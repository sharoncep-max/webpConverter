package com.example.webpconverter.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

interface FileOperationAccess {
    suspend fun create(source: SourceFile): String
    suspend fun copyAndVerify(source: String, output: String)
    suspend fun verifyCopy(source: String, output: String)
    fun exists(uri: String): Boolean
    fun delete(uri: String)
}

/** Checkpoints keep a restarted move from deleting its only verified copy. */
class FileOperationProcessor(private val access: FileOperationAccess) {
    suspend fun process(
        initial: BatchEntry,
        operation: BatchOperation,
        checkpoint: (BatchEntry) -> Unit
    ): BatchEntry {
        require(operation != BatchOperation.CONVERT)
        if (initial.finished) return initial
        var entry = initial
        if (operation != BatchOperation.DELETE) {
            if (!entry.copyVerified) {
                entry.outputUri?.let { if (access.exists(it)) access.delete(it) }
                entry = entry.copy(outputUri = null)
                checkpoint(entry)
                currentCoroutineContext().ensureActive()
                val output = access.create(entry.source)
                entry = entry.copy(outputUri = output)
                checkpoint(entry)
                access.copyAndVerify(entry.source.uri, output)
                entry = entry.copy(copyVerified = true)
                checkpoint(entry)
            } else if (!access.exists(requireNotNull(entry.outputUri))) {
                throw IOException("The verified destination copy is missing. The source was not deleted.")
            }
        }
        if (operation == BatchOperation.MOVE || operation == BatchOperation.DELETE) {
            currentCoroutineContext().ensureActive()
            val sourceExists = access.exists(entry.source.uri)
            if (!sourceExists && !entry.deletionStarted) {
                throw IOException("The source file no longer exists.")
            }
            if (sourceExists && operation == BatchOperation.MOVE) {
                access.verifyCopy(entry.source.uri, requireNotNull(entry.outputUri))
            }
            entry = entry.copy(deletionStarted = true)
            checkpoint(entry)
            currentCoroutineContext().ensureActive()
            if (sourceExists) access.delete(entry.source.uri)
        }
        return entry.copy(finished = true, error = null).also(checkpoint)
    }
}
