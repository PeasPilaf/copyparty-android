# Contributing and CI

The app is built and tested **only** in GitHub Actions: many contributors (and agents) never run an
Android build locally, so CI is the compiler and the only reviewer that matters. This file holds the
engineering detail; [README.md](README.md) describes what the app does.

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

## Project status

The features in the README exist and are enforced by three CI jobs: unit and Robolectric tests plus a
capability-parity gate, a contract job that feeds the app's rendered config to real copyparty, and an
emulator job that drives the shipped app against the real embedded server.

## Build and test — on GitHub only

This project intentionally does not support local Android builds. CI is the only compiler: `./gradlew`
on a laptop is not part of the workflow, and every build, test and contract check runs in GitHub
Actions. The checks that do not need an Android build can still be run by hand:
`python3 scripts/check_ci_contract.py`, `python3 scripts/check_readme_contract.py` and
`python3 scripts/check_capability_parity.py` need only the checkout, and
`python3 scripts/check_boot_contract.py` additionally needs Python 3.12 with `copyparty==1.20.26`
installed. `scripts/check_server_contract.py` is driven by the renderer binary, which CI builds with
`:core:installDist`.

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
| `Android build + unit tests` | 60 min | `:core:test`, `:app:testPlayDebugUnitTest`, `:app:assemblePlayDebug`, `:app:assemblePlayRelease`, `:app:assembleFossRelease`, `:app:bundlePlayRelease`, `scripts/check_play_compliance.py`, `scripts/check_capability_parity.py`, `:app:lintPlayDebug` | `test-reports`, `copyparty-apks` |
| `Config contract (real copyparty)` | 20 min | `scripts/check_ci_contract.py`, `scripts/check_readme_contract.py`, `scripts/check_boot_contract.py`, `:core:installDist`, the renderer CLI, `scripts/check_server_contract.py` | `config-contract` |
| `Android device test (emulator)` | 60 min | `:app:connectedFossDebugAndroidTest` on an API 34 emulator | `instrumented-test-reports` |

The Android job builds both release flavors and the Play AAB on every run
(`:app:assemblePlayRelease`, `:app:assembleFossRelease`, `:app:bundlePlayRelease`), so release-only
packaging or Chaquopy breakage cannot sit unnoticed until the next tag. With no keystore secrets it
uses the debug signing config and still produces an installable APK.

### Journey tests (the emulator job)

The emulator job runs the self-hosted (`foss`) debug build, because that is the one with every
user-facing capability, and it drives the shipped app against the real embedded server. Nothing about
the server is faked. Each test name is a promise:

| Test | Promise |
| --- | --- |
| `UiJourneyTest.a novice can create a share, start, browse, download and delete it` | Home's first-run state reaches the folder picker, a share exists, the server starts, the in-app browser lists and downloads the file, and the share can be deleted again |
| `UiJourneyTest.the address shown on Home really serves the shared folder` | the address Home prints is copyparty's own page and serves the file byte for byte |
| `UiJourneyTest.deleting the last share returns the app to the first-run state` | deleting the last share survives an activity recreation and leaves no ghost row |
| `UiJourneyTest.a file a client uploads shows up in the app's listing` | the served folder really contains client files, and the app lists a file it did not create |
| `FossStorageJourneyTest.granting all files access lets the picker reach internal storage and serve a real folder` | All files access makes the picker reach `Download`, and the server serves a file that is really there - not a copy in the app's folder |

`FossStorageJourneyTest` grants `MANAGE_EXTERNAL_STORAGE` through `appops` and seeds `/sdcard/Download`
through `UiAutomation.executeShellCommand`, the same way a shell on the device would, so the test
covers the self-hosted promise instead of the app's private folder.

### Server truthfulness tests (host tests)

The server lifecycle is covered by plain JVM and Robolectric tests, because those promises are about
what the app says, not what it draws:

| Test | Promise |
| --- | --- |
| `PortInUseTest` | `Running` requires copyparty's own fingerprint on the port and a live interpreter; a foreign listener becomes `Port <port> is used by another app. Choose another port in Settings.` |
| `ApplyConfigTest` | an edit re-renders the config and restarts the server with one `ACTION_RESTART`; the old URLs never stay on screen; applying while stopped just starts |
| `NotificationJourneyTest` | the running notification shows the address with Open and Stop, and a failed start leaves a dismissible, tappable notification |
| `NotificationPermissionTest` | the first start asks for POST_NOTIFICATIONS, and Android versions without it are never asked |
| `BootStartedServerTest` | start-on-boot starts the server and posts a confirmation, and refuses a broken configuration |
| `ServerLogTailTest` | the last lines of copyparty's log are readable, and a missing or unreadable log is empty text instead of a crash |

### Release workflow

