package com.peaspilaf.copyparty.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Which folders the app may expose. Sharing any folder on internal storage needs All files access
 * (MANAGE_EXTERNAL_STORAGE); without it the app falls back to a private folder that still works but
 * is only reachable through the app (and USB/MTP on many devices).
 */
interface StorageAccess {
    fun hasAllFilesAccess(): Boolean

    /** Intent that opens the system "All files access" toggle for this app. */
    fun allFilesAccessIntent(): Intent

    /** Always-writable folder owned by the app. Used as the zero-permission default share. */
    fun appShareRoot(): File

    /** Top-level folders the in-app folder picker may browse. */
    fun browseRoots(): List<File>

    fun listFolders(dir: File): List<File> =
        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }.orEmpty()
}

class AndroidStorageAccess(private val context: Context) : StorageAccess {
    override fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageManager()
    }

    override fun allFilesAccessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    override fun appShareRoot(): File =
        File(context.filesDir, "share").apply { mkdirs() }

    override fun browseRoots(): List<File> = buildList {
        add(appShareRoot())
        if (hasAllFilesAccess()) {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()?.let { add(it) }
        }
    }
}
