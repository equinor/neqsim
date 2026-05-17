"""
llm_client — unified text-only LLM client for PaperLab tools.

Wraps litellm / openai / anthropic with one call signature, retries on
transient failures, and an offline fallback so unit tests still pass when no
provider is configured.

Used by ``book_writer.py`` (and any future long-running orchestrator) so that
all LLM calls share one place to configure backoff, timeouts, and logging.
"""

from __future__ import annotations

import json
import os
import subprocess
import time
import uuid
from pathlib import Path
from typing import List, Dict, Optional


_HAS_LITELLM = False
_HAS_OPENAI = False
_HAS_ANTHROPIC = False
_HAS_REQUESTS = False
try:
    import requests  # type: ignore
    _HAS_REQUESTS = True
except ImportError:
    requests = None  # type: ignore

# --- Key-free providers ----------------------------------------------------
# "github"          -> GitHub Models REST, authenticated via `gh auth token`.
#                      One-time `gh auth login`; no API key in env.
# "copilot-bridge"  -> File-exchange: paperflow writes prompts to
#                      .llm_bridge/pending/<id>.json; an in-IDE agent
#                      (e.g. VS Code Copilot Chat) writes the answer to
#                      .llm_bridge/done/<id>.json. Lets the running Copilot
#                      session BE the LLM, no API key, no extra cost beyond
#                      the existing Copilot license.

_GITHUB_MODELS_URL = "https://models.github.ai/inference/chat/completions"
_BRIDGE_DEFAULT_DIR = ".llm_bridge"
_BRIDGE_POLL_SECONDS = 2.0
_BRIDGE_DEFAULT_TIMEOUT = 1800.0  # 30 min — agent has time to draft a long section

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
    """Return True if at least one provider is usable.

    Includes the SDK-based providers (litellm/openai/anthropic) and the two
    key-free providers (`github` via gh CLI, `copilot-bridge` via file
    exchange).
    """
    if _HAS_LITELLM or _HAS_OPENAI or _HAS_ANTHROPIC:
        return True
    # github provider needs gh CLI + token
    if _gh_token() is not None and _HAS_REQUESTS:
        return True
    # copilot-bridge always available (filesystem only)
    return True


# --- gh CLI helpers --------------------------------------------------------

def _gh_token() -> Optional[str]:
    """Return GitHub auth token via `gh auth token`, or None."""
    try:
        out = subprocess.run(
            ["gh", "auth", "token"],
            capture_output=True, text=True, timeout=5,
        )
        token = (out.stdout or "").strip()
        return token or None
    except Exception:
        return None


def _github_models_call(
    messages: List[Dict[str, str]],
    *,
    model: str,
    max_tokens: int,
    temperature: float,
    timeout: float,
) -> str:
    if not _HAS_REQUESTS:
        raise LLMError("`requests` is required for the github provider. `pip install requests`.")
    token = _gh_token()
    if not token:
        raise LLMError(
            "github provider needs `gh auth login` first (one-time, uses your "
            "GitHub credentials — no API key needed)."
        )
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    body = {
        "model": model,
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
    }
    resp = requests.post(_GITHUB_MODELS_URL, headers=headers, json=body, timeout=timeout)
    if resp.status_code >= 400:
        raise LLMError(f"GitHub Models HTTP {resp.status_code}: {resp.text[:400]}")
    data = resp.json()
    try:
        return data["choices"][0]["message"]["content"] or ""
    except (KeyError, IndexError, TypeError) as exc:
        raise LLMError(f"Unexpected GitHub Models response: {data!r}") from exc


# --- copilot-bridge (file exchange) ----------------------------------------

def _bridge_dir() -> Path:
    root = os.environ.get("PAPERLAB_BRIDGE_DIR") or _BRIDGE_DEFAULT_DIR
    p = Path(root)
    (p / "pending").mkdir(parents=True, exist_ok=True)
    (p / "done").mkdir(parents=True, exist_ok=True)
    return p


def _bridge_call(
    messages: List[Dict[str, str]],
    *,
    model: str,
    max_tokens: int,
    temperature: float,
    timeout: float,
) -> str:
    """Write a prompt for an external agent (e.g. VS Code Copilot Chat) and
    poll for its reply. The agent must write a JSON file with a ``content``
    field to ``<bridge_dir>/done/<id>.json``."""
    bridge = _bridge_dir()
    job_id = uuid.uuid4().hex[:12]
    pending_path = bridge / "pending" / f"{job_id}.json"
    done_path = bridge / "done" / f"{job_id}.json"
    payload = {
        "id": job_id,
        "model": model,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "messages": messages,
    }
    pending_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(
        f"[copilot-bridge] Awaiting reply at {done_path} "
        f"(timeout {int(timeout)}s, poll {_BRIDGE_POLL_SECONDS}s).",
        flush=True,
    )
    deadline = time.time() + max(timeout, _BRIDGE_DEFAULT_TIMEOUT)
    while time.time() < deadline:
        if done_path.exists():
            try:
                data = json.loads(done_path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                # treat plain text as the content
                return done_path.read_text(encoding="utf-8")
            content = data.get("content") if isinstance(data, dict) else None
            if isinstance(content, str):
                return content
            return json.dumps(data)
        time.sleep(_BRIDGE_POLL_SECONDS)
    raise LLMError(f"copilot-bridge: no reply at {done_path} within {timeout}s.")


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
            "No LLM provider available. Options: "
            "(1) `pip install litellm` and set OPENAI_API_KEY (or similar); "
            "(2) `gh auth login` and use --provider github (no API key); "
            "(3) use --provider copilot-bridge to delegate to a running "
            "VS Code Copilot Chat agent (no API key, no SDK)."
        )

    last_err: Optional[Exception] = None
    sleep_s = backoff
    for attempt in range(retries):
        try:
            if provider in ("github", "copilot"):
                return _github_models_call(
                    messages,
                    model=model,
                    max_tokens=max_tokens,
                    temperature=temperature,
                    timeout=timeout,
                )

            if provider in ("copilot-bridge", "bridge", "agent"):
                # File-exchange — single attempt; retries don't help here.
                return _bridge_call(
                    messages,
                    model=model,
                    max_tokens=max_tokens,
                    temperature=temperature,
                    timeout=max(timeout, _BRIDGE_DEFAULT_TIMEOUT),
                )

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
