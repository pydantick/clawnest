"""Model registry. Per-model knobs differ — thinking/effort are gated by model."""
from __future__ import annotations

from dataclasses import dataclass

DEFAULT_MODEL = "claude-opus-4-8"


@dataclass(frozen=True)
class ModelSpec:
    id: str
    label: str
    adaptive_thinking: bool   # adaptive thinking supported (Claude 4.6+); Haiku 4.5 -> False
    effort: bool              # output_config.effort supported; 400s on Haiku 4.5
    max_output: int           # sane streaming output cap (<= model's hard limit)


# IDs verified against the Claude API model catalog (mid-2026).
MODELS: dict[str, ModelSpec] = {
    "claude-opus-4-8":   ModelSpec("claude-opus-4-8",   "Opus 4.8",   True,  True,  32000),
    "claude-sonnet-4-6": ModelSpec("claude-sonnet-4-6", "Sonnet 4.6", True,  True,  16000),
    "claude-haiku-4-5":  ModelSpec("claude-haiku-4-5",  "Haiku 4.5",  False, False, 8000),
}


def resolve(model: str | None) -> ModelSpec:
    return MODELS.get(model or DEFAULT_MODEL, MODELS[DEFAULT_MODEL])
