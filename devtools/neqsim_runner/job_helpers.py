"""
Job helpers — utilities for scripts running inside a worker subprocess.

Use these in your simulation scripts to:
- Read job arguments
- Save/load checkpoints for resume-on-failure
- Write structured results
- Report progress

Example simulation script::

    from neqsim_runner.job_helpers import (
        get_args, get_output_dir, save_checkpoint, load_checkpoint,
        save_result, report_progress
    )

    args = get_args()
    output_dir = get_output_dir()

    # Resume from checkpoint if available
    checkpoint = load_checkpoint()
    start_iter = checkpoint.get("iteration", 0) if checkpoint else 0

    for i in range(start_iter, 100):
        # ... run NeqSim simulation ...
        result = run_simulation(args["pressure"], args["temperature"])

        # Checkpoint every 10 iterations
        if i % 10 == 0:
            save_checkpoint({"iteration": i, "partial_results": partial})
            report_progress(i, 100)

    save_result({"final_output": result})
"""

import json
import os
from pathlib import Path


def get_args():
    """
    Get the job arguments passed by the supervisor.

    Returns
    -------
    dict
        The arguments dict from the job spec.
    """
    args_str = os.environ.get("NEQSIM_JOB_ARGS", "{}")
    return json.loads(args_str)


def get_output_dir():
    """
    Get the output directory for this job's results.

    Returns
    -------
    Path
        The output directory (already created by the worker).
    """
    d = Path(os.environ.get("NEQSIM_OUTPUT_DIR", "."))
    d.mkdir(parents=True, exist_ok=True)
    return d


def save_checkpoint(data):
    """
    Save a checkpoint for resume-on-failure.

    The supervisor passes the checkpoint path via environment variable.
    On restart, the script can load this checkpoint to resume from where it
    left off rather than starting over.

    Parameters
    ----------
    data : dict
        Arbitrary JSON-serializable checkpoint data.
    """
    path = os.environ.get("NEQSIM_CHECKPOINT_PATH")
    if not path:
        return  # no checkpoint path — running outside of runner, silently skip
    os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
    # Write atomically: write to temp file then rename
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, path)


def load_checkpoint():
    """
    Load the last saved checkpoint, if any.

    Returns
    -------
    dict or None
        The checkpoint data, or None if no checkpoint exists.
    """
    path = os.environ.get("NEQSIM_CHECKPOINT_PATH")
    if not path or not os.path.exists(path):
        return None
    try:
        with open(path, "r") as f:
            return json.load(f)
    except (json.JSONDecodeError, IOError):
        return None


def save_result(data, filename="results.json"):
    """
    Save structured results to the output directory.

    Parameters
    ----------
    data : dict
        JSON-serializable result data.
    filename : str
        Output filename (default: results.json).
    """
    output_dir = get_output_dir()
    path = output_dir / filename
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def report_progress(current, total, message=None):
    """
    Print a progress update that the supervisor can parse from logs.

    Parameters
    ----------
    current : int
        Current step/iteration.
    total : int
        Total steps/iterations.
    message : str, optional
        Additional progress message.
    """
    pct = (current / total * 100) if total > 0 else 0
    msg = f"[PROGRESS] {current}/{total} ({pct:.1f}%)"
    if message:
        msg += f" — {message}"
    print(msg, flush=True)


def get_neqsim_mode():
    """
    Get the NeqSim initialization mode.

    Returns
    -------
    str
        "devtools" or "pip", depending on how NeqSim was loaded.
    """
    return os.environ.get("NEQSIM_MODE", "unknown")
