package com.peaspilaf.copyparty.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.peaspilaf.copyparty.MainActivity
import com.peaspilaf.copyparty.R

/**
 * The one place the server's notifications are built, so the foreground service and the boot
 * receiver cannot drift apart.
 *
 * The promises these builders keep:
 *
 *  * running: the notification carries the address a client should type, an `Open` action back to
 *    the app and a `Stop` action that stops the server without finding the app again;
 *  * failed: the notification is *kept* (dismissible, tapping it opens the app) instead of being
 *    removed with the foreground state, because a silent failure is what made a dead server look
 *    like a bug in the app;
 *  * boot: a boot-started server gets its own confirmation notification, separate from the running
 *    one, so the phone says what happened after a reboot.
 */
object ServerNotifications {
    const val CHANNEL_ID = "server"

    /** The foreground notification: starting, running and failed all replace each other here. */
    const val RUNNING_NOTIFICATION_ID = 1001

    /** The boot confirmation is deliberately a different notification, not a replacement. */
    const val BOOT_NOTIFICATION_ID = 1002

    const val OPEN_ACTION = "Open"

    private const val OPEN_REQUEST_CODE = 0
    private const val STOP_REQUEST_CODE = 1

    /** "Starting…": the service calls this before the interpreter has answered. */
    fun starting(context: Context): Notification =
        base(context, context.getString(R.string.notification_server_starting), null)
            .setOngoing(true)
            .addAction(0, stopLabel(context), stopServer(context))
            .build()

    /** "Running at <address>": the app is serving files and the notification says where. */
    fun running(context: Context, address: String): Notification =
        base(context, context.getString(R.string.notification_server_running), address)
            .setOngoing(true)
            .addAction(0, OPEN_ACTION, openApp(context))
            .addAction(0, stopLabel(context), stopServer(context))
            .build()

    /**
     * A start failed. This one is posted *after* the foreground state is gone: it is dismissible,
     * taps through to the app, keeps the reason, and says the tap will open the app.
     */
    fun failed(context: Context, message: String): Notification =
        base(context, context.getString(R.string.notification_server_failed), "$message\nTap to open")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    /** A server that came back after a reboot says so, and stays separate from the running one. */
    fun bootStarted(context: Context): Notification =
        base(context, "Copyparty started on boot", "Tap to open")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    /**
     * Posts [notification] as [id], creating the channel first: Android drops a notification whose
     * channel does not exist, which is how a boot-time confirmation can vanish without a trace.
     *
     * The id is explicit rather than derived from the notification, because the boot confirmation is
     * a *second* notification: reusing [RUNNING_NOTIFICATION_ID] would replace the one that says
     * where the server is.
     */
    fun post(context: Context, notification: Notification, id: Int = RUNNING_NOTIFICATION_ID) {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // A denied POST_NOTIFICATIONS permission is a silent drop on Android 13+, not an error.
        runCatching { manager.notify(id, notification) }
    }

    /** Creates the single channel this app uses, with the same quiet settings every time. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_server),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_server_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun base(context: Context, title: String, text: String?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(title)
            .setContentText(text.orEmpty())
            .setContentIntent(openApp(context))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun stopLabel(context: Context): String = context.getString(R.string.notification_action_stop)

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopServer(context: Context): PendingIntent = PendingIntent.getService(
        context,
        STOP_REQUEST_CODE,
        Intent(context, ServerService::class.java).setAction(ServerService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
