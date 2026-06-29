"""Agentic loop, backed by the Claude Agent SDK (Claude Code).

Subscription OAuth tokens (sk-ant-oat...) are rejected by the raw anthropic SDK,
so we drive Claude Code via claude_agent_sdk. Plain API keys work here too.
Claude Code runs its own tools (Bash/Read/Write/...);
they're approved programmatically via `can_use_tool`, which (unlike
permission_mode="bypassPermissions" → --dangerously-skip-permissions) is allowed
to run as root.

Projects: each (project, agent) conversation keeps its own context. We persist the
conversation_id -> Claude Code session_id map to disk so the context survives
reconnects and restarts, and give each project its own working directory.
"""
from __future__ import annotations

import json
import os
import re
from typing import Awaitable, Callable

from claude_agent_sdk import (
    AssistantMessage,
    ClaudeAgentOptions,
    ClaudeSDKClient,
    PermissionResultAllow,
    ResultMessage,
    StreamEvent,
    ToolResultBlock,
    ToolUseBlock,
    UserMessage,
)

Send = Callable[[dict], Awaitable[None]]
ShouldStop = Callable[[], bool]

# Default toolset for a persona; "all_tools" personas get the full set incl. web.
_TOOLSET = ["Bash", "Read", "Write", "Edit", "Glob", "Grep"]
_ALL_TOOLS = _TOOLSET + ["MultiEdit", "WebFetch", "WebSearch", "Task", "TodoWrite", "NotebookEdit"]

# Appended to every persona's system prompt so two-way file/photo exchange works.
# Incoming: the app uploads attachments via SFTP and notes their absolute paths in the
# message as `[Вложение: /path]`. Outgoing: the agent writes a file and emits a marker
# the app parses into a downloadable attachment (images shown inline).
_FILE_PROTOCOL = (
    "\n\n## Файлы и фото\n"
    "Пользователь может прикреплять файлы — их абсолютные пути приходят в сообщении как "
    "`[Вложение: /путь]`. Открывай их инструментом Read (Read умеет читать и изображения, "
    "так что фото ты видишь).\n"
    "Чтобы ОТПРАВИТЬ пользователю файл или картинку — сохрани его на диск (любой абсолютный "
    "путь) и добавь в ответ на ОТДЕЛЬНОЙ строке маркер: `[[FILE:/абсолютный/путь]]`. "
    "Можно несколько маркеров. Никогда не вставляй base64 в текст — только маркер с путём."
)

# Lets the agent message the user proactively (after a background monitor/event fires),
# even with no active turn. It runs the openclaw-notify CLI; the server delivers it to the app.
_NOTIFY_PROTOCOL = (
    "\n\n## Уведомления (написать пользователю позже)\n"
    "Если нужно сообщить пользователю ПОЗЖЕ — когда сработает фоновый монитор/событие или "
    "завершится долгая задача — запусти фоновый процесс (`nohup ... &`) и в нужный момент вызови:\n"
    "  openclaw-notify \"текст сообщения\"\n"
    "Сообщение придёт пользователю прямо в этот чат, даже без его запроса. Текущий чат задан "
    "в переменной окружения $OPENCLAW_CONV — если пишешь отдельный скрипт-монитор, подставь её "
    "значение в команду явно: `openclaw-notify --conv \"<значение $OPENCLAW_CONV>\" \"текст\"`, "
    "иначе фоновый процесс может её не унаследовать."
)

# Our model ids -> Claude Code model aliases (per-agent model selection).
_MODEL_ALIAS = {
    "claude-opus-4-8": "opus",
    "claude-sonnet-4-6": "sonnet",
    "claude-haiku-4-5": "haiku",
}

_STATE_DIR = "/var/lib/openclaw"
_SESS_FILE = os.path.join(_STATE_DIR, "sessions.json")
_PROJECTS_DIR = os.path.join(_STATE_DIR, "projects")


