package com.peaspilaf.copyparty.data.saf

import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile

/** One document of a Storage Access Framework tree, as the mirror needs to represent it. */
@Serializable
data class SafEntry(
    val rel: String,
    val uri: String,
    val dir: Boolean,
    val size: Long = 0,
    val mtime: Long = 0,
)

/**
 * The saved description of one SAF share: which tree it came from and what the app-space mirror of
 * that tree contains. The mirror itself is real directories plus placeholder files pre-sized to the
 * real file sizes; content is pulled from SAF on first read (see [SafBridge]).
 */
@Serializable
data class SafManifest(
    val treeUri: String,
    val rootName: String = "",
    val entries: List<SafEntry> = emptyList(),
)

/**
 * Builds and reads the app-space mirror of a SAF tree.
 *
 * copyparty is path-based, so it cannot list a `content://` tree. The mirror gives it ordinary
 * directories and ordinary file sizes, which is enough for listing, HEAD and permissions. The file
 * *content* is not copied up front: placeholders are sparse files with the right length.
 */
object SafMirror {
    fun directory(filesDir: File, shareId: String): File = File(File(filesDir, "saf"), shareId)

    fun treeRoot(filesDir: File, shareId: String): File = File(directory(filesDir, shareId), "tree")

    fun manifestFile(filesDir: File, shareId: String): File = File(directory(filesDir, shareId), "manifest.json")

    /** Writes the mirror for [entries] under [root] and the manifest next to it. */
    fun build(root: File, manifestFile: File, manifest: SafManifest) {
        root.deleteRecursively()
        root.mkdirs()
        for (entry in manifest.entries.sortedBy { it.rel }) {
            val target = File(root, entry.rel)
            if (entry.dir) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                // A sparse placeholder of the right length: listing shows the real size without
                // pulling a single byte from Android storage.
                RandomAccessFile(target, "rw").use { it.setLength(entry.size.coerceAtLeast(0)) }
                if (entry.mtime > 0) target.setLastModified(entry.mtime)
            }
        }
        manifestFile.parentFile?.mkdirs()
        manifestFile.writeText(SafJson.encode(manifest))
    }

    fun read(manifestFile: File): SafManifest? = runCatching {
        SafJson.decode(SafManifest.serializer(), manifestFile.readText())
    }.getOrNull()
}

/** The JSON settings used for the manifest file. */
internal object SafJson {
    val format = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(manifest: SafManifest): String =
        format.encodeToString(SafManifest.serializer(), manifest)

    fun decode(serializer: kotlinx.serialization.KSerializer<SafManifest>, text: String): SafManifest =
        format.decodeFromString(serializer, text)
}
