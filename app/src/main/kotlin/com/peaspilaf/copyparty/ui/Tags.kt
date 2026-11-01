package com.peaspilaf.copyparty.ui

import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ThemeMode

/**
 * The testTags this UI puts on its controls.
 *
 * `UiTags` in the unit tests is the same vocabulary from the test side; keeping it here as well
 * means a screen cannot quietly rename a control without the matching test failing.
 */
internal object Tags {
    // Shell
    const val NavHome = "nav-home"
    const val NavShares = "nav-shares"
    const val NavPeople = "nav-people"
    const val NavSettings = "nav-settings"

    const val ScreenHome = "screen-home"
    const val ScreenShares = "screen-shares"
    const val ScreenPeople = "screen-people"
    const val ScreenSettings = "screen-settings"

    // Home
    const val StartServer = "start-server"
    const val StopServer = "stop-server"
    const val ServerStatus = "server-status"
    const val ServerUrl = "server-url"
    const val ValidationBanner = "validation-banner"

    // Shares
    const val AddShare = "add-share"
    const val ShareRow = "share-row"
    const val ShareName = "share-name"
    const val SharePath = "share-path"
    const val ShareAccess = "share-access"
    const val ShareEnabled = "share-enabled"
    const val ShareEdit = "share-edit"
    const val FolderPicker = "folder-picker"
    const val FolderSelect = "folder-select"

    // Share editor
    const val ShareEditor = "share-editor"
    const val ShareEditorBack = "share-editor-back"
    const val ShareEditorSave = "share-editor-save"
    const val ShareLinkField = "share-link-field"
    const val AnyoneOnNetwork = "anyone-on-network"
    const val ShareIssues = "share-issues"

    // People
    const val AddPerson = "add-person"
    const val PersonDialog = "person-dialog"
    const val PersonNameField = "person-name-field"
    const val PersonPasswordField = "person-password-field"
    const val PersonSave = "person-save"

    // Connect: in-app browser and QR code
    const val OpenBrowser = "open-browser"
    const val ShowQr = "show-qr"
    const val CopyUrl = "copy-url"
    const val ScreenQr = "screen-qr"
    const val QrBack = "qr-back"
    const val QrImage = "qr-image"
    const val QrUrl = "qr-url"
    const val QrCopy = "qr-copy"
    const val BrowserChrome = "browser-chrome"
    const val BrowserWebView = "browser-webview"
    const val BrowserBack = "browser-back"
    const val BrowserUrl = "browser-url"
    const val BrowserReload = "browser-reload"
    const val BrowserExternal = "browser-external"

    // Settings
    const val SettingsPort = "settings-port"
    const val SettingsLanAccess = "settings-lan-access"
    const val SettingsStartOnBoot = "settings-start-on-boot"
    const val SettingsServerName = "settings-server-name"
    const val SettingsSave = "settings-save"
    const val SettingsErrors = "settings-errors"

    fun permission(principal: String, permission: Permission): String =
        "permission-$principal-${permission.name}"

    fun personRow(name: String): String = "person-row-$name"

    fun deletePerson(name: String): String = "delete-person-$name"

    fun themeOption(mode: ThemeMode): String = "theme-${mode.name}"
}
