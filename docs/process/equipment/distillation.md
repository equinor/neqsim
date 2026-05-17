---
title: Distillation Equipment
description: Documentation for distillation column equipment in NeqSim process simulation. Covers staged columns, solvers, formal specifications, side draws, pumparounds, condenser and reboiler modes, hydraulics, shortcut initialization, tray efficiency, dynamic screening, and rate-based packed columns.
keywords: "distillation, column, tray, absorber, stripper, deethanizer, debutanizer, reboiler, condenser, reflux, side draw, pumparound, hydraulics, Murphree efficiency, shortcut distillation, rate-based packed column, NGL, inside-out solver, AUTO solver, matrix inside-out, Naphtali-Sandholm, MESH residual, convergence diagnostics"
---

NeqSim's distillation package provides equilibrium-stage columns, shortcut design,
hydraulic rating, internal recycles, side products, and a separate rate-based packed
column model for absorption and stripping. The main implementation lives in
`neqsim.process.equipment.distillation`.

## Capability Map

| Area | Main APIs | Notes |
|------|-----------|-------|
| Rigorous staged columns | `DistillationColumn`, `SimpleTray`, `Condenser`, `Reboiler` | Equilibrium-stage MESH-style model with tray-by-tray flash calculations. |
| Solvers | `SolverType` | Direct, damped, inside-out, adaptive matrix inside-out, Wegstein, sum-rates, Newton temperature correction, Naphtali-Sandholm, MESH residual, and `AUTO`. |
| Formal specifications | `ColumnSpecification`, convenience setters | Product purity, component recovery, reflux ratio, product flow rate, and duty specifications. |
| Side products | `setGasSideDrawFraction`, `setLiquidSideDrawFraction`, `addSideDrawFlowSpecification` | Side draws are external product streams and are included in outlet stream and mass-balance reporting. |
| Pumparounds | `addLiquidPumparound` | Internal liquid draw/return circuits solved as column tear variables. |
| Hardware modes | `CondenserMode`, `ReboilerMode` | Partial or total condenser, fixed liquid reflux split, equilibrium reboiler, and vapor boilup ratio mode. |
| Hydraulics and sizing | `calcColumnInternals`, `enableHydraulicPressureDropCoupling` | Rates tray or packing hydraulics and can couple total pressure drop back into the pressure profile. |
| Efficiency | `setMurphreeEfficiency`, `setMurphreeEfficiencies` | Column-wide and per-stage Murphree vapor efficiency correction. |
| Shortcut design | `ShortcutDistillationColumn`, `initializeFromShortcut` | Fenske-Underwood-Gilliland estimates and rigorous-column initialization. |
| Tray optimization | `findOptimalNumberOfTrays`, `findEconomicOptimalTrayConfiguration` | Searches tray count and feed tray, optionally with economic ranking. |
| Dynamics | `runTransient`, `DynamicColumnModel.EXPERIMENTAL_EULER` | Explicit-Euler holdup screening model. It is not a rigorous DAE dynamic column model. |
| Rate-based packed columns | `RateBasedPackedColumn` | Segment-based packed absorption/stripping with film mass transfer, heat transfer, packing hydraulics, and JSON diagnostics. |

## Tray Numbering

The `DistillationColumn` constructor is:

```java
DistillationColumn column = new DistillationColumn(name, simpleTrayCount, hasReboiler,
    hasCondenser);
```

The `simpleTrayCount` argument excludes the optional reboiler and condenser. Tray indices used by
`addFeedStream`, side draws, pumparounds, and per-stage efficiency are internal bottom-up stage
indices:

| Hardware | Internal index convention |
|----------|---------------------------|
| Reboiler present | Index `0` is the reboiler. |
| Simple trays | Above the reboiler, increasing upward. |
| Condenser present | Last internal index is the condenser. |

For example, `new DistillationColumn("T-100", 10, true, true)` creates 12 internal stages:
reboiler, 10 simple trays, and condenser.

## Basic Usage

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.StreamInterface;

DistillationColumn column = new DistillationColumn("Deethanizer", 10, true, true);
column.addFeedStream(feedStream, 5);
column.setTopPressure(25.0);
column.setBottomPressure(26.0);
column.setCondenserTemperature(273.15 - 10.0);
column.setReboilerTemperature(273.15 + 105.0);
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
column.run();

