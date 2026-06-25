# Skill: Design Reactor / Chemical Equilibrium Benchmark

## Purpose

Create a structured test matrix for evaluating Gibbs energy minimization
solvers across reaction systems, conditions, and difficulty levels.

## When to Use

- Starting a paper on chemical equilibrium algorithms (Gibbs reactor)
- Benchmarking Jacobian formulations or solver improvements
- Comparing Gibbs minimization against reference solutions (JANAF/NASA CEA)
- Evaluating convergence for reactive systems with trace species

## Benchmark Design Procedure

### Step 1: Select Reaction Systems

Choose test systems that span different thermochemical challenges:

| System | Feed Components | Key Products | Nc | Challenge |
|--------|----------------|-------------|----:|-----------|
| **Claus (direct)** | H2S, O2, N2 | H2O, S8, SO2 | 5+ | Sulfur precipitation, trace species |
| **Claus (two-stage)** | H2S, O2 → SO2; then H2S + SO2 | S, H2O | 5+ | Multi-reactor, intermediate species |
| **Methane combustion** | CH4, O2, N2 | CO2, H2O, CO, NO | 7+ | High temperature, many products |
| **Steam methane reforming** | CH4, H2O | CO, H2, CO2 | 5 | Endothermic, equilibrium-limited |
| **Water-gas shift** | CO, H2O | CO2, H2 | 4 | Temperature-sensitive equilibrium |
| **Ammonia synthesis** | N2, H2 | NH3 | 3 | High pressure, sparse products |
| **CO2 hydrogenation** | CO2, H2 | CH3OH, H2O, CO | 5 | Catalyst-dependent selectivity |
| **Iron sulfide corrosion** | Fe, H2S | FeS, H2 | 4 | Solid product formation |
| **Sour gas sweetening** | H2S, CO2, CH4, MEA | Various | 8+ | Acid gas + amine chemistry |

### Step 2: Define Condition Sweeps

For each system, define the parameter space:

```python
import numpy as np

def generate_reactor_conditions(system):
    """Generate test conditions for a reaction system."""
    cases = []

    # Temperature sweep (most important for equilibrium)
    T_values = np.linspace(system["T_min_K"], system["T_max_K"], system["n_T"])

    # Pressure sweep
    P_values = np.logspace(
        np.log10(system["P_min_bara"]),
        np.log10(system["P_max_bara"]),
        system["n_P"]
    )

    # Feed composition perturbations
    for T in T_values:
        for P in P_values:
            # Stoichiometric feed
            cases.append({"T_K": float(T), "P_bara": float(P),
                          "feed": system["stoichiometric_feed"],
                          "label": "stoichiometric"})
            # Excess reactant A
            cases.append({"T_K": float(T), "P_bara": float(P),
                          "feed": system["excess_A_feed"],
                          "label": "excess_A"})
            # Excess reactant B
            cases.append({"T_K": float(T), "P_bara": float(P),
                          "feed": system["excess_B_feed"],
                          "label": "excess_B"})
    return cases
```

Standard ranges by system:

| System | T range (K) | P range (bara) | Key variable |
|--------|------------|----------------|--------------|
| Claus | 400–1200 | 1–50 | O2/H2S ratio |
| Combustion | 800–2500 | 1–50 | Equivalence ratio |
| Steam reforming | 600–1200 | 1–50 | Steam/carbon ratio |
| Water-gas shift | 400–800 | 1–50 | CO/H2O ratio |
| Ammonia | 400–800 | 50–300 | N2/H2 ratio |

### Step 3: Add Stress Cases

Stress cases specific to Gibbs minimization:

1. **Trace species challenge**: Initial guess with < 1e-8 mol for a species
   that should be significant at equilibrium (tests step sizing)
2. **Near-complete reaction**: Conditions where one reactant is nearly consumed
   (tests handling of near-zero mole numbers)
3. **Inert dilution**: 0%, 50%, 90%, 99% N2 dilution (tests scaling with dilution)
4. **Adiabatic vs isothermal**: Same feed at both modes (tests energy balance coupling)
5. **Phase boundary**: Conditions near condensation/solidification of products
6. **High-temperature dissociation**: T > 2000 K where minor species become significant
7. **Ill-conditioned Jacobian**: Systems with species spanning 10+ orders of magnitude

### Step 4: Define Metrics

Every reactor benchmark case must record:

