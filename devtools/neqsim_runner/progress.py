"""
Task progress tracker — survives context window exhaustion.

When an LLM agent's context window fills up during a long task, the agent
loses track of the workflow state. This module writes a machine-readable
``progress.json`` to the task folder after every milestone. A fresh agent
(or the same agent after context reset) reads it and resumes exactly
where the previous one left off.

The progress file tracks:
- Current phase and step (Phase 0/1/2/3, step within phase)
- What has been completed (with evidence paths)
- What is next (concrete action)
- Key decisions and parameters (so the fresh agent doesn't re-derive them)
- Job IDs for any runner submissions

Usage from an agent::

    from neqsim_runner.progress import TaskProgress

    # Start or resume a task
    progress = TaskProgress("task_solve/2026-04-08_my_task")

    # Check if we're resuming
    if progress.is_resuming():
        print(progress.resume_summary())
        # Agent reads this and knows where to pick up

    # After completing a milestone, checkpoint
    progress.complete_milestone(
        "step1_research",
        summary="Completed scope and research. Task is Type B (Process), "
                "Standard scale. Using SRK EOS, 3-stage compression.",
        outputs=["step1_scope_and_research/task_spec.md",
                 "step1_scope_and_research/notes.md"],
        decisions={"eos": "SRK", "scale": "Standard", "type": "B",
                   "fluid": "rich gas with 85% methane"},
    )

    # Set what comes next
    progress.set_next_action(
        "Create main analysis notebook with 3-stage compression simulation"
    )

    # Agent can store key parameters that took effort to derive
    progress.store_context("fluid_composition", {
        "methane": 0.85, "ethane": 0.07, "propane": 0.05, "nC4": 0.03
    })
"""

import json
import os
from datetime import datetime, timezone
from pathlib import Path