StreamInterface overhead = column.getGasOutStream();
StreamInterface bottoms = column.getLiquidOutStream();
```

Use `getInletStreams()` and `getOutletStreams()` for process-topology introspection. Outlet streams
include the terminal products plus any configured non-zero side-draw product streams.

## Builder Pattern

The fluent builder covers the common rigorous-column setup path. Use the constructor when only one
end of the column has hardware, because the builder currently exposes `withCondenserAndReboiler()`
as the hardware shortcut.

```java
import neqsim.process.equipment.distillation.ColumnSpecification;
import neqsim.process.equipment.distillation.DistillationColumn;

DistillationColumn column = DistillationColumn.builder("Deethanizer")
    .numberOfTrays(15)
    .withCondenserAndReboiler()
    .topPressure(25.0, "bara")
    .bottomPressure(26.0, "bara")
    .temperatureTolerance(1.0e-3)
    .massBalanceTolerance(1.0e-2)
    .maxIterations(100)
    .insideOut()
    .internalDiameter(2.5)
    .addFeedStream(feedStream, 8)
    .topProductPurity("ethane", 0.95)
    .bottomSpecification(new ColumnSpecification(
        ColumnSpecification.SpecificationType.PRODUCT_PURITY,
        ColumnSpecification.ProductLocation.BOTTOM, 0.98, "propane"))
    .build();
```

| Builder method | Description |
|----------------|-------------|
| `numberOfTrays(int)` | Number of simple trays, excluding condenser and reboiler. |
| `withCondenserAndReboiler()` | Adds both terminal hardware items. |
| `topPressure(double, String)`, `bottomPressure(double, String)`, `pressure(double, String)` | Sets endpoint pressures. |
| `temperatureTolerance(double)`, `massBalanceTolerance(double)`, `tolerance(double)` | Sets convergence tolerances. |
| `maxIterations(int)` | Sets requested solver iterations. |
| `dampedSubstitution()`, `insideOut()`, `autoSolver()` | Selects common solver strategies. |
| `relaxationFactor(double)` | Starting damping factor for damped substitution. |
| `internalDiameter(double)` | Internal diameter in metres. |
| `addFeedStream(StreamInterface, int)` | Adds a feed to an internal tray index. |
| `topProductPurity(String, double)` | Adds a top product purity specification. |
| `bottomSpecification(ColumnSpecification)` | Adds any bottom-end specification object. |
| `build()` | Creates the configured column. |

## Operating Specifications

Direct operating specifications are applied before the inner column solver runs.

```java
column.setTopPressure(25.0);
column.setBottomPressure(26.0);
column.setCondenserTemperature(263.15);
column.setReboilerTemperature(378.15);
column.setCondenserRefluxRatio(3.0);
column.getCondenser().setHeatInput(-5.0e6);
column.getReboiler().setHeatInput(6.0e6);
column.setReboilerBoilupRatio(2.5);
```

Product-quality and recovery targets use `ColumnSpecification`. Purity and recovery targets are
dimensionless fractions from `0` to `1`; product-flow-rate specifications are evaluated internally
in `mol/hr`.

```java
column.setTopProductPurity("ethane", 0.95);
column.setBottomProductPurity("propane", 0.98);
column.setTopComponentRecovery("ethane", 0.99);
column.setBottomComponentRecovery("propane", 0.99);
column.setBottomProductFlowRate(1000.0, "mol/hr");

ColumnSpecification topFlow = new ColumnSpecification(
    ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE,
    ColumnSpecification.ProductLocation.TOP, 500.0);
topFlow.setTolerance(1.0e-3);
topFlow.setMaxIterations(30);
column.setTopSpecification(topFlow);
```

For iterative specifications, NeqSim wraps the selected inner solver in an outer adjustment loop and
uses condenser or reboiler temperature as the manipulated variable where possible.

## Condenser and Reboiler Modes

```java
column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
column.setCondenserLiquidReflux(500.0, "kg/hr");
DistillationColumn.CondenserMode condenserMode = column.getCondenserMode();

