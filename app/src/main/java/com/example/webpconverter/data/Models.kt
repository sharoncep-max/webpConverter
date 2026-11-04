package com.example.webpconverter.data

import java.util.Locale

enum class OutputFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

enum class BatchOperation(val label: String, val ongoing: String, val completedLabel: String) {
    CONVERT("Convert", "Converting", "Converted"),
    COPY("Copy", "Copying", "Copied"),
    MOVE("Move", "Moving", "Moved"),
    DELETE("Delete", "Deleting", "Deleted")
}

enum class FileTypeFilter(val label: String) {
    WEBP("WebP only"),
    OTHER("Other files");

    fun matches(name: String): Boolean = when (this) {
        WEBP -> isWebpName(name)
        OTHER -> !isWebpName(name)
    }
}

data class SourceFile(
    val uri: String,
    val name: String,
    val size: Long?,
    val mimeType: String? = null
)

data class BatchEntry(
    val source: SourceFile,
    val outputUri: String? = null,
    val finished: Boolean = false,
    val error: String? = null,
    val copyVerified: Boolean = false,
    val deletionStarted: Boolean = false
)

data class Batch(
    val destination: String,
    val format: OutputFormat,
    val entries: List<BatchEntry>,
    val operation: BatchOperation = BatchOperation.CONVERT,
    val inputFolder: String? = null
) {
    val completed: Int get() = entries.count { it.finished }
    val succeeded: Int get() = entries.count { it.finished && it.error == null }
    val failed: Int get() = entries.count { it.finished && it.error != null }
}

fun isWebpName(name: String): Boolean = name.endsWith(".webp", ignoreCase = true)

fun reserveOutputName(sourceName: String, format: OutputFormat, used: MutableSet<String>): String {
    return reserveName(sourceName.substringBeforeLast('.', sourceName), format.extension, used)
}

fun reserveFileName(sourceName: String, used: MutableSet<String>): String {
    val dot = sourceName.lastIndexOf('.')
    val hasExtension = dot < sourceName.lastIndex &&
        (dot > 0 || (dot == 0 && isWebpName(sourceName)))
    val base = if (hasExtension) sourceName.substring(0, dot) else sourceName
    val extension = if (hasExtension) sourceName.substring(dot + 1) else null
    return reserveName(base, extension, used)
}

private fun reserveName(baseName: String, extension: String?, used: MutableSet<String>): String {
    val invalidCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    val base = baseName.replace(invalidCharacters, "_")
        .trim().take(120).ifBlank { if (extension == null) "file" else "image" }
    val extensionSuffix = extension?.let { ".${it.replace(invalidCharacters, "_")}" }.orEmpty()
    var suffix = 0
    while (true) {
        val name = if (suffix == 0) "$base$extensionSuffix"
        else "$base ($suffix)$extensionSuffix"
        if (used.add(name.lowercase(Locale.ROOT))) return name
        suffix++
    }
}
