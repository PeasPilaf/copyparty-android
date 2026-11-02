"""Start and stop copyparty inside the app process.

copyparty normally runs as a CLI/TUI program: it installs signal handlers and exits when one
arrives, and it keeps runtime state (session keys, uploader-IP salts, a server name) in a config
directory resolved from the environment. On Android there is no controlling terminal, the Python
interpreter does not own the process signals, and the default config directory is not writable.

So this module:

* points copyparty's runtime state at an app-private directory before copyparty is imported,
* neutralises the signal-installation API (copyparty calls signal.signal() from a worker thread,
  which CPython rejects outside the main thread),
* remembers the running SvcHub so the Android foreground service can shut it down through the same
  path a real SIGTERM would take,
* runs copyparty's own ``main()`` in a daemon thread and surfaces any startup traceback to Kotlin.
"""
import os
import threading
import traceback

_lock = threading.RLock()
_hub = None
_thread = None
_error = None
_patched = False
_configured_dir = None


def configure_environment(runtime_dir):
    """Make copyparty's runtime state live under app-private storage.

    copyparty resolves its state directory from XDG_CONFIG_HOME/HOME and falls back to TMPDIR. None
    of the usual Android homes are reliably writable for the app, so both are pointed at
    ``runtime_dir`` (created if needed) before copyparty's ``main()`` runs.
    """
    global _configured_dir
    runtime_dir = os.path.abspath(runtime_dir)
    os.makedirs(runtime_dir, exist_ok=True)
    tmp_dir = os.path.join(runtime_dir, "tmp")
    os.makedirs(tmp_dir, exist_ok=True)
    os.environ["XDG_CONFIG_HOME"] = runtime_dir
    os.environ["HOME"] = runtime_dir
    os.environ["TMPDIR"] = tmp_dir
    _configured_dir = runtime_dir
    return runtime_dir


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
        try:
            return original_run(self, *args, **kwargs)
        finally:
            _hub = None

    sh.SvcHub.run = run
    _patched = True


def configure_saf(roots_json):
    """Register the SAF mirror roots before copyparty is imported, then install the shim."""
    from . import saf

    count = saf.configure(roots_json)
    saf.install()
    return count


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


def start(conf_path, log_path="", runtime_dir="", saf_roots="[]"):
    """Start copyparty with the config at ``conf_path``. Returns "" or an error message."""
    global _thread, _error
    with _lock:
        if _thread is not None and _thread.is_alive():
            return ""
        if _hub is not None:
            return "a previous copyparty instance is still shutting down"
        if not conf_path or not os.path.isfile(conf_path):
            return "config file does not exist: " + str(conf_path)

        _error = None
        conf_path = os.path.abspath(conf_path)
        if not runtime_dir:
            runtime_dir = os.path.join(os.path.dirname(conf_path), "runtime")
        configure_environment(runtime_dir)
        _patch_signals()
        _patch_svchub()
        try:
            configure_saf(saf_roots)
        except Exception:
            # A broken SAF mirror must never stop a normal (path-based) server from starting.
            traceback.print_exc()

        argv = ["copyparty", "-c", conf_path, "--http-only", "--no-crt", "-q"]
        if log_path:
            argv += ["-lo", os.path.abspath(log_path), "--flo", "2"]

        _thread = threading.Thread(target=_run, args=(argv,), name="copyparty-main", daemon=True)
        _thread.start()
    return ""


def stop(timeout=8.0):
    """Ask copyparty to shut down. Returns True when it was running."""
    global _hub, _thread
    with _lock:
        hub = _hub
        thread = _thread
    if hub is None:
        return False

    # SvcHub.run() parks itself in SvcHub._signal_thr() waiting for a signal. shutdown() ends in
    # sys.exit(), so it must run on that thread: deliver SIGTERM through the hub's own handler, the
    # same path a real signal takes. Calling shutdown() from here would set the stopping flag but
    # leave the run() thread blocked in sig.get() forever.
    import signal

    try:
        hub.signal_handler(signal.SIGTERM, None)
    except BaseException:
        try:
            hub.shutdown()
        except BaseException:
            pass

    if thread is not None:
        thread.join(timeout)
    with _lock:
        _hub = None
        _thread = None
    return True


def is_alive():
    return _thread is not None and _thread.is_alive() and not _error


def last_error():
    return _error or ""
