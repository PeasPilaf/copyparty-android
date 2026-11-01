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
  UI inside the app. Accounts, permissions, server settings, the in-app browser and the QR code that
  gets another device onto the server are native Compose.
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

## What the app does

| Screen | What it is for |
| --- | --- |
| Home | Start/stop the server, see its address, open the in-app browser, show the QR code, copy the link, and see why the setup cannot start if it cannot |
| Shares | Every shared folder with its link, who can open it, and an on/off switch |
| Share editor | Pick a folder, then tick exactly what each person (or anyone on the network) may do: view, download, upload, move, delete, administer |
| People | Accounts and passwords (generated for you) that the share ACLs refer to |
| Settings | Port, LAN-only vs all interfaces, start on boot, server name, theme, and the last lines of the server log for diagnostics |
| Browser | The real copyparty web UI in a WebView, with back/reload/open-in-Chrome |

## Project status

Everything described here exists, and the promises are enforced by CI: unit and Robolectric tests,
a contract job that feeds the app's rendered config to real copyparty, and an emulator job that boots
the embedded CPython + copyparty inside the Android app process. The README is itself checked by
`scripts/check_readme_contract.py` so it cannot claim a job, task or permission that does not exist.

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

Grants are per person (`alice`) or for everyone (`*`). A grant with no permissions is ignored, and a
share whose grants match nobody is rendered as a volume with no `accs:` lines at all. copyparty then
neither lists it nor serves it, which is what `scripts/check_server_contract.py` asserts by name: a
private volume is absent from the anonymous volume list, and a volume with no grants is absent from
the volume list even for a signed-in account.

Example of what the UI writes for one public and one private folder (abridged: the renderer also
writes a generated header and, when the service starts it, a log path):

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
APK (`copyparty-release-apk`) from [Releases](../../releases).

Every CI run also publishes `copyparty-release-smoke-apk`: the release variant is built with the
debug key, because CI never passes signing secrets. It exists so the release path is exercised on
every commit, not only on tags. A tagged release APK is debug-signed too unless the release secrets
are set.

Requirements: Android 8.0+ (API 26), `arm64-v8a` phone or `x86_64` emulator.

## Build and test — on GitHub only

This project intentionally does not support local Android builds. CI is the only compiler: `./gradlew`
on a laptop is not part of the workflow, and every build, test and contract check runs in GitHub
Actions. The checks that do not need an Android build can still be run by hand:
`python3 scripts/check_ci_contract.py` and `python3 scripts/check_readme_contract.py` need only the
checkout, and `python3 scripts/check_boot_contract.py` additionally needs Python 3.12 with
`copyparty==1.20.26` installed. `scripts/check_server_contract.py` is driven by the renderer binary,
which CI builds with `:core:installDist`.

### Pinned toolchain

| Component | Version | Pinned in |
| --- | --- | --- |
| JDK | Java 17 (Temurin) | `.github/workflows/ci.yml`, `release.yml`, `app/build.gradle.kts`, `core/build.gradle.kts` |
| Build Python | 3.12 | `ci.yml`, `release.yml`, Chaquopy in `app/build.gradle.kts` |
| Embedded copyparty | 1.20.26 | `COPYPARTY_VERSION` in `ci.yml`, `app/build.gradle.kts` |
| Gradle | 8.14.3 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.2.0 | `gradle/libs.versions.toml` |
| Compose BOM | 2025.07.00 | `gradle/libs.versions.toml` |
| compileSdk / targetSdk | 36 | `app/build.gradle.kts` |
| minSdk | 26 (Android 8.0) | `app/build.gradle.kts` |
| Emulator | API 34, x86_64, google_apis | `ci.yml` |
| Robolectric | API 34 | `app/src/test/resources/robolectric.properties` |

### CI jobs

`.github/workflows/ci.yml` runs three jobs; their display names and artifact names are stable because
the README and `scripts/check_ci_contract.py` refer to them.

| Job | Timeout | What it runs | Artifacts |
| --- | --- | --- | --- |
| `Android build + unit tests` | 60 min | `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug` | `test-reports`, `copyparty-debug-apk`, `copyparty-release-smoke-apk` |
| `Config contract (real copyparty)` | 20 min | `scripts/check_ci_contract.py`, `scripts/check_readme_contract.py`, `scripts/check_boot_contract.py`, `:core:installDist`, the renderer CLI, `scripts/check_server_contract.py` | `config-contract` |
| `Android device test (emulator)` | 60 min | `:app:connectedDebugAndroidTest` on an API 34 emulator | `instrumented-test-reports` |

The Android job builds the release variant on every run (`:app:assembleRelease`), so release-only
packaging or Chaquopy breakage cannot sit unnoticed until the next tag. With no keystore secrets it
uses the debug signing config and still produces an installable APK.

### Release workflow

`.github/workflows/release.yml` runs on tags matching `v*` (and manually). It builds
`:app:assembleRelease` and always uploads the APK as `copyparty-release-apk`; on a tag it also
publishes that APK to a GitHub release with generated notes. A real keystore is optional: the four
`ANDROID_KEYSTORE_*` / `ANDROID_KEY_*` secrets switch the build from debug signing to release
signing.

### Dependency updates

