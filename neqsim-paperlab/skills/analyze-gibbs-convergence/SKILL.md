# Skill: Analyze Gibbs Convergence

## Purpose

Interpret Gibbs energy minimization convergence metrics, analyze Jacobian
conditioning, verify element balance closure, and produce publication-quality
figures for chemical equilibrium papers.

## When to Use

- After running Gibbs reactor benchmark experiments
- When analyzing convergence of Newton-Raphson Gibbs minimization
- When comparing solver variants (baseline vs optimized)
- When investigating failure cases in chemical equilibrium

## Analysis Procedure

### Step 1: Load and Parse Results

```python
import json
import pandas as pd
import numpy as np

def load_reactor_results(results_dir, solver_name):
    """Load JSONL results into DataFrame."""
    records = []
    with open(f"{results_dir}/raw/{solver_name}_results.jsonl") as f:
        for line in f:
            records.append(json.loads(line))
    return pd.DataFrame(records)
```

### Step 2: Equilibrium Composition vs Temperature

The most important figure for a chemical equilibrium paper:

```python
import matplotlib.pyplot as plt

def plot_equilibrium_composition(df, system_name, save_path):
    """Plot equilibrium mole fractions vs temperature for all species."""
    fig, ax = plt.subplots(figsize=(10, 7))

    species = [col for col in df.columns if col.startswith("n_")]

    for species_col in species:
        name = species_col.replace("n_", "")
        ax.semilogy(df["T_K"] - 273.15, df[species_col],
                     label=name, linewidth=2)

    ax.set_xlabel("Temperature (°C)", fontsize=12)
    ax.set_ylabel("Equilibrium mole fraction", fontsize=12)
    ax.set_title(f"Chemical Equilibrium — {system_name}", fontsize=14)
    ax.legend(loc="best", fontsize=10)
    ax.grid(True, alpha=0.3)
    ax.set_ylim(bottom=1e-12)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 3: Convergence Iteration Analysis

```python
def plot_iteration_heatmap(df, save_path):
    """Heatmap of iteration count in T-P space."""
    fig, ax = plt.subplots(figsize=(10, 7))

    pivot = df.pivot_table(values="iterations", index="P_bara",
                           columns="T_K", aggfunc="mean")

    im = ax.pcolormesh(pivot.columns - 273.15, pivot.index,
                        pivot.values, cmap="YlOrRd", shading="auto")
    plt.colorbar(im, ax=ax, label="Iterations")

    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bara)")
    ax.set_title("Newton Iteration Count")
    ax.set_yscale("log")

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 4: Element Balance Verification

Critical for validating Gibbs reactor correctness:

```python
def verify_element_balance(feed_composition, product_composition,
                            element_matrix):
    """Verify element conservation.

    element_matrix: dict mapping element -> {species: count}
    Example: {"H": {"H2S": 2, "H2O": 2, "H2": 2}, "S": {"H2S": 1, "S8": 8}}
    """
    results = {}
    for element, species_counts in element_matrix.items():
        feed_total = sum(feed_composition.get(sp, 0) * count
                         for sp, count in species_counts.items())
        prod_total = sum(product_composition.get(sp, 0) * count
                         for sp, count in species_counts.items())

        if feed_total > 0:
            rel_error = abs(feed_total - prod_total) / feed_total
        else:
            rel_error = 0.0 if prod_total == 0 else float("inf")

        results[element] = {
            "feed": feed_total,
            "product": prod_total,
            "relative_error": rel_error
        }
    return results

def plot_element_balance_closure(df, save_path):
    """Plot element balance errors across all cases."""
    fig, ax = plt.subplots(figsize=(8, 6))

    elements = [col for col in df.columns if col.startswith("elem_err_")]

    for col in elements:
        elem_name = col.replace("elem_err_", "").upper()
        errors = df[col].replace(0, 1e-16)  # avoid log(0)
        ax.semilogy(range(len(errors)), sorted(errors),
                     label=elem_name, linewidth=2)

    ax.axhline(y=1e-10, color="red", linestyle="--",
               alpha=0.5, label="Target (1e-10)")
    ax.set_xlabel("Case index (sorted)")
    ax.set_ylabel("Relative element balance error")
    ax.set_title("Element Balance Closure")
    ax.legend()
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 5: Jacobian Condition Number Analysis

```python
def plot_jacobian_conditioning(df, save_path):
    """Analyze Jacobian conditioning across conditions."""
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    # Condition number vs temperature
    ax = axes[0]
    ax.scatter(df["T_K"] - 273.15, df["jacobian_cond_number"],
               c=df["iterations"], cmap="viridis", alpha=0.5, s=20)
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("log₁₀(Condition Number)")
    ax.set_title("Jacobian Conditioning vs Temperature")
    ax.grid(True, alpha=0.3)

    # Condition number vs iterations
    ax = axes[1]
    ax.scatter(df["jacobian_cond_number"], df["iterations"],
               alpha=0.3, s=20)
    ax.set_xlabel("log₁₀(Condition Number)")
    ax.set_ylabel("Iterations to Convergence")
    ax.set_title("Conditioning vs Convergence Speed")
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 6: Adiabatic vs Isothermal Comparison

