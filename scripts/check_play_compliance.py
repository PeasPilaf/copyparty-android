#!/usr/bin/env python3
"""Prove the two builds are what they claim to be, from the built artifacts.

* the Play artifact must not request MANAGE_EXTERNAL_STORAGE (Google Play only grants broad file
  access to a short list of declared, reviewed app categories) nor the legacy external-storage
  permissions;
* the self-hosted artifact must keep MANAGE_EXTERNAL_STORAGE, because that is what lets it serve any
  folder on the device.

The check reads the merged manifest that actually shipped (aapt2 badging on the APK), not the
AndroidManifest.xml sources, so a manifest overlay that silently stops working fails CI.
"""
import glob
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_BUILD = os.path.join(ROOT, "app", "build.gradle.kts")
PLAY_APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "play", "release")
FOSS_APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "foss", "release")
PLAY_AAB = os.path.join(ROOT, "app", "build", "outputs", "bundle", "playRelease")

BROAD_STORAGE = (
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
)


def find_aapt2():
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if not sdk:
            continue
        candidates = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")))
        if candidates:
            return candidates[-1]
    return shutil.which("aapt2")


def single(directory, pattern):
    matches = sorted(glob.glob(os.path.join(directory, pattern)))
    return matches[-1] if matches else None


def permissions(aapt2, apk):
    result = subprocess.run(
        [aapt2, "dump", "badging", apk],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(f"aapt2 could not read {apk}:\n{result.stderr}")
    return result.stdout


def main() -> int:
    aapt2 = find_aapt2()
    if not aapt2:
        print("could not find aapt2 (set ANDROID_HOME)", file=sys.stderr)
        return 1

    play_apk = single(PLAY_APK, "*.apk")
    foss_apk = single(FOSS_APK, "*.apk")
    play_aab = single(PLAY_AAB, "*.aab")
    missing = [name for name, path in (("Play APK", play_apk), ("self-hosted APK", foss_apk), ("Play AAB", play_aab)) if not path]
    if missing:
        print(f"missing build artifacts: {', '.join(missing)} (run the assemble task first)", file=sys.stderr)
        return 2

    failures = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + ("" if ok else f"  [{detail}]"))
        if not ok:
            failures.append(name)

    play_manifest = permissions(aapt2, play_apk)
    for permission in BROAD_STORAGE:
        check(f"the Play APK does not request {permission}", permission not in play_manifest)
    check("the Play APK still requests network access", "android.permission.INTERNET" in play_manifest)
    check("the Play AAB is produced for the store", play_aab is not None)

    foss_manifest = permissions(aapt2, foss_apk)
    check(
        "the self-hosted APK keeps direct folder access",
        "android.permission.MANAGE_EXTERNAL_STORAGE" in foss_manifest,
    )
    check("the self-hosted APK still requests network access", "android.permission.INTERNET" in foss_manifest)

    version = re.search(r'versionName\s*=\s*"([^"]+)"', open(APP_BUILD, encoding="utf-8").read())
    expected_version = version.group(1) if version else ""
    package = next(
        (line for line in play_manifest.splitlines() if line.startswith("package:")),
        "",
    )
    check(
        "the Play APK is the app itself at the version in the build file",
        "name='com.peaspilaf.copyparty'" in package and expected_version in package,
        f"{package} (expected version {expected_version!r})",
    )

    # The two artifacts must differ in the one way this whole flavor split exists for.
    check(
        "the two flavors really differ on storage",
        ("MANAGE_EXTERNAL_STORAGE" in foss_manifest) and ("MANAGE_EXTERNAL_STORAGE" not in play_manifest),
    )

    if failures:
        print(f"\n{len(failures)} play compliance check(s) failed", file=sys.stderr)
        return 1
    print("\nall play compliance checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
