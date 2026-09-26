"""Build and start bounded, headless agent sessions (GitHub Copilot CLI by default).

The session may read the task and write only inside it; pushing, sending and posting are
denied. The command is recorded in ``cycle.json`` so a reviewer can see what ran.
"""

import os
import shutil
import subprocess

DEFAULT_DENY = ["shell(git push)", "shell(git commit)", "shell(gh pr create)"]


def build_command(task_dir, cycle_dir, prompt, agent="continuous-improvement",
                  executable="copilot", deny=None, extra_args=None):
    """Return the argv list for a headless agent session."""
    argv = [executable, "--agent", agent, "--silent", "--add-dir", str(task_dir),
            "--log-dir", os.path.join(str(cycle_dir), "agent_log")]
    for rule in (deny if deny is not None else DEFAULT_DENY):
        argv += ["--deny-tool", rule]
    argv += list(extra_args or [])
    argv += ["-p", prompt]
    return argv


def triage_prompt(cycle_id, triggers):
    return ("Triage cycle {} of this living task. Read continuous/cycles/{}/triggers.json and "
            "cycle.json. Triggers: {}. Write continuous/cycles/{}/agent_review.md and propose "
            "ledger items only through continuous/ledger/events.jsonl with status 'proposed'. "
            "Do not promote a baseline and do not write outside the task folder.").format(
                cycle_id, cycle_id, ", ".join(triggers), cycle_id)


def critic_prompt(state, reason):
    return ("Act as an independent critic of this living task. The solve loop reports '{}' "
            "({}). Read continuous/state.json and the latest cycle files only. Look for evidence "
            "AGAINST the conclusion: inputs reused as validation, unit or basis errors, gains from "
            "relaxed assumptions, missing constraints. Write continuous/critic_review.md.").format(
                state, reason)


def launch(argv, timeout=1800, cwd=None):
    """Run the agent session; returns a result dict and never raises."""
    if not shutil.which(argv[0]):
        return {"status": "not_installed", "command": argv[0]}
    try:
        completed = subprocess.run(argv, cwd=cwd, timeout=timeout, capture_output=True,
                                   text=True)
        return {"status": "ok" if completed.returncode == 0 else "fail",
                "returncode": completed.returncode}
    except subprocess.TimeoutExpired:
        return {"status": "timeout", "timeout_s": timeout}
    except OSError as error:
        return {"status": "fail", "message": str(error)}
