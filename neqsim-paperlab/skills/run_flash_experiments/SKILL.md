# Skill: Run Flash Experiments

## Purpose

Execute NeqSim flash calculations in batch mode, collect metrics, and produce
structured result files for paper-quality benchmarking.

## When to Use

- Running a benchmark suite designed by the `design_flash_benchmark` skill
- Comparing baseline vs candidate algorithms
- Generating raw data for the validation agent

## Execution Procedure

### Step 1: Load Benchmark Config

```python
import json

with open("benchmark_config.json") as f:
    config = json.load(f)
```

### Step 2: Generate All Cases

```python
import numpy as np
from itertools import product

def generate_all_cases(config):
    """Generate all benchmark cases from config."""
    cases = []
    case_id = 0

    for family in config["families"]:
        # Base composition
        base = family["base_composition"]

        # Composition variants
        names = list(base.keys())
        alpha = np.array([base[n] for n in names]) * family["dirichlet_concentration"]
        np.random.seed(42)  # Reproducible

        compositions = [base]  # Include base
        for _ in range(family["n_composition_variants"] - 1):
            x = np.random.dirichlet(alpha)
            compositions.append(dict(zip(names, x.tolist())))

        # PT grid
        T_vals = np.linspace(family["T_range_K"][0], family["T_range_K"][1], family["n_T"])
        P_vals = np.logspace(
            np.log10(family["P_range_bara"][0]),
            np.log10(family["P_range_bara"][1]),
            family["n_P"]
        )

        for comp in compositions:
            for T, P in product(T_vals, P_vals):
                cases.append({
                    "case_id": f"{family['name'][:2].upper()}-{case_id:05d}",
                    "family": family["name"],
                    "components": comp,
                    "T_K": float(T),
                    "P_bara": float(P)
                })
                case_id += 1

    return cases
```

### Step 3: Run Single Flash Case

```python
import time
from tools.neqsim_bootstrap import get_jneqsim
jneqsim = get_jneqsim()

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

EOS_MAP = {
    "SRK": SystemSrkEos,
    "PR": SystemPrEos,
}

def run_flash_case(case, eos_name="SRK", timing_repeats=3):
    """Run a single TPflash and return metrics."""
    EosClass = EOS_MAP[eos_name]

    # Create fluid system
    fluid = EosClass(case["T_K"], case["P_bara"])
    for comp_name, frac in case["components"].items():
        fluid.addComponent(comp_name, frac)
    fluid.setMixingRule("classic")

    ops = ThermodynamicOperations(fluid)

    # Warmup run
    try:
        ops.TPflash()
    except Exception:
        pass

    # Timed runs
    times_ns = []
    for _ in range(timing_repeats):
        # Reset and re-flash
        fluid2 = fluid.clone()
        ops2 = ThermodynamicOperations(fluid2)

        t0 = time.perf_counter_ns()
        try:
            ops2.TPflash()
            elapsed = time.perf_counter_ns() - t0
            times_ns.append(elapsed)

            fluid2.initProperties()
            n_phases = int(fluid2.getNumberOfPhases())
            beta_vapor = float(fluid2.getBeta(0)) if n_phases > 0 else 0.0
            converged = True
            error = None
        except Exception as e:
            elapsed = time.perf_counter_ns() - t0
            times_ns.append(elapsed)
            n_phases = -1
            beta_vapor = -1.0
            converged = False
            error = str(e)

    median_time_ms = float(np.median(times_ns)) / 1e6

    return {
        "case_id": case["case_id"],
        "family": case["family"],
        "eos": eos_name,
        "T_K": case["T_K"],
        "P_bara": case["P_bara"],
        "converged": converged,
        "cpu_time_ms": round(median_time_ms, 4),
        "n_phases": n_phases,
        "beta_vapor": round(beta_vapor, 6) if beta_vapor >= 0 else None,
        "error": error
    }
```

### Step 4: Run Full Suite

```python
import json
import os
from pathlib import Path

def run_benchmark_suite(config, algorithm_name, results_dir):
    """Run the complete benchmark suite."""
    cases = generate_all_cases(config)
    results_path = Path(results_dir) / "raw"
    results_path.mkdir(parents=True, exist_ok=True)

    output_file = results_path / f"{algorithm_name}_results.jsonl"

    n_converged = 0
    n_total = 0
    failures = []

    with open(output_file, "w") as f:
        for i, case in enumerate(cases):
            result = run_flash_case(case, eos_name=config["eos_models"][0])
            result["algorithm"] = algorithm_name
            f.write(json.dumps(result) + "\n")

            n_total += 1
            if result["converged"]:
                n_converged += 1
            else:
                failures.append(result)

            if (i + 1) % 100 == 0:
                print(f"  Progress: {i+1}/{len(cases)} "
                      f"({n_converged}/{n_total} converged)")

    # Write summary
    summary = {
        "algorithm": algorithm_name,
        "eos": config["eos_models"][0],
        "total_cases": n_total,
        "converged": n_converged,
        "failed": n_total - n_converged,
        "convergence_rate_pct": round(100.0 * n_converged / n_total, 2)
    }

    with open(Path(results_dir) / f"summary_{algorithm_name}.json", "w") as f:
        json.dump(summary, f, indent=2)

    # Write failures
    with open(Path(results_dir) / f"failures_{algorithm_name}.json", "w") as f:
        json.dump(failures, f, indent=2)

    return summary
```

### Step 5: Record Metadata

```python
import platform
import subprocess

def record_metadata(results_dir):
    """Record benchmark environment metadata."""
    metadata = {
        "date": "2026-03-31",
        "hostname": platform.node(),
        "os": platform.platform(),
        "python": platform.python_version(),
        "java": "OpenJDK 17",  # or read from java -version
        "cpu": platform.processor(),
        "neqsim_version": "3.3.0",
        "random_seed": 42
    }
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True, text=True
        )
        metadata["git_commit"] = result.stdout.strip()
    except Exception:
        metadata["git_commit"] = "unknown"

    with open(Path(results_dir) / "benchmark_metadata.json", "w") as f:
        json.dump(metadata, f, indent=2)
```

## Output Files

| File | Format | Content |
|------|--------|---------|
| `raw/<algorithm>_results.jsonl` | JSONL | One result per line, all cases |
| `summary_<algorithm>.json` | JSON | Aggregate statistics |
| `failures_<algorithm>.json` | JSON | Failed case details |
| `benchmark_metadata.json` | JSON | Environment info |

## Error Handling

- **Java exception**: Catch, record error message, mark as failed
- **Timeout**: Set a 10-second per-case timeout
- **NaN/Inf results**: Detect and mark as failed
- **Memory issues**: Run in batches of 500, flush results to disk

## Performance Tips

- Use `fluid.clone()` instead of recreating from scratch
- Warm up the JVM before timing
- Report median of 3 runs, not mean (avoids GC outliers)
- Run baseline and candidate interleaved, not sequentially
