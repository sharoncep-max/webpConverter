package com.example.webpconverter.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.webpconverter.conversion.ConversionWorker
import com.example.webpconverter.data.Batch
import com.example.webpconverter.data.BatchEntry
import com.example.webpconverter.data.BatchOperation
import com.example.webpconverter.data.BatchStore
import com.example.webpconverter.data.DocumentStore
import com.example.webpconverter.data.FileTypeFilter
import com.example.webpconverter.data.OutputFormat
import com.example.webpconverter.data.SourceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ConverterState(
    val input: String? = null,
    val output: String? = null,
    val files: List<SourceFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val format: OutputFormat = OutputFormat.PNG,
    val scanning: Boolean = false,
    val submitting: Boolean = false,
    val restoring: Boolean = true,
    val error: String? = null,
    val work: WorkInfo? = null,
    val report: Batch? = null,
    val management: Boolean = false,
    val fileType: FileTypeFilter = FileTypeFilter.WEBP,
    val fileAction: BatchOperation = BatchOperation.COPY,
    val activeOperation: BatchOperation = BatchOperation.CONVERT
) {
    val busy: Boolean get() = restoring || submitting || work?.state?.isFinished == false
    val operation: BatchOperation get() = if (management) fileAction else BatchOperation.CONVERT
    val visibleFileType: FileTypeFilter get() = if (management) fileType else FileTypeFilter.WEBP
    val canRun: Boolean get() = !busy && !scanning && selected.isNotEmpty() && input != null &&
        files.asSequence().filter { visibleFileType.matches(it.name) }
            .map { it.uri }.toSet().containsAll(selected) &&
        (operation == BatchOperation.DELETE || output != null)
}

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("folders", 0)
    private val workManager = WorkManager.getInstance(application)
    private val documents = DocumentStore(application.contentResolver)
    private val batches = BatchStore(application)
    private val mutable = MutableStateFlow(ConverterState(
        input = preferences.getString("input", null),
        output = preferences.getString("output", null),
        format = OutputFormat.valueOf(preferences.getString("format", "PNG")!!),
        fileType = FileTypeFilter.valueOf(preferences.getString("fileType", "WEBP")!!)
    ))
    val state = mutable.asStateFlow()
    private var scanJob: Job? = null
    private var scanGeneration = 0
    private var resetGeneration = 0
    private var scannedFiles: List<SourceFile> = emptyList()

    private fun ConverterState.withVisibleFiles(): ConverterState {
        val visible = scannedFiles.filter { visibleFileType.matches(it.name) }
        return copy(files = visible, selected = selected.intersect(visible.map { it.uri }.toSet()))
    }

    init {
        refresh()
        viewModelScope.launch {
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(ConversionWorker.UNIQUE_WORK),
                BatchStore.changes
            ) { infos, _ -> infos }.collect { infos ->
                val latest = infos.firstOrNull { !it.state.isFinished }
                    ?: infos.firstOrNull { it.id.toString() == preferences.getString("batch", null) }
                if (latest != null) {
                    val generation = resetGeneration
                    try {
                        val report = withContext(Dispatchers.IO) { batches.load(latest.id.toString()) }
                        if (generation != resetGeneration) return@collect
                        val refreshFiles = latest.state.isFinished &&
                            (state.value.work?.id != latest.id || state.value.work?.state?.isFinished != true ||
                                state.value.report != report)
                        mutable.update { it.copy(
                            work = latest, report = report, restoring = false,
                            activeOperation = report.operation,
                            management = if (!latest.state.isFinished)
                                report.operation != BatchOperation.CONVERT else it.management,
                            fileAction = if (!latest.state.isFinished && report.operation != BatchOperation.CONVERT)
                                report.operation else it.fileAction
                        ).withVisibleFiles() }
                        if (refreshFiles && report.operation in listOf(BatchOperation.MOVE, BatchOperation.DELETE)) {
                            refresh()
                        }
                    } catch (exception: Exception) {
                        if (exception is CancellationException) throw exception
                        if (generation != resetGeneration) return@collect
                        mutable.update { it.copy(work = latest, report = null, restoring = false) }
                        showError("Cannot load operation report", exception)
                    }
                } else mutable.update { it.copy(work = latest, report = null, restoring = false) }
            }
        }
    }

    fun chooseFolder(uri: Uri, input: Boolean, grantedFlags: Int) {
        if (state.value.busy) return
        try {
            val requestedFlags = grantedFlags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            check(requestedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                "The provider did not grant read access."
            }
            check(input || requestedFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                "The destination must allow writing."
            }
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, requestedFlags)
            preferences.edit().putString(if (input) "input" else "output", uri.toString()).apply()
            if (input) scannedFiles = emptyList()
            mutable.update {
                if (input) it.copy(input = uri.toString(), files = emptyList(), selected = emptySet(), error = null)
                else it.copy(output = uri.toString(), error = null)
            }
            if (input) refresh()
        } catch (exception: Exception) {
            showError("Cannot retain access to this folder. Choose a writable device folder", exception)
        }
    }

    fun refresh() {
        if (state.value.busy && !state.value.restoring) return
        val folder = state.value.input ?: return
        scanJob?.cancel()
        val generation = ++scanGeneration
        scanJob = viewModelScope.launch {
            mutable.update { it.copy(scanning = true, error = null) }
            try {
                val files = withContext(Dispatchers.IO) { documents.scan(Uri.parse(folder)) }
                if (generation != scanGeneration) return@launch
                scannedFiles = files
                mutable.update { it.withVisibleFiles() }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                if (generation != scanGeneration) return@launch
                scannedFiles = emptyList()
                mutable.update { it.copy(files = emptyList(), selected = emptySet()) }
                showError("Cannot scan folder. Reselect it if permission was revoked", exception)
            } finally {
                if (generation == scanGeneration) mutable.update { it.copy(scanning = false) }
            }
        }
    }

    fun toggle(uri: String) {
        if (state.value.busy || state.value.scanning) return
        mutable.update {
            if (it.files.none { file -> file.uri == uri && it.visibleFileType.matches(file.name) }) it
            else it.copy(selected = if (uri in it.selected) it.selected - uri else it.selected + uri)
        }
    }

    fun selectAll() {
        if (state.value.busy || state.value.scanning) return
        mutable.update {
            val visibleUris = it.files.filter { file -> it.visibleFileType.matches(file.name) }
                .map { file -> file.uri }.toSet()
            it.copy(selected = if (it.selected.containsAll(visibleUris)) emptySet() else visibleUris)
        }
    }

    fun format(format: OutputFormat) {
        if (state.value.busy) return
        preferences.edit().putString("format", format.name).apply()
        mutable.update { it.copy(format = format) }
    }

    fun management(enabled: Boolean) {
        if (state.value.busy) return
        mutable.update { it.copy(management = enabled).withVisibleFiles() }
    }

    fun fileType(filter: FileTypeFilter) {
        if (state.value.busy || !state.value.management || state.value.fileType == filter) return
        preferences.edit().putString("fileType", filter.name).apply()
        mutable.update { it.copy(fileType = filter).withVisibleFiles() }
    }

    fun reset() {
        if (state.value.busy) return
        try {
            check(preferences.edit().remove("input").remove("output")
                .remove("format").remove("fileType").remove("batch").commit()) {
                "Could not clear saved fields and operation status."
            }
            resetGeneration++
            scanGeneration++
            scanJob?.cancel()
            scanJob = null
            scannedFiles = emptyList()
            mutable.update { ConverterState(restoring = false, management = it.management) }
        } catch (exception: Exception) {
            showError("Cannot reset", exception)
        }
    }

    fun fileAction(operation: BatchOperation) {
        if (state.value.busy || operation == BatchOperation.CONVERT) return
        mutable.update { it.copy(fileAction = operation) }
    }

    fun convert() {
        val snapshot = state.value
        if (!snapshot.canRun) return
        mutable.update { it.copy(
            submitting = true, error = null, report = null, activeOperation = snapshot.operation
        ) }
        viewModelScope.launch {
            try {
                if (snapshot.operation in listOf(BatchOperation.MOVE, BatchOperation.DELETE)) {
                    check(getApplication<Application>().contentResolver.persistedUriPermissions.any {
                        it.uri.toString() == snapshot.input && it.isWritePermission
                    }) { "Reselect the input folder and grant write access to move or delete files." }
                }
                if (snapshot.operation in listOf(BatchOperation.COPY, BatchOperation.MOVE)) {
                    check(!documents.sameFolder(Uri.parse(snapshot.input), Uri.parse(snapshot.output))) {
                        "Choose a destination different from the input folder."
                    }
                }
                val id = UUID.randomUUID().toString()
                withContext(Dispatchers.IO) {
                    batches.save(id, Batch(
                        snapshot.output.orEmpty(), snapshot.format,
                        snapshot.files.filter { it.uri in snapshot.selected }.map { BatchEntry(it) },
                        snapshot.operation, snapshot.input
                    ))
                }
                val actualRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
                    .setId(UUID.fromString(id))
                    .setInputData(workDataOf(ConversionWorker.BATCH_ID to id))
                    .build()
                // Persist the ID before enqueueing, so process recreation can find its report.
                val info = withContext(Dispatchers.IO) {
                    check(preferences.edit().putString("batch", id).commit()) {
                        "Could not persist the batch identifier."
                    }
                    workManager.enqueueUniqueWork(
                        ConversionWorker.UNIQUE_WORK, ExistingWorkPolicy.KEEP, actualRequest
                    ).result.get()
                    workManager.getWorkInfoById(actualRequest.id).get()
                }
                mutable.update { it.copy(work = info) }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                showError("Cannot start ${snapshot.operation.label.lowercase()}", exception)
            } finally {
                mutable.update { it.copy(submitting = false) }
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    workManager.cancelUniqueWork(ConversionWorker.UNIQUE_WORK).result.get()
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                showError("Cannot cancel operation", exception)
            }
        }
    }

    fun dismissError() { mutable.update { it.copy(error = null) } }

    private fun showError(message: String, exception: Exception) {
        Log.e("ConverterViewModel", message, exception)
        mutable.update { it.copy(error = "$message: ${exception.message ?: exception.javaClass.simpleName}") }
    }
}
