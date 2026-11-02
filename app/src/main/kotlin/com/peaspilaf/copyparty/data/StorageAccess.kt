package com.peaspilaf.copyparty.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    /**
     * Opens the closest system screen that can grant All files access.
     *
     * Not every device has [Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION]: some OEM
     * Settings builds never register it, older builds may not have the global list either, and
     * starting an activity from a non-Activity context without NEW_TASK throws. So the
     * implementation tries the app-specific screen, then the global list, then this app's own
     * details page, and reports whether any of them could actually be started. The caller must
     * show the manual steps when this returns false - a tap must never do nothing.
     */
    fun openAllFilesAccess(): Boolean

    /**
     * Intent for this app's "All files access" toggle. Kept as the first route of
     * [openAllFilesAccess] and for tests that only need the intent itself.
     */
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
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri())

    /**
     * Ordered fallback for devices whose Settings app is missing one of the usual screens.
     *
     * Resolution is checked before each launch so a route that cannot work is skipped quietly,
     * and a throw from `startActivity` (an OEM build that advertises but rejects it) moves on to
     * the next route. The last route is the app details page, from which the user can still reach
     * "All files access". Only when every route fails does this return false, so the picker can
     * explain the manual steps instead of leaving the tap with no visible effect.
     */
    override fun openAllFilesAccess(): Boolean =
        allFilesAccessCandidates().any { intent -> canResolve(intent) && start(intent) }

    /** The routes, best first: this app's toggle, the global toggle list, then this app's details. */
    private fun allFilesAccessCandidates(): List<Intent> = listOf(
        allFilesAccessIntent(),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()),
    )

    /**
     * Whether the system advertises a handler for [intent]. A query that itself fails (for example
     * because Android 11+ package visibility hides Settings from the caller) is not proof the route
     * is missing, so it is treated as "try it" rather than as a reason to give up.
     */
    private fun canResolve(intent: Intent): Boolean = runCatching {
        val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        handlers.isNotEmpty()
    }.getOrElse { true }

    private fun start(intent: Intent): Boolean = runCatching {
        val launch = Intent(intent)
        // A non-Activity context (application, ContextThemeWrapper, ...) must start a new task or
        // the platform throws AndroidRuntimeException. Activity context keeps its normal task.
        if (context !is Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        true
    }.getOrDefault(false)

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)

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
