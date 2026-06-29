"""WebSocket server + message protocol. Binds to the WireGuard interface only.

See PROTOCOL.md for the wire format. Connection is already encrypted + mutually
authenticated by WireGuard; the pairing token is a device-binding second factor.
"""
from __future__ import annotations

import asyncio
import json
import os
import re

import websockets
from anthropic import AsyncAnthropic

from .agent import rename_project, run_turn
from .audit import AuditLog
from .config import Config, load_config
from .executor import CommandRunner
from .models import DEFAULT_MODEL, MODELS, resolve
from .personas import load_personas

# OAuth/subscription tokens (sk-ant-oat...) go on Authorization: Bearer and need this beta header.
_OAUTH_BETA = "oauth-2025-04-20"

# Server-side persistence of the *displayed* chat history + the user's project list, so a
# fresh app install (or a new phone) restores everything from the VPS. Keyed by the app's
# thread key ("<project>:<persona>"). The Claude Code context itself is persisted separately
# in agent.py (sessions.json); this is the human-readable transcript the UI shows.
_STATE_DIR = "/var/lib/openclaw"
_TRANSCRIPTS_FILE = os.path.join(_STATE_DIR, "transcripts.json")
_PROJECTS_FILE = os.path.join(_STATE_DIR, "projects.json")
_MAX_MSGS_PER_CONV = 1000


