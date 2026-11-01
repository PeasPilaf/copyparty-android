# Copyparty for Android

[copyparty](https://github.com/9001/copyparty) is a file server you can run anywhere Python runs:
resumable uploads and downloads in any browser, WebDAV, deduplication, thumbnails, and per-folder,
per-user permissions. It is fantastic on a laptop or NAS. On a phone it is a Python program with a
terminal UI, which is not how you want to use a phone.

This repo packages copyparty as an ordinary Android app:

* **One tap to serve.** A foreground service runs a real copyparty (the same Python package, embedded
  through Chaquopy) on your phone.
* **Fine-grained folder sharing.** Pick any folder, give it a link, then choose exactly who may see,
  download, upload, move, delete or administer it. Anonymous access is a checkbox, not a config file.
* **Desktop power, phone UI.** Browsing, uploading and downloading happens in the real copyparty web
  UI inside the app. Everything else (accounts, permissions, server settings) is native Compose.
* **Deliberately small.** Only the parts of copyparty that make sense on a phone are exposed. No FTP,
  no SFTP, no SMB, no TFTP, no TLS certificate management, no zeroconf.

```
┌──────────────────────── Android app ────────────────────────┐
│ Compose UI ── AppViewModel ── ConfigStore (JSON on disk)    │
│      │                              │                        │
│      │                     ConfigRenderer (Kotlin)           │
│      ▼                              ▼                        │
│ ServerRuntime ──▶ foreground service ──▶ copyparty.conf (rendered)
│                              │                               │
│                     PythonBridge (Chaquopy)                  │
│                              ▼                               │
│                 embedded CPython + copyparty  :3923          │
└──────────────────────────────────────────────────────────────┘
        ▲ LAN clients (phone, laptop, TV) browse over HTTP/WebDAV
```

## Sharing model

A **share** is a folder on the device plus the permissions other people get on it. It maps 1:1 to a
copyparty volume and its `accs:` ACL, so the app never invents semantics copyparty does not have.

| App permission | copyparty flag | What it allows |
| --- | --- | --- |
| View | `r` | List the folder and download from it |
| Download only | `g` | Download known paths without listing the folder |
| Upload | `w` | Put new files in the folder |
| Move / rename | `m` | Move and rename entries |
| Delete | `d` | Permanently delete entries |
| Admin | `a` | See uploader IPs, reload config |

Grants are per person (`alice`) or for everyone (`*`). The app hides a share from people with no
grant: a private volume does not even appear in the volume list, which is exactly how copyparty
behaves and what the contract tests in CI assert.

Example of what the UI writes for one public and one private folder:

```ini
[global]
  p: 3923
  i: 0.0.0.0
  http-only
  no-crt
  no-robots
  q
  no-voldump
  name: my phone

[accounts]
  alice: hunter2

[/private]
  /storage/emulated/0/Documents/private
  accs:
    rwd: alice
  flags:
    e2d

[/public]
  /storage/emulated/0/Pictures
  accs:
    r: *
```

## Folder access

Sharing an arbitrary folder on internal storage needs Android's **All files access**
(`MANAGE_EXTERNAL_STORAGE`). The app asks for it only when you pick such a folder, and works without
it using its private `share` folder (usable through the app, and over USB/MTP on most devices). This
is a sideloading-first app; Play Store distribution would need the All-files-access exemption or a
SAF-based storage bridge.

## Install

Builds are produced by GitHub Actions; nothing is built on a developer laptop (see below). Download
the latest `copyparty-debug-apk` artifact from the [Actions tab](../../actions), or a tagged release
APK from [Releases](../../releases). Both are signed (debug-signed unless release secrets are set).

Requirements: Android 8.0+ (API 26), `arm64-v8a` phone or `x86_64` emulator.

## Build and test — on GitHub only

This project intentionally does not support local Android builds. `./gradlew` on a laptop is not part
of the workflow; every build and test runs in CI:

| Workflow job | What it proves |
| --- | --- |
| `Android build + unit tests` | Domain/config-rendering tests, Robolectric Compose UI tests, lint, debug APK |
| `Config contract (real copyparty)` | The exact config the app renders is fed to **real** copyparty, which is then checked over HTTP: public listing/download, private denied without password, password unlock, anonymous upload denied, account upload accepted, WebDAV, 404 |
| `Android device test (emulator)` | Embedded CPython + copyparty boots inside the Android app process and serves a file over HTTP |

The contract job is the interesting one: it does not read the renderer code, it pipes state through
the renderer binary and then verifies behaviour against upstream copyparty, so a wrong flag or a bad
config line fails CI before it reaches a phone.

## Roadmap

* Share links with expiry/password (copyparty `--shr`) surfaced as first-class app objects
* Storage Access Framework bridge so Play Store builds can share non-app folders
* Optional HTTPS with a self-signed certificate and QR-code provisioning
* Home-screen widgets / quick settings tile for start-stop
* Translations

## Credits

* [copyparty](https://github.com/9001/copyparty) by ed (MIT) — the actual file server
* [Chaquopy](https://chaquo.com/chaquopy/) (MIT) — CPython for Android
* [Jinja2](https://palletsprojects.com/projects/jinja/) (BSD-3-Clause) — copyparty template engine
* [ZXing](https://github.com/zxing/zxing) (Apache-2.0) — QR codes

This project is not affiliated with the copyparty author. See [NOTICE](NOTICE).
