---
name: neqsim-distillation-design
description: "Distillation column design rules for NeqSim. USE WHEN: setting up distillation columns, troubleshooting convergence, selecting internals (trays/packing), sizing columns, or analyzing column performance. Covers DistillationColumn setup, solver selection, feed tray optimization, reflux ratio, and internals selection per industry standards."
last_verified: "2026-07-04"
---

# Distillation Design Rules

Guide for distillation column modeling and design in NeqSim.

## Column Setup Pattern

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

// Create feed
SystemInterface feed = new SystemSrkEos(273.15 + 50.0, 15.0);
feed.addComponent("methane", 0.10);
feed.addComponent("ethane", 0.25);
feed.addComponent("propane", 0.30);
feed.addComponent("n-butane", 0.20);
feed.addComponent("n-pentane", 0.15);
feed.setMixingRule("classic");

Stream feedStream = new Stream("feed", feed);
feedStream.setFlowRate(10000.0, "kg/hr");
feedStream.setTemperature(50.0, "C");
feedStream.setPressure(15.0, "bara");
feedStream.run();

// Create column (name, stages, hasCondenser, hasReboiler)
DistillationColumn column = new DistillationColumn("Deethanizer", 15, true, true);
column.addFeedStream(feedStream, 8);  // Feed on stage 8

// Specifications
column.setCondenserTemperature(273.15 - 30.0);  // Kelvin
column.getReboiler().setHeatInput(1e6);          // Watts
// OR set reflux ratio:
// column.getCondenser().setRefluxRatio(2.5);

column.run();
```

## Solver Selection

```java
// Direct substitution (default) - robust sequential tray sweeps
column.setSolverType(DistillationColumn.SolverType.DIRECT_SUBSTITUTION);

// Inside-Out — faster for ideal/near-ideal systems
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);

// Adaptive matrix inside-out — bypasses matrix setup on small columns,
// tries a component-balance matrix warm start on larger columns, then
// finishes with rigorous inside-out polishing
column.setSolverType(DistillationColumn.SolverType.MATRIX_INSIDE_OUT);

// Damped substitution - for difficult convergence
column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);

// MESH residual monitor - for residual auditing
column.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);

// Naphtali-Sandholm - guarded simultaneous MESH residual Newton solver
column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
// Optional: seed stage temperatures as initial guesses only, not fixed specs
column.setSeedTemperature(3, 273.15 + 45.0);
// Adjust max iterations
column.setMaxNumberOfIterations(200);
```

### Runtime control: iteration budget, damping and tolerance

Three knobs decide how long a column runs before it gives up. Getting them wrong
is the usual cause of a column that burns minutes per solve inside a
`ProcessModel` outer loop.

```java
// 1. ITERATION BUDGET — setMaxNumberOfIterations(n) is only a SOFT FLOOR.
//    The effective budget is max(n, 5 * numberOfTrays) and can still be expanded
//    by the overflow/polish extensions. setMaxNumberOfIterations(10) on an
//    11-tray column therefore does NOT cap anything (11*5 = 55 -> ~187 with
//    overflow). NeqSim logs a warning when the request is below the tray floor.
column.setMaxNumberOfIterations(20, true);   // HARD cap (2-arg overload)
column.setHardIterationCap(true);            // or flip the flag separately
column.getEffectiveMaxNumberOfIterations();  // what the solver will actually use

// 2. DAMPING — the adaptive controller clamps the sequential step at
//    minSequentialRelaxation (default 0.5). setRelaxationFactor now lowers that
//    floor when you ask for heavier damping, so a request below 0.5 takes effect
//    instead of being silently clamped back. Use this to break limit cycles.
column.setRelaxationFactor(0.3);
column.setMinSequentialRelaxation(0.2);      // explicit floor if needed
column.setMinInsideOutRelaxation(0.2);       // inside-out tear streams

