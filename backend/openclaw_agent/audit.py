"""Append-only, hash-chained audit log of every command the agent executes.

Each record links to the previous via prev_hash, so deletion or edits of earlier
records become detectable (the chain breaks). This does not stop a root attacker
from rewriting the whole file, but it makes silent tampering detectable and gives
the phone a basis to keep an independent copy (see PROTOCOL.md, get_audit).
"""
from __future__ import annotations

import hashlib
import json
import os
import time

_GENESIS = "0" * 64


class AuditLog:
    def __init__(self, path: str):
        self.path = path
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        self._last_hash = self._read_last_hash()

    def _read_last_hash(self) -> str:
        if not os.path.exists(self.path):
            return _GENESIS
        last = None
        with open(self.path, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    last = line
        if last:
            try:
                return json.loads(last)["hash"]
            except (json.JSONDecodeError, KeyError):
                pass
        return _GENESIS

    def record(self, *, persona: str, command: str, exit_code: int, output: str) -> dict:
        entry = {
            "ts": time.time(),
            "persona": persona,
            "command": command,
            "exit_code": exit_code,
            "output_sha256": hashlib.sha256(output.encode("utf-8", "replace")).hexdigest(),
            "prev_hash": self._last_hash,
        }
        body = json.dumps(entry, sort_keys=True, ensure_ascii=False)
        entry["hash"] = hashlib.sha256((self._last_hash + body).encode("utf-8")).hexdigest()
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
        self._last_hash = entry["hash"]
        return entry

    def tail(self, limit: int = 50) -> list[dict]:
        if not os.path.exists(self.path):
            return []
        with open(self.path, encoding="utf-8") as f:
            lines = [ln for ln in f if ln.strip()]
        out: list[dict] = []
        for ln in lines[-limit:]:
            try:
                out.append(json.loads(ln))
            except json.JSONDecodeError:
                continue
        return out
