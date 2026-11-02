package com.peaspilaf.copyparty.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 *
 * A WebView does nothing useful with a download or an upload unless it is told what to do with
 * them, so both are wired: a download goes to Android's download manager (the notification shows it
 * arriving), and an upload opens the system document picker and hands the choice back to the page.
 * Full-screen route: it keeps its content out from under the status and navigation bars.
 */
@Composable
internal fun BrowserScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var reloadKey by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0) }
    var pendingUpload by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { documents ->
        val callback = pendingUpload
        pendingUpload = null
        // A WebView file input accepts `null` for "the user cancelled", never an empty array.
        callback?.onReceiveValue(if (documents.isEmpty()) null else documents.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
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

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?,
                            ): Boolean {
                                pendingUpload?.onReceiveValue(null)
                                pendingUpload = filePathCallback
                                uploadPicker.launch(arrayOf("*/*"))
                                return true
                            }
                        }
                        setDownloadListener { downloadUrl, _, contentDisposition, mimeType, _ ->
                            enqueueDownload(ctx, downloadUrl, contentDisposition, mimeType)
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}

/** Hands a file link from the page to Android's download manager, where it really arrives. */
private fun enqueueDownload(
    context: Context,
    url: String,
    contentDisposition: String?,
    mimeType: String?,
) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        manager?.enqueue(request)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
