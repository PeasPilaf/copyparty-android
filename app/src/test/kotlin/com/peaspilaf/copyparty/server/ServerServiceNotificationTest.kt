package com.peaspilaf.copyparty.server

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The foreground service is only bearable if it is quiet: one low-importance channel that explains
 * what it is, no notification until the server is actually asked to start, and a start request
 * without a config stops the service instead of leaving an empty notification behind.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ServerServiceNotificationTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext<Application>()

    private fun notifications(): NotificationManager =
        requireNotNull(app.getSystemService(NotificationManager::class.java)) {
            "the app is expected to always have a notification manager"
        }

    @Test
    fun `the server notification channel is quiet and explains itself`() {
        Robolectric.buildService(ServerService::class.java).create().destroy()

        val channel = notifications().notificationChannels.single()

        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channel.name.toString()).isEqualTo(app.getString(R.string.notification_channel_server))
        assertThat(channel.description)
            .isEqualTo(app.getString(R.string.notification_channel_server_description))
        assertThat(channel.canShowBadge()).isFalse()
    }

    @Test
    fun `no notification is posted until the server is asked to start`() {
        Robolectric.buildService(ServerService::class.java).create().destroy()

        assertThat(shadowOf(notifications()).size()).isEqualTo(0)
    }

    @Test
    fun `a start request without a config path stops the service instead of serving forever`() {
        val controller = Robolectric.buildService(ServerService::class.java).create()

        controller.startCommand(0, 0)

        assertThat(shadowOf(controller.get()).isStoppedBySelf()).isTrue()
    }
}
