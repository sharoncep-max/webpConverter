package com.example.webpconverter.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.FileNotFoundException
import java.security.MessageDigest

class DocumentStore(private val resolver: ContentResolver) {
    data class Child(val uri: Uri, val name: String, val mime: String, val size: Long?)

    suspend fun children(tree: Uri): List<Child> {
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val result = mutableListOf<Child>()
        val cursor = resolver.query(childrenUri, columns, null, null, null)
            ?: throw IOException("The folder provider did not return a file list.")
        cursor.use {
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                result += Child(
                    DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name, it.getString(2).orEmpty(), if (it.isNull(3)) null else it.getLong(3)
                )
            }
            if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                throw IOException("This provider is still loading the folder. Wait, then tap Refresh.")
            }
        }
        return result
    }

    suspend fun scan(tree: Uri): List<SourceFile> = children(tree)
        .filter { it.mime != DocumentsContract.Document.MIME_TYPE_DIR }
        .map { SourceFile(it.uri.toString(), it.name, it.size, it.mime.takeIf(String::isNotBlank)) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun documentUri(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        tree, DocumentsContract.getTreeDocumentId(tree)
    )

    fun sameFolder(first: Uri, second: Uri): Boolean =
        first.authority == second.authority &&
            DocumentsContract.getTreeDocumentId(first) == DocumentsContract.getTreeDocumentId(second)

    fun exists(uri: Uri): Boolean = try {
        val cursor = resolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
        ) ?: throw IOException("The provider could not check whether a file exists.")
        cursor.use { it.moveToFirst() }
    } catch (_: FileNotFoundException) {
        false
    }

    fun delete(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) {
            throw IOException("The provider refused to delete the file.")
        }
    }

    suspend fun copyAndVerify(source: Uri, output: Uri) {
        val sourceDigest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(source) ?: throw IOException("Cannot open the source file.")
        input.use {
            val stream = resolver.openOutputStream(output, "wt")
                ?: throw IOException("Cannot open the destination file.")
            stream.use { destination ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = it.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                    sourceDigest.update(buffer, 0, count)
                }
            }
        }
        if (!MessageDigest.isEqual(sourceDigest.digest(), digest(output))) {
            throw IOException("Copy verification failed. The source was kept.")
        }
    }

    suspend fun verifyCopy(source: Uri, output: Uri) {
        if (!MessageDigest.isEqual(digest(source), digest(output))) {
            throw IOException("The source or destination changed after copying. The source was kept.")
        }
    }

    private suspend fun digest(uri: Uri): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val saved = resolver.openInputStream(uri)
            ?: throw IOException("Cannot reopen the copy for verification. The source was kept.")
        saved.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }
}
