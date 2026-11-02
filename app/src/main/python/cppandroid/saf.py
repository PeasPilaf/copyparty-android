"""Read-only copyparty volumes backed by Android's Storage Access Framework.

The Play build cannot ask for All files access (Play only grants it to a short list of app
categories), so a folder the user picks through Android's folder picker is a per-folder grant: a
``content://`` tree with no POSIX paths.

copyparty is path-based, so the app mirrors the *metadata* of that tree into its own storage: real
directories and placeholder files pre-sized to the real file size. The first time copyparty opens a
file (a download, or a README render), this module materializes its content from SAF and then lets
copyparty read it as an ordinary file. Writes are refused: the Play build is read-only, and the
self-hosted build exists for people who want to share a folder read-write.
"""
import errno
import json
import os
import threading

_lock = threading.Lock()
_roots = []  # list of mirror root directories (str)
_materialized = set()  # absolute paths whose content has been pulled from SAF
_installed = False


def configure(roots_json):
    """Register the mirror roots that back SAF shares. Called before copyparty starts."""
    global _roots
    roots = []
    for raw in json.loads(roots_json or "[]"):
        if not isinstance(raw, str) or not raw:
            continue
        roots.append(os.path.abspath(raw))
    with _lock:
        _roots = roots
        _materialized.clear()
    return len(roots)


def _match(path):
    """Return (root, relpath) when ``path`` lives under a SAF mirror root, else None."""
    try:
        absolute = os.path.abspath(path)
    except (TypeError, ValueError):
        return None
    for root in _roots:
        if absolute == root:
            return root, ""
        if absolute.startswith(root + os.sep):
            return root, absolute[len(root) + 1:]
    return None


def _materialize(path):
    """Pull the real bytes for one placeholder file from SAF. Returns False when not possible."""
    hit = _match(path)
    if hit is None:
        return False
    root, rel = hit
    if not rel or not os.path.isfile(path):
        return False
    with _lock:
        if path in _materialized:
            return True
    try:
        from com.peaspilaf.copyparty.data.saf import SafBridge

        ok = bool(SafBridge.materialize(root, rel))
    except Exception:
        ok = False
    if ok:
        with _lock:
            _materialized.add(path)
    return ok


def _read_only(path):
    return _match(path) is not None


def _deny(path):
    raise OSError(errno.EROFS, "This folder is read-only in the Play build: " + str(path))


def _ensure_materialized(path):
    if not _materialize(path):
        raise OSError(errno.EIO, "Could not read this file from Android storage: " + str(path))


def install():
    """Patch copyparty's filesystem entry points. Idempotent; a no-op when no SAF share exists."""
    global _installed
    if _installed:
        return
    _installed = True
    if not _roots:
        return

    import builtins

    import copyparty.bos.bos as bos

    original_bos_open = bos.open
    original_builtin_open = builtins.open

    def bos_open(path, *args, **kwargs):
        if _read_only(path):
            flags = args[0] if args else kwargs.get("flags", os.O_RDONLY)
            if flags & (os.O_WRONLY | os.O_RDWR | os.O_APPEND | os.O_CREAT | os.O_TRUNC):
                _deny(path)
            _ensure_materialized(path)
        return original_bos_open(path, *args, **kwargs)

    def builtin_open(file, mode="r", *args, **kwargs):
        if isinstance(file, (str, bytes)) and _read_only(file):
            if any(flag in mode for flag in ("w", "a", "x", "+")):
                _deny(file)
            _ensure_materialized(file)
        return original_builtin_open(file, mode, *args, **kwargs)

    def guarded(name):
        original = getattr(bos, name, None)
        if original is None:
            return

        def wrapper(*args, **kwargs):
            for value in args[:2]:
                if isinstance(value, (str, bytes)) and _read_only(value):
                    _deny(value)
            return original(*args, **kwargs)

        setattr(bos, name, wrapper)

    bos.open = bos_open
    builtins.open = builtin_open
    for name in ("rename", "replace", "unlink", "rmdir", "mkdir", "makedirs", "chmod", "chown"):
        guarded(name)

    # A few codepaths bypass bos; make the common os-level writers fail on the mirror too.
    original_os_remove = os.remove
    original_os_unlink = os.unlink
    original_os_rename = os.rename

    def os_remove(path, *a, **k):
        if _read_only(path):
            _deny(path)
        return original_os_remove(path, *a, **k)

    def os_unlink(path, *a, **k):
        if _read_only(path):
            _deny(path)
        return original_os_unlink(path, *a, **k)

    def os_rename(src, dst, *a, **k):
        if _read_only(src) or _read_only(dst):
            _deny(src)
        return original_os_rename(src, dst, *a, **k)

    os.remove = os_remove
    os.unlink = os_unlink
    os.rename = os_rename
