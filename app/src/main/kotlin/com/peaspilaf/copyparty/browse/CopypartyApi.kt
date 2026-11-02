package com.peaspilaf.copyparty.browse

import android.net.Uri
import java.io.File

/**
 * One line of a copyparty folder listing: a folder or a file.
 *
 * [name] is the display name (URL-decoded, no trailing slash), [size] and [modified] are what the
 * server reported (`sz` / `ts`) and are 0 when it does not say. A folder's [size] is the total size
 * copyparty reports for it, not a real value to show.
 */
data class BrowseEntry(
    val name: String,
    val isFolder: Boolean,
    val size: Long = 0,
    val modified: Long = 0,
)

/**
 * Why a file operation failed, in the three cases the UI has to tell apart.
 *
 * [Unauthorized] is the one that has a fix the user can perform (type the share's password), so it is
 * a type of its own instead of a message to pattern-match.
 */
sealed class CopypartyException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 401/403: the folder needs the owner's password, or this client may not do that. */
    class Unauthorized(message: String = "This folder needs the share owner's password.") :
        CopypartyException(message)

    /** The server answered, but not with something a file browser can use. */
    class BadResponse(message: String) : CopypartyException(message)

    /** The server could not be reached at all (wrong address, server stopped, network gone). */
    class Unreachable(message: String, cause: Throwable? = null) : CopypartyException(message, cause)
}

/**
 * The network half of the native file screen, as an interface so a UI test can answer without a
 * server. Every call takes the server root or the volume URL as [baseUrl] and never a user name: the
 * generated config authenticates with a password only (`PW` header).
 *
 * Paths are volume-relative: `""` is the volume root, `"sub"` a folder in it, `"sub/notes.txt"` a file.
 */
interface CopypartyApi {
    /** The folder listing of [path] (`<baseUrl>/<path>?ls=json`). */
    fun list(baseUrl: String, path: String = "", password: String? = null): List<BrowseEntry>

    /** Downloads [path] into [sink] and returns [sink]. Nothing is created when the server refuses. */
    fun download(baseUrl: String, path: String, password: String? = null, sink: File): File

    /** Uploads [file] to [path] with a `PUT`; [onProgress] gets (bytesSent, totalBytes). */
    fun upload(
        baseUrl: String,
        file: File,
        path: String,
        password: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    )
}

/**
 * Where a finished download went: [location] is the sentence fragment the screen shows after
 * `Saved: ` (for example `Downloads/report.pdf`) and [uri] is what Open and Share hand to Android.
 */
data class SavedDownload(val location: String, val uri: Uri)

/**
 * Saves a downloaded temp file somewhere the user can find it again. Implemented over MediaStore on
 * Android 10+ and over the app's own Downloads folder below that; a test substitutes a fake.
 */
fun interface DownloadSaver {
    fun save(fileName: String, source: File): SavedDownload
}

/**
 * Lets the user choose a file to upload. The production implementation opens the system document
 * picker and copies the chosen document to a temp file, so the network code only ever sees a `File`.
 */
fun interface BrowseFilePicker {
    fun pick(onPicked: (File) -> Unit)
}
