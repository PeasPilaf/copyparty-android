package com.peaspilaf.copyparty.ui

import android.content.Intent
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.data.ConfigStore
import com.peaspilaf.copyparty.data.StorageAccess
import com.peaspilaf.copyparty.server.ServerRuntime
import com.peaspilaf.copyparty.server.ServerStatus
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [ConfigStore] that records every write, so a test can assert both what the UI shows and
 * what it persisted. No file is ever touched.
 */
class FakeConfigStore(initial: AppState = AppState()) : ConfigStore {
    var state: AppState = initial
        private set

    val saves: MutableList<AppState> = mutableListOf()

    override fun load(): AppState = state

    override fun save(state: AppState) {
        this.state = state
        saves += state
    }
}

/**
 * [ServerRuntime] whose status the test owns. Starting flips to `Starting`, nothing real runs.
 * [applyConfig] is the restart-to-apply path: it records the call and moves the runtime back to
 * `Starting`, exactly like a real restart would - it never pretends the new config was already live.
 */
class FakeServerRuntime(initial: ServerStatus = ServerStatus.Stopped) : ServerRuntime {
    private val flow = MutableStateFlow(initial)

    override val status: StateFlow<ServerStatus> = flow

    var startCalls = 0
        private set
    var stopCalls = 0
        private set
    var applyCalls = 0
        private set

    override fun start(): ServerStatus {
        startCalls++
        flow.value = ServerStatus.Starting
        return flow.value
    }

    override fun stop() {
        stopCalls++
        flow.value = ServerStatus.Stopped
    }

    override fun applyConfig(): ServerStatus {
        applyCalls++
        flow.value = ServerStatus.Starting
        return flow.value
    }
}

/**
 * Storage that never touches the device.
 *
 * [appRoot] always exists and is always browsable. [phoneRoot] only becomes a browse root when the
 * build supports external folders *and* All files access has been granted - that is the state the
 * picker has to re-read when the user comes back from the system settings screen, so [allFilesAccess]
 * is a `var` a test can flip mid-composition.
 *
 * A folder can be made [unreadable] so the picker has to tell "I cannot open this" apart from
 * "there is nothing in here".
 */
class FakeStorageAccess(
    val appRoot: File = Files.createTempDirectory("copyparty-ui-app").toFile(),
    val phoneRoot: File = Files.createTempDirectory("copyparty-ui-phone").toFile(),
    var allFilesAccess: Boolean = false,
    override val supportsExternalFolders: Boolean = true,
    private val unreadable: MutableSet<File> = mutableSetOf(),
    /** What [openAllFilesAccess] reports; false is the "no system screen worked" case. */
    var openAllFilesAccessResult: Boolean = true,
) : StorageAccess {

    init {
        appRoot.mkdirs()
        phoneRoot.mkdirs()
    }

    /** How many times the picker asked the system to show its All-files-access screen. */
    var openCalls: Int = 0
        private set

    /** The intent the fake would have launched, so tests can assert the requested screen. */
    var lastOpenIntent: Intent? = null
        private set

    override fun hasAllFilesAccess(): Boolean = allFilesAccess

    override fun openAllFilesAccess(): Boolean {
        openCalls++
        lastOpenIntent = allFilesAccessIntent()
        return openAllFilesAccessResult
    }

    override fun allFilesAccessIntent(): Intent =
        Intent(GRANT_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun appShareRoot(): File = appRoot

    override fun browseRoots(): List<File> = buildList {
        add(appRoot)
        if (supportsExternalFolders && allFilesAccess) add(phoneRoot)
    }

    override fun listFolders(dir: File): List<File> =
        if (dir in unreadable) {
            throw SecurityException("the test pretends this folder cannot be read: $dir")
        } else {
            super.listFolders(dir)
        }

    /** Makes [dir] report "cannot read" instead of "empty". */
    fun makeUnreadable(dir: File) {
        unreadable += dir
    }

    companion object {
        /** The action the fake fires for "Allow access to all files"; never a real system screen. */
        const val GRANT_ACTION = "com.peaspilaf.copyparty.test.ALL_FILES_ACCESS"
    }
}

/** A ViewModel wired to the in-memory fakes above. */
fun testViewModel(
    store: FakeConfigStore = FakeConfigStore(),
    server: FakeServerRuntime = FakeServerRuntime(),
    storage: StorageAccess = FakeStorageAccess(),
): AppViewModel = AppViewModel(store, server, storage)
