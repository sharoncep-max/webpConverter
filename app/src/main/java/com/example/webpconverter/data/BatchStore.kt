package com.example.webpconverter.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class BatchStore(context: Context) {
    private val directory = File(context.filesDir, "batches").apply {
        check(isDirectory || mkdirs()) { "Cannot create private batch storage." }
    }

    private fun file(id: String): AtomicFile {
        UUID.fromString(id)
        return AtomicFile(File(directory, "$id.json"))
    }

    fun save(id: String, batch: Batch) {
        val entries = JSONArray()
        batch.entries.forEach { entry ->
            entries.put(JSONObject().apply {
                put("uri", entry.source.uri)
                put("name", entry.source.name)
                put("size", entry.source.size ?: JSONObject.NULL)
                put("mimeType", entry.source.mimeType ?: JSONObject.NULL)
                put("output", entry.outputUri ?: JSONObject.NULL)
                put("finished", entry.finished)
                put("error", entry.error ?: JSONObject.NULL)
                put("copyVerified", entry.copyVerified)
                put("deletionStarted", entry.deletionStarted)
            })
        }
        val bytes = JSONObject().apply {
            put("destination", batch.destination)
            put("format", batch.format.name)
            put("entries", entries)
            put("operation", batch.operation.name)
            put("inputFolder", batch.inputFolder ?: JSONObject.NULL)
        }.toString().toByteArray(Charsets.UTF_8)
        val atomic = file(id)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (exception: Exception) {
            atomic.failWrite(stream)
            throw exception
        }
        revision.update { it + 1 }
    }

    fun load(id: String): Batch {
        val json = JSONObject(file(id).openRead().bufferedReader().use { it.readText() })
        val entries = json.getJSONArray("entries")
        return Batch(
            json.getString("destination"),
            OutputFormat.valueOf(json.getString("format")),
            List(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                BatchEntry(
                    SourceFile(
                        item.getString("uri"), item.getString("name"),
                        if (item.isNull("size")) null else item.getLong("size"),
                        if (!item.has("mimeType") || item.isNull("mimeType")) null
                        else item.getString("mimeType")
                    ),
                    if (item.isNull("output")) null else item.getString("output"),
                    item.getBoolean("finished"),
                    if (item.isNull("error")) null else item.getString("error"),
                    item.optBoolean("copyVerified"),
                    item.optBoolean("deletionStarted")
                )
            },
            BatchOperation.valueOf(json.optString("operation", "CONVERT")),
            if (json.isNull("inputFolder")) null else json.getString("inputFolder")
        )
    }

    companion object {
        private val revision = MutableStateFlow(0L)
        val changes = revision.asStateFlow()
    }
}