def _load_sessions() -> dict:
    try:
        with open(_SESS_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _save_sessions(d: dict) -> None:
    try:
        os.makedirs(_STATE_DIR, exist_ok=True)
        tmp = _SESS_FILE + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(d, f)
        os.replace(tmp, _SESS_FILE)
    except Exception:
        pass


# conversation_id ("<project>:<persona>") -> Claude Code session_id. Persistent.
_SESSIONS: dict = _load_sessions()


def _project_cwd_path(project: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9_-]", "_", project or "default")[:64] or "default"
    return os.path.join(_PROJECTS_DIR, safe)


def _project_cwd(project: str) -> str | None:
    cwd = _project_cwd_path(project)
    try:
        os.makedirs(cwd, exist_ok=True)
        return cwd
    except Exception:
        return None


def rename_project(old: str, new: str) -> None:
    """Re-key persistent contexts (<old>:persona -> <new>:persona) and move the cwd."""
    changed = False
    for k in list(_SESSIONS.keys()):
        if k.startswith(old + ":"):
            _SESSIONS[new + k[len(old):]] = _SESSIONS.pop(k)
            changed = True
    if changed:
        _save_sessions(_SESSIONS)
    try:
        oc = _project_cwd_path(old)
        nc = _project_cwd_path(new)
        if os.path.isdir(oc) and not os.path.exists(nc):
            os.rename(oc, nc)
    except Exception:
        pass


def _model_for(spec) -> str | None:
    mid = getattr(spec, "id", None)
    if not mid:
        return None
    return _MODEL_ALIAS.get(mid, mid)


async def _allow_all(tool_name, tool_input, context):
    return PermissionResultAllow()


def _auth_env(api_key: str | None, key_type: str) -> dict[str, str]:
    if not api_key:
        return {}
    if key_type == "oauth":
        return {"CLAUDE_CODE_OAUTH_TOKEN": api_key}
    return {"ANTHROPIC_API_KEY": api_key}


def _tool_result_text(content) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for x in content:
            parts.append(x.get("text", "") if isinstance(x, dict) else str(x))
        return "".join(parts)
    return str(content)


def _short(x) -> str:
    s = str(x)
    return s if len(s) <= 200 else s[:200] + "…"


def _resolve_cwd(cwd: str | None, project: str) -> str | None:
    """Explicit user-given working dir if set, else the auto per-project dir."""
    if cwd and cwd.strip():
        p = os.path.expanduser(cwd.strip())
        try:
            os.makedirs(p, exist_ok=True)
        except Exception:
            pass
        return p
    return _project_cwd(project)


async def run_turn(session, persona, spec, user_text, conv, project, cwd, send: Send, should_stop: ShouldStop) -> str:
    """Run one user turn. `conv` keys the persistent context (project:persona);
    `cwd` is an optional explicit working dir (else auto under the project)."""
    cwd_path = _resolve_cwd(cwd, project)
    resume_id = _SESSIONS.get(conv)
    # The app displays threads per (project, persona); proactive notifications must target
    # that same key so they land in the right chat.
    notify_conv = f"{project}:{persona.get('id', '')}"
    try:
        return await _run_once(session, persona, spec, user_text, conv, cwd_path, resume_id, send, should_stop, notify_conv)
    except Exception:
        # Stale/invalid session (e.g. the project's working dir changed) makes
        # Claude Code fail at resume — drop it and retry fresh instead of erroring.
        if resume_id is not None:
            _SESSIONS.pop(conv, None)
            _save_sessions(_SESSIONS)
            return await _run_once(session, persona, spec, user_text, conv, cwd_path, None, send, should_stop, notify_conv)
        raise


async def _run_once(session, persona, spec, user_text, conv, cwd_path, resume_id, send: Send, should_stop: ShouldStop, notify_conv: str = "") -> str:
    options = ClaudeAgentOptions(
        system_prompt=(persona.get("system_prompt", "") or "") + _FILE_PROTOCOL + _NOTIFY_PROTOCOL,
        model=_model_for(spec),
        allowed_tools=(_ALL_TOOLS if persona.get("all_tools") else _TOOLSET),
        can_use_tool=_allow_all,
        include_partial_messages=True,
        env={**_auth_env(session.api_key, session.key_type),
             "OPENCLAW_CONV": notify_conv,
             "OPENCLAW_PERSONA": persona.get("id", "")},
        cwd=cwd_path,
        resume=resume_id,
    )
    used_model: str | None = None
    pending: dict = {}
    question_ids: set = set()  # AskUserQuestion tool ids — rendered as interactive cards, not raw blocks
    try:
        async with ClaudeSDKClient(options=options) as client:
            session.sdk_client = client
            await client.query(user_text)
            async for msg in client.receive_response():
                if should_stop():
                    return "interrupted"

                if isinstance(msg, StreamEvent):
                    ev = getattr(msg, "event", None)
                    if isinstance(ev, dict):
                        if ev.get("type") == "message_start":
                            m = (ev.get("message") or {}).get("model")
                            if m:
                                used_model = m
                        elif ev.get("type") == "content_block_delta":
                            await _delta(ev, send)

                elif isinstance(msg, AssistantMessage):
                    for b in msg.content:
                        if isinstance(b, ToolUseBlock):
                            inp = b.input if isinstance(b.input, dict) else {}
                            # AskUserQuestion: headless Claude Code can't prompt, so surface
                            # the question to the app as interactive options instead of a raw
                            # tool block (the app answers via a follow-up message).
                            if b.name == "AskUserQuestion":
                                question_ids.add(b.id)
                                await send({"type": "question", "id": b.id, "questions": inp.get("questions", [])})
                                continue
                            cmd = inp.get("command")
                            shown = cmd if cmd is not None else _short(inp)
                            try:
                                pending[b.id] = shown
                            except Exception:
                                pass
                            await send({"type": "tool_use", "tool": b.name, "command": shown})
                        # text/thinking already streamed via StreamEvent deltas

                elif isinstance(msg, UserMessage):
                    for b in getattr(msg, "content", []):
                        if isinstance(b, ToolResultBlock):
                            tid = getattr(b, "tool_use_id", None)
                            if tid in question_ids:
                                question_ids.discard(tid)
                                continue  # suppress the auto "did not answer" result
                            out = _tool_result_text(b.content)
                            code = 1 if b.is_error else 0
                            cmd = pending.pop(tid, "?")
                            audit = getattr(session, "audit", None)
                            if audit is not None:
                                try:
                                    audit.record(persona=persona.get("id", "?"), command=cmd, exit_code=code, output=out)
                                except Exception:
                                    pass
                            await send({"type": "tool_result", "exit_code": code, "output": out})

                elif isinstance(msg, ResultMessage):
                    sid = getattr(msg, "session_id", None)
                    if sid:
                        _SESSIONS[conv] = sid
                        _save_sessions(_SESSIONS)
                    await send({
                        "type": "turn_end",
                        "stop_reason": "error" if getattr(msg, "is_error", False) else "end_turn",
                        "model": used_model,
                        "usage": {
                            "cost_usd": getattr(msg, "total_cost_usd", None),
                            "num_turns": getattr(msg, "num_turns", None),
                        },
                    })
    finally:
        session.sdk_client = None
    return "end_turn"


async def _delta(ev: dict, send: Send) -> None:
    delta = ev.get("delta") or {}
    dt = delta.get("type")
    if dt == "text_delta":
        await send({"type": "text_delta", "text": delta.get("text", "")})
    elif dt == "thinking_delta":
        await send({"type": "thinking_delta", "text": delta.get("thinking", "")})
