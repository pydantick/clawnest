#!/usr/bin/env bash
# Provisioning script run by the app over SSH (as root) on the user's VPS.
# No WireGuard / no VPN: the app reaches the agent through an SSH local port-forward.
# Ensures the openclaw agent has a token (and optionally the Claude key), then prints
# the token + the agent's bind host/port as JSON for the app.
#
# The app injects CLAUDE_KEY by replacing the placeholder below before sending.
set -euo pipefail

CLAUDE_KEY='__CLAUDE_KEY__'
ENV_FILE=/etc/openclaw/openclaw.env
CONFIG=/etc/openclaw/config.json
AGENT_HOST=10.13.37.1
AGENT_PORT=8765

mkdir -p /etc/openclaw

# Where does the agent actually listen? (from config.json if present)
if [ -f "$CONFIG" ] && command -v python3 >/dev/null 2>&1; then
  AGENT_HOST="$(python3 -c 'import json;print(json.load(open("/etc/openclaw/config.json")).get("bind_host","10.13.37.1"))' 2>/dev/null || echo 10.13.37.1)"
  AGENT_PORT="$(python3 -c 'import json;print(json.load(open("/etc/openclaw/config.json")).get("bind_port",8765))' 2>/dev/null || echo 8765)"
fi

restart=0

# Token: reuse the agent's existing token, else generate one.
token=""
if [ -f "$ENV_FILE" ]; then
  token="$(grep -E '^OPENCLAW_TOKEN=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)"
fi
if [ -z "$token" ]; then
  token="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"
  echo "OPENCLAW_TOKEN=${token}" >> "$ENV_FILE"
  restart=1
fi

# Optional Claude key — set the key AND its type (api vs oauth), restart if changed.
if [ -n "$CLAUDE_KEY" ]; then
  case "$CLAUDE_KEY" in
    sk-ant-oat*) ktype=oauth ;;
    *)           ktype=api ;;
  esac
  cur_key="$(grep -E '^OPENCLAW_ANTHROPIC_API_KEY=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2- || true)"
  cur_type="$(grep -E '^OPENCLAW_KEY_TYPE=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2- || true)"
  if [ "$cur_key" != "$CLAUDE_KEY" ]; then
    if grep -q '^OPENCLAW_ANTHROPIC_API_KEY=' "$ENV_FILE" 2>/dev/null; then
      sed -i "s|^OPENCLAW_ANTHROPIC_API_KEY=.*|OPENCLAW_ANTHROPIC_API_KEY=${CLAUDE_KEY}|" "$ENV_FILE"
    else
      echo "OPENCLAW_ANTHROPIC_API_KEY=${CLAUDE_KEY}" >> "$ENV_FILE"
    fi
    restart=1
  fi
  if [ "$cur_type" != "$ktype" ]; then
    if grep -q '^OPENCLAW_KEY_TYPE=' "$ENV_FILE" 2>/dev/null; then
      sed -i "s|^OPENCLAW_KEY_TYPE=.*|OPENCLAW_KEY_TYPE=${ktype}|" "$ENV_FILE"
    else
      echo "OPENCLAW_KEY_TYPE=${ktype}" >> "$ENV_FILE"
    fi
    restart=1
  fi
fi

# Restart only if something changed, and WAIT until the agent is listening again
# (otherwise the app would connect into a dead socket = "connection reset").
if [ "$restart" = "1" ]; then
  systemctl restart openclaw-agent 2>/dev/null || true
  for _ in $(seq 1 40); do
    if ss -tln 2>/dev/null | grep -q ":${AGENT_PORT} "; then break; fi
    sleep 0.5
  done
fi

echo "===OPENCLAW-RESULT==="
echo "{\"token\": \"${token}\", \"agent_host\": \"${AGENT_HOST}\", \"agent_port\": ${AGENT_PORT}}"
echo "===END==="
