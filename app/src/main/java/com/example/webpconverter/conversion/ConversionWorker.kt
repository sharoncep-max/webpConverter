package com.example.webpconverter.conversion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.webpconverter.MainActivity
import com.example.webpconverter.R
import com.example.webpconverter.data.Batch
import com.example.webpconverter.data.BatchEntry
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.BatchStore
import com.example.webpconverter.data.DocumentStore
import com.example.webpconverter.data.FileOperationAccess
import com.example.webpconverter.data.FileOperationProcessor
import com.example.webpconverter.data.SourceFile
import com.example.webpconverter.data.isWebpName
import com.example.webpconverter.data.reserveFileName
import com.example.webpconverter.data.reserveOutputName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class ConversionWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    private var operation = BatchOperation.CONVERT

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batchId = inputData.getString(BATCH_ID)
            ?: return@withContext Result.failure(workDataOf(ERROR to "Missing batch identifier."))
        try {
            val store = BatchStore(applicationContext)
            var batch = store.load(batchId)
            operation = batch.operation
            setForeground(foreground(batch.completed, batch.entries.size))
            if (operation != BatchOperation.CONVERT) {
                return@withContext manageFiles(batchId, batch, store)
            }
            if (batch.entries.any { !isWebpName(it.source.name) }) {
                throw IOException("Conversion only accepts files ending in .webp.")
            }
            val resolver = applicationContext.contentResolver
            val documents = DocumentStore(resolver)
            val destination = Uri.parse(batch.destination)
            val existing = documents.children(destination)
            val usedNames = existing.mapTo(mutableSetOf()) { it.name.lowercase(Locale.ROOT) }
            val protectedUris = existing.mapTo(mutableSetOf()) { it.uri.toString() }
            val converter = ImageConverter(resolver)
            for (index in batch.entries.indices) {
                currentCoroutineContext().ensureActive()
                var entry = batch.entries[index]
                if (entry.finished) continue
                // A process may have died between creating the output and committing its result.
                entry.outputUri?.let { old ->
                    if (!DocumentsContract.deleteDocument(resolver, Uri.parse(old))) {
                        throw IOException("Cannot remove interrupted output: ${entry.source.name}")
                    }
                    protectedUris.remove(old)
                    entry = entry.copy(outputUri = null, error = null)
                    batch = batch.replace(index, entry)
                    store.save(batchId, batch)
                }
                publishProgress(batch.completed, batch.entries.size)
                setForeground(foreground(batch.completed + 1, batch.entries.size))
                var created: Uri? = null
                try {
                    val name = reserveOutputName(entry.source.name, batch.format, usedNames)
                    val output = DocumentsContract.createDocument(
                        resolver, documents.documentUri(destination), batch.format.mimeType, name
                    ) ?: throw IOException("The destination provider could not create a file.")
                    if (output.toString() in protectedUris) {
                        throw IOException("The provider returned an existing file; it was not overwritten.")
                    }
                    created = output
                    entry = entry.copy(outputUri = output.toString())
                    batch = batch.replace(index, entry)
                    store.save(batchId, batch)
                    converter.convert(Uri.parse(entry.source.uri), output, batch.format)
                    entry = entry.copy(finished = true, error = null)
                    protectedUris += output.toString()
                } catch (cancelled: CancellationException) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val cleanup = created?.let { removePartial(it) }
                        entry = entry.copy(
                            outputUri = if (cleanup == null) null else created.toString(),
                            error = cleanup
                        )
                        store.save(batchId, batch.replace(index, entry))
                    }
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "File conversion failed", error)
                    val cleanup = created?.let { removePartial(it) }
                    entry = entry.copy(
                        finished = true,
                        outputUri = if (cleanup == null) null else created.toString(),
                        error = listOfNotNull(error.message ?: error.javaClass.simpleName, cleanup)
                            .joinToString(" ")
                    )
                } catch (error: OutOfMemoryError) {
                    Log.e(TAG, "Insufficient image memory", error)
                    val cleanup = created?.let { removePartial(it) }
                    entry = entry.copy(
                        finished = true,
                        outputUri = if (cleanup == null) null else created.toString(),
                        error = "Insufficient memory for this image. ${cleanup.orEmpty()}".trim()
                    )
                }
                batch = batch.replace(index, entry)
                store.save(batchId, batch)
                publishProgress(batch.completed, batch.entries.size)
            }
            Result.success(workDataOf(
                COMPLETED to batch.completed, TOTAL to batch.entries.size,
                SUCCEEDED to batch.succeeded, FAILED to batch.failed
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Batch operation stopped", error)
            Result.failure(workDataOf(ERROR to (error.message ?: "Batch operation stopped.").take(1500)))
        }
    }

    private suspend fun manageFiles(batchId: String, initial: Batch, store: BatchStore): Result {
        var batch = initial
        val resolver = applicationContext.contentResolver
        val documents = DocumentStore(resolver)
        val source = Uri.parse(requireNotNull(batch.inputFolder) { "Missing source folder." })
        val destination = if (operation == BatchOperation.DELETE) null else Uri.parse(batch.destination)
        if (destination != null && documents.sameFolder(source, destination)) {
            throw IOException("Choose a destination different from the input folder.")
        }
        val existing = destination?.let { documents.children(it) }.orEmpty()
        val usedNames = existing.mapTo(mutableSetOf()) { it.name.lowercase(Locale.ROOT) }
        val protectedUris = existing.mapTo(mutableSetOf()) { it.uri.toString() }
        protectedUris += batch.entries.map { it.source.uri }
        val access = object : FileOperationAccess {
            override suspend fun create(source: SourceFile): String {
                val output = DocumentsContract.createDocument(
                    resolver, documents.documentUri(requireNotNull(destination)), source.outputMimeType(),
                    reserveFileName(source.name, usedNames)
                ) ?: throw IOException("The destination provider could not create a file.")
                if (output.toString() in protectedUris) {
                    throw IOException("The provider returned an existing file; it was not overwritten.")
                }
                return output.toString()
            }
            override suspend fun copyAndVerify(source: String, output: String) =
                documents.copyAndVerify(Uri.parse(source), Uri.parse(output))
            override suspend fun verifyCopy(source: String, output: String) =
                documents.verifyCopy(Uri.parse(source), Uri.parse(output))
            override fun exists(uri: String): Boolean = documents.exists(Uri.parse(uri))
            override fun delete(uri: String) = documents.delete(Uri.parse(uri))
        }
        val processor = FileOperationProcessor(access)
        for (index in batch.entries.indices) {
            currentCoroutineContext().ensureActive()
            var entry = batch.entries[index]
            if (entry.finished) continue
            publishProgress(batch.completed, batch.entries.size)
            setForeground(foreground(batch.completed + 1, batch.entries.size))
            fun checkpoint(updated: BatchEntry) {
                entry = updated
                batch = batch.replace(index, updated)
                store.save(batchId, batch)
            }
            fun cleanup(): String? = try {
                if (!entry.copyVerified) {
                    entry.outputUri?.let { uri ->
                        if (documents.exists(Uri.parse(uri))) removePartial(Uri.parse(uri)) else null
                    }
                } else "A verified copy was saved at ${entry.outputUri}. Check both folders before retrying."
            } catch (error: Exception) {
                Log.e(TAG, "File operation cleanup failed", error)
                "Could not check or remove partial output: ${entry.outputUri}. Check it manually."
            }
            try {
                processor.process(entry, operation, ::checkpoint)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val note = cleanup()
                    checkpoint(entry.copy(
                        outputUri = if (!entry.copyVerified && note == null) null else entry.outputUri,
                        error = note
                    ))
                }
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "File ${operation.label.lowercase(Locale.ROOT)} failed", error)
                val note = cleanup()
                checkpoint(entry.copy(
                    finished = true,
                    outputUri = if (!entry.copyVerified && note == null) null else entry.outputUri,
                    error = listOfNotNull(error.message ?: error.javaClass.simpleName, note).joinToString(" ")
                ))
            }
            entry.outputUri?.let { protectedUris += it }
            publishProgress(batch.completed, batch.entries.size)
        }
        return Result.success(workDataOf(
            COMPLETED to batch.completed, TOTAL to batch.entries.size,
            SUCCEEDED to batch.succeeded, FAILED to batch.failed
        ))
    }

    private fun Batch.replace(index: Int, entry: com.example.webpconverter.data.BatchEntry): Batch =
        copy(entries = entries.toMutableList().apply { this[index] = entry })

    private fun removePartial(uri: Uri): String? = try {
        if (DocumentsContract.deleteDocument(applicationContext.contentResolver, uri)) null
        else "Could not delete partial output: $uri. Remove it manually."
    } catch (error: Exception) {
        Log.e(TAG, "Partial output cleanup failed", error)
        "Could not delete partial output: $uri. Remove it manually."
    }

    private suspend fun publishProgress(completed: Int, total: Int) {
        setProgress(workDataOf(COMPLETED to completed, TOTAL to total))
    }

    private fun foreground(current: Int, total: Int): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW
        ))
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_convert)
            .setContentTitle(
                if (operation == BatchOperation.CONVERT) "${operation.ongoing} WebP images"
                else "${operation.ongoing} files"
            )
            .setContentText("${operation.ongoing} $current of $total")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, current, false)
            .addAction(0, context.getString(R.string.cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_WORK = "webp-conversion"
        const val BATCH_ID = "batch_id"
        const val COMPLETED = "completed"
        const val TOTAL = "total"
        const val SUCCEEDED = "succeeded"
        const val FAILED = "failed"
        const val ERROR = "error"
        private const val CHANNEL = "conversion"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "ConversionWorker"
    }
}

private fun SourceFile.outputMimeType(): String {
    mimeType?.takeIf { it.isNotBlank() }?.let { return it }
    if (isWebpName(name)) return "image/webp"
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}
