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

/** [ServerRuntime] whose status the test owns. Starting flips to `Starting`, nothing real runs. */
class FakeServerRuntime(initial: ServerStatus = ServerStatus.Stopped) : ServerRuntime {
    private val flow = MutableStateFlow(initial)

    override val status: StateFlow<ServerStatus> = flow

    var startCalls = 0
        private set
    var stopCalls = 0
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
}

/**
 * Storage that never touches the device: the app share folder is a fresh temporary directory and it
 * is the only browse root, which makes the folder picker a single, deterministic choice.
 */
class FakeStorageAccess(
    val appRoot: File = Files.createTempDirectory("copyparty-ui-share").toFile(),
    private val allFilesAccess: Boolean = false,
) : StorageAccess {

    override fun hasAllFilesAccess(): Boolean = allFilesAccess

    override fun allFilesAccessIntent(): Intent =
        throw UnsupportedOperationException("UI tests never open the system settings screen")

    override fun appShareRoot(): File = appRoot

    override fun browseRoots(): List<File> = listOf(appRoot)
}

/** A ViewModel wired to the in-memory fakes above. */
fun testViewModel(
    store: FakeConfigStore = FakeConfigStore(),
    server: FakeServerRuntime = FakeServerRuntime(),
    storage: FakeStorageAccess = FakeStorageAccess(),
): AppViewModel = AppViewModel(store, server, storage)
