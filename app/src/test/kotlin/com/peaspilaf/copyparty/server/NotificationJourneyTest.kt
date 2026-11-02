package com.peaspilaf.copyparty.server

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.MainActivity
import com.peaspilaf.copyparty.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * J23 — the notification is the server's only body while the app is in the background.
 *
 * The promise: while the server runs, the notification tells you where to connect and lets you stop
 * it without finding the app again; when a start fails, the notification *stays* (dismissible, and
 * tapping it opens the app) instead of being deleted along with the foreground state - the old
 * behaviour left a silent, unexplained failure.
 *
 * `ServerNotifications` is the single place those notifications are built, so the service and the
 * boot receiver cannot drift apart.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationJourneyTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val manager: NotificationManager
        get() = requireNotNull(app.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        manager.cancelAll()
    }

    @Test
    fun `the running notification shows the address and stop action`() {
        val address = "http://192.168.1.20:3923"

        val notification = ServerNotifications.running(app, address)

        val shadow = shadowOf(notification)
        val visible = "${shadow.contentTitle} ${shadow.contentText}"
        assertThat(visible).contains(address)
        assertThat(visible).contains(app.getString(R.string.notification_server_running))
        assertThat(notification.actions.map { it.title.toString() }).containsAtLeast("Open", "Stop")
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT)
            .isNotEqualTo(0)
        assertThat(notification.contentIntent).isNotNull()
    }

    @Test
    fun `the stop action in the notification stops the server`() {
        val notification = ServerNotifications.running(app, "http://127.0.0.1:3923")

        val stop = notification.actions.single { it.title.toString() == "Stop" }
        val intent = shadowOf(stop.actionIntent).savedIntent

        assertThat(intent.action).isEqualTo(ServerService.ACTION_STOP)
        assertThat(intent.component?.className).isEqualTo(ServerService::class.java.name)
    }

    @Test
    fun `the open action in the notification returns to the app`() {
        val notification = ServerNotifications.running(app, "http://127.0.0.1:3923")

        val open = notification.actions.single { it.title.toString() == "Open" }
        val intent = shadowOf(open.actionIntent).savedIntent

        assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
    }

    @Test
    fun `a failed start leaves a tappable failure notification`() {
        val notification = ServerNotifications.failed(app, "The server did not start listening on port 3923.")

        val shadow = shadowOf(notification)
        val visible = "${shadow.contentTitle} ${shadow.contentText}".lowercase()
        assertThat(visible).contains("could not start")
        assertThat(visible).contains("tap to open")
        assertThat(shadow.contentText.toString()).contains("port 3923")
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
        assertThat(shadowOf(notification.contentIntent).savedIntent.component?.className)
            .isEqualTo(MainActivity::class.java.name)
    }

    @Test
    fun `the starting notification says what is happening`() {
        val notification = ServerNotifications.starting(app)

        val visible = "${shadowOf(notification).contentTitle} ${shadowOf(notification).contentText}"
        assertThat(visible).contains(app.getString(R.string.notification_server_starting))
    }

    @Test
    fun `posting a notification also creates its channel so nothing is silently dropped`() {
        // Android drops a notification whose channel does not exist, which is exactly how a boot-time
        // confirmation can vanish without a trace.
        ServerNotifications.post(app, ServerNotifications.running(app, "http://127.0.0.1:3923"))

        assertThat(manager.notificationChannels.map { it.id }).contains(ServerNotifications.CHANNEL_ID)
        assertThat(shadowOf(manager).size()).isEqualTo(1)
    }

    @Test
    fun `a boot started server posts its own confirmation, separate from the running notification`() {
        val notification = ServerNotifications.bootStarted(app)

        val visible = "${shadowOf(notification).contentTitle} ${shadowOf(notification).contentText}".lowercase()
        assertThat(visible).contains("started on boot")
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
        assertThat(notification.contentIntent).isNotNull()

        // "Separate" has to mean a second notification: reusing the running id would replace the one
        // that carries the address, and the boot confirmation would be lost the moment it appeared.
        ServerNotifications.post(app, ServerNotifications.running(app, "http://127.0.0.1:3923"))
        ServerNotifications.post(
            app,
            ServerNotifications.bootStarted(app),
            ServerNotifications.BOOT_NOTIFICATION_ID,
        )

        assertThat(shadowOf(manager).size()).isEqualTo(2)
        val boot = shadowOf(manager).getNotification(ServerNotifications.BOOT_NOTIFICATION_ID)
        assertThat(boot).isNotNull()
        assertThat("${shadowOf(boot).contentTitle} ${shadowOf(boot).contentText}".lowercase())
            .contains("started on boot")
        val running = shadowOf(manager).getNotification(ServerNotifications.RUNNING_NOTIFICATION_ID)
        assertThat(shadowOf(running).contentText.toString()).contains("3923")
    }
}
