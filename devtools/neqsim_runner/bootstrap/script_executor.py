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

    with open(job_file, "r", encoding="utf-8") as f:
        job_spec = json.load(f)

    script_path = job_spec["script"]
    job_args = job_spec.get("args", {})
    output_dir = job_spec.get("output_dir", ".")
    checkpoint_path = job_spec.get("checkpoint_path")
    project_root = job_spec.get("project_root")
    require_devtools = bool(job_spec.get("require_devtools", True))

    # -- Validate script exists --
    if not os.path.exists(script_path):
        error_msg = (f"Script not found: {script_path}\n"
                     "It may have been moved or deleted since job submission.")
        print(f"ERROR: {error_msg}", file=sys.stderr)
        os.makedirs(output_dir, exist_ok=True)
        with open(os.path.join(output_dir, "_status.json"), "w", encoding="utf-8") as f:
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
        # Task runner workflows must use devtools so workspace Java classes are
        # available without packaging/copying a JAR into neqsim-python.
        if project_root:
            devtools_path = os.path.join(project_root, "devtools")
            if devtools_path not in sys.path:
                sys.path.insert(0, devtools_path)
            os.environ["NEQSIM_PROJECT_ROOT"] = project_root
            os.environ["NEQSIM_REQUIRE_DEVTOOLS"] = "1"
            from neqsim_dev_setup import neqsim_init, neqsim_classes
            ns = neqsim_init(project_root=project_root, recompile=False, verbose=True)
            ns = neqsim_classes(ns)
            os.environ["NEQSIM_MODE"] = "devtools"
        elif require_devtools:
            raise RuntimeError(
                "NeqSim Runner requires devtools mode, but project_root is missing. "
                "Use AgentBridge from inside the NeqSim repository or pass project_root."
            )
        else:
            raise RuntimeError("NeqSim Runner pip mode is disabled for repository task workflows.")
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
            "NEQSIM_MODE": os.environ.get("NEQSIM_MODE"),
        }
        if "ns" in locals():
            script_globals["ns"] = ns
            script_globals["JClass"] = ns.JClass

        with open(script_path, "r", encoding="utf-8") as f:
            code = compile(f.read(), script_path, "exec")
            exec(code, script_globals)

        print("Job completed successfully")

        # Write success marker
        with open(os.path.join(output_dir, "_status.json"), "w", encoding="utf-8") as f:
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
        with open(os.path.join(output_dir, "_status.json"), "w", encoding="utf-8") as f:
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