def _load_json(path: str, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def _save_json(path: str, data) -> None:
    try:
        os.makedirs(_STATE_DIR, exist_ok=True)
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        os.replace(tmp, path)
    except Exception:
        pass


_TRANSCRIPTS: dict = _load_json(_TRANSCRIPTS_FILE, {})  # "<project>:<persona>" -> [message, ...]
_PROJECTS: list = _load_json(_PROJECTS_FILE, [])        # [{id,name,cwd,shared}, ...]

# Proactive push: agents append lines to outbox.jsonl (via the openclaw-notify CLI) when a
# background monitor/event fires; a watcher forwards new lines to the connected app(s) and
# records them in history. _OUTBOX_OFF tracks how many lines have been delivered.
_OUTBOX_FILE = os.path.join(_STATE_DIR, "outbox.jsonl")
_OFFSET_FILE = os.path.join(_STATE_DIR, "outbox.offset")
_CLIENTS: set = set()  # active per-connection send() callables


def _read_offset() -> int:
    try:
        with open(_OFFSET_FILE, encoding="utf-8") as f:
            return int((f.read() or "0").strip() or "0")
    except Exception:
        return 0


def _write_offset(n: int) -> None:
    try:
        os.makedirs(_STATE_DIR, exist_ok=True)
        with open(_OFFSET_FILE, "w", encoding="utf-8") as f:
            f.write(str(n))
    except Exception:
        pass


def _record_push(conv: str, text: str) -> None:
    if not conv:
        return
    lst = _TRANSCRIPTS.setdefault(conv, [])
    lst.append({"role": "assistant", "segs": [{"t": "text", "text": text}]})
    if len(lst) > _MAX_MSGS_PER_CONV:
        del lst[: len(lst) - _MAX_MSGS_PER_CONV]
    _save_json(_TRANSCRIPTS_FILE, _TRANSCRIPTS)


async def _outbox_watcher() -> None:
    """Forward new outbox lines to connected clients. Only advances the offset while at
    least one client is connected, so messages queued while the phone is away are delivered
    on the next connect (and stored in history regardless)."""
    while True:
        await asyncio.sleep(1.0)
        if not _CLIENTS:
            continue
        try:
            with open(_OUTBOX_FILE, encoding="utf-8") as f:
                lines = f.read().splitlines()
        except FileNotFoundError:
            continue
        except Exception:
            continue
        offset = _read_offset()
        if offset >= len(lines):
            continue
        for line in lines[offset:]:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except Exception:
                continue
            conv, text = str(rec.get("conv", "")), str(rec.get("text", ""))
            if not text:
                continue
            _record_push(conv, text)
            payload = {"type": "push", "conv": conv, "text": text}
            for snd in list(_CLIENTS):
                try:
                    await snd(payload)
                except Exception:
                    pass
        _write_offset(len(lines))


def _detect_key_type(key: str) -> str:
    return "oauth" if key.startswith("sk-ant-oat") else "api"


def _make_client(key: str, key_type: str) -> AsyncAnthropic:
    if key_type == "oauth":
        # Subscription/OAuth key. Experimental: verify against current API behavior.
        return AsyncAnthropic(auth_token=key, default_headers={"anthropic-beta": _OAUTH_BETA})
    return AsyncAnthropic(api_key=key)


class Session:
    def __init__(self, cfg: Config, personas: dict, audit: AuditLog):
        self.cfg = cfg
        self.personas = personas
        self.audit = audit
        self.authed = False
        self.api_key = cfg.api_key
        self.key_type = cfg.key_type
        self.client: AsyncAnthropic | None = None
        self.histories: dict[str, list] = {}
        self.runner: CommandRunner | None = None
        self.current_task: asyncio.Task | None = None
        self.stop_flag = False
        # Claude Code SDK session ids per conversation (multi-turn context) + live client
        self.sdk_sessions: dict[str, str] = {}
        self.sdk_client = None

    def ensure_client(self) -> AsyncAnthropic | None:
        if self.client is None and self.api_key:
            self.client = _make_client(self.api_key, self.key_type)
        return self.client


async def _handle_prompt(sess: Session, msg: dict, send) -> None:
    persona = sess.personas.get(msg.get("persona", "")) or next(iter(sess.personas.values()))
    spec = resolve(msg.get("model") or persona.get("default_model") or DEFAULT_MODEL)
    conv = str(msg.get("conversation_id", persona.get("id", "default")))
    project = str(msg.get("project", "default"))
    cwd = msg.get("cwd")
    user_text = msg.get("text", "")

    sess.stop_flag = False
    try:
        await run_turn(sess, persona, spec, user_text, conv, project, cwd, send, lambda: sess.stop_flag)
    except asyncio.CancelledError:
        # Interrupted or superseded by a new prompt. Stay silent: the interrupt handler
        # already notifies the app, and sending "interrupted" here could land on the NEXT
        # turn (the cancellation completes after the new turn has started streaming).
        pass
    except Exception as e:  # surface to the app rather than killing the connection
        await send({"type": "error", "message": f"{type(e).__name__}: {e}"})


async def handle(ws, cfg: Config, personas: dict, audit: AuditLog) -> None:
    sess = Session(cfg, personas, audit)

    async def send(obj: dict) -> None:
        await ws.send(json.dumps(obj, ensure_ascii=False))

    try:
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                await send({"type": "error", "message": "Невалидный JSON"})
                continue
            mtype = msg.get("type")

            if mtype == "auth":
                if cfg.token and msg.get("token") == cfg.token:
                    sess.authed = True
                    _CLIENTS.add(send)  # eligible to receive proactive pushes
                    await send({"type": "auth_ok",
                                "models": [{"id": m.id, "label": m.label} for m in MODELS.values()]})
                else:
                    await send({"type": "error", "message": "Неверный токен"})
                    await ws.close()
                    return
                continue

            if not sess.authed:
                await send({"type": "error", "message": "Требуется авторизация"})
                continue

            if mtype == "set_api_key":
                key = msg.get("key", "")
                sess.api_key = key
                sess.key_type = msg.get("key_type") or _detect_key_type(key)
                sess.client = None
                await send({"type": "ok", "message": "Ключ принят", "key_type": sess.key_type})

            elif mtype == "list_personas":
                await send({"type": "personas", "personas": list(personas.values())})

            elif mtype == "save_persona":
                p = msg.get("persona") or {}
                pid = str(p.get("id") or "").strip()
                if pid:
                    safe = re.sub(r"[^a-z0-9_-]", "_", pid.lower())[:64] or "agent"
                    os.makedirs(cfg.personas_dir, exist_ok=True)
                    with open(os.path.join(cfg.personas_dir, f"{safe}.json"), "w", encoding="utf-8") as f:
                        json.dump(p, f, ensure_ascii=False, indent=2)
                    personas.clear()
                    personas.update(load_personas(cfg.personas_dir))
                await send({"type": "personas", "personas": list(personas.values())})

            elif mtype == "rename_project":
                old = str(msg.get("old", ""))
                new = str(msg.get("new", "")).strip()
                if old and new and old != new:
                    rename_project(old, new)
                await send({"type": "ok", "message": "renamed"})

            elif mtype == "get_audit":
                await send({"type": "audit", "entries": audit.tail(int(msg.get("limit", 50)))})

            elif mtype == "save_message":
                conv = str(msg.get("conversation_id", ""))
                m = msg.get("message")
                if conv and isinstance(m, dict):
                    lst = _TRANSCRIPTS.setdefault(conv, [])
                    lst.append(m)
                    if len(lst) > _MAX_MSGS_PER_CONV:
                        del lst[: len(lst) - _MAX_MSGS_PER_CONV]
                    _save_json(_TRANSCRIPTS_FILE, _TRANSCRIPTS)

            elif mtype == "save_projects":
                projs = msg.get("projects")
                if isinstance(projs, list):
                    _PROJECTS[:] = projs  # mutate in place (no global rebind needed)
                    _save_json(_PROJECTS_FILE, _PROJECTS)

            elif mtype == "clear_conversation":
                conv = str(msg.get("conversation_id", ""))
                if conv and conv in _TRANSCRIPTS:
                    _TRANSCRIPTS.pop(conv, None)
                    _save_json(_TRANSCRIPTS_FILE, _TRANSCRIPTS)

            elif mtype == "get_state":
                await send({"type": "state", "projects": _PROJECTS, "conversations": _TRANSCRIPTS})

            elif mtype == "interrupt":
                sess.stop_flag = True
                if sess.runner:
                    sess.runner.kill()
                if sess.current_task and not sess.current_task.done():
                    sess.current_task.cancel()
                await send({"type": "interrupted"})

            elif mtype == "prompt":
                # Supersede any turn that's still winding down (e.g. just interrupted, but its
                # SDK subprocess cleanup hasn't finished) instead of rejecting the new prompt.
                if sess.current_task and not sess.current_task.done():
                    sess.stop_flag = True
                    if sess.runner:
                        sess.runner.kill()
                    sess.current_task.cancel()
                    try:
                        await asyncio.wait_for(
                            asyncio.gather(sess.current_task, return_exceptions=True), timeout=8
                        )
                    except Exception:
                        pass
                if not sess.api_key:
                    await send({"type": "error", "message": "Claude-ключ не задан"})
                    continue
                sess.stop_flag = False
                sess.current_task = asyncio.create_task(_handle_prompt(sess, msg, send))

            else:
                await send({"type": "error", "message": f"Неизвестный тип: {mtype}"})
    except websockets.ConnectionClosed:
        pass
    finally:
        _CLIENTS.discard(send)
        if sess.runner:
            sess.runner.kill()
        if sess.current_task and not sess.current_task.done():
            sess.current_task.cancel()


async def main() -> None:
    cfg = load_config()
    personas = load_personas(cfg.personas_dir)
    audit = AuditLog(cfg.audit_path)

    async def handler(ws):
        await handle(ws, cfg, personas, audit)

    # Keep pinging every 20s for NAT/keepalive, but tolerate a long pong delay
    # (60s): during a heavy turn the event loop or the SSH tunnel can stall briefly,
    # and a tight 20s pong timeout would wrongly drop a healthy in-flight turn.
    async with websockets.serve(handler, cfg.bind_host, cfg.bind_port,
                                max_size=8 * 1024 * 1024, ping_interval=20, ping_timeout=60):
        asyncio.create_task(_outbox_watcher())  # deliver proactive notifications
        print(f"OpenClaw agent listening on ws://{cfg.bind_host}:{cfg.bind_port}", flush=True)
        await asyncio.Future()  # run forever


def run() -> None:
    asyncio.run(main())
