#!/usr/bin/env python3
"""Assert that the README says only what the repository actually does.

This is the documentation test for a project whose only compiler is CI. It reads the README, the
workflows, the build files and the permission model, then fails when the README names a job, artifact,
gradle task, version, API level or permission flag that no longer exists (or when it stops documenting
how to add a permission or how to watch CI).

Every check name is a sentence: the output is the index of claims this README is allowed to make.
"""
import os
import re
import sys

# Importing the sibling checker must not leave __pycache__ in the repository.
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from check_ci_contract import (  # noqa: E402 - sibling script, same directory
    CI_PATH,
    DEPENDABOT_PATH,
    RELEASE_PATH,
    ROOT,
    env_value,
    job_blocks,
    job_field,
    read,
    upload_artifact_names,
)

README_PATH = os.path.join(ROOT, "README.md")
APP_BUILD = os.path.join(ROOT, "app", "build.gradle.kts")
MODEL = os.path.join(
    ROOT, "core", "src", "main", "kotlin", "com", "peaspilaf", "copyparty", "core", "Model.kt"
)
LIBS = os.path.join(ROOT, "gradle", "libs.versions.toml")
WRAPPER = os.path.join(ROOT, "gradle", "wrapper", "gradle-wrapper.properties")

GRADLE_TASK = re.compile(r"(?<![A-Za-z0-9_]):[a-z][a-z0-9]*:[A-Za-z][A-Za-z0-9]*")
PERMISSION = re.compile(r"([A-Z]+)\('(.)'\)")


def catalog_version(text, key):
    """Version of [key] in gradle/libs.versions.toml, or None."""
    match = re.search(r"^" + re.escape(key) + r'\s*=\s*"([^"]+)"\s*$', text, re.MULTILINE)
    return match.group(1) if match else None


def section(text, heading):
    """Body of the first ``## `` section whose heading contains [heading], or None."""
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("## ") and heading.lower() in line.lower():
            body = []
            for follow in lines[index + 1:]:
                if follow.startswith("## "):
                    break
                body.append(follow)
            return "\n".join(body)
    return None


