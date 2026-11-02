package com.peaspilaf.copyparty.ui

import com.peaspilaf.copyparty.core.Permission
import com.peaspilaf.copyparty.core.ThemeMode

/**
 * The testTags this UI puts on its controls.
 *
 * `UiTags` in the unit tests is the same vocabulary from the test side; keeping it here as well
 * means a screen cannot quietly rename a control without the matching test failing. Every value is
 * the literal string the journeys use, so the emulator tests and the unit tests agree.
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
    const val HomeStart = "home-start"
    const val HomeStop = "home-stop"
    const val HomeAddress = "home-address"
    const val HomeCopy = "home-copy"
    const val HomeQr = "home-qr"
    const val HomeBrowse = "home-browse"
    const val HomeServerLog = "home-server-log"
    const val ExpertEntry = "expert-entry"
    const val ImportFiles = "import-files"
    const val FirstRunChecklist = "first-run-checklist"
    const val FirstRunChooseFolder = "first-run-choose-folder"
    const val HomeRestartToApply = "home-restart-to-apply"
    const val ServerStatus = "server-status"
    const val ValidationBanner = "validation-banner"

    // Storage access
    const val StorageGrant = "storage-grant"

    // Folder picker
    const val FolderPicker = "folder-picker"
    const val PickerRootApp = "picker-root-app"
    const val PickerRootPhone = "picker-root-phone"
    const val PickerBreadcrumb = "picker-breadcrumb"
    const val PickerUp = "picker-up"
    const val PickerSelect = "picker-select"
    const val PickerUnreadable = "picker-unreadable"
    const val PickerEmpty = "picker-empty"

    fun pickerFolder(name: String): String = "picker-folder-$name"

    // Shares
    const val AddShare = "add-share"
    const val EmptyAddShare = "empty-add-share"
    const val ShareRow = "share-row"
    const val ShareName = "share-name"
    const val ShareUrl = "share-url"
    const val ShareAccess = "share-access"
    const val ShareEnabled = "share-enabled"
    const val ShareEdit = "share-edit"
    const val ShareCopy = "share-copy"
    const val ShareSend = "share-send"
    const val ShareDelete = "share-delete"
    const val ShareDeleteDialog = "share-delete-dialog"
    const val ShareDeleteConfirm = "share-delete-confirm"
    const val ShareDeleteCancel = "share-delete-cancel"

    // Share editor
    const val ShareEditor = "share-editor"
    const val ShareEditorBack = "share-editor-back"
    const val ShareEditorSave = "share-editor-save"
    const val ShareLinkField = "share-link-field"
    const val EditorFolder = "editor-folder"
    const val EditorChangeFolder = "editor-change-folder"
    const val EditorNoAccess = "editor-no-access"
    const val EditorDelete = "editor-delete"
    const val EditorDeleteDialog = "editor-delete-dialog"
    const val EditorDeleteConfirm = "editor-delete-confirm"
    const val EditorDeleteCancel = "editor-delete-cancel"
    const val PresetAnyoneView = "preset-anyone-view"
    const val PresetPeople = "preset-people"
    const val PresetDropbox = "preset-dropbox"
    const val AnyoneOnNetwork = "anyone-on-network"
    const val ShareIssues = "share-issues"

    fun permission(principal: String, permission: Permission): String =
        "permission-$principal-${permission.name}"

    // People
    const val AddPerson = "add-person"
    const val EmptyAddPerson = "empty-add-person"
    const val PersonDialog = "person-dialog"
    const val PersonNameField = "person-name-field"
    const val PersonPassword = "person-password"
    const val PersonGenerate = "person-generate"
    const val PersonPasswordReveal = "person-password-reveal"
    const val PersonSave = "person-save"
    const val PersonRow = "person-row"
    const val PersonEdit = "person-edit"
    const val DeletePersonDialog = "delete-person-dialog"
    const val DeletePersonConfirm = "delete-person-confirm"
    const val DeletePersonCancel = "delete-person-cancel"

    fun deletePerson(name: String): String = "delete-person-$name"

    // Connect: in-app browser and QR code
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
    const val ExpertToggle = "expert-toggle"
    const val SettingsExpert = "settings-expert"

    fun themeOption(mode: ThemeMode): String = "theme-${mode.name}"
}