// 3. TOLERANCE — the default absolute temperature tolerance (~0.02-0.03 K) can
//    be ~10x tighter than the enclosing ProcessModel boundary gate (1e-3
//    relative ~ 0.27 K at 270 K), so the column keeps iterating on a residual
//    the plant model already accepts. Align them:
column.setTemperatureToleranceRelative(1.0e-3);  // returns the absolute K value
column.getReferenceTemperature();                // basis used for the conversion
```

Rule of thumb for a column inside a multi-area `ProcessModel`: set a hard
iteration cap, damp below 0.5 if the tray temperatures oscillate, and match the
column tolerance to the model tolerance passed to
`ProcessModel.runUntilConverged(maxIter, tol)`.

### What `solved()` actually checks

`solved()` is not a single residual. It requires the solve status to be
`RIGOROUS_CONVERGED` or `RECONCILED_PRODUCTS` **and** every active residual gate
to pass:

| Gate | Source | Default tolerance |
|------|--------|-------------------|
| Temperature | mean per-tray change of the last sweep | `~0.02 K x complexity`; `NaN` for simultaneous solvers, which then require the MESH gate instead |
| Mass | external feed/product balance | `~0.016 x complexity` |
| Energy | tray enthalpy balance | not enforced by default (`setEnforceEnergyBalanceTolerance(true)`) |
| Internal traffic | max tray traffic / feed | 100 |
| Per-tray material balance | `getLastTrayMaterialBalanceError()` — summed absolute tray imbalance / tray throughput | `getTrayMaterialBalanceTolerance()`, default `0.02` |
| MESH infinity norm | all residual families | `getMeshResidualTolerance()`, default `1.0` |

The overall feed/product balance closing to machine precision does **not** mean
the column solved: each tray must close its own component balance too. Read
`per-tray material imbalance` in `getConvergenceDiagnostics()` before trusting
duties or a tray profile.

> Do not gate on the MESH `material:` infinity norm. Those entries scale each
> component by its own throughput, so a trace component going from 1e-25 to
> 1.2e-25 mol/hr reads as a 0.17 residual. Use
> `getLastTrayMaterialBalanceError()`, which weights by tray throughput.

### Solver Selection Guide

| System Type | Recommended Solver | Notes |
|------------|-------------------|-------|
| Ideal HC (demethanizer, deethanizer) | `INSIDE_OUT` | Fast, robust |
| Larger HC fractionators | `MATRIX_INSIDE_OUT` | Adaptive: small columns bypass matrix overhead; larger columns try the matrix warm start before rigorous inside-out polishing |
| Non-ideal (alcohols, water) | `DAMPED_SUBSTITUTION` or `DIRECT_SUBSTITUTION` | More conservative for non-ideal K-values |
| Absorbers (no condenser/reboiler) | `SUM_RATES` or `DIRECT_SUBSTITUTION` | Flow-corrected updates can help absorber/stripper cases |
| Wide-boiling (C1 to C20+) | `DAMPED_SUBSTITUTION` | Increase iterations and monitor residuals |
| Cryogenic (< -100°C) | `INSIDE_OUT` with residual diagnostics | Careful with phase identification |
| Solver audit / residual convergence | `MESH_RESIDUAL` or `NAPHTALI_SANDHOLM` | `MESH_RESIDUAL` records diagnostics and enforces the MESH/product-draw gate by default; `NAPHTALI_SANDHOLM` attempts guarded simultaneous MESH correction |

## Column Specification Combinations

| Bottom Spec | Top Spec | Comment |
|------------|---------|---------|
| Reboiler duty | Condenser temperature | Most common |
| Reboiler duty | Reflux ratio | Alternative |
| Top product purity | Reboiler duty | Product-quality control |
| Bottom temperature | Reflux ratio | Direct T control |

```java
// Common specification patterns

// Pattern 1: Condenser T + Reboiler duty
column.setCondenserTemperature(273.15 - 30.0);
column.getReboiler().setHeatInput(1.5e6);

// Pattern 2: Reflux ratio + Reboiler duty
column.getCondenser().setRefluxRatio(3.0);
column.getReboiler().setHeatInput(2.0e6);

// Pattern 3: Product quality plus boilup ratio
column.setTopProductPurity("ethane", 0.98);
column.setReboilerBoilupRatio(2.0);
```

## Reading Column Results

```java
column.run();

// Condenser and reboiler duties
double condenserDuty = column.getCondenser().getDuty();  // Watts
double reboilerDuty = column.getReboiler().getDuty();    // Watts

// Product streams
Stream overhead = (Stream) column.getGasOutStream();
Stream bottoms = (Stream) column.getLiquidOutStream();

// Stage temperatures and compositions
for (int stage = 0; stage < column.getTrays().size(); stage++) {
    double stageTemp = column.getTray(stage).getTemperature() - 273.15;
    // Composition on each stage
}

