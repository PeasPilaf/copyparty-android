package com.peaspilaf.copyparty.server

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The one hook a start button calls before starting the server.
 *
 * From Android 13 a foreground service is silent unless `POST_NOTIFICATIONS` was requested, so the
 * device needs a real permission request before the first start; otherwise the server runs without
 * the notification that says where to connect (and on some devices is killed without a trace).
 *
 * Contract for the caller: invoke the returned function when the user asks to start the server. It
 * launches `ActivityResultContracts.RequestPermission` for `POST_NOTIFICATIONS` on Android 13+, and
 * does nothing at all on older versions or when the permission is already granted, so it is always
 * safe to call - the start itself must not wait for the answer (the notification can appear on the
 * next start if the user says no).
 */
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