column.setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM);
column.setReboilerVaporBoilupRatio(1.8);
DistillationColumn.ReboilerMode reboilerMode = column.getReboilerMode();
```

`setCondenserLiquidReflux(value, unit)` configures the `LIQUID_REFLUX_SPLIT` mode. Use it instead
of calling `setCondenserMode(LIQUID_REFLUX_SPLIT)` directly because the fixed reflux flow is
required.

## Solver Options

| Solver type | Strategy | Typical use |
|-------------|----------|-------------|
| `DIRECT_SUBSTITUTION` | Classic tray-by-tray substitution. | Default for simple, well-posed columns. |
| `DAMPED_SUBSTITUTION` | Sequential substitution with an initial fixed relaxation factor. | Stiffer cases where direct substitution overshoots. |
| `INSIDE_OUT` | Inside-out style flow correction with K-value tracking and polishing. | General deethanizer/depropanizer and multi-feed work. |
| `MATRIX_INSIDE_OUT` | Adaptive matrix warm start plus rigorous inside-out polishing. | Larger hydrocarbon fractionators where matrix setup cost is justified. |
| `WEGSTEIN` | Accelerated successive substitution after warm-up. | Well-conditioned fixed-point problems. |
| `SUM_RATES` | Flow-corrected tearing method. | Absorbers, strippers, and flow-sensitive columns. |
| `NEWTON` | Tray-temperature Newton accelerator. | Difficult temperature convergence. It is not full simultaneous MESH Newton. |
| `NAPHTALI_SANDHOLM` | Guarded simultaneous correction of MESH blocks after inside-out warm start. | Residual-driven hydrocarbon fractionators. |
| `MESH_RESIDUAL` | Inside-out initialization plus full residual auditing. | Material, equilibrium, summation, energy, product-draw, and spec residual checks. |
| `AUTO` | Runs candidate strategies on column copies and accepts the solved/best candidate. | Agent workflows and uncertain cases where robust automatic selection is useful. |

```java
column.setSolverType(DistillationColumn.SolverType.AUTO);
column.run();
DistillationColumn.SolverType selected = column.getLastSolverTypeUsed();
```

## Side Draws

Side draws withdraw a fraction of tray vapor or liquid traffic. They are true external product
streams: `getOutletStreams()` includes them, and `getMassBalance(unit)` subtracts them from the
feed-product balance.

```java
column.setGasSideDrawFraction(6, 0.05);
column.setLiquidSideDrawFraction(4, 0.10);

StreamInterface gasSideDraw = column.getSideDrawStream(6,
    DistillationColumn.SideDrawPhase.GAS);
List<StreamInterface> allSideDraws = column.getSideDrawStreams();
```

For target side-product flow rates, use side-draw flow specifications. The column adjusts the
corresponding tray split fraction as an outer tear variable.

```java
DistillationColumn.ColumnSideDrawSpecification spec = column.addSideDrawFlowSpecification(6,
    DistillationColumn.SideDrawPhase.GAS, 100.0, "kg/hr");
spec.setTolerance(1.0e-4);
spec.setMaxIterations(15);
column.setMaxColumnTearIterations(20);
column.setColumnTearTolerance(1.0e-4);
column.run();

double actualDraw = spec.getLastActualFlowRate();
double drawResidual = spec.getLastRelativeResidual();
boolean tearConverged = column.isLastColumnTearConverged();
```

If the requested side-draw flow is physically impossible, the split is bounded by available tray
traffic and the latest tear-variable diagnostics report non-convergence.

## Pumparounds

Liquid pumparounds are internal draw/return circuits. They are not external products and do not
appear in `getOutletStreams()`.

```java
DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("PA-1", 4, 6,
    0.15, 10.0);
column.setMaxPumparoundIterations(12);
column.setPumparoundTolerance(1.0e-4);
column.run();

StreamInterface returnStream = pumparound.getReturnStream();
double latestChange = column.getLastPumparoundRelativeChange();
```

The `temperatureDrop` argument is in Kelvin. Positive values cool the returned liquid; negative
values heat it. A non-finite or below-zero-K return temperature fails explicitly.

## Hydraulics and Pressure-Drop Coupling

`calcColumnInternals()` evaluates tray or packing hydraulics for the latest column state.

```java
import neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner;

