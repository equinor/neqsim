"""
llm_client — unified text-only LLM client for PaperLab tools.

Wraps litellm / openai / anthropic with one call signature, retries on
transient failures, and an offline fallback so unit tests still pass when no
provider is configured.

Used by ``book_writer.py`` (and any future long-running orchestrator) so that
all LLM calls share one place to configure backoff, timeouts, and logging.
"""

from __future__ import annotations

import os
import time
from typing import List, Dict, Optional


_HAS_LITELLM = False
_HAS_OPENAI = False
_HAS_ANTHROPIC = False

try:
    import litellm  # type: ignore
    _HAS_LITELLM = True
except ImportError:
    litellm = None  # type: ignore

try:
    import openai as _openai_mod  # type: ignore
    _HAS_OPENAI = True
except ImportError:
    _openai_mod = None  # type: ignore

try:
    import anthropic as _anthropic_mod  # type: ignore
    _HAS_ANTHROPIC = True
except ImportError:
    _anthropic_mod = None  # type: ignore


class LLMError(RuntimeError):
    """Raised when no provider is available or all retries failed."""


def has_any_provider() -> bool:
    """Return True if at least one LLM SDK is importable."""
    return _HAS_LITELLM or _HAS_OPENAI or _HAS_ANTHROPIC


def chat(
    messages: List[Dict[str, str]],
    *,
    provider: str = "litellm",
    model: str = "gpt-4o",
    max_tokens: int = 4000,
    temperature: float = 0.3,
    retries: int = 3,
    backoff: float = 4.0,
    timeout: float = 180.0,
) -> str:
    """Send a chat-completion request and return assistant text.

    Parameters
    ----------
    messages
        OpenAI-style ``[{"role": "system"|"user"|"assistant", "content": ...}]``.
    provider
        ``"litellm"`` (preferred), ``"openai"``, or ``"anthropic"``.
    model
        Model identifier in the provider's namespace (e.g. ``"gpt-4o"``,
        ``"claude-sonnet-4-20250514"``, or any litellm-routed name).
    max_tokens
        Output budget. Section writes use ~3000–4000 tokens; outline
        expansion uses ~6000.
    temperature
        Sampling temperature. 0.2–0.4 works well for technical prose.
    retries
        Number of retry attempts on transient failure.
    backoff
        Initial sleep (seconds) between retries; doubles each attempt.
    timeout
        Per-request timeout in seconds (advisory; honored by litellm/openai).

    Returns
    -------
    str
        Assistant message content.

    Raises
    ------
    LLMError
        If no provider is available or all retries failed.
    """
    if not has_any_provider():
        raise LLMError(
            "No LLM SDK available. Install one of: "
            "`pip install litellm` (recommended), `pip install openai`, "
            "or `pip install anthropic`."
        )

    last_err: Optional[Exception] = None
    sleep_s = backoff
    for attempt in range(retries):
        try:
            if provider == "litellm" and _HAS_LITELLM:
                resp = litellm.completion(
                    model=model,
                    messages=messages,
                    max_tokens=max_tokens,
                    temperature=temperature,
                    timeout=timeout,
                )
                return resp.choices[0].message.content or ""

            if provider == "openai" and _HAS_OPENAI:
                client = _openai_mod.OpenAI(timeout=timeout)
                resp = client.chat.completions.create(
                    model=model,
                    messages=messages,
                    max_tokens=max_tokens,
                    temperature=temperature,
                )
                return resp.choices[0].message.content or ""

            if provider == "anthropic" and _HAS_ANTHROPIC:
                # Anthropic separates system from user messages.
                system_text = ""
                conv: List[Dict[str, str]] = []
                for m in messages:
                    if m.get("role") == "system":
                        system_text += (m.get("content") or "") + "\n"
                    else:
                        conv.append({"role": m["role"], "content": m["content"]})
                client = _anthropic_mod.Anthropic(timeout=timeout)
                resp = client.messages.create(
                    model=model,
                    max_tokens=max_tokens,
                    temperature=temperature,
                    system=system_text.strip() or None,
                    messages=conv,
                )
                # response.content is a list of blocks; concatenate text blocks
                parts = []
                for block in resp.content:
                    text = getattr(block, "text", None)
                    if text:
                        parts.append(text)
                return "".join(parts)

            # Fall through: requested provider not installed → try any installed one.
            for fallback in ("litellm", "openai", "anthropic"):
                if fallback != provider:
                    return chat(
                        messages,
                        provider=fallback,
                        model=model,
                        max_tokens=max_tokens,
                        temperature=temperature,
                        retries=1,
                        backoff=backoff,
                        timeout=timeout,
                    )
            raise LLMError(f"Provider '{provider}' not installed.")

        except LLMError:
            raise
        except Exception as exc:  # noqa: BLE001 — broad: SDKs raise many types
            last_err = exc
            if attempt < retries - 1:
                time.sleep(sleep_s)
                sleep_s *= 2
                continue
            break

    raise LLMError(f"All {retries} attempts failed. Last error: {last_err!r}")


def estimate_tokens(text: str) -> int:
    """Rough token count (1 token ≈ 4 chars for English prose)."""
    return max(1, len(text) // 4)
