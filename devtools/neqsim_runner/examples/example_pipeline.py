"""
Example: Multi-job pipeline — submit a batch of parametric simulations.

This script submits multiple jobs to the runner, each with different
parameters. The supervisor runs them sequentially (or in parallel),
with automatic retry on failure.

Run with::

    python example_pipeline.py
    python -m neqsim_runner run  # if not already running

Or use the programmatic API from an LLM agent workflow.
"""

import sys
from pathlib import Path

# Add devtools to path
devtools = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(devtools))

from neqsim_runner import submit_job, run_supervisor


def main():
    # ── Define a parametric sweep ──
    pressures = [30.0, 60.0, 90.0, 120.0]
    temperatures = [15.0, 25.0, 40.0]

    # Submit one job per (pressure, temperature) combination
    job_ids = []
    for p in pressures:
        for t in temperatures:
            job_id = submit_job(
                script=str(Path(__file__).parent / "example_simple.py"),
                args={"pressure_bara": p, "temperature_C": t},
                max_retries=2,
                timeout_seconds=300,
            )
            job_ids.append(job_id)
            print(f"Submitted: {job_id} (P={p} bara, T={t} C)")

    print(f"\n{len(job_ids)} jobs submitted — starting supervisor...\n")

    # ── Run all jobs ──
    run_supervisor()

    # ── Collect results ──
    print("\n=== Results ===")
    from neqsim_runner import job_status
    import json

    for jid in job_ids:
        job = job_status(jid)
        print(f"\n{jid}: {job.status.value}")
        if job.result_path:
            result_file = Path(job.result_path) / "results.json"
            if result_file.exists():
                with open(result_file) as f:
                    data = json.load(f)
                print(f"  Density: {data.get('density_kg_m3', '?'):.2f} kg/m3")
                print(f"  Z: {data.get('Z_factor', '?'):.4f}")


if __name__ == "__main__":
    main()
