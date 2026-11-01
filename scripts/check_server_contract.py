#!/usr/bin/env python3
"""Boot real copyparty with the config the app rendered and assert the HTTP contract.

Usage: check_server_contract.py <workdir>

<workdir> must contain state.json (written by make_contract_state.py) and copyparty.conf (written by
the app's Kotlin renderer through its CLI). Every check maps to a promise the app makes to its user:
public folders are readable, private folders are not, a folder nobody was granted is invisible, and
the permissions the UI exposes actually work over HTTP/WebDAV.
"""
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

TIMEOUT_S = 60


def wait_for_port(port: int, timeout: float = TIMEOUT_S) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=1):
                return True
        except OSError:
            time.sleep(0.2)
    return False


def request(url: str, method: str = "GET", data: bytes | None = None, headers=None):
    req = urllib.request.Request(url, method=method, data=data, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check_server_contract.py <workdir>", file=sys.stderr)
        return 2
    work = os.path.abspath(sys.argv[1])
    conf = os.path.join(work, "copyparty.conf")
    with open(os.path.join(work, "state.json")) as f:
        state = json.load(f)
    port = int(state["settings"]["port"])
    base = f"http://127.0.0.1:{port}"

    log_path = os.path.join(work, "server.log")
    log = open(log_path, "w")
    proc = subprocess.Popen(
        [sys.executable, "-m", "copyparty", "-c", conf],
        stdout=log,
        stderr=subprocess.STDOUT,
    )

    failures: list[str] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        print(("PASS  " if ok else "FAIL  ") + name + ("" if ok else f"  [{detail}]"))
        if not ok:
            failures.append(name)

    try:
        if not wait_for_port(port):
            print(open(log_path).read(), file=sys.stderr)
            print(f"copyparty did not listen on port {port}", file=sys.stderr)
            return 1

        status, root_body = request(base + "/")
        check("volume list is served at /", status == 200 and "public" in root_body, f"status={status}")
        check(
            "a private volume is absent from the anonymous volume list",
            "/private/" not in root_body,
            f"status={status} body={root_body!r}",
        )
        check(
            "a volume with no grants is absent from the volume list",
            "/nogrant/" not in root_body,
            f"status={status} body={root_body!r}",
        )

        status, body = request(base + "/public/")
        check("public folder lists its files", status == 200 and "hello.txt" in body, f"status={status}")

        status, body = request(base + "/public/hello.txt")
        check("public file downloads", status == 200 and body == "hello from copyparty\n", f"status={status} body={body!r}")

        status, _ = request(base + "/private/")
        check("private folder is denied without a password", status in (401, 403), f"status={status}")

        status, body = request(base + "/private/?pw=hunter2")
        check("private folder opens with the password", status == 200 and "secret.txt" in body, f"status={status}")

        status, account_body = request(base + "/?pw=hunter2")
        check(
            "an account sees its private volume in the volume list",
            status == 200 and "/private/" in account_body,
            f"status={status} body={account_body!r}",
        )
        check(
            "a volume with no grants stays absent for an authenticated account",
            "/nogrant/" not in account_body,
            f"status={status} body={account_body!r}",
        )

        status, _ = request(base + "/nogrant/")
        check("a folder nobody was granted is not served", status in (401, 403), f"status={status}")

        status, _ = request(base + "/private/put-by-anon.txt", method="PUT", data=b"nope")
        check("anonymous upload is denied", status in (401, 403), f"status={status}")

        status, _ = request(
            base + "/private/put-by-alice.txt?pw=hunter2",
            method="PUT",
            data=b"uploaded by the contract test",
        )
        check("account upload is accepted", status in (200, 201, 204), f"status={status}")

        status, body = request(base + "/private/put-by-alice.txt?pw=hunter2")
        check("uploaded file reads back", status == 200 and body == "uploaded by the contract test", f"status={status}")

        status, _ = request(base + "/private/?pw=hunter2", method="PROPFIND", headers={"Depth": "0"})
        check("WebDAV is available on the same volume", status == 207, f"status={status}")

        status, _ = request(base + "/public/does-not-exist.txt")
        check("missing files 404", status == 404, f"status={status}")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
        log.close()

    if failures:
        print("\n--- server log ---", file=sys.stderr)
        print(open(log_path).read(), file=sys.stderr)
        print(f"\n{len(failures)} contract check(s) failed", file=sys.stderr)
        return 1
    print("\nall contract checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
