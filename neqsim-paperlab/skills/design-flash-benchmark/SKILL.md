# Skill: Design Flash Benchmark

## Purpose

Create a structured test matrix for comparing flash algorithm performance
across fluid types, thermodynamic conditions, and difficulty levels.

## When to Use

- Starting a new flash algorithm comparison study
- Extending an existing benchmark to new fluid families
- Designing stress tests for near-critical or multi-phase regions

## Benchmark Design Procedure

### Step 1: Select Fluid Families

Choose from these standard families:

| Family | Components | Mole Fractions | Characteristics |
|--------|-----------|----------------|-----------------|
| **Lean gas** | CH4(0.90), C2(0.05), C3(0.03), N2(0.01), CO2(0.01) | Fixed or ±10% | Easy, mostly single-phase |
| **Rich gas** | CH4(0.70), C2(0.10), C3(0.08), nC4(0.05), nC5(0.03), N2(0.02), CO2(0.02) | Fixed or ±15% | Moderate, clear two-phase |
| **Gas condensate** | CH4(0.65), C2(0.08), C3(0.06), nC4(0.04), nC5(0.03), nC6(0.02), nC7(0.02), nC10(0.05), N2(0.02), CO2(0.03) | ±20% | Near-critical behavior |
| **CO2-rich** | CO2(0.80), CH4(0.10), N2(0.05), H2S(0.03), C2(0.02) | ±15% | Strong non-ideality |
| **Wide-boiling** | CH4(0.50), nC4(0.15), nC10(0.15), nC16(0.10), nC20(0.10) | ±20% | Large volatility range |
| **Sour gas** | CH4(0.60), CO2(0.15), H2S(0.10), C2(0.08), C3(0.05), N2(0.02) | ±15% | Acid gas behavior |

### Step 2: Define PT Space

For each family, define the pressure-temperature sampling grid:

```python
import numpy as np

def generate_pt_grid(T_min_K, T_max_K, P_min_bara, P_max_bara, n_T=20, n_P=20):
    """Generate a regular PT grid."""
    T_values = np.linspace(T_min_K, T_max_K, n_T)
    P_values = np.logspace(np.log10(P_min_bara), np.log10(P_max_bara), n_P)
    cases = []
    for T in T_values:
        for P in P_values:
            cases.append({"T_K": float(T), "P_bara": float(P)})
    return cases
```

Standard ranges by family:

| Family | T range (K) | P range (bara) | Focus region |
|--------|------------|----------------|--------------|
| Lean gas | 200–400 | 1–200 | Dew point region |
| Rich gas | 220–450 | 5–300 | Two-phase dome |
| Gas condensate | 250–500 | 10–500 | Near cricondenbar |
| CO2-rich | 250–400 | 10–200 | CO2 critical region |
| Wide-boiling | 300–600 | 1–100 | Large T range |

### Step 3: Add Stress Cases

Add cases specifically designed to challenge the algorithm:

1. **Near bubble point**: T slightly above Tbub at given P
2. **Near dew point**: T slightly below Tdew at given P
3. **Near critical**: T ≈ Tc ± 5K, P ≈ Pc ± 5 bar
4. **Very low vapor fraction**: β ≈ 0.001
5. **Very high vapor fraction**: β ≈ 0.999
6. **Single phase (verification)**: Known single-phase conditions
7. **Trace components**: One component at < 1e-6 mole fraction

### Step 4: Define Composition Perturbation

Use Dirichlet sampling to generate composition variants:

```python
from numpy.random import dirichlet

def perturb_composition(base_comp, n_variants=10, concentration=50):
    """Generate composition variants around a base composition.

    Higher concentration = less perturbation.
    """
    names = list(base_comp.keys())
    alpha = np.array([base_comp[n] for n in names]) * concentration
    variants = []
    for _ in range(n_variants):
        x = dirichlet(alpha)
        variants.append(dict(zip(names, x.tolist())))
    return variants
```

### Step 5: Define Metrics

Every benchmark case must record:

| Metric | Type | Unit | How to Measure |
|--------|------|------|----------------|
| `converged` | bool | — | Did the flash converge? |
| `iterations` | int | — | Total iterations (SS + NR) |
| `ss_iterations` | int | — | Successive substitution iterations only |
| `nr_iterations` | int | — | Newton-Raphson iterations only |
| `cpu_time_ms` | float | ms | Wall-clock time (median of 3 runs) |
| `residual_norm` | float | — | Final norm of equilibrium residuals |
| `stability_tested` | bool | — | Was stability analysis triggered? |
| `stability_iters` | int | — | TPD minimization iterations |
| `n_phases` | int | — | Number of phases at equilibrium |
| `beta_vapor` | float | — | Vapor phase fraction |
| `phase_id_correct` | bool | — | Correct phase identification? |

### Step 6: Estimate Total Cases

Target: **500–2000 cases per algorithm version**.

| Component | Cases |
|-----------|-------|
| 6 families × 20 PT points | 120 base cases |
| 10 composition variants each | 1200 cases |
| 50 stress cases | 50 cases |
| Total | ~1250 cases |

### Step 7: Generate Config File

Output `benchmark_config.json`:

```json
{
  "benchmark_id": "tpflash_2026_01",
  "created": "2026-03-31",
  "algorithms": ["baseline", "candidate_eigenvalue_switch"],
  "eos_models": ["SRK"],
  "timing_repeats": 3,
  "families": [
    {
      "name": "lean_gas",
      "base_composition": {"methane": 0.90, "ethane": 0.05, "propane": 0.03, "nitrogen": 0.01, "CO2": 0.01},
      "n_composition_variants": 10,
      "dirichlet_concentration": 50,
      "T_range_K": [200, 400],
      "P_range_bara": [1, 200],
      "n_T": 20,
      "n_P": 20
    }
  ],
  "stress_cases": {
    "near_critical": 20,
    "near_bubble": 10,
    "near_dew": 10,
    "trace_component": 10
  }
}
```

## Checklist

- [ ] All components available in NeqSim database
- [ ] PT ranges cover the two-phase region for each family
- [ ] Stress cases are well-defined
- [ ] Metrics list is complete
- [ ] Total case count is between 500 and 2000
- [ ] Random seed is fixed for reproducibility
