package com.peaspilaf.copyparty.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The real copyparty web UI, in the app. Browsing, uploading and downloading files is copyparty's
 * job; this wrapper gives it a back button, a reload and an "open in Chrome" escape hatch.
 */
@Composable
internal fun BrowserScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var reloadKey by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        BrowserChrome(
            url = url,
            progress = progress,
            onBack = onBack,
            onReload = { reloadKey += 1 },
            onOpenExternal = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
        )
        key(reloadKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag(Tags.BrowserWebView),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                progress = 5
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                progress = 100
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}

/** The browser toolbar, split out so it can be tested without instantiating a WebView. */
@Composable
internal fun BrowserChrome(
    url: String,
    progress: Int,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(Tags.BrowserChrome)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(Tags.BrowserBack)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag(Tags.BrowserUrl),
            )
            IconButton(onClick = onReload, modifier = Modifier.testTag(Tags.BrowserReload)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
            IconButton(onClick = onOpenExternal, modifier = Modifier.testTag(Tags.BrowserExternal)) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser")
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
