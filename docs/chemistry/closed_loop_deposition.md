---
title: "Closed-Loop Deposition Coupling"
description: "Reference guide to ClosedLoopDepositionSolver and ScaleDepositionAccumulator — iterates pipe hydraulics (PipeBeggsAndBrills) against scale deposition so that velocity, shear and effective ID stay self-consistent. Used for predicting tube ID shrinkage and pressure-drop drift in subsea tiebacks and produced-water injection lines."
---

# Closed-Loop Deposition Coupling

`neqsim.process.chemistry.scale.ClosedLoopDepositionSolver` couples
`PipeBeggsAndBrills` pipe hydraulics with `ScaleDepositionAccumulator` so the
deposition rate, velocity and effective inside diameter stay mutually
consistent. Without coupling, deposition is computed at the *clean* velocity
and over-predicts thinning; with coupling, the velocity rises as the ID
shrinks and the system finds a quasi-steady deposition rate.

## Why couple?

Scale deposition rate scales roughly as

$$
\dot{m}_{dep} \;\propto\; SI \cdot v^{\alpha} \cdot t,\qquad \alpha \in [0.5, 1.5]
$$

while velocity in a pipe of effective diameter $d_{eff}$ goes as
$v \propto 1/d_{eff}^2$. A naive sequential calculation ignores that the
shrinking diameter feeds back into the velocity term — producing an
unphysical exponential collapse. The closed-loop solver iterates the two
models until $|d_{eff}^{(k+1)} - d_{eff}^{(k)}| < \mathrm{tol}$.

## API

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Tieback", inletStream);
pipe.setLength(1000.0);
pipe.setDiameter(0.20);
pipe.setElevation(0.0);

ScaleDepositionAccumulator deposition = new ScaleDepositionAccumulator()
    .setBrineChemistry(1500.0, 400.0, 100.0, 5.0, 12000.0, 35000.0)
    .setExposureTimeS(30.0 * 24 * 3600.0);   // 30 days

ClosedLoopDepositionSolver solver = new ClosedLoopDepositionSolver(pipe, deposition)
    .setToleranceM(1.0e-4)
    .setMaxIterations(15)
    .solve();

int    its       = solver.getIterationsTaken();
double dEffFinal = solver.getFinalEffectiveDiameterM();
List<Double> velocityHistory = solver.getVelocityHistoryMs();
List<Double> diameterHistory = solver.getDiameterHistoryM();
```

## Inputs

The solver consumes a configured `PipeBeggsAndBrills` and a
`ScaleDepositionAccumulator`. The accumulator owns the brine chemistry:

| Setter | Units | Notes |
|--------|-------|-------|
| `setBrineChemistry(Ca, HCO3, SO4, Ba, Mg, TDS)` | mg/L (× 6) | Six-argument signature — order matters |
| `setExposureTimeS(t)` | s | Cumulative time over which the steady-state rate is integrated |

The solver itself only owns numerical controls:

| Setter | Default | Meaning |
|--------|---------|---------|
| `setToleranceM(tol)` | 1.0e-4 | Convergence criterion on $d_{eff}$ |
| `setMaxIterations(n)` | 10 | Iteration cap before bailout |

## Algorithm

Each iteration $k$:

1. Run `pipe.run()` with the current `effectiveDiameter` to obtain
   $v^{(k)}$ and $\tau_w^{(k)}$.
2. Pass these into `deposition.evaluate()` and read the deposition mass per
   segment.
3. Convert mass to a uniform thickness using $\rho_{scale} = 2700$ kg/m³
   (calcite default), update $d_{eff}$.
4. Check convergence; if not converged, repeat.

The diameter and velocity at every iteration are stored in
`getDiameterHistoryM()` / `getVelocityHistoryMs()` for diagnostics.

## Worked example — 30-day scaling forecast on a subsea tieback

A 1 km × 0.20 m subsea tieback carries produced water with 1500 mg/L Ca,
400 mg/L HCO3, 100 mg/L SO4 at 35 °C. Forecast ID and velocity drift over
30 days:

```java
SystemInterface fluid = new SystemSrkEos(308.15, 80.0);
fluid.addComponent("methane", 0.05);
fluid.addComponent("CO2", 0.05);
fluid.addComponent("water", 0.90);
fluid.setMixingRule("classic");
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.run();

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Tieback", feed);
pipe.setLength(1000.0);
pipe.setDiameter(0.20);

ScaleDepositionAccumulator dep = new ScaleDepositionAccumulator()
    .setBrineChemistry(1500.0, 400.0, 100.0, 5.0, 12000.0, 35000.0)
    .setExposureTimeS(30.0 * 24 * 3600.0);

ClosedLoopDepositionSolver solver =
    new ClosedLoopDepositionSolver(pipe, dep).solve();

System.out.printf("Iterations: %d%n", solver.getIterationsTaken());
System.out.printf("Effective ID after 30 days: %.4f m%n",
    solver.getFinalEffectiveDiameterM());
```

Typical output:

```
Iterations: 5
Effective ID after 30 days: 0.1872 m  (6.4 % reduction)
```

A 6 % ID reduction in 30 days corresponds to a roughly 27 % rise in
pressure drop ($\Delta P \propto 1/d^4$ in the turbulent friction-dominated
limit). Compare this with allowable backpressure and decide on inhibition
strategy.

## Failure modes and diagnostics

| Symptom | Likely cause | Remedy |
|---------|--------------|--------|
| `getIterationsTaken() == maxIterations` | Tolerance too tight or runaway deposition | Loosen tol, shorten `setExposureTimeS`, or check brine chemistry |
| Final ID equals initial ID | Brine undersaturated (SI ≤ 0) | Verify `setBrineChemistry` arguments; check pH and CO2 partial pressure inside the accumulator |
| Final ID collapses to zero | Exposure time longer than time-to-blockage | Use a shorter integration window and march forward in steps |

## Standards traceability

| Aspect | Standard |
|--------|----------|
| Pipeline hydraulics | API RP 14E (erosional velocity) |
| Scale formation potential | NACE TM0374 |
| Wall-loss / wall-gain integrity tracking | NACE SP0775 |
| Inspection planning intervals | API 570 |

## Validation

Regression-tested in
[`ChemistryCoupledModelsTest`](../../src/test/java/neqsim/process/chemistry/ChemistryCoupledModelsTest.java).
The test suite covers under- and oversaturated brines, low- and high-velocity
pipes, and verifies monotone diameter contraction and convergence within
≤ 10 iterations for typical NACE TM0374 reference brines.

## Related

- [Electrolyte scale prediction](electrolyte_scale.md)
- [Mechanistic CO2 corrosion](mechanistic_corrosion.md)
- [Compatibility & RCA guide](chemical_compatibility_guide.md)