class TaskProgress:
    """
    Manages a ``progress.json`` file in the task folder.

    This is the agent's external memory — it survives context window
    exhaustion, session boundaries, and agent restarts.

    Parameters
    ----------
    task_dir : str or Path
        Path to the task_solve folder.
    """

    FILENAME = "progress.json"

    # Canonical milestone names (ordered)
    MILESTONES = [
        "task_created",
        "phase0_classified",
        "step1_spec_written",
        "step1_research_done",
        "step1_analysis_done",
        "step2_notebook_created",
        "step2_notebook_executed",
        "step2_validation_done",
        "step2_benchmark_done",
        "step2_uncertainty_done",
        "step2_results_saved",
        "step3_report_generated",
        "task_completed",
    ]

    def __init__(self, task_dir):
        self.task_dir = Path(task_dir).resolve()
        self.progress_file = self.task_dir / self.FILENAME
        self._data = self._load()

    def _load(self):
        """Load existing progress or create new."""
        if self.progress_file.exists():
            try:
                with open(self.progress_file, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError):
                pass
        return self._new_progress()

    def _new_progress(self):
        """Create a fresh progress structure."""
        return {
            "version": 1,
            "task_dir": str(self.task_dir),
            "created_at": _now(),
            "updated_at": _now(),
            "current_phase": "phase0",
            "completed_milestones": [],
            "next_action": "Classify the task and determine scale",
            "decisions": {},
            "context": {},
            "job_ids": [],
            "milestone_log": [],
            "error_log": [],
        }

    def _save(self):
        """Persist to disk (atomic write)."""
        self._data["updated_at"] = _now()
        self.task_dir.mkdir(parents=True, exist_ok=True)
        tmp = self.progress_file.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self._data, f, indent=2)
        tmp.replace(self.progress_file)

    # ── Query methods ──

    def is_resuming(self):
        """True if there's existing progress (not a fresh start)."""
        return len(self._data.get("completed_milestones", [])) > 0

    def current_phase(self):
        """Get the current phase name."""
        return self._data.get("current_phase", "phase0")

    def next_action(self):
        """Get the next action description."""
        return self._data.get("next_action", "Unknown")

    def completed_milestones(self):
        """Get list of completed milestone names."""
        return list(self._data.get("completed_milestones", []))

    def is_milestone_done(self, name):
        """Check if a specific milestone has been completed."""
        return name in self._data.get("completed_milestones", [])

    def get_decision(self, key, default=None):
        """Retrieve a stored decision."""
        return self._data.get("decisions", {}).get(key, default)

    def get_context(self, key, default=None):
        """Retrieve stored context data."""
        return self._data.get("context", {}).get(key, default)

    def get_job_ids(self):
        """Get all runner job IDs associated with this task."""
        return list(self._data.get("job_ids", []))

    def resume_summary(self, format="text"):
        """
        Generate a compact summary for a fresh agent to read.

        Parameters
        ----------
        format : str
            ``"text"`` for a human-readable string (default), or
            ``"json"`` for a machine-parseable dict with structured
            milestone data, outputs, and decisions.

        Returns
        -------
        str or dict
            Text summary or structured dict depending on format.
        """
        if format == "json":
            return self._resume_summary_json()
        return self._resume_summary_text()

    def _resume_summary_json(self):
        """Structured resume data for machine parsing."""
        d = self._data
        return {
            "task_dir": str(self.task_dir),
            "current_phase": d.get("current_phase", "phase0"),
            "updated_at": d.get("updated_at"),
            "completed_milestones": [
                {
                    "name": m.get("name", ""),
                    "summary": m.get("summary", ""),
                    "outputs": m.get("outputs", []),
                    "timestamp": m.get("timestamp", ""),
                }
                for m in d.get("milestone_log", [])
            ],
            "decisions": dict(d.get("decisions", {})),
            "context": dict(d.get("context", {})),
            "next_action": d.get("next_action", ""),
            "job_ids": list(d.get("job_ids", [])),
            "recent_errors": d.get("error_log", [])[-3:],
        }

    def _resume_summary_text(self):
        """Human-readable resume summary."""
        d = self._data
        completed = d.get("completed_milestones", [])
        decisions = d.get("decisions", {})
        errors = d.get("error_log", [])

        lines = [
            "=== TASK RESUME POINT ===",
            f"Task: {self.task_dir.name}",
            f"Phase: {d.get('current_phase', '?')}",
            f"Last update: {d.get('updated_at', '?')}",
            "",
            "COMPLETED:",
        ]
        for m in completed:
            lines.append(f"  [done] {m}")

        lines.append("")
        lines.append("KEY DECISIONS:")
        for k, v in decisions.items():
            val = v if not isinstance(v, dict) else json.dumps(v, indent=None)
            lines.append(f"  {k}: {val}")

        if errors:
            lines.append("")
            lines.append("RECENT ERRORS (fix or skip):")
            for err in errors[-3:]:
                lines.append(f"  [{err.get('timestamp', '?')}] {err.get('message', '?')}")

        lines.append("")
        lines.append(f">>> NEXT ACTION: {d.get('next_action', 'Check the task folder')}")
        lines.append("=========================")

        return "\n".join(lines)

    # ── Mutation methods ──

    def complete_milestone(self, name, summary="", outputs=None, decisions=None):
        """
        Mark a milestone as complete.

        Parameters
        ----------
        name : str
            Milestone name (use MILESTONES constants or custom).
        summary : str
            Brief description of what was accomplished.
        outputs : list of str, optional
            Paths to files produced (relative to task_dir).
        decisions : dict, optional
            Key decisions made during this milestone.
        """
        if name not in self._data["completed_milestones"]:
            self._data["completed_milestones"].append(name)

        self._data["milestone_log"].append({
            "name": name,
            "timestamp": _now(),
            "summary": summary,
            "outputs": outputs or [],
        })

        if decisions:
            self._data["decisions"].update(decisions)

        # Auto-advance phase based on milestone name
        if name.startswith("step1"):
            self._data["current_phase"] = "step1"
        elif name.startswith("step2"):
            self._data["current_phase"] = "step2"
        elif name.startswith("step3"):
            self._data["current_phase"] = "step3"
        elif name == "task_completed":
            self._data["current_phase"] = "completed"

        self._save()

    def set_next_action(self, description):
        """Set what the agent should do next."""
        self._data["next_action"] = description
        self._save()

    def store_decision(self, key, value):
        """Store a key decision (persists for the next agent)."""
        self._data["decisions"][key] = value
        self._save()

    def store_context(self, key, value):
        """
        Store derived context data that was expensive to compute.

        This is for things like fluid compositions, equipment sizing results,
        API method names discovered by searching, etc. — anything that the
        agent spent significant context deriving and shouldn't have to re-derive.
        """
        self._data["context"][key] = value
        self._save()

    def add_job_id(self, job_id):
        """Record a runner job ID."""
        if job_id not in self._data["job_ids"]:
            self._data["job_ids"].append(job_id)
            self._save()

    def log_error(self, message, recoverable=True):
        """
        Log an error for the next agent to see.

        Parameters
        ----------
        message : str
            What went wrong.
        recoverable : bool
            Whether this can be worked around.
        """
        self._data["error_log"].append({
            "timestamp": _now(),
            "message": message,
            "recoverable": recoverable,
        })
        # Keep only last 10 errors
        self._data["error_log"] = self._data["error_log"][-10:]
        self._save()

    def set_phase(self, phase):
        """Manually set the current phase."""
        self._data["current_phase"] = phase
        self._save()

    def to_dict(self):
        """Return the full progress state as a dict."""
        return dict(self._data)


def _now():
    """ISO timestamp."""
    return datetime.now(timezone.utc).isoformat()
