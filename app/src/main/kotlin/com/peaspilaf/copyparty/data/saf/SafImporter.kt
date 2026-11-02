package com.peaspilaf.copyparty.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The result of mirroring a folder the user picked through Android's folder picker. */
data class ImportedSafFolder(
    val rootName: String,
    val localPath: String,
    val treeUri: String,
    val entryCount: Int,
)

/** Mirrors a Storage Access Framework folder into app storage, read-only. */
interface SafImporter {
    suspend fun import(treeUri: Uri, shareId: String): ImportedSafFolder

    /** The last import error, for the UI to show instead of a dead picker. */
    fun lastError(): String?
}

/**
 * Production [SafImporter]: walks the granted `content://` tree with [DocumentsContract], records
 * every document, and writes the app-space mirror (directories plus sized placeholders) that
 * copyparty can list.
 *
 * Only the metadata is touched here. File bytes are pulled from SAF later, on first read, by
 * [SafBridge], so picking a 50 GiB photo library does not copy 50 GiB.
 */
class AndroidSafImporter(
    private val context: Context,
    private val filesDir: File,
) : SafImporter {
    @Volatile
    private var error: String? = null

    override fun lastError(): String? = error

    override suspend fun import(treeUri: Uri, shareId: String): ImportedSafFolder = withContext(Dispatchers.IO) {
        error = null
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val entries = scan(treeUri)
        if (entries.isEmpty()) {
            error = "The folder could not be read: " + treeUri
            throw IllegalStateException(error)
        }
        val rootName = rootDisplayName(treeUri)
        val manifest = SafManifest(treeUri = treeUri.toString(), rootName = rootName, entries = entries)
        val root = SafMirror.treeRoot(filesDir, shareId)
        SafMirror.build(root, SafMirror.manifestFile(filesDir, shareId), manifest)
        ImportedSafFolder(
            rootName = rootName,
            localPath = root.absolutePath,
            treeUri = treeUri.toString(),
            entryCount = entries.count { !it.dir },
        )
    }

    private fun rootDisplayName(treeUri: Uri): String {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return "Shared folder"
        val documentUri = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId) }.getOrNull()
            ?: return "Shared folder"
        return queryName(documentUri) ?: documentUri.lastPathSegment ?: "Shared folder"
    }

    private fun queryName(documentUri: Uri): String? = runCatching {
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun scan(treeUri: Uri): List<SafEntry> {
        val entries = mutableListOf<SafEntry>()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return entries
        walk(treeUri, rootDocId, "", entries)
        return entries
    }

    private fun walk(treeUri: Uri, documentId: String, parentRel: String, entries: MutableList<SafEntry>) {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        }.getOrNull() ?: return
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val rows = runCatching {
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " COLLATE NOCASE",
            )?.use { cursor ->
                val collected = mutableListOf<Triple<String, String, Boolean>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val mtime = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    val rel = if (parentRel.isEmpty()) name else "$parentRel/$name"
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    entries += SafEntry(rel = rel, uri = uri.toString(), dir = isDir, size = size, mtime = mtime)
                    collected += Triple(id, rel, isDir)
                }
                collected
            }
        }.getOrNull() ?: return
        for ((id, rel, isDir) in rows) {
            if (isDir) walk(treeUri, id, rel, entries)
        }
    }
}
