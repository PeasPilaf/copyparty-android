package com.peaspilaf.copyparty.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.peaspilaf.copyparty.BuildConfig
import java.io.File

/**
 * Which folders the app may expose. In the self-hosted build, any folder on internal storage can be
 * shared after granting All files access (MANAGE_EXTERNAL_STORAGE). The Play build never requests
 * that permission, so it shares the app's own folder - a normal folder on the device that needs no
 * permissions and is also reachable over USB.
 */
interface StorageAccess {
    /**
     * Whether this build may offer folders outside the app's own folder at all.
     *
     * The self-hosted build can (after All files access); the Play build cannot, because it must not
     * request that permission. Screens use this to say which build the user has instead of showing
     * a button that can never work - the Play build only explains, and offers "Add files".
     */
    val supportsExternalFolders: Boolean get() = true

    fun hasAllFilesAccess(): Boolean

    /** Intent that opens the system "All files access" toggle for this app. */
    fun allFilesAccessIntent(): Intent

    /** Always-writable folder owned by the app. Used as the zero-permission default share. */
    fun appShareRoot(): File

    /** Top-level folders the in-app folder picker may browse. */
    fun browseRoots(): List<File>

    /**
     * Sub-folders of [dir]. Throws [SecurityException] when the folder exists but cannot be listed,
     * so the picker can say "this folder cannot be opened" instead of pretending it is empty.
     */
    fun listFolders(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        if (!dir.canRead()) throw SecurityException("Cannot read ${dir.absolutePath}")
        val children = dir.listFiles() ?: throw SecurityException("Cannot read ${dir.absolutePath}")
        return children
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
    }
}

class AndroidStorageAccess(private val context: Context) : StorageAccess {
    /**
     * The Play build has no broad-storage permission, so it can only offer its own folder. Below
     * Android 11 there is no All-files-access permission either and this app does not request the
     * legacy read/write permissions, so offering the rest of the phone there would be a dead end.
     */
    override val supportsExternalFolders: Boolean
        get() = BuildConfig.ALL_FILES_ACCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun hasAllFilesAccess(): Boolean {
        // The Play build never requests the permission: sharing is limited to the app's own folder.
        if (!BuildConfig.ALL_FILES_ACCESS) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        // The platform cannot always answer this question: during early boot the storage user may
        // not exist yet, and a plain JVM test host has no platform user at all. Not being able to
        // ask is not permission: fall back to the app's private share folder instead of crashing
        // the folder picker.
        return runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }

    override fun allFilesAccessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    override fun appShareRoot(): File =
        File(context.filesDir, "share").apply { mkdirs() }

    override fun browseRoots(): List<File> = buildList {
        add(appShareRoot())
        if (hasAllFilesAccess()) {
            @Suppress("DEPRECATION")
            val sharedStorage = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            if (sharedStorage != null) add(sharedStorage)
        }
    }
}