column.setInternalDiameter(2.5);
ColumnInternalsDesigner designer = column.calcColumnInternals("sieve");
double totalPressureDropPa = designer.getTotalPressureDrop();
```

Hydraulic coupling is opt-in. When enabled, the column rates internals, converts total hydraulic
pressure drop to a linear pressure profile between top and bottom, and re-solves until the hydraulic
tear variable is within tolerance.

```java
column.enableHydraulicPressureDropCoupling("sieve");
column.run();
double coupledPressureDropPa = column.getLastHydraulicPressureDropPa();
double hydraulicResidual = column.getLastHydraulicPressureDropResidual();
```

Supported internals names include common tray types such as `"sieve"`, `"valve"`,
`"bubble-cap"`, and packed-column mode `"packed"`, depending on available internals data.

## Efficiency and Internals

Murphree vapor efficiency can be set globally or per stage.

```java
column.setMurphreeEfficiency(0.70);
column.setMurphreeEfficiency(3, 0.65);
double stageEfficiency = column.getMurphreeEfficiency(3);
column.clearPerStageMurphreeEfficiency();
```

Use mechanical-design classes and `calcColumnInternals()` for actual-tray counts, HETP-style
height estimates, flooding, weeping, entrainment, downcomer backup, and pressure-drop checks. The
`findEconomicOptimalTrayConfiguration` methods also use tray efficiency to convert theoretical
stages to actual trays for cost ranking.

## Shortcut Initialization and Tray Optimization

`ShortcutDistillationColumn` provides Fenske-Underwood-Gilliland estimates. A rigorous column can
use those estimates directly as a starting design.

```java
DistillationColumn.ShortcutInitializationResult init = column.initializeFromShortcut(feedStream,
    "ethane", "propane", 0.98, 0.98, 1.3);
if (init.isFeasible()) {
  column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
  column.run();
}
```

Search utilities are available for tray count, feed tray, and economic ranking.

```java
column.setMaxTrayOptimizationCandidates(200);
column.setMaxTrayOptimizationTimeSeconds(20.0);
int trays = column.findOptimalNumberOfTrays(0.95, "ethane", true, 30);
DistillationColumn.EconomicTrayOptimizationResult economic =
    column.findEconomicOptimalTrayConfiguration(0.95, "ethane", true, 30);
```

Optimization mutates the column to the selected feasible candidate. Capture or copy the model first
if the current tray count must be preserved.

## Dynamic Screening Model

The current distillation dynamics are explicitly experimental:
`getDynamicColumnModel()` returns `DynamicColumnModel.EXPERIMENTAL_EULER`, and
`isDynamicColumnModelExperimental()` returns `true`.

```java
column.setDynamicColumnEnabled(true);
column.setDynamicEnergyEnabled(true);
column.setTrayWeirHeight(0.05);
column.setTrayWeirLength(1.0);
column.setTrayDryPressureDrop(200.0);
```

The transient model uses explicit-Euler tray holdup updates, Francis-weir liquid overflow, optional
per-tray energy tracking, and a simplified vapor hydraulic relation. Treat it as a screening tool
for qualitative inventory response, not as a replacement for commercial DAE dynamic simulators in
control-system design or safety-critical trip studies.

## Rate-Based Packed Column

Use `RateBasedPackedColumn` for counter-current packed absorption and stripping when equilibrium
stages are not the right model.

```java
import neqsim.process.equipment.distillation.RateBasedPackedColumn;

RateBasedPackedColumn absorber = new RateBasedPackedColumn("CO2 absorber", gasIn, liquidIn);
absorber.setColumnDiameter(1.2);
absorber.setPackedHeight(6.0);
absorber.setNumberOfSegments(12);
absorber.setPackingType("Pall-Ring-50");
absorber.setTransferComponents("CO2");
absorber.run();

