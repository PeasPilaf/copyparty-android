#!/usr/bin/env python3
"""Assert that the CI workflow keeps the promises the README makes to contributors.

CI is the only compiler in this project, so `.github/workflows/ci.yml` is production code. This test
reads the workflows as text (the runners do not ship PyYAML) and fails when a job loses its timeout,
its cache, its uploaded artifact or its stable display name, when the release variant stops being
built on every run, or when the dependabot config stops being the grouped weekly setup the README
describes.

Every check name is a sentence: the output is the documentation for what "CI is green" means.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CI_PATH = os.path.join(ROOT, ".github", "workflows", "ci.yml")
RELEASE_PATH = os.path.join(ROOT, ".github", "workflows", "release.yml")
DEPENDABOT_PATH = os.path.join(ROOT, ".github", "dependabot.yml")
APP_BUILD = os.path.join(ROOT, "app", "build.gradle.kts")
ROBOLECTRIC = os.path.join(ROOT, "app", "src", "test", "resources", "robolectric.properties")

CI_JOBS = {
    "android": "Android build + unit tests",
    "config-contract": "Config contract (real copyparty)",
    "instrumented": "Android device test (emulator)",
}
CI_ARTIFACTS = (
    "test-reports",
    "copyparty-debug-apk",
    "copyparty-release-smoke-apk",
    "config-contract",
    "instrumented-test-reports",
)
GRADLE_TASKS = (
    ":core:test",
    ":app:testDebugUnitTest",
    ":app:assembleDebug",
    ":app:assembleRelease",
    ":app:lintDebug",
    ":core:installDist",
    ":app:connectedDebugAndroidTest",
)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def job_blocks(text):
    """Map job id -> the job's lines.

    Job ids are the only keys at exactly two spaces under the top-level ``jobs:`` key, so a small
    indentation-aware scan is enough and avoids depending on a YAML library.
    """
    lines = text.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.rstrip() == "jobs:")
    except StopIteration:
        return {}
    blocks = {}
    current = None
    for line in lines[start + 1:]:
        if line and not line[0].isspace() and not line.lstrip().startswith("#"):
            break
        match = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if match:
            current = match.group(1)
            blocks[current] = []
        elif current is not None:
            blocks[current].append(line)
    return blocks


def job_field(lines, field):
    pattern = re.compile(r"^    " + re.escape(field) + r":\s*(.+?)\s*$")
    for line in lines:
        match = pattern.match(line)
        if match:
            return match.group(1).strip().strip('"')
    return None


def upload_artifact_names(lines):
    """Names of every ``actions/upload-artifact`` step in a job block."""
    names = []
    for index, line in enumerate(lines):
        if "actions/upload-artifact@v4" not in line:
            continue
        for follow in lines[index + 1:]:
            if re.match(r"^      - ", follow):
                break
            match = re.match(r"^\s+name:\s*([A-Za-z0-9._-]+)\s*$", follow)
            if match:
                names.append(match.group(1))
                break
    return names


def upload_has_setting(lines, artifact, setting):
    for index, line in enumerate(lines):
        if not re.match(r"^\s+name:\s*" + re.escape(artifact) + r"\s*$", line):
            continue
        return any(setting in follow for follow in lines[index + 1:index + 6])
    return False


def env_value(text, key):
    match = re.search(r'^\s*' + re.escape(key) + r':\s*"([^"]+)"\s*$', text, re.MULTILINE)
    return match.group(1) if match else None


def dependabot_blocks(text):
    blocks = {}
    current = None
    for line in text.splitlines():
        match = re.match(r'^  - package-ecosystem:\s*"?([A-Za-z-]+)"?\s*$', line)
        if match:
            current = match.group(1)
            blocks[current] = []
        elif current is not None:
            blocks[current].append(line)
    return blocks


def main():
    failures = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + ("" if ok else f"  [{detail}]"))
        if not ok:
            failures.append(name)

    ci = read(CI_PATH)
    release = read(RELEASE_PATH)
    dependabot = read(DEPENDABOT_PATH)
    app_build = read(APP_BUILD)
    robolectric = read(ROBOLECTRIC)
    blocks = job_blocks(ci)

    # ---------------------------------------------------------------- triggers and shared versions
    check("CI runs on every pull request", "pull_request:" in ci)
    check("CI runs on main after a merge", re.search(r"^  push:\n    branches: \[main\]", ci, re.MULTILINE) is not None)
    check("CI can be started by hand", "workflow_dispatch:" in ci)
    check("a newer run cancels an older run of the same ref", "cancel-in-progress: true" in ci)
    check("CI only needs read access to the repository", re.search(r"^permissions:\n  contents: read", ci, re.MULTILINE) is not None)

    copyparty_version = env_value(ci, "COPYPARTY_VERSION")
    python_version = env_value(ci, "PYTHON_VERSION")
    release_python_version = env_value(release, "PYTHON_VERSION")
    check("the embedded copyparty version is pinned in one place", copyparty_version is not None, "COPYPARTY_VERSION")
    check(
        "the copyparty version CI tests is the version the app embeds",
        copyparty_version is not None and f"copyparty=={copyparty_version}" in app_build,
        f"ci={copyparty_version}",
    )
    check(
        "the python version CI uses is the version Chaquopy embeds",
        python_version is not None and f'version = "{python_version}"' in app_build,
        f"ci={python_version}",
    )
    check("the release workflow uses the same python version as CI", release_python_version == python_version,
          f"ci={python_version} release={release_python_version}")

    # ------------------------------------------------------------------------------- CI job shape
    check("CI keeps exactly the three documented jobs", set(blocks) == set(CI_JOBS), ", ".join(sorted(blocks)))
    for job_id, expected_name in CI_JOBS.items():
        lines = blocks.get(job_id, [])
        timeout = job_field(lines, "timeout-minutes")
        check(f"job {job_id} keeps its stable display name", job_field(lines, "name") == expected_name,
              str(job_field(lines, "name")))
        check(f"job {job_id} cannot hang forever", timeout is not None and timeout.isdigit() and int(timeout) > 0,
              str(timeout))
        check(f"job {job_id} checks out the repository", "actions/checkout@v4" in "\n".join(lines))
        check(f"job {job_id} caches its toolchain between runs",
              "gradle/actions/setup-gradle@v4" in "\n".join(lines) or "actions/cache@v4" in "\n".join(lines))
        check(f"job {job_id} uses the pinned python version",
              "python-version: ${{ env.PYTHON_VERSION }}" in "\n".join(lines))
        check(f"job {job_id} uploads its results as artifacts", bool(upload_artifact_names(lines)))

    # ------------------------------------------------------------------------------ android build
    android = blocks.get("android", [])
    android_text = "\n".join(android)
    for task in GRADLE_TASKS[:5]:
        check(f"CI runs {task}", task in android_text)
    check("the host unit tests keep their stable task list", ":core:test :app:testDebugUnitTest" in android_text)
    check("every CI run builds the release variant so it cannot rot", ":app:assembleRelease" in android_text)
    check("CI keeps the debug APK artifact name stable", "copyparty-debug-apk" in upload_artifact_names(android))
    check("CI publishes the release smoke APK", "copyparty-release-smoke-apk" in upload_artifact_names(android))
    check("CI publishes test and lint reports", "test-reports" in upload_artifact_names(android))
    check("the debug APK is only reported when it exists",
          upload_has_setting(android, "copyparty-debug-apk", "if-no-files-found: error"))
    check("the release smoke APK is only reported when it exists",
          upload_has_setting(android, "copyparty-release-smoke-apk", "if-no-files-found: error"))

    # ---------------------------------------------------------------------------- config contract
    contract = blocks.get("config-contract", [])
    contract_text = "\n".join(contract)
    check("CI runs the boot lifecycle contract on plain CPython",
          "python3 scripts/check_boot_contract.py" in contract_text)
    check("CI installs the copyparty version the app embeds",
          "copyparty==${COPYPARTY_VERSION}" in contract_text)
    check("CI builds the renderer CLI before using it", ":core:installDist" in contract_text)
    check("CI generates the contract state with the app's own renderer",
          "python3 scripts/make_contract_state.py" in contract_text)
    check("CI renders the contract config through the app's CLI",
          "core/build/install/core/bin/core" in contract_text)
    check("CI checks the served HTTP contract", "python3 scripts/check_server_contract.py" in contract_text)
    check("CI checks the workflow against its own promises", "python3 scripts/check_ci_contract.py" in contract_text)
    check("CI checks the README against the workflows", "python3 scripts/check_readme_contract.py" in contract_text)
    check("the contract job's artifact name is stable", "config-contract" in upload_artifact_names(contract))

    # ---------------------------------------------------------------------------- device test job
    instrumented = blocks.get("instrumented", [])
    instrumented_text = "\n".join(instrumented)
    emulator_api = re.search(r"api-level:\s*(\d+)", instrumented_text)
    robolectric_api = re.search(r"^sdk=(\d+)\s*$", robolectric, re.MULTILINE)
    check(
        "the emulator runs at the same API level Robolectric pins",
        emulator_api is not None and robolectric_api is not None and emulator_api.group(1) == robolectric_api.group(1),
        f"emulator={emulator_api.group(1) if emulator_api else None} robolectric={robolectric_api.group(1) if robolectric_api else None}",
    )
    check("the device test runs the connected Android test task",
          ":app:connectedDebugAndroidTest" in instrumented_text)
    check("the device test uses the emulator runner action",
          "reactivecircus/android-emulator-runner@v2" in instrumented_text)
    check("the device test publishes its reports",
          "instrumented-test-reports" in upload_artifact_names(instrumented))

    published_artifacts = set()
    for lines in blocks.values():
        published_artifacts.update(upload_artifact_names(lines))
    for artifact in CI_ARTIFACTS:
        check(f"CI publishes the {artifact} artifact", artifact in published_artifacts)

    # ------------------------------------------------------------------------------- release path
    release_blocks = job_blocks(release)
    check("the release workflow has exactly one job", set(release_blocks) == {"release"}, ", ".join(sorted(release_blocks)))
    release_lines = release_blocks.get("release", [])
    release_text = "\n".join(release_lines)
    release_timeout = job_field(release_lines, "timeout-minutes")
    check("the release job keeps its stable display name", job_field(release_lines, "name") == "Release APK",
          str(job_field(release_lines, "name")))
    check("the release job cannot hang forever",
          release_timeout is not None and release_timeout.isdigit() and int(release_timeout) > 0, str(release_timeout))
    check("the release job caches gradle between runs", "gradle/actions/setup-gradle@v4" in release_text)
    check("a tagged push builds a release", re.search(r'tags: \["v\*"\]', release) is not None)
    check("the release workflow may publish GitHub release assets", "contents: write" in release)
    check("the release workflow builds the release variant", ":app:assembleRelease" in release_text)
    check("the release workflow uploads the APK", "copyparty-release-apk" in upload_artifact_names(release_lines))
    check("the release APK is only reported when it exists",
          upload_has_setting(release_lines, "copyparty-release-apk", "if-no-files-found: error"))
    check("a tagged release publishes the APK to GitHub releases", "softprops/action-gh-release@v2" in release_text)
    check("release runs cannot cancel each other mid-publish", "cancel-in-progress: false" in release)
    check("a real signing key is optional",
          "ANDROID_KEYSTORE_BASE64:" in release_text and "if: env.ANDROID_KEYSTORE_BASE64 != ''" in release_text)

    # --------------------------------------------------------------------------------- dependabot
    check("dependabot uses version 2 config", re.search(r"^version:\s*2\s*$", dependabot, re.MULTILINE) is not None)
    dependabot_jobs = dependabot_blocks(dependabot)
    check("dependabot updates gradle dependencies", "gradle" in dependabot_jobs)
    check("dependabot updates the actions used by the workflows", "github-actions" in dependabot_jobs)
    for ecosystem in ("gradle", "github-actions"):
        lines = dependabot_jobs.get(ecosystem, [])
        text = "\n".join(lines)
        check(f"dependabot checks {ecosystem} weekly", re.search(r'interval:\s*"weekly"', text) is not None)
        check(f"dependabot groups {ecosystem} updates into one pull request",
              "groups:" in text and '"*"' in text)

    if failures:
        print(f"\n{len(failures)} workflow check(s) failed", file=sys.stderr)
        return 1
    print("\nall workflow checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
