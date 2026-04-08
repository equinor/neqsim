"""Notebook executor - runs a .ipynb in a Jupyter kernel subprocess."""
import json
import os
import sys
import shutil
import traceback


def main():
    job_file = os.environ.get("NEQSIM_JOB_FILE")
    if not job_file:
        print("ERROR: NEQSIM_JOB_FILE not set", file=sys.stderr)
        sys.exit(1)

    with open(job_file, "r") as f:
        job_spec = json.load(f)

    notebook_path = job_spec["script"]
    output_dir = job_spec.get("output_dir", ".")
    timeout = job_spec.get("timeout", 3600)
    project_root = job_spec.get("project_root")

    os.makedirs(output_dir, exist_ok=True)

    # -- Set environment for the Jupyter kernel --
    if project_root:
        os.environ["NEQSIM_PROJECT_ROOT"] = project_root
    # Pass job args so the kernel can read them
    job_args = job_spec.get("args", {})
    os.environ["NEQSIM_JOB_ARGS"] = json.dumps(job_args)
    os.environ["NEQSIM_OUTPUT_DIR"] = output_dir

    # -- Validate notebook exists --
    if not os.path.exists(notebook_path):
        error_msg = (f"Notebook not found: {notebook_path}\n"
                     f"It may have been moved or deleted since job submission.")
        print(f"ERROR: {error_msg}", file=sys.stderr)
        with open(os.path.join(output_dir, "_status.json"), "w") as f:
            json.dump({"status": "failed", "error": error_msg,
                       "timestamp": __import__("datetime").datetime.now(
                           __import__("datetime").timezone.utc).isoformat()}, f, indent=2)
        sys.exit(1)

    # -- Execute the notebook --
    try:
        import nbformat
        from nbconvert.preprocessors import ExecutePreprocessor
    except ImportError:
        error_msg = ("nbformat and nbconvert are required for notebook execution. "
                     "Install with: pip install nbformat nbconvert")
        print(f"ERROR: {error_msg}", file=sys.stderr)
        with open(os.path.join(output_dir, "_status.json"), "w") as f:
            json.dump({"status": "failed", "error": error_msg,
                       "timestamp": __import__("datetime").datetime.now(
                           __import__("datetime").timezone.utc).isoformat()}, f, indent=2)
        sys.exit(2)

    try:
        print(f"Executing notebook: {notebook_path}")
        with open(notebook_path, "r", encoding="utf-8") as f:
            nb = nbformat.read(f, as_version=4)

        ep = ExecutePreprocessor(
            timeout=timeout,
            kernel_name="python3",
        )
        # Run in the notebook's directory so relative paths work
        nb_dir = os.path.dirname(os.path.abspath(notebook_path))
        ep.preprocess(nb, {"metadata": {"path": nb_dir}})

        # Save executed notebook to output directory
        nb_name = os.path.basename(notebook_path)
        executed_path = os.path.join(output_dir, nb_name)
        with open(executed_path, "w", encoding="utf-8") as f:
            nbformat.write(nb, f)
        print(f"Executed notebook saved: {executed_path}")

        # Backup original before overwriting
        import tempfile
        backup_path = notebook_path + ".backup"
        if not os.path.exists(backup_path):
            shutil.copy2(notebook_path, backup_path)
        # Atomic overwrite: write to temp file then replace
        temp_fd, temp_path = tempfile.mkstemp(
            suffix=".ipynb", dir=os.path.dirname(os.path.abspath(notebook_path)))
        try:
            with os.fdopen(temp_fd, "w", encoding="utf-8") as tf:
                nbformat.write(nb, tf)
            os.replace(temp_path, notebook_path)
        except Exception:
            if os.path.exists(temp_path):
                os.remove(temp_path)
            raise
        print(f"Original notebook updated with outputs: {notebook_path}")

        # Check if a results.json was produced by the notebook
        # (notebooks typically write results.json to the task root)
        nb_parent = os.path.dirname(os.path.abspath(notebook_path))
        task_root = os.path.dirname(nb_parent)
        for candidate in [
            os.path.join(task_root, "results.json"),
            os.path.join(nb_parent, "results.json"),
            os.path.join(nb_dir, "results.json"),
        ]:
            if os.path.exists(candidate):
                dest = os.path.join(output_dir, "results.json")
                if os.path.abspath(candidate) != os.path.abspath(dest):
                    shutil.copy2(candidate, dest)
                print(f"Found results.json: {candidate}")
                break

        # Copy figures if present
        figures_dir = os.path.join(task_root, "figures")
        if os.path.isdir(figures_dir):
            dest_figures = os.path.join(output_dir, "figures")
            if os.path.abspath(figures_dir) != os.path.abspath(dest_figures):
                if os.path.exists(dest_figures):
                    shutil.rmtree(dest_figures)
                shutil.copytree(figures_dir, dest_figures)
            print(f"Copied figures from: {figures_dir}")

        # Write success marker
        with open(os.path.join(output_dir, "_status.json"), "w") as f:
            json.dump({
                "status": "success",
                "executed_notebook": executed_path,
                "timestamp": __import__("datetime").datetime.now(
                    __import__("datetime").timezone.utc).isoformat(),
            }, f, indent=2)
        sys.exit(0)

    except Exception as e:
        error_msg = traceback.format_exc()
        print(f"ERROR: Notebook execution failed: {e}", file=sys.stderr)
        print(error_msg, file=sys.stderr)

        # Save partially-executed notebook if possible
        try:
            nb_name = os.path.basename(notebook_path)
            partial_path = os.path.join(output_dir, "PARTIAL_" + nb_name)
            with open(partial_path, "w", encoding="utf-8") as f:
                nbformat.write(nb, f)
            print(f"Partial notebook saved: {partial_path}", file=sys.stderr)
        except Exception as save_err:
            print(f"WARNING: Could not save partial notebook: {save_err}", file=sys.stderr)

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
