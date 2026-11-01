"""Start and stop copyparty inside the app process.

copyparty normally runs as a CLI/TUI program: it installs signal handlers and exits when one
arrives. On Android there is no controlling terminal and the Python interpreter does not own the
process signals, so this module:

* neutralises the signal API copyparty installs handlers through (they are only used to shut down
  and to reload, both of which the app drives directly through Java),
* remembers the running SvcHub so the Android foreground service can shut it down cleanly,
* runs copyparty's own `main()` in a daemon thread and surfaces any startup traceback to Kotlin.
"""
import os
import sys
import threading
import traceback

_lock = threading.RLock()
_hub = None
_thread = None
_error = None
_patched = False


def _patch_signals():
    import signal

    def noop(*args, **kwargs):
        return None

    # copyparty calls signal.signal() from its worker thread, which CPython rejects.
    signal.signal = noop
    signal.pthread_sigmask = noop
    signal.setitimer = noop


def _patch_svchub():
    global _patched
    if _patched:
        return
    import copyparty.svchub as sh

    original_run = sh.SvcHub.run

    def run(self, *args, **kwargs):
        global _hub
        _hub = self
        return original_run(self, *args, **kwargs)

    sh.SvcHub.run = run
    _patched = True


def _run(argv):
    global _error
    try:
        from copyparty.__main__ import main

        main(argv)
    except SystemExit as ex:
        code = ex.code
        if code not in (0, None):
            _error = "copyparty exited with code %r" % (code,)
    except BaseException:
        _error = traceback.format_exc()


def start(conf_path, log_path=""):
    """Start copyparty with the config at `conf_path`. Returns "" or an error message."""
    global _thread, _error
    with _lock:
        if _thread is not None and _thread.is_alive():
            return ""
        _error = None
        if not os.path.isdir(os.path.dirname(conf_path) or "."):
            return "config folder does not exist: " + conf_path

        _patch_signals()
        _patch_svchub()

        argv = ["copyparty", "-c", conf_path, "--http-only", "--no-crt", "-q"]
        if log_path:
            argv += ["-lo", log_path, "--flo", "2"]

        _thread = threading.Thread(target=_run, args=(argv,), name="copyparty-main", daemon=True)
        _thread.start()
    return ""


def stop(timeout=8.0):
    """Ask copyparty to shut down. Returns True when it was running."""
    global _hub
    with _lock:
        hub = _hub
    if hub is None:
        return False

    def _shutdown():
        try:
            hub.shutdown()
        except BaseException:
            pass

    threading.Thread(target=_shutdown, name="copyparty-stop", daemon=True).start()
    if _thread is not None:
        _thread.join(timeout)
    return True


def is_alive():
    return _thread is not None and _thread.is_alive()


def last_error():
    return _error or ""
