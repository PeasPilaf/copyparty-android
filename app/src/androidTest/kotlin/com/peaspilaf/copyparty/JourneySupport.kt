package com.peaspilaf.copyparty

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.net.HttpURLConnection
import java.net.URL

/**
 * The vocabulary and the small verbs the emulator journeys are written in.
 *
 * Every tag here is a literal `Modifier.testTag` string from the shared vocabulary the three regions
 * agreed on, and each one sits on the node that carries the value (the `Text` for text), so the
 * `textOf` helper reads what a user reads. The journeys look nodes up in the unmerged tree, so a tag
 * inside a merged row still resolves.
 *
 * The HTTP helpers exist because two of the promises are about the *server*, not the pixels: the
 * address must really serve the folder, and a download or upload that the system file picker hides
 * from a test must still be proven end to end.
 */
internal object JourneyTags {
    // Home
    const val HOME_START = "home-start"
    const val HOME_STOP = "home-stop"
    const val HOME_ADDRESS = "home-address"
    const val HOME_BROWSE = "home-browse"
    const val HOME_RESTART_TO_APPLY = "home-restart-to-apply"
    const val HOME_SERVER_LOG = "home-server-log"
    const val FIRST_RUN_CHECKLIST = "first-run-checklist"

    // Shell (unchanged from the current UI)
    const val NAV_HOME = "nav-home"
    const val NAV_SHARES = "nav-shares"
    const val ADD_SHARE = "add-share"

    // Folder picker
    const val PICKER_ROOT_APP = "picker-root-app"
    const val PICKER_ROOT_PHONE = "picker-root-phone"
    const val PICKER_BREADCRUMB = "picker-breadcrumb"
    const val PICKER_SELECT = "picker-select"
    const val PICKER_UNREADABLE = "picker-unreadable"

    // Shares list and editor
    const val SHARE_ROW = "share-row"
    const val SHARE_URL = "share-url"
    const val SHARE_EDIT = "share-edit"
    const val SHARE_DELETE = "share-delete"
    const val SHARE_DELETE_CONFIRM = "share-delete-confirm"
    const val EDITOR_DELETE = "editor-delete"
    const val EDITOR_DELETE_CONFIRM = "editor-delete-confirm"
    const val SHARE_EDITOR_SAVE = "share-editor-save"

    // In-app browser (BrowseScreen)
    const val BROWSE_LIST = "browse-list"
    const val BROWSE_ROW = "browse-row"
    const val BROWSE_DOWNLOAD = "browse-download"
    const val BROWSE_BACK = "browse-back"
}

/** Waits until a tagged node exists, then returns it (unmerged tree, so tags inside rows resolve). */
internal fun ComposeTestRule.firstTag(tag: String, timeoutMs: Long = 120_000): SemanticsNodeInteraction {
    awaitTag(tag, timeoutMs)
    return onAllNodesWithTag(tag, useUnmergedTree = true).onFirst()
}

internal fun ComposeTestRule.awaitTag(tag: String, timeoutMs: Long = 120_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeTestRule.awaitNoTag(tag: String, timeoutMs: Long = 60_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }
}

/** Taps a tagged control, scrolling its screen first when it sits below the fold. */
internal fun ComposeTestRule.tap(tag: String, timeoutMs: Long = 60_000) {
    val node = firstTag(tag, timeoutMs)
    if (!node.isDisplayed()) {
        node.performScrollTo()
    }
    node.performClick()
}

/** The text a tagged node carries, whether it is the node's own text or a merged row's first line. */
internal fun ComposeTestRule.textOf(tag: String, timeoutMs: Long = 120_000): String =
    firstTag(tag, timeoutMs).fetchSemanticsNode().readableText()

/** The text a tagged node carries, or null when the node is not on screen (safe inside waitUntil). */
internal fun ComposeTestRule.textOrNull(tag: String): String? {
    val node = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        ?: return null
    return node.readableText()
}

/** A node's own text, or its content description; empty when it carries neither. */
private fun SemanticsNode.readableText(): String {
    val text = runCatching {
        config[SemanticsProperties.Text].joinToString(" ") { it.text }
    }.getOrDefault("")
    if (text.isNotEmpty()) return text
    return runCatching {
        config[SemanticsProperties.ContentDescription].joinToString(" ")
    }.getOrDefault("")
}

/** Waits until some visible control shows [text], then taps it (used for folder rows by name). */
internal fun ComposeTestRule.tapTextWhenShown(text: String, timeoutMs: Long = 60_000) {
    waitUntil(timeoutMs) {
        onAllNodes(hasText(text, substring = false), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    val node = onAllNodes(hasText(text, substring = false), useUnmergedTree = true).onFirst()
    if (!node.isDisplayed()) {
        node.performScrollTo()
    }
    node.performClick()
}

private val ADDRESS_IN_TEXT = Regex("https?://[^\\s,;]+")

/** The first address inside a piece of screen text, e.g. the one Home shows for a running server. */
internal fun addressIn(text: String): String =
    ADDRESS_IN_TEXT.find(text)?.value?.trimEnd(',', '.')
        ?: throw AssertionError("no server address on screen; the text was '$text'")

/**
 * A per-share link as an absolute URL: the row may print a path (`/photos`) and the running address
 * lives on Home, so the journey joins the two the way a user would.
 */
internal fun absoluteShareUrl(homeAddress: String, shareText: String): String {
    val text = shareText.trim()
    if (text.startsWith("http://") || text.startsWith("https://")) return text.trimEnd('/')
    val origin = Regex("https?://[^/\\s]+").find(homeAddress)?.value
        ?: throw AssertionError("no origin in the home address '$homeAddress'")
    return origin.trimEnd('/') + "/" + text.trimStart('/').ifEmpty { "" }
}

/** One plain HTTP GET; throws with the URL and the status so a journey failure is readable. */
internal fun httpGet(url: String, timeoutMs: Int = 20_000): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        instanceFollowRedirects = true
    }
    return try {
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            throw AssertionError("GET $url returned $status")
        }
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/** Polls until the folder listing at [url] shows [needle], so a slow copyparty boot is not a failure. */
internal fun awaitHttpContains(url: String, needle: String, timeoutMs: Long = 120_000): String {
    val deadline = System.currentTimeMillis() + timeoutMs
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        try {
            val body = String(httpGet(url), Charsets.UTF_8)
            if (body.contains(needle)) return body
            last = AssertionError("$url did not mention '$needle' yet")
        } catch (t: Throwable) {
            last = t
        }
        Thread.sleep(500)
    }
    throw AssertionError("$url never served a listing that mentions '$needle'", last)
}

/** Polls until [url] answers 200, then returns the bytes - the "the address really serves it" proof. */
internal fun awaitHttpBytes(url: String, timeoutMs: Long = 120_000): ByteArray {
    val deadline = System.currentTimeMillis() + timeoutMs
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        try {
            return httpGet(url)
        } catch (t: Throwable) {
            last = t
        }
        Thread.sleep(500)
    }
    throw AssertionError("$url never served its bytes", last)
}
