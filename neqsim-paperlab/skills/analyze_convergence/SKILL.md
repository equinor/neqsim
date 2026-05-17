# Skill: Analyze Convergence

## Purpose

Interpret flash algorithm convergence metrics, identify patterns, and produce
publication-quality analysis for the Results section.

## When to Use

- After running benchmark experiments
- When comparing baseline vs candidate algorithms
- When investigating failure cases

## Analysis Procedure

### Step 1: Load and Parse Results

```python
import json
import pandas as pd

def load_results(results_dir, algorithm_name):
    """Load JSONL results into DataFrame."""
    records = []
    with open(f"{results_dir}/raw/{algorithm_name}_results.jsonl") as f:
        for line in f:
            records.append(json.loads(line))
    return pd.DataFrame(records)
```

### Step 2: Convergence Rate by Family

```python
def convergence_by_family(df):
    """Calculate convergence rate per fluid family."""
    return df.groupby("family").agg(
        total=("converged", "count"),
        converged=("converged", "sum"),
        rate_pct=("converged", lambda x: round(100 * x.mean(), 2)),
        median_time_ms=("cpu_time_ms", "median")
    ).reset_index()
```

### Step 3: Convergence Maps

Generate 2D convergence maps in (T, P) space:

```python
import matplotlib.pyplot as plt
import numpy as np

def plot_convergence_map(df, family_name, algorithm_name, save_path):
    """Plot convergence success/failure in TP space."""
    fam = df[df["family"] == family_name]

    fig, ax = plt.subplots(figsize=(8, 6))

    conv = fam[fam["converged"] == True]
    fail = fam[fam["converged"] == False]

    ax.scatter(conv["T_K"] - 273.15, conv["P_bara"],
               c="green", alpha=0.3, s=10, label="Converged")
    ax.scatter(fail["T_K"] - 273.15, fail["P_bara"],
               c="red", alpha=0.8, s=20, marker="x", label="Failed")

    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bara)")
    ax.set_title(f"Convergence Map — {family_name} — {algorithm_name}")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.set_yscale("log")

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 4: Iteration Comparison

```python
def plot_iteration_comparison(df_base, df_cand, family_name, save_path):
    """Compare iteration counts between algorithms."""
    # Merge on case_id for paired comparison
    merged = df_base.merge(df_cand, on="case_id", suffixes=("_base", "_cand"))

    # Only cases where both converged
    both = merged[(merged["converged_base"]) & (merged["converged_cand"])]

    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    # Histogram comparison
    ax = axes[0]
    bins = np.arange(0, 50, 1)
    ax.hist(both["iterations_base"], bins=bins, alpha=0.5,
            label="Baseline", color="blue")
    ax.hist(both["iterations_cand"], bins=bins, alpha=0.5,
            label="Candidate", color="orange")
    ax.set_xlabel("Iterations")
    ax.set_ylabel("Count")
    ax.set_title(f"Iteration Distribution — {family_name}")
    ax.legend()
    ax.grid(True, alpha=0.3)

    # Parity plot
    ax = axes[1]
    ax.scatter(both["iterations_base"], both["iterations_cand"],
               alpha=0.3, s=10)
    max_iter = max(both["iterations_base"].max(), both["iterations_cand"].max())
    ax.plot([0, max_iter], [0, max_iter], "k--", alpha=0.5, label="y = x")
    ax.set_xlabel("Baseline Iterations")
    ax.set_ylabel("Candidate Iterations")
    ax.set_title(f"Iteration Parity — {family_name}")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.set_aspect("equal")

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 5: Timing Analysis

```python
def plot_timing_comparison(df_base, df_cand, save_path):
    """Box plot of timing by family."""
    fig, ax = plt.subplots(figsize=(10, 6))

    families = sorted(df_base["family"].unique())
    positions = np.arange(len(families))
    width = 0.35

    base_times = [df_base[df_base["family"] == f]["cpu_time_ms"].values
                  for f in families]
    cand_times = [df_cand[df_cand["family"] == f]["cpu_time_ms"].values
                  for f in families]

    bp1 = ax.boxplot(base_times, positions=positions - width/2,
                     widths=width, patch_artist=True,
                     boxprops=dict(facecolor="lightblue"))
    bp2 = ax.boxplot(cand_times, positions=positions + width/2,
                     widths=width, patch_artist=True,
                     boxprops=dict(facecolor="lightsalmon"))

    ax.set_xticks(positions)
    ax.set_xticklabels(families, rotation=45, ha="right")
    ax.set_ylabel("CPU Time (ms)")
    ax.set_title("Flash Timing Comparison by Family")
    ax.legend([bp1["boxes"][0], bp2["boxes"][0]], ["Baseline", "Candidate"])
    ax.grid(True, alpha=0.3, axis="y")

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 6: Failure Analysis

```python
def analyze_failures(df_base, df_cand):
    """Identify cases where algorithms disagree."""
    merged = df_base.merge(df_cand, on="case_id", suffixes=("_base", "_cand"))

    # Cases candidate fixes
    fixed = merged[(~merged["converged_base"]) & (merged["converged_cand"])]

    # Cases candidate breaks
    broken = merged[(merged["converged_base"]) & (~merged["converged_cand"])]

    # Cases both fail
    both_fail = merged[(~merged["converged_base"]) & (~merged["converged_cand"])]

    return {
        "fixed_by_candidate": len(fixed),
        "broken_by_candidate": len(broken),
        "both_fail": len(both_fail),
        "fixed_cases": fixed["case_id"].tolist(),
        "broken_cases": broken["case_id"].tolist()
    }
```

### Step 7: Statistical Summary Table

Generate a table suitable for the paper:

```python
def generate_results_table(df_base, df_cand):
    """Generate the main comparison table."""
    rows = []
    for family in sorted(df_base["family"].unique()):
        b = df_base[df_base["family"] == family]
        c = df_cand[df_cand["family"] == family]
        rows.append({
            "Family": family,
            "N": len(b),
            "Conv_base_%": round(100 * b["converged"].mean(), 1),
            "Conv_cand_%": round(100 * c["converged"].mean(), 1),
            "Iter_base_med": b[b["converged"]]["iterations"].median(),
            "Iter_cand_med": c[c["converged"]]["iterations"].median(),
            "Time_base_ms": round(b[b["converged"]]["cpu_time_ms"].median(), 2),
            "Time_cand_ms": round(c[c["converged"]]["cpu_time_ms"].median(), 2),
        })
    return pd.DataFrame(rows)
```

## Figure Catalog

Every paper should include:

| Figure | Shows | Section |
|--------|-------|---------|
| Convergence maps (per family) | Where in TP space algorithms succeed/fail | Results |
| Iteration histograms | Distribution comparison | Results |
| Iteration parity plot | Paired case comparison | Results |
| Timing box plots | Speed comparison by family | Results |
| Failure regions | Where failures cluster | Discussion |
| Improvement map | Where candidate improves over baseline | Results |

## Table Catalog

| Table | Shows | Section |
|-------|-------|---------|
| Algorithm summary | Settings, description | Methods |
| Fluid family definitions | Components, ranges | Methods |
| Main results | Conv rate, iterations, timing by family | Results |
| Statistical tests | p-values, effect sizes | Results |
| Failure catalog excerpt | Notable failure cases | Discussion |
