# NeqSim Runner — Supervised Simulation Execution

Eliminates manual kernel restarts by running each NeqSim job in an **isolated subprocess** with its own JVM. A supervisor monitors jobs, retries failures, and resumes from checkpoints.

## Why?

| Problem | Solution |
|---------|----------|
| JVM crash kills your notebook | Each job runs in its own process — crash kills only that worker |
| Kernel restart loses state | All state persisted to SQLite — survives any restart |
| Manual retry on failure | Supervisor auto-retries up to N times |
| Long Monte Carlo interrupted | Checkpoints save progress every N iterations |
| Can't batch multiple simulations | Submit many jobs, supervisor runs them all |

## Architecture

```
┌─────────────┐     ┌───────────┐     ┌────────────────┐
│  LLM Agent  │────>│ Supervisor │────>│ Worker Process │
│  or CLI     │     │  (Python)  │     │ (own JVM)      │
└─────────────┘     └───────────┘     └────────────────┘
                         │                    │
                    ┌────┴────┐          ┌────┴────┐
                    │ SQLite  │          │ Output  │
                    │ (state) │          │ (JSON)  │
                    └─────────┘          └─────────┘
```

## Quick Start

### CLI

```bash
# One-shot: submit + run
python -m neqsim_runner go my_simulation.py --args '{"pressure": 60}' --retries 3

# Or step by step
python -m neqsim_runner submit my_sim.py --args '{"pressure": 60}'
python -m neqsim_runner submit another_sim.py --args '{"temperature": 30}'
python -m neqsim_runner run  # runs all pending jobs

# Monitor
python -m neqsim_runner status          # all jobs
python -m neqsim_runner status job-abc  # single job
python -m neqsim_runner log job-abc     # event log
python -m neqsim_runner cancel job-abc  # cancel
```

### Python API

```python
from neqsim_runner import submit_job, run_supervisor, job_status, list_jobs

# Submit jobs
job1 = submit_job("simulation_a.py", args={"pressure": 60}, max_retries=3)
job2 = submit_job("simulation_b.py", args={"pressure": 90}, max_retries=3)

# Run all pending
run_supervisor()

# Check results
result = job_status(job1)
print(result.status)       # 'success'
print(result.result_path)  # path to output directory
```

## Writing Job Scripts

Job scripts are plain Python files. Use `job_helpers` for checkpointing and results:

```python
from neqsim_runner.job_helpers import (
    get_args, get_output_dir, save_checkpoint, load_checkpoint,
    save_result, report_progress
)

# Read parameters
args = get_args()
pressure = args["pressure_bara"]

# Resume from checkpoint if available
checkpoint = load_checkpoint()
start = checkpoint["iteration"] if checkpoint else 0

for i in range(start, 100):
    # ... NeqSim simulation ...
    result = run_simulation(pressure)

    # Save progress every 10 iterations
    if i % 10 == 0:
        save_checkpoint({"iteration": i, "partial": results})
        report_progress(i, 100)

# Save final results
save_result({"power_kW": final_power, "temperature_C": final_temp})
```

### Environment variables available in job scripts

| Variable | Description |
|----------|-------------|
| `NEQSIM_JOB_ARGS` | JSON string with job arguments |
| `NEQSIM_OUTPUT_DIR` | Directory for result files |
| `NEQSIM_CHECKPOINT_PATH` | Path for checkpoint file |
| `NEQSIM_MODE` | `"devtools"` or `"pip"` |

## Output Structure

```
task_solve/runner_output/
  job-abc123/
    _job_spec.json          # Job definition
    _worker_bootstrap.py    # Worker entry point
    stdout.log              # Process stdout
    stderr.log              # Process stderr
    output/
      results.json          # Your results
      _status.json          # Success/failure marker
    checkpoint.json         # Last checkpoint
```

## Examples

| Example | Description |
|---------|-------------|
| `examples/example_simple.py` | Single flash calculation |
| `examples/example_monte_carlo.py` | Monte Carlo with checkpointing |
| `examples/example_pipeline.py` | Parametric sweep (multiple jobs) |

## Integration with LLM Agents

The runner is designed for agent workflows where the LLM plans and the runner executes:

```python
# Agent decides what to simulate
jobs = []
for case in agent_generated_cases:
    job_id = submit_job(
        script="simulation_template.py",
        args=case,
        max_retries=3,
        timeout_seconds=1800,
    )
    jobs.append(job_id)

# Runner handles execution, retries, recovery
run_supervisor()

# Agent reads results
for jid in jobs:
    job = job_status(jid)
    if job.status.value == "success":
        # read job.result_path / "results.json"
        pass
```
