package com.peaspilaf.copyparty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.peaspilaf.copyparty.ui.AppRoot
import com.peaspilaf.copyparty.ui.AppViewModel
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CopypartyApp).container
        val viewModel = AppViewModel(container.configStore, container.serverRuntime, container.storage)
        setContent {
            val state by viewModel.state.collectAsState()
            CopypartyTheme(themeMode = state.settings.theme) {
                AppRoot(viewModel)
            }
        }
    }
}
