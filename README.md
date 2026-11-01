# Copyparty for Android

Copyparty is a file server for your phone: pick folders, decide who may see or change them, start
the server, and open them from any browser on the same network. This app turns
[copyparty](https://github.com/9001/copyparty) into a normal Android app, so you get its resumable
uploads, WebDAV and per-folder, per-user permissions without a terminal.

* **One tap to serve.** Start the server from the home screen; a notification keeps it running while
  you use other apps and tells you where to connect.
* **Fine-grained sharing.** For every folder choose exactly what each person may do — view,
  download, upload, rename, delete, administer — or make it public to anyone on the network.
* **Use it from the phone or from a laptop.** The app has a built-in browser for your files, and a
  QR code so another device can join by scanning instead of typing an address.
* **Small on purpose.** Only the useful parts of copyparty are exposed. No FTP, SFTP, SMB or TFTP,
  no TLS certificate management, no zeroconf. Start it, share a folder, done.

## What the app does

| Screen | What it is for |
| --- | --- |
| Home | Start and stop the server, see its address, open the built-in browser, show the QR code, copy the link, and see why the setup cannot start if it cannot |
| Shares | Every shared folder, its link, who can open it, and an on/off switch |
| Share editor | Pick a folder, then tick what each person (or anyone on the network) may do |
| People | Accounts and passwords (generated for you) that the share rules refer to |
| Settings | Port, network visibility, start on boot, server name, theme, and the server log |
| Browser | Your files in the built-in browser, with back, reload and open-in-Chrome |

## Sharing model

A **share** is a folder plus the rules for who may use it.

| Permission | What it allows |
| --- | --- |
| View | See the folder and download from it |
| Download only | Download known files without listing the folder |
| Upload | Add new files |
| Move / rename | Rename or move files and folders |
| Delete | Permanently delete |
| Admin | See uploader IPs and reload the server configuration |

Rules are per person (`alice`) or for everyone (`*`). Someone with no rule cannot see the folder at
all: a share nobody was granted access to does not appear to anyone.

## Your files

* **The app's folder** works out of the box. It is a normal folder on the phone that you can also
  reach over USB, and it needs no permissions.
* **Any other folder** on the phone can be shared too. Android asks you to allow file access once,
  after which the app can serve that folder like any other.

The **Play Store build** shares the app's folder only, because Google Play restricts broad storage
access. The **self-hosted build** (GitHub Releases / F-Droid) can additionally serve any folder on
the phone directly.

## Install

Download the APK from [Releases](../../releases) and install it (allow installing unknown apps when
Android asks). Requirements: Android 8.0 or newer (API 26); `arm64-v8a` phone (or `x86_64` emulator).

Builds are produced by CI — see [CONTRIBUTING.md](CONTRIBUTING.md). Every release attaches the
self-hosted APK (any folder), the Play-build APK (app folder only), and the Play AAB.

## Roadmap

* Share links with an expiry date and password
* Serve folders picked through the system file picker without copying them in
* Optional HTTPS with a self-signed certificate
* Quick-settings tile and home-screen widget for start/stop
* Translations

## Credits

* [copyparty](https://github.com/9001/copyparty) by ed (MIT) — the file server
* [Chaquopy](https://chaquo.com/chaquopy/) (MIT) — CPython for Android
* [Jinja2](https://palletsprojects.com/projects/jinja/) (BSD-3-Clause) — copyparty templates
* [ZXing](https://github.com/zxing/zxing) (Apache-2.0) — QR codes

Not affiliated with the copyparty author. See [NOTICE](NOTICE).
