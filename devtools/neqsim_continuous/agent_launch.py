"""Build and start bounded, headless agent sessions (GitHub Copilot CLI by default).

The session may read the task and write only inside it; pushing, sending and posting are
denied. The command is recorded in ``cycle.json`` so a reviewer can see what ran.
"""

import os
import shutil
import subprocess

DEFAULT_DENY = ["shell(git push)", "shell(git commit)", "shell(gh pr create)"]


def build_command(task_dir, cycle_dir, prompt, agent="continuous-improvement",
                  executable="copilot", deny=None, extra_args=None, model=None):
    """Return the argv list for a headless agent session.

    Pin ``model``: a scheduled run otherwise uses the user's interactive default, and when that model is
    withdrawn the CLI exits with code 1 and every later cycle silently loses its review.
    """
    argv = [executable, "--agent", agent, "--silent", "--add-dir", str(task_dir),
            "--log-dir", os.path.join(str(cycle_dir), "agent_log")]
    if model:
        argv += ["--model", str(model)]
    for rule in (deny if deny is not None else DEFAULT_DENY):
        argv += ["--deny-tool", rule]
    argv += list(extra_args or [])
    argv += ["-p", prompt]
    return argv


def triage_prompt(cycle_id, triggers, daily=False):
    text = ("Triage cycle {} of this living task. Read continuous/cycles/{}/triggers.json and "
            "cycle.json. Triggers: {}. Write continuous/cycles/{}/agent_review.md and propose "
            "ledger items only through continuous/ledger/events.jsonl with status 'proposed'. "
            "Do not promote a baseline and do not write outside the task folder.").format(
                cycle_id, cycle_id, ", ".join(triggers), cycle_id)
    if daily:
        text += (" Also read and merge (never discard earlier items of the same day) today's entry "
                 "continuous/daily/<YYYY-MM-DD>.json (schema: `neqsim task-daily <task> --template`) "
                 "and continuous/daily/_standing.json; the Word daily report is built from them. "
                 "Answer first, plain language, numbers with units, second-hand information marked unverified.")
    text += (" If continuous/hypotheses.yaml exists, never set cause confidences by hand: append each new piece of "
             "evidence once as an observation with P(observation | each cause), a reliability and, for a repeat of "
             "the same mechanism, a family; then run `neqsim task-hypotheses <task>` and report its posterior and "
             "its ranked next test.")
    return text

def critic_prompt(state, reason):
    return ("Act as an independent critic of this living task. The solve loop reports '{}' "
            "({}). Read continuous/state.json and the latest cycle files only. Look for evidence "
            "AGAINST the conclusion: inputs reused as validation, unit or basis errors, gains from "
            "relaxed assumptions, missing constraints. Write continuous/critic_review.md.").format(
                state, reason)


def classify_failure(text):
    """Return a short reason for a failed headless session from its output, or an empty string."""
    lowered = (text or "").lower()
    if "is not available" in lowered and "model" in lowered:
        return "model_unavailable"
    if any(token in lowered for token in ("not logged in", "authentication", "unauthorized", "401", "log in")):
        return "not_authenticated"
    if any(token in lowered for token in ("rate limit", "quota", "429")):
        return "quota_or_rate_limit"
    return ""




# Tried after the configured model, in order, when the CLI reports it unavailable. Keep short and current.
DEFAULT_FALLBACK_MODELS = ["claude-sonnet-5", "gpt-5.6-sol"]


def launch(argv, timeout=1800, cwd=None):
    """Run the agent session; returns a result dict (with the reason and last output lines) and never raises."""
    if not shutil.which(argv[0]):
        return {"status": "not_installed", "command": argv[0]}
    try:
        completed = subprocess.run(argv, cwd=cwd, timeout=timeout, capture_output=True,
                                   text=True, encoding="utf-8", errors="replace")
        if completed.returncode == 0:
            return {"status": "ok", "returncode": 0}
        tail = ((completed.stderr or "") + "\n" + (completed.stdout or "")).strip()[-600:]
        return {"status": "fail", "returncode": completed.returncode,
                "reason": classify_failure(tail), "output_tail": tail}
    except subprocess.TimeoutExpired:
        return {"status": "timeout", "timeout_s": timeout}
    except OSError as error:
        return {"status": "fail", "message": str(error)}


def model_candidates(config):
    """Models to try in order: the configured one (or the CLI default), then the fallbacks.

    ``None`` means "no --model flag". Fallbacks are only used after a ``model_unavailable`` failure.
    """
    config = config or {}
    first = config.get("model")
    fallbacks = config.get("fallback_models")
    if fallbacks is None:
        fallbacks = DEFAULT_FALLBACK_MODELS
    candidates = [first]
    candidates += [m for m in fallbacks if m and m != first]
    return candidates


def run_with_fallback(task_dir, cycle_dir, prompt, config, agent=None, extra_args=None,
                      success=None, builder=None):
    """Run a headless review, falling back to other models when the model is unavailable.

    ``success`` is an optional callable returning True when the expected output file exists.
    Returns the last ``launch`` result plus ``model`` and ``attempts``; a failure is never silent: the
    result carries ``status`` != ``ok`` and a ``reason`` the caller turns into a visible trigger.
    """
    build = builder or build_command
    attempts, result = [], {"status": "fail", "reason": "no_attempt"}
    for model in model_candidates(config):
        argv = build(task_dir, cycle_dir, prompt, agent=agent or config.get("agent", "continuous-improvement"),
                     executable=config.get("executable", "copilot"), deny=config.get("deny"),
                     extra_args=extra_args, model=model)
        result = launch(argv, timeout=int(config.get("timeout_s", 1800)), cwd=str(task_dir))
        result["model"] = model or "default"
        result["command"] = argv
        if result["status"] == "ok" and success is not None and not success():
            result.update(status="fail", reason="no_output")
        attempts.append({"model": result["model"], "status": result["status"],
                         "reason": result.get("reason", "")})
        if result["status"] == "ok" or result.get("reason") != "model_unavailable":
            break
    result["attempts"] = attempts
    return result
