#!/usr/bin/env python3
"""Create a throwaway folder tree and the AppState JSON that describes it.

Usage: make_contract_state.py <workdir> > <workdir>/state.json

The CI contract job pipes this JSON through the app's own Kotlin renderer, boots real copyparty
with the result and then checks the HTTP behaviour with check_server_contract.py.

Three shares cover the three visibility promises the app makes: a public folder (anonymous read), a
private folder (one account, password required) and a folder nobody was granted (no ``accs:`` line,
so copyparty must hide it from everyone).
"""
import json
import os
import sys

PORT = 39501


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: make_contract_state.py <workdir>", file=sys.stderr)
        return 2
    work = os.path.abspath(sys.argv[1])
    public = os.path.join(work, "public")
    private = os.path.join(work, "private")
    nogrant = os.path.join(work, "nogrant")
    os.makedirs(public, exist_ok=True)
    os.makedirs(private, exist_ok=True)
    os.makedirs(nogrant, exist_ok=True)
    with open(os.path.join(public, "hello.txt"), "w") as f:
        f.write("hello from copyparty\n")
    with open(os.path.join(private, "secret.txt"), "w") as f:
        f.write("top secret\n")
    with open(os.path.join(nogrant, "nobody.txt"), "w") as f:
        f.write("nobody should ever see this\n")

    state = {
        "version": 1,
        "users": [{"name": "alice", "password": "hunter2"}],
        "shares": [
            {
                "id": "public",
                "name": "Public",
                "urlPath": "public",
                "localPath": public,
                "enabled": True,
                "grants": [{"principal": "*", "permissions": ["VIEW"]}],
                # Expert surface: per-volume advanced flags must survive the renderer and remain
                # accepted by the real server the contract job boots.
                "extraVolFlags": "nodupe",
            },
            {
                "id": "private",
                "name": "Private",
                "urlPath": "private",
                "localPath": private,
                "enabled": True,
                "grants": [{"principal": "alice", "permissions": ["VIEW", "UPLOAD", "DELETE"]}],
            },
            {
                "id": "nogrant",
                "name": "Nobody",
                "urlPath": "nogrant",
                "localPath": nogrant,
                "enabled": True,
                "grants": [],
            },
        ],
        "settings": {
            "port": PORT,
            "lanAccess": False,
            "startOnBoot": False,
            "serverName": "contract-test",
            "theme": "SYSTEM",
            # Expert surface: raw global directives are appended to [global] and must boot.
            "expertMode": True,
            "extraArgs": "--no-logues",
        },
    }
    json.dump(state, sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
