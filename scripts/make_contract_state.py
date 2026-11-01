#!/usr/bin/env python3
"""Create a throwaway folder tree and the AppState JSON that describes it.

Usage: make_contract_state.py <workdir> > <workdir>/state.json

The CI contract job pipes this JSON through the app's own Kotlin renderer, boots real copyparty
with the result and then checks the HTTP behaviour with check_server_contract.py.
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
    os.makedirs(public, exist_ok=True)
    os.makedirs(private, exist_ok=True)
    with open(os.path.join(public, "hello.txt"), "w") as f:
        f.write("hello from copyparty\n")
    with open(os.path.join(private, "secret.txt"), "w") as f:
        f.write("top secret\n")

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
            },
            {
                "id": "private",
                "name": "Private",
                "urlPath": "private",
                "localPath": private,
                "enabled": True,
                "grants": [{"principal": "alice", "permissions": ["VIEW", "UPLOAD", "DELETE"]}],
            },
        ],
        "settings": {
            "port": PORT,
            "lanAccess": False,
            "startOnBoot": False,
            "serverName": "contract-test",
            "theme": "SYSTEM",
        },
    }
    json.dump(state, sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
