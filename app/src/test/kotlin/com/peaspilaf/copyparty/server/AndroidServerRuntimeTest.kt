package com.peaspilaf.copyparty.server

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ConfigRenderer
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import com.peaspilaf.copyparty.data.ConfigStore
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val REFUSED = "the system refused to start the service"

/**
 * What happens between pressing "start" and the foreground service owning the server: a broken
 * configuration must never reach the service, a good one becomes the exact config file copyparty
 * reads plus one explicit start intent, and any failure of the system call comes back as a message
 * the screen can show instead of a silently dead server.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AndroidServerRuntimeTest {
    private lateinit var app: Application
    private lateinit var store: FakeConfigStore
    private lateinit var runtime: AndroidServerRuntime

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        store = FakeConfigStore(validState())
        runtime = AndroidServerRuntime(app, store)
        ServerState.set(ServerStatus.Stopped)
    }

    @After
    fun tearDown() {
        ServerState.set(ServerStatus.Stopped)
    }

    @Test
    fun `an invalid state fails without writing a config file`() {
        store.state = AppState()

        val status = runtime.start()

        assertThat(status).isInstanceOf(ServerStatus.Failed::class.java)
        assertThat(ServerState.status.value).isEqualTo(status)
        assertThat(File(app.filesDir, "copyparty/copyparty.conf").exists()).isFalse()
        assertThat(shadowOf(app).allStartedServices).isEmpty()
    }

    @Test
    fun `a valid state is written to the app private config file byte for byte`() {
        val status = runtime.start()

        assertThat(status).isEqualTo(ServerStatus.Starting)
        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Starting)

        val dir = File(app.filesDir, "copyparty")
        val configFile = File(dir, "copyparty.conf")
        assertThat(configFile.isFile).isTrue()

        val expected = ConfigRenderer.render(store.state, File(dir, "copyparty.log").absolutePath)
        assertWithMessage("the config copyparty reads must be exactly what the renderer produced")
            .that(configFile.readBytes().contentEquals(expected.toByteArray(Charsets.UTF_8)))
            .isTrue()
    }

    @Test
    fun `starting hands the foreground service the config path, port and lan setting`() {
        store.state = validState(port = 4646, lanAccess = true)

        runtime.start()

        val intent = shadowOf(app).allStartedServices.single()
        assertThat(intent.component).isEqualTo(ComponentName(app, ServerService::class.java))
        assertThat(intent.action).isEqualTo(ServerService.ACTION_START)
        assertThat(intent.getStringExtra(ServerService.EXTRA_CONFIG_PATH))
            .isEqualTo(File(app.filesDir, "copyparty/copyparty.conf").absolutePath)
        assertThat(intent.getIntExtra(ServerService.EXTRA_PORT, -1)).isEqualTo(4646)
        assertThat(intent.getBooleanExtra(ServerService.EXTRA_LAN_ACCESS, false)).isTrue()
    }

    @Test
    fun `a system that refuses to start the service turns into a failure with the reason`() {
        val failing = AndroidServerRuntime(FailingContext(app, failForegroundStart = true), store)

        val status = failing.start()

        assertThat(status).isInstanceOf(ServerStatus.Failed::class.java)
        assertThat((status as ServerStatus.Failed).message).contains(REFUSED)
        assertThat(ServerState.status.value).isEqualTo(status)
    }

    @Test
    fun `stopping a starting server asks the service to stop and shows the stopping state`() {
        runtime.start()

        runtime.stop()

        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Stopping)
        assertThat(shadowOf(app).allStartedServices.map { it.action })
            .containsExactly(ServerService.ACTION_START, ServerService.ACTION_STOP)
            .inOrder()
    }

    @Test
    fun `a service that cannot be reached still leaves the server stopped`() {
        val failing = AndroidServerRuntime(FailingContext(app, failStartService = true), store)
        failing.start()

        failing.stop()

        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Stopped)
        assertThat(shadowOf(app).nextStoppedService.action).isEqualTo(ServerService.ACTION_STOP)
    }

    @Test
    fun `stopping when nothing is running does not pretend to be stopping`() {
        runtime.stop()

        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Stopped)
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

private class FakeConfigStore(var state: AppState) : ConfigStore {
    override fun load(): AppState = state

    override fun save(state: AppState) {
        this.state = state
    }
}

/** A [Context] whose service calls fail the way an Android device can make them fail. */
private class FailingContext(
    base: Context,
    private val failForegroundStart: Boolean = false,
    private val failStartService: Boolean = false,
) : ContextWrapper(base) {
    override fun startForegroundService(service: Intent): ComponentName? {
        if (failForegroundStart) throw IllegalStateException(REFUSED)
        return super.startForegroundService(service)
    }

    override fun startService(service: Intent): ComponentName? {
        if (failStartService) throw IllegalStateException(REFUSED)
        return super.startService(service)
    }
}