// Convergence metrics
int iterations = column.getLastIterationCount();
double massResidual = column.getLastMassResidual();
double energyResidual = column.getLastEnergyResidual();
boolean matrixWarmStartUsed = column.wasMatrixInsideOutWarmStartUsed();
boolean matrixWarmStartBypassed = column.wasMatrixInsideOutWarmStartBypassed();
int matrixIterations = column.getLastMatrixInsideOutIterationCount();
```

## Feed Tray Location Rules

### Kirkbride Correlation

For binary or pseudo-binary separations:

$$
\log\left(\frac{N_R}{N_S}\right) = 0.206 \log\left[\left(\frac{B}{D}\right) \left(\frac{x_{HK,F}}{x_{LK,F}}\right)^2 \left(\frac{x_{LK,B}}{x_{HK,D}}\right)^2 \right]
$$

Where $N_R$ = rectifying stages, $N_S$ = stripping stages, $B/D$ = bottoms/distillate ratio.

### Rules of Thumb

| Column Type | Feed Tray (from top) | Notes |
|------------|---------------------|-------|
| Demethanizer | 40-60% of stages | Light key is very volatile |
| Deethanizer | 50-70% of stages | Moderate volatility |
| Depropanizer | 40-60% of stages | Balanced separation |
| Debutanizer | 50-60% of stages | Similar to depropanizer |
| Crude column | 60-80% of stages | Flash zone near bottom |

## Minimum Stages and Reflux

### Fenske Equation (Minimum Stages)

$$
N_{min} = \frac{\log\left(\frac{x_{LK,D}}{x_{HK,D}} \cdot \frac{x_{HK,B}}{x_{LK,B}}\right)}{\log(\alpha_{LK/HK})}
$$

### Underwood Equation (Minimum Reflux)

$$
R_{min} = \frac{1}{\alpha - 1}\left(\frac{x_D}{\alpha - \theta} - \frac{1 - x_D}{1 - \theta}\right)
$$

### Design Heuristics

- Actual stages ≈ 2 × minimum stages (Gilliland correlation)
- Actual reflux ≈ 1.2-1.5 × minimum reflux
- Stage efficiency: 50-70% for trays, 70-90% HETP/stage for packing

## Convergence Troubleshooting

| Problem | Solution |
|---------|----------|
| Column does not converge | Increase max iterations to 200-500 |
| Oscillating temperature profile | Reduce condenser/reboiler specs, use `DAMPED_SUBSTITUTION`; audit with `MESH_RESIDUAL` before trying `NAPHTALI_SANDHOLM` |
| Wrong product split | Check feed tray location and specifications |
| Negative flows on stages | Too many stages or wrong specifications |
| Condenser too cold | Check if subcooled liquid is physical (binary dewpoint) |
| Reboiler too hot | May be decomposing — check component stability |

### Steps to Debug

1. Start with fewer stages (5-8) and get convergence
2. Gradually increase stages
3. Use liberal specifications first (higher reflux), then tighten
4. Check feed condition (vapor fraction) — subcooled feed may need enthalpy adjustment
5. Verify component K-values make physical sense at column conditions

## Column Sizing (Diameter)

### Souders-Brown Correlation

$$
V_{flood} = K_{SB} \sqrt{\frac{\rho_L - \rho_V}{\rho_V}}
$$

Where $K_{SB}$ = 0.03-0.07 m/s for trays, 0.02-0.05 for packing.

Design velocity = 70-85% of flooding.

```java
// After running column, get phase properties for sizing
SystemInterface topFluid = column.getTray(0).getFluid();
topFluid.initProperties();
double rhoV = topFluid.getPhase("gas").getDensity("kg/m3");
double rhoL = topFluid.getPhase("oil").getDensity("kg/m3");

double Ksb = 0.05;  // m/s for sieve trays
double Vflood = Ksb * Math.sqrt((rhoL - rhoV) / rhoV);
double Vdesign = 0.80 * Vflood;

double gasFlow = column.getGasOutStream().getFlowRate("m3/hr") / 3600.0;
double area = gasFlow / Vdesign;
double diameter = Math.sqrt(4.0 * area / Math.PI);
```

## Internals Selection

| Internals | When to Use | Typical HETP (m) |
|-----------|------------|-------------------|
| Sieve trays | General service, fouling | 0.5-0.7 |
| Valve trays | Variable turndown | 0.4-0.6 |
| Bubble cap trays | Low liquid rates | 0.5-0.8 |
| Random packing (Pall rings) | Low pressure drop, corrosive | 0.3-0.6 |
| Structured packing (Mellapak) | Vacuum, low ΔP | 0.2-0.5 |

## Common Pitfalls

1. **Feed flash**: Ensure feed is at correct T/P for column conditions
2. **Missing components**: All components in feed must be present in EOS
3. **Mixing rule**: Always set before column construction
4. **Heavy key in top / light key in bottom**: Small amounts are normal — zero means perfect separation (unrealistic)
5. **Column pressure profile**: Default is constant — set stage pressures for realistic profile
6. **Condenser type**: Total vs partial condenser changes mass balance