| Metric | Type | Unit | How to Measure |
|--------|------|------|----------------|
| `converged` | bool | — | Did the solver converge? |
| `iterations` | int | — | Newton iterations to convergence |
| `cpu_time_ms` | float | ms | Wall-clock time |
| `final_residual_norm` | float | — | ||F|| at convergence |
| `element_balance_error` | float | — | Max relative element imbalance |
| `gibbs_energy_J_mol` | float | J/mol | Total Gibbs energy at equilibrium |
| `jacobian_cond_number` | float | — | log10(condition number) at convergence |
| `n_species_converged` | int | — | Species with n > 1e-20 at equilibrium |
| `min_mole_number` | float | mol | Smallest non-zero species amount |
| `max_lambda` | float | — | Largest Lagrange multiplier magnitude |
| `mode` | string | — | "isothermal" or "adiabatic" |
| `outlet_T_K` | float | K | Outlet temperature (adiabatic mode) |
| `energy_balance_error` | float | — | |H_in - H_out - Q| / |H_in| |

### Step 5: Reference Solutions

For validation, compare against:

| Source | Coverage | Access |
|--------|----------|--------|
| **NASA CEA** | Combustion, high-T equilibrium | Free online tool (glenn.nasa.gov) |
| **JANAF Tables** | Standard Gibbs free energy of formation | Published tables |
| **Aspen Plus** | Industrial reaction systems | Licensed software |
| **Cantera** | Open-source chemical kinetics / equilibrium | Free Python package |
| **HSC Chemistry** | General thermochemical equilibrium | Licensed software |

For each system, obtain at least 5 reference points at different conditions.

### Step 6: Generate Config File

Output `benchmark_config.json`:

```json
{
  "benchmark_id": "gibbs_reactor_2026",
  "created": "2026-03-31",
  "solver_variants": [
    {
      "name": "baseline",
      "description": "GibbsReactor with default settings",
      "settings": {"minIterations": 100, "adaptiveStepSize": false}
    },
    {
      "name": "optimized",
      "description": "GibbsReactor with adaptive step + min_iter=3",
      "settings": {"minIterations": 3, "adaptiveStepSize": true}
    }
  ],
  "reaction_systems": [
    {
      "name": "claus_direct",
      "feed_components": {"H2S": 0.10, "oxygen": 0.05, "nitrogen": 0.85},
      "expected_products": ["H2O", "S8", "SO2"],
      "T_range_K": [400, 1200],
      "P_range_bara": [1, 50],
      "n_T": 10,
      "n_P": 5,
      "perturbations": ["stoichiometric", "excess_H2S", "excess_O2", "diluted"],
      "reference_source": "NASA CEA"
    }
  ],
  "stress_cases": {
    "trace_species": 10,
    "near_complete_reaction": 10,
    "high_dilution": 10,
    "ill_conditioned": 5
  }
}
```

## Python Execution Pattern for Reactor Benchmarks

```python
from tools.neqsim_bootstrap import get_jneqsim
jneqsim = get_jneqsim()

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
GibbsReactor = jneqsim.process.equipment.reactor.GibbsReactor

def run_gibbs_reactor_case(case, mode="isothermal"):
    """Run a single Gibbs reactor case and return metrics."""
    import time

    fluid = SystemSrkEos(case["T_K"], case["P_bara"])
    for comp, frac in case["feed"].items():
        fluid.addComponent(comp, frac)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)

    feed = Stream("feed", fluid)
    feed.setFlowRate(1000.0, "kg/hr")
    feed.run()

    reactor = GibbsReactor("gibbs", feed)
    if mode == "adiabatic":
        reactor.setEnergyMode(
            jneqsim.process.equipment.reactor.GibbsReactor.EnergyMode.ADIABATIC)

    # Configure solver
    reactor.setMinIterations(3)
    reactor.setUseAdaptiveStepSize(True)

    process = ProcessSystem()
    process.add(feed)
    process.add(reactor)

    t0 = time.perf_counter_ns()
    try:
        process.run()
        elapsed_ms = (time.perf_counter_ns() - t0) / 1e6

        out_fluid = reactor.getOutletStream().getFluid()
        return {
            "converged": reactor.hasConverged(),
            "iterations": reactor.getActualIterations(),
            "cpu_time_ms": round(elapsed_ms, 3),
            "outlet_T_K": round(float(out_fluid.getTemperature()), 2),
            "n_phases": int(out_fluid.getNumberOfPhases()),
            "error": None
        }
    except Exception as e:
        elapsed_ms = (time.perf_counter_ns() - t0) / 1e6
        return {
            "converged": False,
            "cpu_time_ms": round(elapsed_ms, 3),
            "error": str(e)
        }
```

## Checklist

- [ ] At least 3 reaction systems selected
- [ ] Temperature and pressure ranges cover the relevant equilibrium region
- [ ] Stress cases include trace species and ill-conditioned scenarios
- [ ] Reference solutions from NASA CEA or published data are identified
- [ ] Element balance verification is included in metrics
- [ ] Both isothermal and adiabatic modes are tested
- [ ] Total case count is between 200 and 1000
- [ ] Random seed is fixed for composition perturbations