`.github/dependabot.yml` asks for weekly, grouped updates: one pull request per ecosystem (`gradle`
and `github-actions`), so version bumps are reviewed as a single change instead of a wall of pull
requests.

### Contract scripts

These scripts are the tests for everything that is not Kotlin unit tests. They are plain Python and
run in CI exactly as shown; each check prints `PASS`/`FAIL` plus the promise it stands for.

| Script | Promise |
| --- | --- |
| `scripts/check_boot_contract.py` | `boot.py` starts real copyparty on plain CPython, serves a file, stops through the SvcHub signal path, and starts again on the same port |
| `scripts/make_contract_state.py` | writes a throwaway tree and `state.json` with a public, a private and a no-grant volume |
| `scripts/check_server_contract.py` | boots real copyparty with the app-rendered config and checks every HTTP/WebDAV ACL promise below |
| `scripts/check_ci_contract.py` | every CI job keeps its stable name, timeout, cache and artifacts; the release smoke build stays in CI; dependabot stays grouped and weekly |
| `scripts/check_readme_contract.py` | this README matches the workflows: job names, timeouts, artifact names, gradle tasks, versions, emulator API level, and the permission walkthrough |

The config job pipes `state.json` through the app's own renderer binary
(`core/build/install/core/bin/core`) and hands the result to **real** copyparty, so a wrong flag or a
bad config line fails CI before it reaches a phone.

### What the contract asserts

Over HTTP and WebDAV, against the config the app rendered:

* the volume list is served at `/`, and the public folder lists and downloads its file;
* a private folder is denied without a password and opens with `?pw=…`;
* a private volume is absent from the anonymous volume list, and present for its account;
* a volume with no grants is absent from the volume list and is not served, even for a signed-in
  account;
* anonymous upload is denied and an account upload is accepted and reads back;
* WebDAV `PROPFIND` returns `207`, and a missing file returns `404`.

## Watching CI

CI is the only build. These `gh` commands are how to see what happened and get the evidence out:

```bash
# the last ten runs, newest first
gh run list --workflow CI --limit 10

# follow the newest run (Ctrl-C stops watching, not the run)
gh run watch "$(gh run list --workflow CI --limit 1 --json databaseId -q '.[0].databaseId')"

# only the steps that failed
gh run view <run-id> --log-failed

# list the jobs and their ids, then read one job's full log
gh run view <run-id> --json jobs -q '.jobs[] | [.databaseId, .name] | @tsv'
gh run view <run-id> --job <job-id> --log

# download an artifact (e.g. the debug APK) into the current directory
gh run download <run-id> -n copyparty-debug-apk

# start a run by hand on main
gh workflow run CI --ref main

# tagged releases
gh release list --limit 5
gh release view <tag>
```

How to read a failure:

* `Android build + unit tests` — a compile error or a unit/Robolectric test. Download `test-reports`
  and open `index.html`; the failing test name is the specification that broke.
* `Config contract (real copyparty)` — read the `FAIL  <sentence>` line in the log. The
  `config-contract` artifact contains `state.json`, the rendered `copyparty.conf` and `server.log`.
  A failure here means the app wrote a config real copyparty does not accept or does not enforce.
* `Android device test (emulator)` — check `instrumented-test-reports`; a boot timeout is infra, an
  assertion failure is the embedded interpreter.
* `Docs and workflow contract` — this README, `ci.yml` or `release.yml` drifted. Both scripts run on
  a laptop with `python3`; fix the file, not the check.
* The release smoke step — the release variant, signing fallback or Chaquopy packaging broke.

## Adding a new share permission end to end

The permission model is copyparty's ACL flags, so a new permission touches exactly these places:

1. `core/src/main/kotlin/com/peaspilaf/copyparty/core/Model.kt` — add the constant to `Permission`
   with copyparty's single-letter flag. Adding an enum constant changes the frozen public API that
   the other regions compile against, so coordinate it.
2. `core/src/main/kotlin/com/peaspilaf/copyparty/core/ConfigRenderer.kt` — emit the flag in
   `accsFlags`. Keep the canonical order (`r`, `g`, `w`, `m`, `d`, `a`) and the rule that `r` already
   implies download, so `g` is only written when `r` is absent.
3. `core/src/main/kotlin/com/peaspilaf/copyparty/core/Validator.kt` — add any pairing warning, the
   way move/delete currently warns when it comes without upload.
4. `core/src/test/kotlin/com/peaspilaf/copyparty/core/ConfigRendererTest.kt` — pin the rendered flag
   string (`accsFlags(...)`) so the mapping cannot silently change.
5. `scripts/make_contract_state.py` — grant the new permission on a contract volume.
6. `scripts/check_server_contract.py` — add a check whose name is a sentence and perform the real
   action over HTTP (upload, move, delete, WebDAV, …), then assert the result.
7. `README.md` — add the row to the permission table above. `scripts/check_readme_contract.py` fails
   until every `Permission` flag is documented, so this step is not optional.
8. Any UI that labels or toggles permissions — add the label/icon; exhaustive `when` expressions over
   `Permission` will fail to compile until the new constant is handled, which is the intended safety
   net.
9. CI needs no new job: the `Android build + unit tests` job runs `:core:test` and the
   `Config contract (real copyparty)` job runs the HTTP check. Watch the run with the `gh` commands
   above.

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