`.github/workflows/release.yml` runs on tags matching `v*` (and manually). It builds
`:app:assemblePlayRelease`, `:app:assembleFossRelease` and `:app:bundlePlayRelease`, uploads them as
`copyparty-release-artifacts`; on a tag it also
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
| `scripts/check_capability_parity.py` | every capability in `AppViewModel` and `StorageAccess` has a control a user can reach - a mutation nothing calls, or a storage capability nothing uses, fails CI unless it is allowlisted with a written reason |

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

* `Android build + unit tests` — a compile error, a unit/Robolectric test, or the capability-parity
  gate. Download `test-reports` and open `index.html`; the failing test name is the specification that
  broke. A `FAIL  every view model mutation has a reachable control` line names the function whose
  control is missing.
* `Config contract (real copyparty)` — read the `FAIL  <sentence>` line in the log. The
  `config-contract` artifact contains `state.json`, the rendered `copyparty.conf` and `server.log`.
  A failure here means the app wrote a config real copyparty does not accept or does not enforce.
* `Android device test (emulator)` — check `instrumented-test-reports`; a boot timeout is infra, an
  assertion failure is the journey promise that broke (the screen and the promise are in the test
  name).
* `Docs and workflow contract` — this README, `ci.yml` or `release.yml` drifted. All three contract
  scripts run on a laptop with `python3`; fix the file, not the check.
* The release smoke step — the release variant, signing fallback or Chaquopy packaging broke.

## Adding a capability a user can reach

The capability-parity gate (`scripts/check_capability_parity.py`) exists because two shipped features
were unreachable: `AppViewModel.deleteShare()` with no delete control, and
`StorageAccess.browseRoots()` / `allFilesAccessIntent()` with no picker that used them.

When you add a command to `AppViewModel` (a public function whose name is a command verb) or a
capability to `StorageAccess`, do one of these in the same change:

1. **Give it a control.** Call it from `app/src/main/kotlin` outside its own file, with the testTag the
   journeys use. This is the normal case.
2. **Allowlist it, with a reason.** Add the name to `ALLOWLIST` in the script and write the one-line
   reason in the same entry. An entry that outlives its capability fails the gate, so the list cannot
   rot into a pile of forgotten names.

Extend the emulator journey when the control is part of a user story; the journey tests are the
documentation for what a screen promises.

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

## Build variants

One codebase, two artifacts, because Google Play and self-hosting have different storage rules:

| Flavor | For | Storage |
| --- | --- | --- |
| `play` | Google Play (AAB) | The app's own folder. The Play build does **not** request `MANAGE_EXTERNAL_STORAGE`; Google Play only grants broad file access to a short list of declared, reviewed app categories, and a personal file server is not reliably one of them. |
| `foss` | GitHub Releases / F-Droid (APK) | Any folder on the device after granting All files access, plus the app's own folder. |

Both builds have every screen, including Expert mode; only the storage reach differs.

`scripts/check_play_compliance.py` proves it from the built artifacts: it fails if the Play APK or
AAB requests `MANAGE_EXTERNAL_STORAGE` (or the legacy external-storage permissions), and it fails if
the self-hosted APK loses direct folder access.

### Gradle tasks used by CI

| Task | Meaning |
| --- | --- |
| `:core:test` | JVM tests for the sharing model, config renderer and validator |
| `:app:testPlayDebugUnitTest` | Robolectric + Compose + server-lifecycle tests for the Play build |
| `:app:assemblePlayDebug` | Debuggable Play APK (also the flavor the emulator starts from) |
| `:app:assemblePlayRelease` | Play release APK (store-build smoke test) |
| `:app:bundlePlayRelease` | Play AAB |
| `:app:assembleFossRelease` | Self-hosted release APK |
| `:app:lintPlayDebug` | Lint report |
| `:app:connectedFossDebugAndroidTest` | The journeys on the emulator, against the self-hosted build |
| `:core:installDist` | Renderer CLI used by the config contract job |

Artifacts: `test-reports`, `copyparty-apks` (play debug/release APK, play AAB, foss release APK),
`config-contract`, `instrumented-test-reports`, `emulator-diagnostics` (logcat plus the app's
copyparty log when the emulator job fails), and `copyparty-release-artifacts` from the release
workflow.

## Permission flags

The UI labels are the product; the copyparty ACL flags are the wire format they map to.

| UI label | Flag |
| --- | --- |
| View | `r` |
| Download only | `g` |
| Upload | `w` |
| Move / rename | `m` |
| Delete | `d` |
| Admin | `a` |

## Repository layout

| Path | What lives there |
| --- | --- |
| `core/` | Pure-JVM sharing model, copyparty config renderer and validator (no Android) |
| `app/` | Compose UI, foreground service, Python bridge, embedded copyparty |
| `app/src/androidTest/` | The emulator journeys: real activity, real embedded server, no fakes |
| `scripts/` | Contract checks: config against real copyparty, boot lifecycle, docs/CI consistency, capability parity |
| `.github/workflows/` | CI and release pipelines |