def main():
    failures = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + ("" if ok else f"  [{detail}]"))
        if not ok:
            failures.append(name)

    readme = read(README_PATH)
    ci = read(CI_PATH)
    release = read(RELEASE_PATH)
    dependabot = read(DEPENDABOT_PATH)
    app_build = read(APP_BUILD)
    model = read(MODEL)
    libs = read(LIBS)
    wrapper = read(WRAPPER)
    ci_jobs = job_blocks(ci)
    release_jobs = job_blocks(release)

    check("the README says CI is the only compiler", "CI is the only compiler" in readme)
    check("the README says local Android builds are not supported",
          "does not support local Android builds" in readme)

    # ------------------------------------------------------------------- CI jobs: names, timeouts
    readme_lines = readme.splitlines()
    for job_id, lines in ci_jobs.items():
        name = job_field(lines, "name")
        timeout = job_field(lines, "timeout-minutes")
        check(f"the README names the {name} job exactly as the workflow does", f"`{name}`" in readme, name)
        row = next((line for line in readme_lines if line.startswith(f"| `{name}`")), None)
        check(f"the README states the {name} timeout", row is not None and f"{timeout} min" in row,
              f"expected {timeout} min in a table row")

    # --------------------------------------------------------------------------- artifact names
    published = set()
    for lines in list(ci_jobs.values()) + list(release_jobs.values()):
        published.update(upload_artifact_names(lines))
    check("the README lists every artifact CI can produce", bool(published))
    for artifact in sorted(published):
        check(f"the README documents the {artifact} artifact", artifact in readme)

    # ------------------------------------------------------------------------------ gradle tasks
    tasks = set(GRADLE_TASK.findall(ci + "\n" + release))
    for task in sorted(tasks):
        check(f"the README documents the {task} gradle task", task in readme)

    # --------------------------------------------------------------- pinned versions and SDK levels
    copyparty_version = env_value(ci, "COPYPARTY_VERSION")
    python_version = env_value(ci, "PYTHON_VERSION")
    java_versions = sorted(set(re.findall(r"java-version:\s*(\d+)", ci)))
    emulator_api = re.search(r"api-level:\s*(\d+)", ci)
    min_sdk = re.search(r"minSdk\s*=\s*(\d+)", app_build)

    check("the README documents the embedded copyparty version",
          copyparty_version is not None and copyparty_version in readme, str(copyparty_version))
    check("the README documents the python version CI and Chaquopy use",
          python_version is not None and python_version in readme, str(python_version))
    check("the README documents the JDK CI builds with",
          bool(java_versions) and all(f"Java {version}" in readme for version in java_versions),
          ",".join(java_versions))
    check("the README documents the emulator API level",
          emulator_api is not None and f"API {emulator_api.group(1)}" in readme,
          emulator_api.group(1) if emulator_api else "missing")
    check("the README documents the minimum Android version",
          min_sdk is not None and f"API {min_sdk.group(1)}" in readme,
          min_sdk.group(1) if min_sdk else "missing")

    # The pinned-toolchain table is also what dependabot edits land against, so a bump in the
    # wrapper or the version catalog must come with the matching README row.
    gradle_version = re.search(r"gradle-(\d[0-9.]*)-(?:bin|all)\.zip", wrapper)
    kotlin_version = catalog_version(libs, "kotlin")
    compose_bom = catalog_version(libs, "composeBom")
    compile_sdk = re.search(r"compileSdk\s*=\s*(\d+)", app_build)
    target_sdk = re.search(r"targetSdk\s*=\s*(\d+)", app_build)

    check("the README documents the Gradle version",
          gradle_version is not None and f"| Gradle | {gradle_version.group(1)} |" in readme,
          gradle_version.group(1) if gradle_version else "missing")
    check("the README documents the Kotlin version",
          kotlin_version is not None and f"| Kotlin | {kotlin_version} |" in readme,
          str(kotlin_version))
    check("the README documents the Compose BOM version",
          compose_bom is not None and f"| Compose BOM | {compose_bom} |" in readme,
          str(compose_bom))
    check("the README documents the compile/target SDK",
          compile_sdk is not None
          and target_sdk is not None
          and compile_sdk.group(1) == target_sdk.group(1)
          and f"| compileSdk / targetSdk | {compile_sdk.group(1)} |" in readme,
          f"compileSdk={compile_sdk.group(1) if compile_sdk else None} "
          f"targetSdk={target_sdk.group(1) if target_sdk else None}")

    # ------------------------------------------------------------------- permission documentation
    permission_flags = PERMISSION.findall(model)
    sharing = section(readme, "Sharing model")
    check("the permission model exposes at least one flag", bool(permission_flags))
    for enum_name, flag in permission_flags:
        check(f"the README documents the {flag} permission flag for {enum_name}",
              sharing is not None and f"`{flag}`" in sharing, f"flag {flag!r}")

    walkthrough = section(readme, "Adding a new share permission")
    check("the README has an end-to-end walkthrough for a new share permission", walkthrough is not None)
    for anchor in (
        "Model.kt",
        "ConfigRenderer.kt",
        "accsFlags",
        "Validator.kt",
        "ConfigRendererTest.kt",
        "make_contract_state.py",
        "check_server_contract.py",
        "README.md",
        "CI",
    ):
        check(f"the permission walkthrough mentions {anchor}", walkthrough is not None and anchor in walkthrough)

    # ---------------------------------------------------------------------------- watching CI
    watching = section(readme, "Watching CI")
    check("the README has a section on watching CI", watching is not None)
    for command in (
        "gh run list",
        "gh run watch",
        "gh run view",
        "gh run download",
        "gh workflow run",
        "gh release list",
        "--log-failed",
    ):
        check(f"the README documents the {command} command", watching is not None and command in watching)

    # -------------------------------------------------------------- contract scripts and promises
    ci_scripts = set(re.findall(r"scripts/[A-Za-z0-9_]+\.py", ci))
    check("CI runs at least one contract script", bool(ci_scripts))
    for script in sorted(ci_scripts):
        check(f"the README documents {script}", script in readme)
    check("the README documents that a volume with no grants stays hidden",
          "no grants" in readme and "volume list" in readme)
    check("the README points at the CI workflow file", ".github/workflows/ci.yml" in readme)
    check("the README documents the release workflow", "release.yml" in readme)

    # ------------------------------------------------------------------------------- dependabot
    check("the README documents the dependabot config", ".github/dependabot.yml" in readme)
    check("the README says dependabot updates weekly", "weekly" in dependabot and "weekly" in readme)

    if failures:
        print(f"\n{len(failures)} README check(s) failed", file=sys.stderr)
        return 1
    print("\nall README checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
