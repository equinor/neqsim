"""Worker bootstrap - runs inside the isolated subprocess."""
import json
import os
import sys
import traceback


def main():
    # -- Read job spec --
    job_file = os.environ.get("NEQSIM_JOB_FILE")
    if not job_file:
        print("ERROR: NEQSIM_JOB_FILE not set", file=sys.stderr)
        sys.exit(1)

    with open(job_file, "r") as f:
        job_spec = json.load(f)

    script_path = job_spec["script"]
    job_args = job_spec.get("args", {})
    output_dir = job_spec.get("output_dir", ".")
    checkpoint_path = job_spec.get("checkpoint_path")

    # -- Validate script exists --
    if not os.path.exists(script_path):
        error_msg = (f"Script not found: {script_path}\n"
                     "It may have been moved or deleted since job submission.")
        print(f"ERROR: {error_msg}", file=sys.stderr)
        os.makedirs(output_dir, exist_ok=True)
        with open(os.path.join(output_dir, "_status.json"), "w") as f:
            json.dump({"status": "failed", "error": error_msg,
                       "timestamp": __import__("datetime").datetime.now(
                           __import__("datetime").timezone.utc).isoformat()}, f, indent=2)
        sys.exit(1)

    # -- Make args available to the script --
    os.environ["NEQSIM_JOB_ARGS"] = json.dumps(job_args)
    os.environ["NEQSIM_OUTPUT_DIR"] = output_dir
    if checkpoint_path:
        os.environ["NEQSIM_CHECKPOINT_PATH"] = checkpoint_path

    os.makedirs(output_dir, exist_ok=True)

    # -- Initialize NeqSim JVM --
    try:
        # Try devtools first (local dev), then pip package
        project_root = job_spec.get("project_root")
        if project_root:
            sys.path.insert(0, os.path.join(project_root, "devtools"))
            from neqsim_dev_setup import neqsim_init, neqsim_classes
            ns = neqsim_init(project_root=project_root, recompile=False, verbose=True)
            ns = neqsim_classes(ns)
            os.environ["NEQSIM_MODE"] = "devtools"
        else:
            from neqsim import jneqsim  # noqa: F401
            os.environ["NEQSIM_MODE"] = "pip"
        print(f"NeqSim initialized (mode: {os.environ['NEQSIM_MODE']})")
    except Exception as e:
        print(f"ERROR: Failed to initialize NeqSim: {e}", file=sys.stderr)
        traceback.print_exc()
        sys.exit(2)

    # -- Execute the simulation script --
    try:
        # Change to script directory for relative imports
        script_dir = os.path.dirname(os.path.abspath(script_path))
        if script_dir not in sys.path:
            sys.path.insert(0, script_dir)

        print(f"Running: {script_path}")
        print(f"Args: {json.dumps(job_args, indent=2)}")

        # Execute the script in a controlled namespace
        script_globals = {
            "__name__": "__main__",
            "__file__": os.path.abspath(script_path),
            "JOB_ARGS": job_args,
            "OUTPUT_DIR": output_dir,
            "CHECKPOINT_PATH": checkpoint_path,
        }

        with open(script_path, "r") as f:
            code = compile(f.read(), script_path, "exec")
            exec(code, script_globals)

        print("Job completed successfully")

        # Write success marker
        with open(os.path.join(output_dir, "_status.json"), "w") as f:
            json.dump({"status": "success", "timestamp": __import__("datetime").datetime.now(
                __import__("datetime").timezone.utc).isoformat()}, f)
        sys.exit(0)

    except SystemExit as e:
        # Re-raise SystemExit to preserve exit code
        raise
    except Exception as e:
        error_msg = traceback.format_exc()
        print(f"ERROR: Job failed: {e}", file=sys.stderr)
        print(error_msg, file=sys.stderr)

        # Write failure marker
        with open(os.path.join(output_dir, "_status.json"), "w") as f:
            json.dump({
                "status": "failed",
                "error": str(e),
                "traceback": error_msg,
                "timestamp": __import__("datetime").datetime.now(
                    __import__("datetime").timezone.utc).isoformat(),
            }, f, indent=2)
        sys.exit(1)


if __name__ == "__main__":
    main()