StreamInterface treatedGas = absorber.getGasOutStream();
String report = absorber.toJson();
```

The rate-based model exposes segment profiles, component-transfer totals, pressure-drop and flood
fraction diagnostics, film/heat-transfer model choices, and equation-oriented residual diagnostics.

## Diagnostics and Results

| Getter | Description |
|--------|-------------|
| `solved()` | Current convergence flag. |
| `getLastSolverTypeUsed()` | Concrete solver that completed the latest run, especially useful when requested solver is `AUTO`. |
| `getLastIterationCount()` | Inner solver iteration count. |
| `getLastSolveTimeSeconds()` | Latest solve wall time. |
| `getLastTemperatureResidual()` | Average tray-temperature residual in Kelvin. |
| `getLastMassResidual()` | Relative mass-balance residual. |
| `getLastEnergyResidual()` | Relative enthalpy-balance residual. |
| `getLastTopSpecificationResidual()`, `getLastBottomSpecificationResidual()` | Endpoint spec errors. |
| `getLastSpecificationResidual()` | Maximum absolute endpoint spec error. |
| `getLastColumnTearIterationCount()` | Outer side-draw, pumparound, and hydraulic tear iterations. |
| `getLastColumnTearResidual()` | Maximum outer tear residual. |
| `isLastColumnTearConverged()` | Whether active side-draw, pumparound, and hydraulic tear variables met tolerance. |
| `getLastPumparoundRelativeChange()` | Maximum latest pumparound return-flow change. |
| `getLastHydraulicPressureDropPa()` | Latest coupled hydraulic pressure drop in Pa. |
| `getLastHydraulicPressureDropResidual()` | Relative pressure-profile change from hydraulic coupling. |
| `getLastMeshResidualNorm()` | Full scaled MESH residual infinity norm. |
| `getLastMeshMaterialResidualNorm()` | Component material residual norm. |
| `getLastMeshEquilibriumResidualNorm()` | Phase-equilibrium residual norm. |
| `getLastMeshSummationResidualNorm()` | Mole-fraction summation residual norm. |
| `getLastMeshEnergyResidualNorm()` | Tray energy residual norm. |
| `getLastMeshProductDrawResidualNorm()` | Terminal product-draw residual norm. |
| `getLastMeshSpecificationResidualNorm()` | Active endpoint specification residual norm. |
| `getLastMeshResidualVector()` | Copy of the complete scaled residual vector. |

The MESH residual gate is diagnostic-only for legacy sequential solvers by default. It is effective
by default for `NAPHTALI_SANDHOLM` and `MESH_RESIDUAL`; call
`setEnforceMeshResidualTolerance(true)` to make it part of the convergence contract for other
solvers.

## Common Workflows

### NGL Fractionation

1. Build the feed with an EOS suitable for the hydrocarbon range, set mixing rule, and run the feed
   stream.
2. Initialize with `ShortcutDistillationColumn` or `initializeFromShortcut` when light and heavy
   keys are known.
3. Run `INSIDE_OUT` or `AUTO` first; use `MESH_RESIDUAL` or `NAPHTALI_SANDHOLM` for residual audit.
4. Add product purity or component recovery specs.
5. Rate internals and enable hydraulic pressure-drop coupling only after a stable base case exists.

### Absorber or Stripper

Use `DistillationColumn` without condenser/reboiler for simple equilibrium-stage absorber or
stripper studies, or `RateBasedPackedColumn` for packed mass-transfer studies.

```java
DistillationColumn absorber = new DistillationColumn("Absorber", 10, false, false);
absorber.addFeedStream(gasStream, 0);
absorber.addFeedStream(leanSolvent, 9);
absorber.setSolverType(DistillationColumn.SolverType.SUM_RATES);
absorber.run();
```

For a stripper with a reboiler and no condenser, use `new DistillationColumn("Stripper", 8, true,
false)`.

## Troubleshooting

| Symptom | Recommended checks |
|---------|--------------------|
| No convergence | Verify tray numbering, feed condition, endpoint temperatures, pressure profile, and component list. Start with `DIRECT_SUBSTITUTION`, `DAMPED_SUBSTITUTION`, or `AUTO`. |
| Oscillation | Reduce aggressive condenser/reboiler specs, set a lower relaxation factor, or use `DAMPED_SUBSTITUTION`. |
| Specification does not close | Check `getLastTopSpecificationResidual()`, `getLastBottomSpecificationResidual()`, feasible product split, and whether the required condenser/reboiler exists. |
| Side-draw spec reports non-converged | The target may exceed available tray traffic or feed component inventory. Inspect `getSideDrawStream(...)`, side-draw fraction, and `getLastColumnTearResidual()`. |
| Pumparound fails | Check draw fraction, tray numbers, and return temperature. A return below 0 K is rejected. |
| Hydraulic coupling fails | Run without coupling first, set a positive internal diameter, and verify `calcColumnInternals(...)` succeeds for the selected internals type. |
| Unexpected dynamic result | Confirm the model is acceptable for screening. The current formulation is `EXPERIMENTAL_EULER`, not a rigorous industrial DAE. |

## Related Documentation

- [Distillation column algorithm](../../wiki/distillation_column.md)
- [Reactive distillation](../reactive_distillation.md)
- [Absorbers](absorbers.md)
- [Heat exchangers](heat_exchangers.md)