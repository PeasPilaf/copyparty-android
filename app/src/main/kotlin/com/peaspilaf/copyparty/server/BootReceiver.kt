package com.peaspilaf.copyparty.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peaspilaf.copyparty.CopypartyApp
import com.peaspilaf.copyparty.core.Severity
import com.peaspilaf.copyparty.core.Validator

/** Restarts the server after a reboot when the user enabled "start on boot". */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CopypartyApp ?: return
        val state = app.container.configStore.load()
        if (!state.settings.startOnBoot) return
        if (Validator.validate(state).any { it.severity == Severity.ERROR }) return
        app.container.serverRuntime.start()
    }
}
