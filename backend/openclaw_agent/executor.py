"""Bash execution as root, in its own process group, with whole-tree kill.

Every command runs via setsid (start_new_session=True) so it gets its own process
group. The Stop button kills the *group*, not just the shell — so backgrounded
children (nmap, docker run, `&` jobs) die too.
"""
from __future__ import annotations

import asyncio
import os
import signal


class CommandRunner:
    def __init__(self, max_output_chars: int = 60000):
        self.max_output_chars = max_output_chars
        self._proc: asyncio.subprocess.Process | None = None
        self._pgid: int | None = None

    async def run(self, command: str) -> tuple[int, str]:
        self._proc = await asyncio.create_subprocess_exec(
            "/bin/bash", "-lc", command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            start_new_session=True,  # own session + process group
        )
        try:
            self._pgid = os.getpgid(self._proc.pid)
        except ProcessLookupError:
            self._pgid = None
        try:
            stdout, _ = await self._proc.communicate()
        finally:
            code = self._proc.returncode if self._proc else -1
            self._proc = None
            self._pgid = None
        out = stdout.decode("utf-8", "replace") if stdout else ""
        return (code if code is not None else -1), self._truncate(out)

    def _truncate(self, out: str) -> str:
        if len(out) <= self.max_output_chars:
            return out
        half = self.max_output_chars // 2
        omitted = len(out) - 2 * half
        return f"{out[:half]}\n\n... [{omitted} символов опущено] ...\n\n{out[-half:]}"

    def kill(self) -> None:
        """Best-effort instant kill of the whole process group."""
        pgid = self._pgid
        if pgid is None:
            return
        for sig in (signal.SIGTERM, signal.SIGKILL):
            try:
                os.killpg(pgid, sig)
            except (ProcessLookupError, PermissionError):
                return
