package com.peaspilaf.copyparty.server

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.data.ConfigStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * J24 — editing while the server runs.
 *
 * The lie this replaces: the screens saved a new permission or a new port and nothing happened to the
 * server, while Home kept showing "Server running" and the old URLs. Now every edit can be applied
 * with one deliberate step (`AppViewModel.applyConfig()` -> `ServerRuntime.applyConfig()`), and the
 * app tells the truth about what that step did:
 *
 *  * the config file is re-rendered from the store *before* the server is asked to restart;
 *  * the service gets one `ACTION_RESTART` carrying the fresh config, not a stop racing a start;
 *  * the status leaves `Running` (the old URLs are gone, because they are the old server's);
 *  * calling it while stopped simply starts the server, and calling it twice is safe;
 *  * a configuration that cannot work is refused without touching the running server.
 *
 * `ServerRuntime` gains `applyConfig(): ServerStatus` (frozen cross-region signature).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ApplyConfigTest {
    private lateinit var app: Application
    private lateinit var store: RuntimeTestStore
    private lateinit var runtime: AndroidServerRuntime

    private val configFile: File get() = File(app.filesDir, "copyparty/copyparty.conf")
    private val logFile: File get() = File(app.filesDir, "copyparty/copyparty.log")

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        store = RuntimeTestStore(validState())
        runtime = AndroidServerRuntime(app, store)
        File(app.filesDir, "copyparty").deleteRecursively()
        ServerState.set(ServerStatus.Stopped)
    }

    private fun startedServiceActions(): List<String?> =
        shadowOf(app).allStartedServices.map { it.action }

    @Test
    fun `applying edited settings re-renders the config and restarts the running server`() {
        runtime.start()
        ServerState.set(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        store.state = validState(port = 4646)

        val status = runtime.applyConfig()

        assertThat(status).isEqualTo(ServerStatus.Starting)
        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Starting)
        assertThat(configFile.readText())
            .isEqualTo(ConfigRenderer.render(store.state, logFile.absolutePath))
        assertThat(startedServiceActions())
            .containsExactly(ServerService.ACTION_START, ServerService.ACTION_RESTART)
            .inOrder()
        val restart = shadowOf(app).allStartedServices.last()
        assertThat(restart.component?.className).isEqualTo(ServerService::class.java.name)
        assertThat(restart.getStringExtra(ServerService.EXTRA_CONFIG_PATH))
            .isEqualTo(configFile.absolutePath)
        assertThat(restart.getIntExtra(ServerService.EXTRA_PORT, -1)).isEqualTo(4646)
    }

    @Test
    fun `applying edits never leaves the old server's urls on screen`() {
        runtime.start()
        ServerState.set(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        store.state = validState(port = 4646)

        runtime.applyConfig()

        assertThat(ServerState.status.value as? ServerStatus.Running)
            .isNull()
    }

    @Test
    fun `the status walks from stopping to starting while the server restarts`() {
        runtime.start()
        val running = ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923)
        ServerState.set(running)
        store.state = validState(port = 4646)
        val seen = mutableListOf<ServerStatus>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { ServerState.status.collect { seen += it } }

        try {
            runtime.applyConfig()
        } finally {
            collector.cancel()
        }

        assertThat(seen).containsExactly(
            running,
            ServerStatus.Stopping,
            ServerStatus.Starting,
        ).inOrder()
    }

    @Test
    fun `applying a config while stopped simply starts the server`() {
        val status = runtime.applyConfig()

        assertThat(status).isEqualTo(ServerStatus.Starting)
        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Starting)
        assertThat(startedServiceActions()).containsExactly(ServerService.ACTION_START)
        assertThat(configFile.readText())
            .isEqualTo(ConfigRenderer.render(store.state, logFile.absolutePath))
    }

    @Test
    fun `applying the same config twice is safe and still applies the latest edit`() {
        store.state = validState(port = 4646)

        val first = runtime.applyConfig()
        store.state = validState(port = 4747)
        val second = runtime.applyConfig()

        assertThat(first).isEqualTo(ServerStatus.Starting)
        assertThat(second).isEqualTo(ServerStatus.Starting)
        // One request per apply, and never a bare stop: a stop racing a start is how a restart loses
        // the port (the interpreter is still shutting down when the new one tries to bind). The
        // first apply had no server to restart, so it starts; the second one restarts.
        val actions = startedServiceActions()
        assertThat(actions).hasSize(2)
        assertThat(actions.first()).isEqualTo(ServerService.ACTION_START)
        assertThat(actions).doesNotContain(ServerService.ACTION_STOP)
        assertThat(actions.drop(1)).containsExactly(ServerService.ACTION_RESTART)
        assertThat(configFile.readText())
            .isEqualTo(ConfigRenderer.render(store.state, logFile.absolutePath))
        assertThat(shadowOf(app).allStartedServices.last().getIntExtra(ServerService.EXTRA_PORT, -1))
            .isEqualTo(4747)
    }

    @Test
    fun `a configuration that cannot work is refused without touching the running server`() {
        runtime.start()
        ServerState.set(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
        val intentsBefore = shadowOf(app).allStartedServices.size
        store.state = AppState(settings = ServerSettings(port = 80))

        val status = runtime.applyConfig()

        assertThat(status).isInstanceOf(ServerStatus.Failed::class.java)
        assertThat(shadowOf(app).allStartedServices).hasSize(intentsBefore)
        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Running(listOf("http://127.0.0.1:3923"), 3923))
    }

    @Test
    fun `the server log the runtime writes is the log the log screen reads`() {
        runtime.start()

        assertThat(configFile.readText())
            .contains("  lo: ${File(app.filesDir, "copyparty/copyparty.log").absolutePath}")
        logFile.parentFile?.mkdirs()
        logFile.writeText("2026-11-01 12:00:00.000 client connected\n")

        assertThat(serverLogTail(app.filesDir)).contains("client connected")
    }

    private fun validState(port: Int = 3923, lanAccess: Boolean = false) = AppState(
        shares = listOf(
            Share(
                id = "photos",
                name = "Photos",
                urlPath = "photos",
                localPath = File(app.filesDir, "share/photos").absolutePath,
                grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
            ),
        ),
        settings = ServerSettings(port = port, lanAccess = lanAccess),
    )
}

/** A store whose state the test can change between the first start and the re-apply. */
private class RuntimeTestStore(var state: AppState) : ConfigStore {
    override fun load(): AppState = state

    override fun save(state: AppState) {
        this.state = state
    }
}
