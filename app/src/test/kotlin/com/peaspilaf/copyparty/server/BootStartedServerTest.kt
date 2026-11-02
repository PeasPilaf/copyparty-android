package com.peaspilaf.copyparty.server

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.CopypartyApp
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.Grant
import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ServerSettings
import com.peaspilaf.copyparty.core.Share
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * J26 — start on boot, and say so.
 *
 * "Start on boot" used to be a switch that quietly did nothing visible: the phone rebooted, and the
 * only sign the server had come back was found by opening the app. Now the boot path starts the
 * server and posts a confirmation notification the user can dismiss, and it still refuses to start a
 * configuration that cannot work.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BootStartedServerTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val manager: NotificationManager
        get() = requireNotNull(app.getSystemService(NotificationManager::class.java))

    private val container
        get() = (app as CopypartyApp).container

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.cancelAll()
        File(app.filesDir, "app-state.json").delete()
        File(app.filesDir, "copyparty").deleteRecursively()
        ServerState.set(ServerStatus.Stopped)
    }

    @After
    fun tearDown() {
        ServerState.set(ServerStatus.Stopped)
        File(app.filesDir, "app-state.json").delete()
        File(app.filesDir, "copyparty").deleteRecursively()
    }

    @Test
    fun `a reboot with start on boot starts the server and posts a confirmation`() {
        container.configStore.save(validState(startOnBoot = true))

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Starting)
        assertThat(shadowOf(app).allStartedServices.map { it.action })
            .containsExactly(ServerService.ACTION_START)
        assertThat(shadowOf(manager).size()).isEqualTo(1)
        val visible = with(shadowOf(shadowOf(manager).allNotifications.single())) {
            "$contentTitle $contentText".lowercase()
        }
        assertThat(visible).contains("started on boot")
    }

    @Test
    fun `a reboot without start on boot starts nothing and stays quiet`() {
        container.configStore.save(validState(startOnBoot = false))

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Stopped)
        assertThat(shadowOf(app).allStartedServices).isEmpty()
        assertThat(shadowOf(manager).size()).isEqualTo(0)
    }

    @Test
    fun `a configuration that cannot work is not started blind at boot`() {
        container.configStore.save(AppState(settings = ServerSettings(port = 80, startOnBoot = true)))

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(shadowOf(app).allStartedServices).isEmpty()
        assertThat(shadowOf(manager).size()).isEqualTo(0)
    }

    @Test
    fun `the boot path ignores unrelated broadcasts`() {
        container.configStore.save(validState(startOnBoot = true))

        BootReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))

        assertThat(ServerState.status.value).isEqualTo(ServerStatus.Stopped)
        assertThat(shadowOf(app).allStartedServices).isEmpty()
    }

    private fun validState(startOnBoot: Boolean) = AppState(
        shares = listOf(
            Share(
                id = "photos",
                name = "Photos",
                urlPath = "photos",
                localPath = File(app.filesDir, "share/photos").absolutePath,
                grants = listOf(Grant(Grant.EVERYONE, setOf(Permission.VIEW))),
            ),
        ),
        settings = ServerSettings(startOnBoot = startOnBoot),
    )
}
