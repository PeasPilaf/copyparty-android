package com.peaspilaf.copyparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * "Changes are saved. Restart to apply."
 *
 * One banner with one action, on every screen a change can be made from. It says what is true - the
 * edit is on disk, the running server still has the old config - and the action really restarts the
 * server through `ServerRuntime.applyConfig()` instead of just hiding the message.
 */
@Composable
internal fun RestartToApplyBanner(
    onRestartToApply: () -> Unit,
    modifier: Modifier = Modifier,
    failure: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Changes are saved. Restart to apply.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRestartToApply,
                modifier = Modifier.testTag(Tags.HomeRestartToApply),
            ) {
                Text("Restart server")
            }
            if (failure != null) {
                Text(
                    text = failure,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("restart-failure"),
                )
            }
        }
    }
}
