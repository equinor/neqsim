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
| Agent context window exhausted | `TaskProgress` checkpoints let a fresh agent resume |

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

**Cross-platform:** Works on Windows, Linux, and macOS. Process groups use
`CREATE_NEW_PROCESS_GROUP` on Windows and `start_new_session` (setsid) on
Unix. All file I/O uses explicit UTF-8 encoding.

## Quick Start

### From an LLM Agent (recommended)

```python
from neqsim_runner.agent_bridge import AgentBridge

bridge = AgentBridge(task_dir="task_solve/2026-04-08_my_task")

# AgentBridge auto-detects the NeqSim project root and runner workers expose
# NEQSIM_PROJECT_ROOT plus <repo>/devtools to notebooks. Task notebooks should
# use neqsim_dev_setup/ns.* so workspace Java classes are loaded directly from
# target/classes.

# Submit a notebook — produces an executed .ipynb with all cell outputs
job_id = bridge.submit_notebook(
    "step2_analysis/01_analysis.ipynb",
    max_retries=3,
    timeout_seconds=3600,
)

# Run all pending jobs (blocking, with automatic retry)
bridge.run_all(max_parallel=1)

# Get executed notebook and results
executed_nb = bridge.get_executed_notebook(job_id)
results = bridge.get_results(job_id)
summary = bridge.summary()
if summary["failed"] or summary["pending"]:
    raise RuntimeError("NeqSim Runner jobs did not all complete successfully")
bridge.merge_results_to_task([job_id])
print(summary)
```

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

## Notebook Execution Modes

The `AgentBridge` supports two modes for notebooks:

### `mode="execute"` (default)

Runs the notebook via nbconvert in a Jupyter kernel subprocess. Produces an
executed `.ipynb` with all cell outputs (plots, tables, print statements) —
identical to "Run All" in VS Code. The original notebook gets a `.backup`
before being overwritten with the executed version.

```python
job_id = bridge.submit_notebook("notebook.ipynb", mode="execute")
```

**Requires:** `pip install nbformat nbconvert`

### `mode="script"`

Converts the notebook to a `.py` script (code cells only) and runs it as a
plain Python job. Faster and lighter, but produces no executed notebook —
only JSON results and logs.

```python
job_id = bridge.submit_notebook("notebook.ipynb", mode="script")
```

## Parametric Sweeps

Run the same script with different parameters — each case gets its own
subprocess and retry budget:

```python
cases = [{"pressure": p, "temp": t}
         for p in [30, 60, 90] for t in [15, 25, 40]]

job_ids = bridge.submit_parametric_sweep(
    "run_case.py", cases,
    max_retries=2, timeout_seconds=600,
)
bridge.run_all(max_parallel=1)
all_results = bridge.collect_results()
```

## Results Collection for Task Reports

Use `merge_results_to_task()` for task folders with more than one notebook. It
loads the existing task-level `results.json`, recursively merges dictionaries,
appends list entries without exact duplicates, and writes the combined file back
to the task root. This prevents a benchmark or uncertainty notebook from
overwriting results created by the main analysis notebook.

```python
job_ids = [
    bridge.submit_notebook("step2_analysis/01_main_analysis.ipynb"),
    bridge.submit_notebook("step2_analysis/02_benchmark_validation.ipynb"),
]
bridge.run_all(max_parallel=1)
summary = bridge.summary()
if summary["failed"] or summary["pending"]:
    raise RuntimeError("NeqSim Runner jobs did not all complete successfully")
bridge.merge_results_to_task(job_ids)
```

`copy_results_to_task(job_id)` still exists for single-job workflows, but it
overwrites the destination by default. Use `copy_results_to_task(job_id,
merge=True)` when preserving existing task-level results matters.

## Writing Job Scripts

Job scripts are plain Python files. Use `job_helpers` for checkpointing and
results:

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
| `NEQSIM_JOB_FILE` | Path to the job spec JSON (used by bootstrap) |
| `NEQSIM_JOB_ARGS` | JSON string with job arguments |
| `NEQSIM_OUTPUT_DIR` | Directory for result files |
| `NEQSIM_CHECKPOINT_PATH` | Path for checkpoint file |
| `NEQSIM_MODE` | `"devtools"` or `"pip"` |
| `NEQSIM_PROJECT_ROOT` | NeqSim project root (devtools mode only) |

## Task Progress (Context Survival)

Long-running tasks can exhaust an LLM agent's context window. The
`TaskProgress` tracker writes checkpoints to `progress.json` so a fresh
agent can resume exactly where the previous one left off:

