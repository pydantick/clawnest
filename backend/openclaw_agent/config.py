"""Runtime configuration: loaded from /etc/openclaw/config.json + env overrides."""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, fields

DEFAULT_CONFIG_PATH = "/etc/openclaw/config.json"


@dataclass
class Config:
    bind_host: str = "10.13.37.1"          # WireGuard interface — never 0.0.0.0
    bind_port: int = 8765
    token: str = ""                         # pairing token (device second factor over WG)
    api_key: str | None = None              # Claude key (may also arrive at runtime from the app)
    key_type: str = "api"                   # "api" (sk-ant-api...) | "oauth" (sk-ant-oat..., subscription)
    default_model: str = "claude-opus-4-8"
    personas_dir: str = "/etc/openclaw/personas"
    audit_path: str = "/var/lib/openclaw/audit.jsonl"
    max_output_chars: int = 60000           # truncate huge tool output before it hits the context window


def load_config(path: str | None = None) -> Config:
    path = path or os.environ.get("OPENCLAW_CONFIG", DEFAULT_CONFIG_PATH)
    data: dict = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)

    known = {f.name for f in fields(Config)}
    cfg = Config(**{k: v for k, v in data.items() if k in known})

    # Env overrides (systemd EnvironmentFile / SSH-bootstrap). Secrets live here, not in config.json.
    cfg.token = os.environ.get("OPENCLAW_TOKEN", cfg.token)
    cfg.api_key = os.environ.get("OPENCLAW_ANTHROPIC_API_KEY") or cfg.api_key
    cfg.key_type = os.environ.get("OPENCLAW_KEY_TYPE", cfg.key_type)
    return cfg
