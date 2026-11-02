package com.peaspilaf.copyparty.data.saf

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * The Kotlin half of the read-only SAF shim. copyparty (Python) calls [materialize] the first time it
 * opens a mirrored file; this copies the real bytes out of the SAF document into the placeholder.
 *
 * Called from Python, so the methods are `@JvmStatic`. The manifest path is derived from the mirror
 * root (`<share>/tree` -> `<share>/manifest.json`), which keeps the Python side free of app paths.
 */
class SafBridge private constructor() {
    companion object {
        private const val TAG = "SafBridge"

        @Volatile
        private var context: Context? = null

        private val manifests = HashMap<String, SafManifest>()
        private val materialized = HashSet<String>()

        @JvmStatic
        fun configure(appContext: Context) {
            context = appContext.applicationContext
            manifests.clear()
            materialized.clear()
        }

        /**
         * Pulls the content of one mirrored file from SAF. Returns false when the document cannot be
         * read, which copyparty surfaces as an I/O error on the request instead of serving zeros.
         */
        @JvmStatic
        @Synchronized
        fun materialize(root: String, rel: String): Boolean {
            val rootFile = File(root)
            val target = File(rootFile, rel)
            if (!target.isFile) return false
            val key = target.absolutePath
            if (key in materialized) return true

            val manifest = manifestFor(rootFile) ?: return false
            val entry = manifest.entries.firstOrNull { it.rel == rel && !it.dir } ?: return false
            val app = context ?: return false

            val input: InputStream = try {
                val uri = Uri.parse(entry.uri)
                if (uri.scheme == "file") {
                    File(uri.path ?: return false).inputStream()
                } else {
                    app.contentResolver.openInputStream(uri) ?: return false
                }
            } catch (error: Exception) {
                Log.w(TAG, "cannot open SAF document for $rel", error)
                return false
            }

            val temp = File(target.parentFile, "." + target.name + ".saf-tmp")
            try {
                input.use { source ->
                    FileOutputStream(temp).use { sink -> source.copyTo(sink, 256 * 1024) }
                }
                // Replace the placeholder with the real file, atomically on the same filesystem.
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                if (entry.mtime > 0) target.setLastModified(entry.mtime)
                materialized.add(key)
                return true
            } catch (error: Exception) {
                Log.w(TAG, "cannot copy SAF document for $rel", error)
                temp.delete()
                return false
            }
        }

        @Synchronized
        private fun manifestFor(root: File): SafManifest? {
            val manifestFile = File(root.parentFile, "manifest.json")
            val cached = manifests[root.absolutePath]
            if (cached != null) return cached
            val loaded = SafMirror.read(manifestFile) ?: return null
            manifests[root.absolutePath] = loaded
            return loaded
        }
    }
}
