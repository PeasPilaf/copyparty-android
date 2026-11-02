#!/usr/bin/env python3
"""Fail when the app can do something the user has no control for.

Two shipped UX bugs motivated this gate: `AppViewModel.deleteShare()` existed with no screen calling
it (a share could never be deleted), and `StorageAccess.allFilesAccessIntent()`/`browseRoots()` existed
with no screen calling them (a self-hosted owner could never leave the app's private folder). Both
compiled, both were unit-tested, and both were invisible in the product.

So this script reads the two capability surfaces and fails when a capability is not reachable from any
screen:

* `AppViewModel`'s public mutation functions (the commands a screen can issue), and
* `StorageAccess`'s capability functions (what the device can offer the picker).

A reference only counts when it lives in a *different file* than the declaration: a capability used by
its own class is not reachable from a screen, which is exactly how `hasAllFilesAccess()` was called by
`browseRoots()` while nothing ever asked the user for the permission.

Only the Python standard library is used, and the script is import-light so `check_readme_contract.py`
can keep reading the same repository.

Every check name is a sentence: the output is the list of user controls the app promises to have.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN_SOURCE = os.path.join(ROOT, "app", "src", "main", "kotlin")
VIEW_MODEL = os.path.join(
    MAIN_SOURCE, "com", "peaspilaf", "copyparty", "ui", "AppViewModel.kt"
)
STORAGE_ACCESS = os.path.join(
    MAIN_SOURCE, "com", "peaspilaf", "copyparty", "data", "StorageAccess.kt"
)

# A public AppViewModel function is a mutation (a command a screen issues) when its name starts with
# one of these verbs. Readers and generators (urls, issues, newShare, suggestUrlPath, randomPassword)
# are deliberately not commands: the screens that need them call them while composing a value.
MUTATION_VERBS = (
    "add",
    "apply",
    "cancel",
    "clear",
    "create",
    "delete",
    "disable",
    "dismiss",
    "enable",
    "import",
    "remove",
    "rename",
    "reset",
    "restart",
    "save",
    "set",
    "start",
    "stop",
    "toggle",
    "update",
)

# Capabilities that intentionally have no control of their own, each with the reason it is allowed.
# An entry here is a decision somebody made, not an oversight: keep the reasons to one line.
ALLOWLIST = {
    "update": "generic state-transition helper; screens call the named mutations above it",
    "allFilesAccessIntent": "raw intent kept for tests; the button calls openAllFilesAccess(), which tries it first",
}


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def main_sources():
    """Map repository-relative path -> text for every Kotlin file the app ships."""
    sources = {}
    for directory, _subdirectories, files in os.walk(MAIN_SOURCE):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(directory, name)
            sources[os.path.relpath(path, ROOT)] = read(path)
    return sources


def members(source, header_pattern, member_pattern):
    """Names of the members matching [member_pattern] inside the body that starts at [header_pattern].

    The body ends at the first line that is exactly ``}``: both surfaces are top-level declarations
    with four-space indented members, so no brace counting is needed.
    """
    lines = source.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if re.search(header_pattern, line))
    except StopIteration:
        return []
    names = []
    for line in lines[start + 1:]:
        if line.rstrip() == "}":
            break
        match = re.match(member_pattern, line)
        if match:
            names.append(match.group(1))
    return names


def view_model_mutations(source):
    """Public AppViewModel functions whose name is a command verb."""
    names = members(source, r"^class AppViewModel\(", r"^    fun ([A-Za-z0-9_]+)\(")
    return [name for name in names if name.lower().startswith(MUTATION_VERBS)]


def storage_capabilities(source):
    """The capability functions the StorageAccess interface promises."""
    return members(source, r"^interface StorageAccess \{", r"^    fun ([A-Za-z0-9_]+)\(")


def unreferenced(names, declared_in, sources):
    """Names that no file other than [declared_in] mentions.

    A declaration is a mention, so the declaration file is skipped entirely; that is what turns
    "only its own class calls this" into a failure.
    """
    return {
        name
        for name in names
        if not any(
            path != declared_in and re.search(r"\b" + re.escape(name) + r"\b", text)
            for path, text in sources.items()
        )
    }


def unaccounted(names, declared_in, sources, allowlist):
    """The names a screen really must gain a control for: no caller and no written decision."""
    return sorted(unreferenced(names, declared_in, sources) - set(allowlist))


def self_test():
    """The detector must still be able to fail, and the allowlist must still silence.

    A gate that silently passes everything is worse than no gate: this feeds the detector a synthetic
    view model with a mutation nobody calls and a screen that calls another one, and requires exactly
    the expected answer - once without and once with an allowlist entry.
    """
    sources = {
        "ui/AppViewModel.kt": (
            "class AppViewModel {\n"
            "    fun deleteShare(id: String) = Unit\n"
            "    fun saveShare(id: String) = Unit\n"
            "}\n"
        ),
        "ui/SharesScreen.kt": "fun screen(viewModel: AppViewModel) { viewModel.saveShare(\"s1\") }\n",
    }
    strict = unaccounted({"deleteShare", "saveShare"}, "ui/AppViewModel.kt", sources, {})
    allowed = unaccounted(
        {"deleteShare", "saveShare"}, "ui/AppViewModel.kt", sources, {"deleteShare": "reason"}
    )
    return strict == ["deleteShare"] and allowed == []


def main():
    failures = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + ("" if ok else f"  [{detail}]"))
        if not ok:
            failures.append(name)

    try:
        sources = main_sources()
    except OSError as error:  # a checkout always has the app sources; make the reason readable
        print(f"FAIL  the parity gate can read the app sources  [{error}]", file=sys.stderr)
        return 1

    try:
        mutations = view_model_mutations(read(VIEW_MODEL))
        capabilities = storage_capabilities(read(STORAGE_ACCESS))
    except OSError as error:
        print(f"FAIL  the parity gate can read the capability surfaces  [{error}]", file=sys.stderr)
        return 1

    check(
        "the parity gate reads the view model and the storage capabilities",
        bool(mutations) and bool(capabilities),
        f"mutations={mutations} capabilities={capabilities}",
    )
    check("the parity gate still fails on a mutation nothing calls", self_test())

    view_model = os.path.relpath(VIEW_MODEL, ROOT)
    storage = os.path.relpath(STORAGE_ACCESS, ROOT)

    for name in sorted(ALLOWLIST):
        check(
            f"the allowlisted capability {name} has a written reason",
            bool(ALLOWLIST[name].strip()),
        )

    stale = sorted(
        name
        for name in ALLOWLIST
        if name not in mutations and name not in capabilities
    )
    check(
        "no allowlist entry outlives the capability it excuses",
        not stale,
        ", ".join(stale),
    )

    missing_mutations = unaccounted(mutations, view_model, sources, ALLOWLIST)
    check(
        "every view model mutation has a reachable control",
        not missing_mutations,
        ", ".join(missing_mutations),
    )

    missing_capabilities = unaccounted(capabilities, storage, sources, ALLOWLIST)
    check(
        "every storage capability has a caller",
        not missing_capabilities,
        ", ".join(missing_capabilities),
    )

    if failures:
        print(f"\n{len(failures)} capability parity check(s) failed", file=sys.stderr)
        print(
            "give each name above a control in app/src/main/kotlin, or add it to ALLOWLIST with a "
            "one-line reason when having no control is a decision",
            file=sys.stderr,
        )
        return 1
    print("\nall capability parity checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
