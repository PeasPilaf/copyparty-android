package com.peaspilaf.copyparty.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * What one "Add files" run did, in the words the snackbar shows.
 *
 * [imported] is the list of files that really landed in the target folder; [failed] counts the
 * documents that could not be read (a provider that went away, a file that is not really there).
 * [summary] is written for a person, not a log: "Added 2 files." / "Added 1 file. 1 could not be
 * read."
 */
data class ImportResult(
    val imported: List<File> = emptyList(),
    val failed: Int = 0,
) {
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (imported.isNotEmpty()) {
                parts += "Added ${imported.size} ${if (imported.size == 1) "file" else "files"}."
            }
            if (failed > 0) {
                parts += "$failed could not be read."
            }
            if (parts.isEmpty()) return "No files were added."
            return parts.joinToString(" ")
        }
}

/**
 * Copies documents the user picked in the system picker into the app's own share folder.
 *
 * The app's folder is the one folder every build can serve, so "Add files" is the way the Play build
 * gets something to serve at all. Copying is deliberately conservative:
 *
 *  * a file that is already there is never replaced - a unique name is chosen instead
 *    (`report.txt` becomes `report (2).txt`), so an import can never destroy the user's data;
 *  * one unreadable document fails alone: the rest of the batch still arrives, and the count of
 *    failures is reported instead of hidden;
 *  * every `Throwable` counts as "cannot read this one" - a content provider can throw anything,
 *    and one broken provider must not take the import down.
 */
class FileImporter(private val resolver: ContentResolver) {

    fun import(documents: List<Uri>, destination: File): ImportResult {
        if (!destination.isDirectory) destination.mkdirs()
        val imported = mutableListOf<File>()
        var failed = 0

        for (uri in documents) {
            var target: File? = null
            try {
                val name = displayName(uri) ?: uri.lastPathSegment ?: "file"
                target = uniqueFile(destination, name)
                val input = resolver.openInputStream(uri)
                    ?: throw IllegalStateException("the document at $uri could not be opened")
                input.use { stream ->
                    target.outputStream().use { out -> stream.copyTo(out) }
                }
                imported += target
            } catch (t: Throwable) {
                failed++
                // A half-written file must not look like a successful import.
                runCatching { target?.delete() }
            }
        }

        return ImportResult(imported = imported, failed = failed)
    }

    /** The document's own name, from the provider or, as a fallback, from the URI itself. */
    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * A name in [directory] that is not taken yet. `report.txt` becomes `report (2).txt`,
     * `report (3).txt`, ... so an import never overwrites an existing file.
     */
    private fun uniqueFile(directory: File, name: String): File {
        val safe = name.replace('/', '_').replace('\\', '_').trim().ifBlank { "file" }
        val first = File(directory, safe)
        if (!first.exists()) return first

        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val extension = if (dot > 0) safe.substring(dot) else ""
        var attempt = 2
        while (true) {
            val candidate = File(directory, "$base ($attempt)$extension")
            if (!candidate.exists()) return candidate
            attempt++
        }
    }
}
