"""Replay PaperLab experiments declared by ``plan.json``.

The replication gate calls this script for both papers and books. A project may
start with only artifact-level traceability and no runnable experiments; in that
case the script writes a valid skipped report that ``check_replication.py`` can
evaluate against an empty claims manifest.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List


def _load_json(path: Path) -> Dict[str, Any]:
    """Load a JSON object from ``path``."""
    with path.open(encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return data


def _as_experiment_list(plan: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Return normalized experiment declarations from a plan."""
    replication = plan.get("replication", {})
    experiments = replication.get("experiments", []) if isinstance(replication, dict) else []
    if not experiments:
        experiments = plan.get("experiments", [])
    if not isinstance(experiments, list):
        raise ValueError("plan replication experiments must be a list")
    normalized = []
    for index, experiment in enumerate(experiments, 1):
        if not isinstance(experiment, dict):
            raise ValueError(f"experiment {index} must be a JSON object")
        normalized.append(experiment)
    return normalized


def _run_command(experiment: Dict[str, Any], paper_dir: Path) -> Dict[str, Any]:
    """Run one command-style experiment and return its report entry."""
    command = experiment.get("command")
    if not command:
        raise ValueError(f"experiment {experiment.get('id', '<unnamed>')} lacks command")
    cwd_value = experiment.get("cwd", ".")
    cwd = (paper_dir / cwd_value).resolve() if not Path(cwd_value).is_absolute() else Path(cwd_value)
    timeout_seconds = int(experiment.get("timeout_seconds", 1800))
    shell = isinstance(command, str)
    completed = subprocess.run(
        command,
        cwd=str(cwd),
        shell=shell,
        text=True,
        capture_output=True,
        timeout=timeout_seconds,
        check=False,
    )
    return {
        "id": experiment.get("id", "command"),
        "type": "command",
        "command": command,
        "cwd": str(cwd),
        "returncode": completed.returncode,
        "stdout_tail": completed.stdout[-4000:],
        "stderr_tail": completed.stderr[-4000:],
        "status": "PASS" if completed.returncode == 0 else "FAIL",
    }


def _replicate_experiments(experiments: Iterable[Dict[str, Any]], paper_dir: Path) -> List[Dict[str, Any]]:
    """Run all supported experiments."""
    entries = []
    for experiment in experiments:
        experiment_type = experiment.get("type", "command")
        if experiment_type != "command":
            entries.append({
                "id": experiment.get("id", experiment_type),
                "type": experiment_type,
                "status": "SKIP",
                "message": f"Unsupported experiment type: {experiment_type}",
            })
            continue
        try:
            entries.append(_run_command(experiment, paper_dir))
        except Exception as error:  # noqa: BLE001 - CLI report should capture all failures.
            entries.append({
                "id": experiment.get("id", "command"),
                "type": "command",
                "status": "FAIL",
                "message": str(error),
            })
    return entries


def build_report(paper_dir: Path) -> Dict[str, Any]:
    """Build a replication report for ``paper_dir``."""
    plan_path = paper_dir / "plan.json"
    results_path = paper_dir / "results.json"
    manifest_path = paper_dir / "claims_manifest.json"
    missing = [str(path.name) for path in (plan_path, results_path, manifest_path) if not path.exists()]
    if missing:
        return {
            "paper_dir": str(paper_dir),
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "status": "FAILED",
            "missing_artifacts": missing,
            "experiments": [],
        }

    plan = _load_json(plan_path)
    results = _load_json(results_path)
    manifest = _load_json(manifest_path)
    experiments = _as_experiment_list(plan)
    entries = _replicate_experiments(experiments, paper_dir) if experiments else []
    failed = [entry for entry in entries if entry.get("status") == "FAIL"]
    status = "FAILED" if failed else ("PASSED" if entries else "SKIPPED")
    return {
        "paper_dir": str(paper_dir),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "status": status,
        "paper_type": plan.get("paper_type", "characterization"),
        "project_type": plan.get("project_type", "paper"),
        "experiment_count": len(entries),
        "results_key_count": len(results.get("key_results", {})),
        "claim_count": len(manifest.get("claims", [])),
        "claim_tolerance_count": len(manifest.get("claim_tolerances", [])),
        "unlinked_claim_count": len(manifest.get("unlinked_claims", [])),
        "experiments": entries,
        "message": "No experiments declared in plan.json" if not entries else "Experiments completed",
    }


def main(argv: List[str] | None = None) -> int:
    """CLI entry point."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper-dir", required=True, help="Paper or book directory")
    parser.add_argument("--output", required=True, help="Path to write replication report JSON")
    args = parser.parse_args(argv)

    paper_dir = Path(args.paper_dir).resolve()
    output = Path(args.output).resolve()
    report = build_report(paper_dir)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"Replication report written to {output}")
    print(f"Status: {report['status']}")
    return 1 if report["status"] == "FAILED" else 0


if __name__ == "__main__":
    sys.exit(main())