```python
from neqsim_runner.progress import TaskProgress

progress = TaskProgress("task_solve/2026-04-08_my_task")

# Check if we're resuming a previous session
if progress.is_resuming():
    print(progress.resume_summary())         # human-readable
    data = progress.resume_summary("json")   # machine-readable dict

# Checkpoint after each milestone
progress.complete_milestone("step1_research_done",
    summary="Research complete. SRK EOS, 3-stage compression.",
    outputs=["step1_scope_and_research/task_spec.md"],
    decisions={"eos": "SRK", "scale": "Standard"})

# Store arbitrary context for the next agent
progress.store_context("feed_composition", {"methane": 0.85, "ethane": 0.07})

# Set the instruction for the next agent
progress.set_next_action("Create notebook: 01_compression.ipynb")
```

## Job Status Lifecycle

Jobs follow a strict state machine with validated transitions:

```
PENDING ──> RUNNING ──> SUCCESS (terminal)
  │            │
  │            ├──> FAILED ──> RETRYING ──> PENDING (retry loop)
  │            │
  │            └──> TIMED_OUT ──> RETRYING ──> PENDING (retry loop)
  │
  └──> CANCELLED (terminal, from any non-terminal state)
```

Invalid transitions (e.g., PENDING → SUCCESS) raise `InvalidTransitionError`:

```python
from neqsim_runner.models import Job, InvalidTransitionError

job = Job("test.py")
job.set_status(JobStatus.RUNNING)    # OK
job.set_status(JobStatus.SUCCESS)    # OK
job.set_status(JobStatus.RUNNING)    # raises InvalidTransitionError
```

## Rate Limiting

The `AgentBridge` guards against runaway job submission with configurable
queue-size thresholds:

```python
bridge = AgentBridge(
    task_dir="task_solve/my_task",
    queue_warn=100,    # log warning when queue exceeds this
    queue_limit=1000,  # raise RuntimeError when queue exceeds this
)
```

## Output Structure

```
task_solve/runner_output/
  job-abc123/
    _job_spec.json            # Job definition
    _worker_bootstrap.py      # Bootstrap entry point (or _notebook_executor.py)
    stdout.log                # Process stdout
    stderr.log                # Process stderr
    output/
      results.json            # Your results
      _status.json            # Success/failure marker
      notebook.ipynb          # Executed notebook (mode="execute" only)
    checkpoint.json           # Last checkpoint
```

## Module Reference

| Module | Purpose |
|--------|---------|
| `models.py` | `Job`, `JobStatus`, `InvalidTransitionError`, transition validation |
| `store.py` | `JobStore` — SQLite persistence with WAL mode, thread-local connections |
| `worker.py` | `WorkerProcess` — subprocess launch, log capture, process tree kill |
| `supervisor.py` | `Supervisor` — watchdog loop, retry, structured logging, monotonic timeouts |
| `agent_bridge.py` | `AgentBridge` — high-level agent API, notebook modes, sweeps, rate limiting |
| `progress.py` | `TaskProgress` — checkpoint/resume for context window survival |
| `job_helpers.py` | Utilities for scripts running inside workers (args, checkpoint, results) |
| `cli.py` | CLI entry point (`python -m neqsim_runner`) |
| `bootstrap/` | Bootstrap scripts executed in worker subprocesses (script + notebook) |

## Examples

| Example | Description |
|---------|-------------|
| `examples/example_simple.py` | Single flash calculation |
| `examples/example_monte_carlo.py` | Monte Carlo with checkpointing |
| `examples/example_pipeline.py` | Parametric sweep (multiple jobs) |

## Tests

```bash
cd devtools
python -m pytest neqsim_runner/tests/test_runner.py -v
```

37 tests covering: models, store, progress, bootstrap validation, transition
validation, rate limiting, structured logging, and monotonic timeouts.

## Platform Notes

| Platform | Process groups | Process kill | Tested |
|----------|---------------|-------------|--------|
| Windows | `CREATE_NEW_PROCESS_GROUP` | `taskkill /F /T /PID` | Yes |
| Linux | `start_new_session=True` (setsid) | `os.killpg(SIGKILL)` | Via CI |
| macOS | `start_new_session=True` (setsid) | `os.killpg(SIGKILL)` | Via CI |
| Docker | Same as Linux | Same as Linux | Untested, expected to work |

**Known limitations:**
- SQLite WAL mode may not work on network filesystems (NFS/SMB). Use local
  storage for the database.
- `os.replace()` can fail on Windows if the target file is locked by an
  antivirus scanner or file watcher. This is rare and transient.
