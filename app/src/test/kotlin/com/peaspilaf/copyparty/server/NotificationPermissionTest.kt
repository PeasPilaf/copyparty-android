package com.peaspilaf.copyparty.server

import android.Manifest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * J27 — the server notification must be allowed to exist.
 *
 * From Android 13 an app that never asks for POST_NOTIFICATIONS runs its foreground service silently:
 * the user starts a file server and nothing in the status bar says so (and the server occasionally
 * dies without a trace). The request has to happen before the first start, and it has to be a normal
 * permission request the user can answer - not a fake toggle.
 *
 * `rememberNotificationPermissionRequester()` in `server/` is the one hook the start button calls. On
 * Android versions that have no such permission it does nothing at all, because asking would show
 * nothing and change nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationPermissionTest {

    @get:Rule
    val compose = createComposeRule()

    /** Every permission the requester asked for, recorded instead of shown to a user. */
    private val requested = mutableListOf<String>()

    /**
     * A registry that records the launch rather than posting a system dialog. The requester only has to
     * launch `ActivityResultContracts.RequestPermission` for POST_NOTIFICATIONS; that is the contract.
     */
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            if (contract is ActivityResultContracts.RequestPermission) {
                requested += input as String
            }
        }
    }

    private fun invokeRequester() {
        var request: (() -> Unit)? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry = registry
                },
            ) {
                request = rememberNotificationPermissionRequester()
            }
        }
        compose.waitForIdle()
        assertThat(request).isNotNull()
        compose.runOnIdle { request!!.invoke() }
    }

    @Test
    fun `the first start asks for notification permission so the server notification can appear`() {
        invokeRequester()

        assertThat(requested).containsExactly(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    @Config(sdk = [29])
    fun `an android version without the notification permission is never asked for it`() {
        invokeRequester()

        assertThat(requested).isEmpty()
    }
}
