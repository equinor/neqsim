"""Minimal test job that doesn't require NeqSim — tests the runner machinery."""
import json
import os
import time

args = json.loads(os.environ.get("NEQSIM_JOB_ARGS", "{}"))
output_dir = os.environ.get("NEQSIM_OUTPUT_DIR", ".")
os.makedirs(output_dir, exist_ok=True)

value = args.get("value", 42)
should_fail = args.get("should_fail", False)

print(f"Test job running with value={value}, should_fail={should_fail}")
time.sleep(1)  # simulate some work

if should_fail:
    raise RuntimeError("Intentional failure for testing")

result = {"computed": value * 2, "status": "ok"}
with open(os.path.join(output_dir, "results.json"), "w") as f:
    json.dump(result, f, indent=2)

print(f"Result: {result}")
