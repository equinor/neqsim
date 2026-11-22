#!/usr/bin/env python3
"""Grade a coding-agent candidate with acceptance tests outside its checkout.

This runner does not invoke an LLM. Supply a candidate made from the catalog's
pinned base, plus a trusted changed-path manifest. Keep this evaluator checkout
separate from the candidate; run candidate code in an isolated, secret-free job.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

EVALUATOR = Path(__file__).resolve().parent
CATALOG = EVALUATOR / "code_health_evals/cases.json"


def digest(path: Path) -> str:
    """Fingerprint evidence instead of trusting filenames alone."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def grade(case: dict, candidate: Path, changed: list, base: str, base_root: Path,
          timeout: int | None = None) -> dict:
    """Run only the catalog's fixed test and record failed/incomplete runs."""
    started = time.monotonic()
    result = {"schema_version": 1, "case": case["id"], "base_revision": base,
              "status": "failed", "errors": [], "candidate_files": {}}
    try:
        if base != case["base_revision"]:
            raise ValueError("Candidate base does not match the pinned case")
        result["base_files_sha256"] = {}
        for name, expected in case["base_files_sha256"].items():
            actual = digest(base_root / name)
            if actual != expected:
                raise ValueError("Pinned base file mismatch: " + name)
            result["base_files_sha256"][name] = actual
            if name not in changed and digest(candidate / name) != expected:
                raise ValueError("Unreported change to a case dependency: " + name)
        if not changed or set(changed) - set(case["allowed_paths"]):
            raise ValueError("Empty candidate or edits outside the allowed task scope")
        for name in changed:
            path = candidate / name
            if path.is_symlink() or not path.resolve().is_relative_to(candidate.resolve()):
                raise ValueError("Candidate path escapes checkout")
            result["candidate_files"][name] = digest(path)
        test = EVALUATOR / "code_health_evals" / case["acceptance_test"]
        if not test.resolve().is_relative_to((EVALUATOR / "code_health_evals").resolve()):
            raise ValueError("Acceptance test escapes evaluator")
        result["acceptance_sha256"] = digest(test)
        env = {key: value for key, value in os.environ.items()
               if key in {"PATH", "SYSTEMROOT", "WINDIR", "TMP", "TEMP", "TMPDIR", "LANG"}}
        env["NEQSIM_EVAL_CANDIDATE"] = str(candidate.resolve())
        env["NEQSIM_EVAL_BASE"] = str(base_root.resolve())
        command = [sys.executable, "-I", str(test)]
        with tempfile.TemporaryDirectory(prefix="neqsim-coding-eval-") as receipt_dir:
            completion = Path(receipt_dir) / "completion.json"
            env["NEQSIM_EVAL_COMPLETION"] = str(completion)
            completed = subprocess.run(command, cwd=candidate, env=env, text=True, capture_output=True,
                                       timeout=timeout or case["timeout_seconds"], check=False)
            if not completion.is_file():
                raise ValueError("Acceptance suite did not complete; process exit alone is insufficient")
            suite = json.loads(completion.read_text(encoding="utf-8"))
            result["acceptance_result"] = suite
            valid_suite = (suite.get("tests_run") == case["expected_tests"]
                           and suite.get("successful") is True and suite.get("skipped") == 0)
        result.update(command=["selected-python", "-I", test.name], exit_code=completed.returncode,
                      output=(completed.stdout + completed.stderr)[-16000:],
                      status="passed" if completed.returncode == 0 and valid_suite else "failed")
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        result["errors"].append(str(error))
    result["elapsed_seconds"] = round(time.monotonic() - started, 3)
    return result


def main(argv=None) -> int:
    """Grade one candidate; missing provenance is an error, not a pass."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", required=True)
    parser.add_argument("--candidate-root", type=Path, required=True)
    parser.add_argument("--base-root", type=Path, required=True)
    parser.add_argument("--base-revision", required=True)
    parser.add_argument("--changed-paths", type=Path, required=True,
                        help="Trusted JSON string list from the candidate diff")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    case = next((c for c in catalog["cases"] if c["id"] == args.case), None)
    if case is None:
        parser.error("Unknown coding case")
    changed = json.loads(args.changed_paths.read_text(encoding="utf-8"))
    if not isinstance(changed, list) or not all(isinstance(p, str) for p in changed):
        parser.error("Changed paths must be a JSON string list")
    result = grade(case, args.candidate_root, changed, args.base_revision, args.base_root)
    result["catalog_sha256"] = digest(CATALOG)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Coding evaluation {args.case}: {result['status']}")
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