```python
def plot_adiabatic_vs_isothermal(df_iso, df_adi, save_path):
    """Compare equilibrium outcomes between modes."""
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))

    # Temperature change in adiabatic mode
    ax = axes[0]
    dT = df_adi["outlet_T_K"] - df_adi["T_K"]
    ax.scatter(df_adi["T_K"] - 273.15, dT, alpha=0.5, s=20)
    ax.axhline(y=0, color="black", linestyle="-", alpha=0.3)
    ax.set_xlabel("Feed Temperature (°C)")
    ax.set_ylabel("ΔT (K)")
    ax.set_title("Adiabatic Temperature Change")
    ax.grid(True, alpha=0.3)

    # Iteration comparison
    ax = axes[1]
    ax.scatter(df_iso["iterations"], df_adi["iterations"], alpha=0.3, s=20)
    max_iter = max(df_iso["iterations"].max(), df_adi["iterations"].max())
    ax.plot([0, max_iter], [0, max_iter], "k--", alpha=0.5)
    ax.set_xlabel("Isothermal Iterations")
    ax.set_ylabel("Adiabatic Iterations")
    ax.set_title("Iteration Cost: Adiabatic vs Isothermal")
    ax.grid(True, alpha=0.3)
    ax.set_aspect("equal")

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 7: Trace Species Behavior

```python
def plot_trace_species(df, species_list, save_path):
    """Show how trace species evolve with temperature on log scale."""
    fig, ax = plt.subplots(figsize=(10, 7))

    for species in species_list:
        col = f"n_{species}"
        if col in df.columns:
            vals = df[col].replace(0, np.nan)
            ax.semilogy(df["T_K"] - 273.15, vals,
                         label=species, linewidth=2, marker="o", markersize=3)

    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Equilibrium moles")
    ax.set_title("Trace Species at Chemical Equilibrium")
    ax.legend()
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

### Step 8: Validation Against Reference Data

```python
def plot_validation_parity(neqsim_results, reference_results,
                            species_name, save_path):
    """Parity plot: NeqSim vs reference (NASA CEA, JANAF, etc.)."""
    fig, ax = plt.subplots(figsize=(7, 7))

    ax.scatter(reference_results, neqsim_results, s=40, alpha=0.7,
               edgecolors="black", linewidths=0.5)

    # Diagonal
    lims = [min(min(reference_results), min(neqsim_results)),
            max(max(reference_results), max(neqsim_results))]
    ax.plot(lims, lims, "k-", alpha=0.5, label="Perfect agreement")

    # ±10% bands
    ax.plot(lims, [l * 1.1 for l in lims], "r--", alpha=0.3, label="±10%")
    ax.plot(lims, [l * 0.9 for l in lims], "r--", alpha=0.3)

    ax.set_xlabel(f"Reference {species_name}")
    ax.set_ylabel(f"Calculated {species_name}")
    ax.set_title(f"Validation: {species_name}")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.set_aspect("equal")

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches="tight")
    plt.close()
```

## Figure Catalog for Gibbs Reactor Papers

| Figure | Shows | Section |
|--------|-------|---------|
| Equilibrium composition vs T | Species distribution at equilibrium | Results |
| Iteration heatmap (T-P) | Where solver works hard | Results |
| Element balance closure | Conservation law verification | Validation |
| Jacobian conditioning | Numerical stability | Discussion |
| Adiabatic vs isothermal | Mode comparison | Results |
| Trace species | Low-abundance species behavior | Results |
| Parity plot vs reference | Accuracy validation | Validation |
| Convergence history | Residual vs iteration for selected cases | Methods/Discussion |

## Table Catalog

| Table | Shows | Section |
|-------|-------|---------|
| Reaction systems tested | Feed, products, conditions | Methods |
| Convergence summary | Rate, iterations, timing by system | Results |
| Element balance summary | Max error per element per system | Validation |
| Reference comparison | AAD% vs NASA CEA / JANAF | Validation |
| Solver variant comparison | If comparing solver settings | Results |

## Key Validation Criteria

For a Gibbs reactor paper to be credible:

1. **Element balance**: Relative error < 1e-10 for ALL elements in ALL cases
2. **Gibbs energy**: Total G strictly decreases (or unchanged) each iteration
3. **Reference agreement**: AAD < 5% vs NASA CEA for major species at equilibrium
4. **Trace species**: Non-negative mole numbers (no unphysical negative values)
5. **Mass balance**: |mass_in - mass_out| / mass_in < 1e-10
