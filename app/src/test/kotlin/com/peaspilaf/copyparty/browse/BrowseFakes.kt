package com.peaspilaf.copyparty.browse

import android.net.Uri
import java.io.File
import java.nio.file.Files

/**
 * The native file screen, without a server: folders, files and upload bodies live in maps, so a UI
 * test decides exactly what a folder contains and can assert exactly what was requested.
 *
 * Paths are the ones BrowseScreen talks in: relative to the share's volume root, `""` for the root
 * itself, `"sub"` for a folder in it and `"sub/notes.txt"` for a file in that folder.
 */
class FakeCopypartyApi : CopypartyApi {

    private val folders = mutableMapOf<String, MutableList<BrowseEntry>>()
    private val contents = mutableMapOf<String, String>()

    /** Folder paths that were listed, in order, so a test can prove a refresh happened. */
    val listed = mutableListOf<String>()

    /** The volume URL each request was aimed at, so a test can prove the share's mount path is used. */
    val baseUrls = mutableListOf<String>()

    /** File paths that were downloaded, in order. */
    val downloaded = mutableListOf<String>()

    /** Uploads that arrived, as `path to content`. */
    val uploads = mutableListOf<Pair<String, String>>()

    /** When set, a call without this password fails with [CopypartyException.Unauthorized]. */
    var requiredPassword: String? = null

    /** The password of the most recent call, so a test can prove the typed password was used. */
    var lastPassword: String? = null
        private set

    var listFailure: Throwable? = null
    var downloadFailure: Throwable? = null
    var uploadFailure: Throwable? = null

    fun putFolder(folder: String, name: String) {
        folders.getOrPut(normalized(folder)) { mutableListOf() }
            .add(BrowseEntry(name, isFolder = true))
    }

    fun putFile(folder: String, name: String, content: String) {
        val path = join(normalized(folder), name)
        contents[path] = content
        folders.getOrPut(normalized(folder)) { mutableListOf() }.removeAll { it.name == name }
        folders.getValue(normalized(folder)).add(
            BrowseEntry(name, isFolder = false, size = content.toByteArray().size.toLong()),
        )
    }

    override fun list(baseUrl: String, path: String, password: String?): List<BrowseEntry> {
        baseUrls += baseUrl
        listed += normalized(path)
        lastPassword = password
        listFailure?.let { throw it }
        requirePassword(password)
        return folders[normalized(path)].orEmpty().toList()
    }

    override fun download(baseUrl: String, path: String, password: String?, sink: File): File {
        downloaded += normalized(path)
        lastPassword = password
        downloadFailure?.let { throw it }
        requirePassword(password)
        val content = contents[normalized(path)] ?: throw CopypartyException.BadResponse("no such file: $path")
        sink.parentFile?.mkdirs()
        sink.writeText(content)
        return sink
    }

    override fun upload(
        baseUrl: String,
        file: File,
        path: String,
        password: String?,
        onProgress: (Long, Long) -> Unit,
    ) {
        lastPassword = password
        uploadFailure?.let { throw it }
        requirePassword(password)
        val target = normalized(path)
        val content = file.readText()
        uploads += target to content
        contents[target] = content
        val folder = target.substringBeforeLast('/', "")
        val name = target.substringAfterLast('/')
        folders.getOrPut(folder) { mutableListOf() }.removeAll { it.name == name }
        folders.getValue(folder).add(BrowseEntry(name, isFolder = false, size = file.length()))
        onProgress(file.length(), file.length())
    }

    private fun requirePassword(password: String?) {
        val wanted = requiredPassword ?: return
        if (password != wanted) throw CopypartyException.Unauthorized()
    }

    private fun normalized(path: String): String = path.trim('/')

    private fun join(folder: String, name: String): String = if (folder.isEmpty()) name else "$folder/$name"
}

/** Remembers what was saved and reports a stable, user-readable location. */
class FakeDownloadSaver : DownloadSaver {
    val saved = mutableListOf<String>()

    override fun save(fileName: String, source: File): SavedDownload {
        saved += fileName
        return SavedDownload("Downloads/$fileName", Uri.fromFile(source))
    }
}

/** Hands [chosen] straight to the screen instead of opening the system document picker. */
class FakeBrowseFilePicker(var chosen: File? = null) : BrowseFilePicker {
    var picks = 0
        private set

    override fun pick(onPicked: (File) -> Unit) {
        picks += 1
        chosen?.let(onPicked)
    }
}

/** A real file on disk, so the upload path sees the same thing a picked document would be copied to. */
fun tempUploadFile(name: String, content: String): File {
    val dir = Files.createTempDirectory("copyparty-upload").toFile()
    return File(dir, name).apply { writeText(content) }
}
