"""Execute converted UniSim models and report how far each one gets.

Runs every generated ``*_neqsim.py`` in its own subprocess (a JVM crash or a
non-converging flowsheet cannot take down the batch) and records the exit code
plus the tail of stderr, so converter defects that only appear at run time are
visible.

Usage:
    python devtools/unisim_run_generated.py --dir <work-dir> --out runs.json
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import subprocess
import sys
import time
from typing import Any, Dict, List


def run_one(path: str, timeout: int) -> Dict[str, Any]:
    """Execute one generated model and summarize the outcome.

    Args:
        path: Path to the generated Python model.
        timeout: Seconds before the run is abandoned.

    Returns:
        Result record for the model.
    """
    started = time.time()
    record: Dict[str, Any] = {'model': os.path.basename(path)}
    try:
        completed = subprocess.run(
            [sys.executable, path], capture_output=True, timeout=timeout,
            cwd=os.path.dirname(path), text=True, errors='replace')
        record['exit_code'] = completed.returncode
        record['ok'] = completed.returncode == 0
        if completed.returncode != 0:
            record['stderr_tail'] = (completed.stderr or '')[-1200:]
    except subprocess.TimeoutExpired:
        record['ok'] = False
        record['exit_code'] = None
        record['stderr_tail'] = f'TIMEOUT after {timeout}s'
    record['elapsed_s'] = round(time.time() - started, 1)
    return record


def main() -> int:
    """Entry point for running the generated models."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dir', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--timeout', type=int, default=300)
    parser.add_argument('--limit', type=int, default=0)
    args = parser.parse_args()

    models = sorted(glob.glob(os.path.join(args.dir, '*', '*_neqsim.py')))
    if args.limit:
        models = models[:args.limit]
    print(f'Running {len(models)} generated models')

    results: List[Dict[str, Any]] = []
    for index, model in enumerate(models, start=1):
        record = run_one(model, args.timeout)
        results.append(record)
        status = 'OK  ' if record['ok'] else 'FAIL'
        first_line = (record.get('stderr_tail', '') or '').strip().splitlines()
        detail = first_line[-1][:80] if first_line else ''
        print(f'[{index}/{len(models)}] {status} {record["model"][:46]:46s} '
              f'{record["elapsed_s"]:6.1f}s {detail}')

    summary = {
        'n_models': len(results),
        'n_ok': sum(1 for r in results if r['ok']),
        'n_fail': sum(1 for r in results if not r['ok']),
    }
    with open(args.out, 'w', encoding='utf-8') as handle:
        json.dump({'summary': summary, 'results': results}, handle, indent=2)
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
