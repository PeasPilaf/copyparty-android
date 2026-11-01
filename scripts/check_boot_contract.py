#!/usr/bin/env python3
"""Exercise the app's Python boot shim against a normal CPython.

`app/src/main/python/cppandroid/boot.py` is what starts and stops copyparty inside the Android
process. It is ordinary pure Python, so CI can run it on the build machine: this test starts a real
copyparty in this process, checks it serves HTTP, stops it (the SvcHub shutdown path), and starts it
again on the same port. That covers the lifecycle bugs the emulator would only hit by accident.
"""
import os
import socket
import sys
import tempfile
import time
import urllib.error
import urllib.request

# Importing the app's Python tree must not leave __pycache__ next to the sources that Chaquopy
# packages into the APK.
sys.dont_write_bytecode = True

PORT = 39601
TIMEOUT_S = 60.0


def wait_for_port(port: int, timeout: float = TIMEOUT_S) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=1):
                return True
        except OSError:
            time.sleep(0.2)
    return False


def get(url: str):
    with urllib.request.urlopen(url, timeout=15) as response:
        return response.status, response.read().decode("utf-8", "replace")


def wait_for_http(url: str, needle: str, timeout: float = TIMEOUT_S):
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        try:
            status, body = get(url)
            if status == 200 and needle in body:
                return body
            last = f"status={status}"
        except Exception as error:  # noqa: BLE001 - diagnostic only
            last = repr(error)
            time.sleep(0.3)
    raise AssertionError(f"never got {needle!r} from {url} ({last})")


def main() -> int:
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    sys.path.insert(0, os.path.join(repo, "app", "src", "main", "python"))

    import cppandroid.boot as boot

    work = tempfile.mkdtemp(prefix="boot-contract-")
    share = os.path.join(work, "share")
    os.makedirs(share)
    with open(os.path.join(share, "hello.txt"), "w") as handle:
        handle.write("hello from the boot contract test\n")

    conf = os.path.join(work, "copyparty.conf")
    with open(conf, "w") as handle:
        handle.write(
            "[global]\n"
            f"  p: {PORT}\n"
            "  i: 127.0.0.1\n"
            "  http-only\n"
            "  no-crt\n"
            "  no-robots\n"
            "  q\n"
            "  no-voldump\n"
            "\n"
            "[/share]\n"
            f"  {share}\n"
            "  accs:\n"
            "    r: *\n"
        )

    log = os.path.join(work, "server.log")
    runtime = os.path.join(work, "runtime")
    failures = []

    def check(name, ok, detail=""):
        print(("PASS  " if ok else "FAIL  ") + name + ("" if ok else f"  [{detail}]"))
        if not ok:
            failures.append(name)

    try:
        error = boot.start(conf, log, runtime)
        check("boot.start accepts the config", error == "", error)
        if error:
            return 1

        check("copyparty is listening", wait_for_port(PORT))
        wait_for_http(f"http://127.0.0.1:{PORT}/share/", "hello.txt")
        check("copyparty serves the shared folder", True)
        check("boot.is_alive is true while running", boot.is_alive())
        check("runtime state lands in the app-private dir", os.path.isdir(runtime))

        check("boot.stop returns true for a running server", boot.stop())
        deadline = time.time() + 10
        while boot.is_alive() and time.time() < deadline:
            time.sleep(0.2)
        check("the run thread exits after stop", not boot.is_alive())

        error = boot.start(conf, log, runtime)
        check("restart after stop is accepted", error == "", error)
        if not error:
            wait_for_http(f"http://127.0.0.1:{PORT}/share/", "hello.txt")
            check("restarted server serves the shared folder", True)
            check("restarted server can be stopped", boot.stop())
    finally:
        if boot.is_alive():
            boot.stop(1.0)
        print(f"workdir: {work}")

    if failures:
        print(f"\n{len(failures)} boot check(s) failed", file=sys.stderr)
        try:
            print(open(log).read(), file=sys.stderr)
        except OSError:
            pass
        return 1
    print("\nall boot lifecycle checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
