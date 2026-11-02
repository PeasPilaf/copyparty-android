# Copyparty for Android

Copyparty is a file server for your phone: pick folders, decide who may see or change them, start
the server, and open them from any browser on the same network. This app turns
[copyparty](https://github.com/9001/copyparty) into a normal Android app, so you get its resumable
uploads, WebDAV and per-folder, per-user permissions without a terminal.

* **One tap to serve.** Add a folder, choose who can open it (or let anyone on your Wi-Fi in), tap
  start, and the app shows the address and a QR code. A notification keeps the server running while
  you use other apps and tells you where to connect.
* **Fine-grained sharing.** For every folder choose exactly what each person may do — view,
  download, upload, rename, delete, administer — or make it public to anyone on the network.
* **Use it from the phone or from a laptop.** The app has a built-in browser for your files, so the
  phone can browse, download and upload to its own server, and a QR code lets another device join by
  scanning instead of typing an address.
* **Honest about what is happening.** If another app is already using the port, or the server cannot
  start, the app says so, keeps a notification you can tap, and shows the server log — it never says
  "running" when nothing is serving your files.
* **Expert mode when you want the real thing.** One switch in Settings reveals the rest of copyparty:
  the full copyparty web UI in the app, the generated `copyparty.conf` to view, copy or export, raw
  global arguments, per-folder advanced flags, and a link to the copyparty control panel. Nothing is
  removed for experts; the everyday screens just stay out of the way.
* **Small on purpose.** Only the useful parts of copyparty are exposed. No FTP, SFTP, SMB or TFTP,
  no TLS certificate management, no zeroconf. Start it, share a folder, done.

## What the app does

| Screen | What it is for |
| --- | --- |
| Home | A first-run checklist, start and stop the server, see its address, copy the link, show the QR code, open the built-in browser, import files into the app's folder, and see why the setup cannot start if it cannot |
| Shares | Every shared folder, its own link, who can open it, and an on/off switch, plus edit and delete |
| Share editor | Pick a folder (the app's folder, or any folder on the phone), then tick what each person — or anyone on the network — may do |
| People | Accounts and passwords (generated for you, and changeable later) that the share rules refer to |
| Settings | Port, network visibility, start on boot, server name, theme, the server log, and Expert mode |
| Browser | Your files in the built-in browser, with back, reload and open-in-Chrome |
| Expert | The full copyparty web UI, the rendered config, raw global arguments and per-folder advanced flags |

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
  reach over USB, and it needs no permissions. **Import files** copies things into it so the server
  always has something to serve.
* **Any other folder** on the phone can be shared too. Android asks you to allow file access once,
  after which the app can serve that folder like any other, in place — the files are not copied.

The **Play Store build** shares the app's folder only, because Google Play restricts broad storage
access. The **self-hosted build** (GitHub Releases / F-Droid) can additionally serve any folder on
the phone directly. Both builds have every screen, Expert mode included; only the storage reach
differs.

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
