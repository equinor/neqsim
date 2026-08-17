---
title: Distillation Equipment
description: Documentation for distillation column equipment in NeqSim process simulation. Covers staged columns, solvers, formal specifications, specification homotopy, side draws, pumparounds, condenser and reboiler modes, hydraulics, shortcut initialization, tray efficiency, dynamic screening, and rate-based packed columns.
keywords: "distillation, column, tray, absorber, stripper, deethanizer, debutanizer, reboiler, condenser, reflux, side draw, pumparound, hydraulics, Murphree efficiency, shortcut distillation, rate-based packed column, NGL, inside-out solver, AUTO solver, automatic solver, specification homotopy, matrix inside-out, Naphtali-Sandholm, MESH residual, convergence diagnostics"
---

NeqSim's distillation package provides equilibrium-stage columns, shortcut design,
hydraulic rating, internal recycles, side products, and a separate rate-based packed
column model for absorption and stripping. The main implementation lives in
`neqsim.process.equipment.distillation`.

## Capability Map

| Area | Main APIs | Notes |
|------|-----------|-------|
| Rigorous staged columns | `DistillationColumn`, `SimpleTray`, `Condenser`, `Reboiler` | Equilibrium-stage MESH-style model with tray-by-tray flash calculations. |
| Solvers | `SolverType` | Direct, damped, inside-out, adaptive matrix inside-out, Wegstein, sum-rates, Newton temperature correction, Naphtali-Sandholm, MESH residual, and `AUTO` with candidate tracing. |
| Formal specifications | `ColumnSpecification`, convenience setters | Product purity, component recovery, reflux ratio, product flow rate, duty specifications, and staged specification homotopy for difficult product targets. |
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
dimensionless fractions from `0` to `1`. Product-flow-rate targets and their absolute residuals use
the unit supplied to `setTopProductFlowRate` or `setBottomProductFlowRate`; common molar and mass
units such as `mol/hr` and `kg/hr` are supported by the product stream. Constructors that do not
supply a unit remain backward compatible and default product flow to `mol/hr`.

```java
column.setTopProductPurity("ethane", 0.95);
column.setBottomProductPurity("propane", 0.98);
column.setTopComponentRecovery("ethane", 0.99);
column.setBottomComponentRecovery("propane", 0.99);
column.setBottomProductFlowRate(1000.0, "kg/hr");

ColumnSpecification topFlow = new ColumnSpecification(
    ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE,
    ColumnSpecification.ProductLocation.TOP, 500.0, null, "kg/hr");
topFlow.setTolerance(1.0e-3); // kg/hr, matching the target
topFlow.setMaxIterations(30);
column.setTopSpecification(topFlow);
```

The selected unit is retained through specification homotopy, diagnostics, warm-state identity,
copying, and serialization. Feasibility screening compares each flow target with external feed flow
in the same unit. When both terminal flow targets use the same unit, their sum is screened as well.
Mixed-unit terminal targets on a column with an external side draw are evaluated during the solve
because their product compositions may differ. `getLastTopSpecificationResidual()` and
`getLastBottomSpecificationResidual()` report flow residuals in the corresponding target unit.

Without an external side draw, top and bottom product-flow targets are not two independent
specifications: steady-state total material balance fixes one terminal flow after the feed and the
other terminal flow are known. The run preflight and `validateSpecifications()` therefore reject
the pair even when its targets sum exactly to feed. Use one product-flow target and an independent
purity, component-recovery, duty, or reflux specification. For the same reason, top and bottom
recovery targets for the same component are dependent without an external side draw and are
rejected before iteration. Different-component recovery targets remain independent. When an
external side draw is active, paired terminal controls can be structurally independent; paired
recoveries for one component are still screened so their sum cannot exceed the feed-component
inventory. These checks intentionally prevent rank-deficient outer solves and leave any previously
accepted tray and product state untouched.

For iterative specifications, NeqSim wraps the selected inner solver in an outer adjustment loop and
uses condenser or reboiler temperature as the manipulated variable where possible. Product purity,
component recovery, and product-flow specifications can be staged with
`setSpecificationHomotopySteps(steps)`. Values above one ramp the effective target from the current
product value to the final user target, leaving the stored `ColumnSpecification` unchanged. When
`AUTO` is selected and an adjustable product specification is active, NeqSim uses three homotopy
stages by default unless the user has configured another stage count.

## Condenser and Reboiler Modes

```java
column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);

column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
column.setCondenserRefluxRatio(1.5); // required split equation for total condensation

column.setCondenserLiquidReflux(500.0, "kg/hr");
DistillationColumn.CondenserMode condenserMode = column.getCondenserMode();
column.clearCondenserRefluxRatio();

column.setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM);
column.setReboilerVaporBoilupRatio(1.8);
DistillationColumn.ReboilerMode reboilerMode = column.getReboilerMode();
```

A total condenser requires an explicit reflux ratio so that the fully condensed liquid is divided
between reflux and distillate. Mode selection preserves an already configured ratio, so the ratio
and total-mode calls can be made in either order. An incomplete total-condenser declaration is an
error in `validateSpecifications()` and fails the run preflight before feed assignment or accepted
tray/product state can change. A failed total-condenser bubble-point calculation is also reported as
an exception instead of continuing with a stale thermodynamic state.

Ratio control and an adjustable endpoint product specification cannot be active at the same column
end. The ratio flash does not use the endpoint temperature that the purity, recovery, or product-flow
outer loop manipulates, so accepting both would create an ineffective control equation. Call
`clearCondenserRefluxRatio()` before using an adjustable top specification, or select
`ReboilerMode.EQUILIBRIUM` before using an adjustable bottom specification. Clearing a ratio
preserves unrelated product specifications. Specifications on a column end without the matching
condenser or reboiler are validation errors and fail during preflight.

`getConvergenceDiagnostics()` reports the configured condenser and reboiler modes plus whether
condenser ratio control is active. This makes retained/restarted models auditable before nearby-point
warm solves.

`setReboilerVaporBoilupRatio(ratio)` configures the direct reboiler mode.
`setReboilerBoilupRatio(ratio)` also records the target as the bottom `REFLUX_RATIO`
specification. Terminal ratios supplied through either the column API or the condenser/reboiler
objects must be finite and non-negative. Invalid values fail before the active ratio, stored
specification, initialization flag, or accepted warm products are changed; invalid ratio state
retained by an older serialized model also fails before a terminal flash. A valid legacy boilup
setter call marks the column for reinitialization so the next nearby-point solve cannot reuse an
incompatible terminal state. After either route,
`setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM)` clears the active reboiler ratio and
any stored bottom reflux-ratio specification, so a later run cannot silently restore the old
ratio. Bottom purity, recovery, product-flow, and duty specifications are preserved.

`setCondenserLiquidReflux(value, unit)` configures the `LIQUID_REFLUX_SPLIT` mode. Use it instead
of calling `setCondenserMode(LIQUID_REFLUX_SPLIT)` directly because the fixed reflux flow is
required. The split never creates condensate to satisfy an oversized request: it returns at most the
available liquid, preserves material and energy, and leaves the column unsolved when the normalized
fixed-reflux shortfall exceeds its acceptance tolerance. The requested, available, delivered, and residual values appear
in
`getConvergenceDiagnostics()`.

If the column rejects the tray state and installs guarded full-feed fallback products, the
condenser's separate liquid product from that rejected state is cleared. The fallback gas and
bottom streams already contain the complete feed inventory, so exposing the old liquid product
would double-count material. Fixed-reflux delivery diagnostics are invalidated in this state;
callers must check for `SolveStatus.FALLBACK_PRODUCTS` and must not treat it as a rigorous
fixed-reflux solution.

A fixed liquid-reflux flow and a top `REFLUX_RATIO` specification are mutually exclusive because
both control the condenser reflux split. Configuration rejects either setter order, and
`validateSpecifications()` plus the run preflight detect contradictory state retained by an older
serialized model or introduced through direct `Condenser` mutation. To recover, call
`setCondenserMode(DistillationColumn.CondenserMode.PARTIAL)` or
`setCondenserMode(DistillationColumn.CondenserMode.TOTAL)` to clear fixed-flow mode, or remove the
top reflux-ratio specification.

## Solver Options

| Solver type | Strategy | Typical use |
|-------------|----------|-------------|
| `DIRECT_SUBSTITUTION` | Classic tray-by-tray substitution. | Default for simple, well-posed columns. |
| `DAMPED_SUBSTITUTION` | Sequential substitution with an initial fixed relaxation factor. | Stiffer cases where direct substitution overshoots. |
| `INSIDE_OUT` | Inside-out style flow correction with K-value tracking and polishing. | General deethanizer/depropanizer and multi-feed work. |
| `MATRIX_INSIDE_OUT` | Adaptive matrix warm start plus rigorous inside-out polishing. | Larger hydrocarbon fractionators where matrix setup cost is justified. |
| `WEGSTEIN` | Accelerated successive substitution after warm-up. | Well-conditioned fixed-point problems. |
| `SUM_RATES` | Flow-corrected tearing method. Native for columns without a condenser; condenser configurations remain guarded to damped substitution. | Absorbers, reboiler-only strippers, and flow-sensitive columns. |
| `NEWTON` | Tray-temperature Newton accelerator. | Difficult temperature convergence. It is not full simultaneous MESH Newton. |
| `NAPHTALI_SANDHOLM` | Guarded simultaneous correction of MESH blocks after inside-out warm start, with early return to coordinated fallback after repeated non-descent steps. | Residual-driven hydrocarbon fractionators. |
| `MESH_RESIDUAL` | Inside-out initialization plus full residual auditing. | Material, equilibrium, summation, energy, product-draw, and spec residual checks. |
| `AUTO` | Runs a feasibility pre-screen and copy-based candidate probes. Fixed-specification reboiler-only strippers try native `SUM_RATES` first; other configurations retain the relaxed damped base and guarded fallback ladder. | Agent workflows and uncertain cases where robust automatic selection and diagnostics are useful. |

```java
column.setSolverType(DistillationColumn.SolverType.AUTO);
column.run();
DistillationColumn.SolverType selected = column.getLastSolverTypeUsed();
```

`AUTO` keeps the requested solver type as `AUTO`, while `getLastSolverTypeUsed()` reports the
concrete solver that completed the run. Inspect `getLastAutoSolverSummary()` or
`getConvergenceDiagnostics()` to see the feasibility pre-screen, candidate list, residuals,
iteration counts, solve times, and fallback notes. For product-specification cases, also inspect
`getLastSpecificationHomotopyStepCount()` to confirm whether staged continuation was used.

Exact unchanged-input reuse is conditional on both an identical problem fingerprint and the same
active convergence-gate configuration that was recorded after the accepted public solve. Changing
an enforced mass, energy, MESH, product-draw, specification, or other tolerance disqualifies the
zero-iteration cache hit. Tolerances for disabled energy and MESH gates, and outer tear tolerances
when no tear variable is configured, do not participate in the cache key. The next invocation
executes the solver path after an active-gate change and either meets the new contract or reports
non-convergence explicitly.

## Side Draws

Side draws withdraw a fraction of tray vapor or liquid traffic. They are true external product
streams: `getOutletStreams()` includes them, and `getMassBalance(unit)` subtracts them from the
feed-product balance. The sequential tray solvers and `NAPHTALI_SANDHOLM` both remove the withdrawn
phase from inter-tray material and energy traffic; the Naphtali-Sandholm solver also fingerprints the
configured split so a changed draw cannot reuse an incompatible warm state. The inside-out stage used
by `MESH_RESIDUAL` similarly retains an accepted tray and product state for exact unchanged-input
reuse when no outer column tear variables are active; changed feed, configuration, tray, or product
state invalidates that fingerprint. Pumparounds, hydraulic pressure coupling, and adjustable side-draw
flow specifications keep their coordinated outer initialization path. An exact liquid
side-draw fraction of `1.0` leaves zero internal downflow across that stage, so NeqSim routes an
explicit or reused `NAPHTALI_SANDHOLM` selection to `MESH_RESIDUAL`. Fractions below `1.0`
retain positive internal traffic and remain eligible for the simultaneous solver.

Multiple external feeds may use different component subsets or component ordering, provided their
thermodynamic models can be mixed by NeqSim. Column product reconciliation accumulates feed and
side-draw inventories by component name on the combined column basis; it does not assume that every
feed shares the first feed's component-array indices. This keeps total-condenser material closure
deterministic for, for example, a main C3-C5 feed plus a C3-C4 side feed on another tray. Repeated
and nearby-point solves retain the same named component basis.

The named basis also applies when a partial condenser, vapor-boilup control, external side draw, and
pumparound are active together. The side draw remains an external product, while the pumparound
draw and return remain one internal circulation with the configured utility duty. Pumparounds use
the coordinated outer tear: `isLastColumnTearConverged()` and
`getLastColumnTearResidual()` report that convergence, and exact sequential-state reuse stays
disabled while the nonlocal return is active. A changed external feed therefore re-solves both the
terminal products and pumparound state on the same component basis.

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

Each tray-phase pair has one manipulated split fraction and therefore accepts at most one flow
specification. Adding a second target for the same tray and phase fails immediately with an
`IllegalArgumentException`; opposite phases on the same tray and the same phase on different trays
remain independent specifications. This degrees-of-freedom check prevents contradictory targets
from alternately overwriting one tear variable. Older serialized columns retaining duplicates also
fail before solver iteration and report the affected tray and phase.

If the requested side-draw flow is physically impossible, the split is bounded by available tray
traffic and the latest tear-variable diagnostics report non-convergence.

For one independent side-draw flow specification, every proposed fraction is solved on a cold
copied column state. Only rigorous or reconciled inner-column results can update the controller or
become the public result; fallback-product and failed candidates are rejected, and the last
accepted state is retained. The safeguarded search interpolates or explores from accepted flow
observations only, avoiding feedback from invalid inner states.

Use `getLastColumnTearRejectedCandidateCount()`, `getLastColumnTearRollbackCount()`,
`getLastColumnTearInnerIterationCount()`, and `getLastColumnTearCandidateHistory()` to audit that
search. The history includes the proposed fraction, observed flow, inner status, and acceptance
decision for each trial. These diagnostics are transient and reset when the column is copied or
deserialized.

## Pumparounds

Liquid pumparounds are internal draw/return circuits. They are neither external feeds nor external
products, so their return and draw streams do not appear in `getInletStreams()` or
`getOutletStreams()`.

```java
DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("PA-1", 4, 6,
    0.15, 10.0);
column.setMaxPumparoundIterations(12);
column.setPumparoundTolerance(1.0e-4);
column.run();

StreamInterface returnStream = pumparound.getReturnStream();
double dutyKw = pumparound.getDuty("kW");
double latestChange = column.getLastPumparoundRelativeChange();
```

Each draw tray exposes one liquid pumparound fraction and one draw stream, so it can feed at most
one pumparound circuit. A zero-fraction standby circuit still owns that draw tray. Registering
another circuit for the same draw tray throws `IllegalArgumentException` without replacing the
first circuit; different draw trays remain independent. Legacy serialized columns containing
duplicate draw ownership fail before solver iteration, and `validateSetup()` reports
`pumparound.degreesOfFreedom`.

The `temperatureDrop` argument is in Kelvin. Positive values cool the returned liquid; negative
values heat it. A non-finite or below-zero-K return temperature fails explicitly. The column uses
a thermodynamic snapshot while seeding tray profiles, so initialization cannot change the public
return temperature. After a converged solve, the draw-to-return temperature difference therefore
equals the configured drop; the return flow and composition remain coupled through the outer tear
iteration.

`ColumnPumparound.getDuty()` reports return-stream enthalpy minus draw-stream enthalpy in watts;
`getDuty(String)` converts that value to another supported power unit. Cooling therefore has negative duty
and heating positive duty. Before the first return update the duty is `Double.NaN`. The public column energy
diagnostic includes the liquid draw as a tray outlet while the return remains a tray inlet, so their
enthalpy difference is retained as pumparound utility duty without treating the internal circulation
as an external feed or product.

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
| `solved()` | Current convergence flag, including active side-draw, pumparound, and hydraulic outer tears. |
| `getLastSolverTypeUsed()` | Concrete solver that completed the latest run, especially useful when requested solver is `AUTO`. |
| `getLastSolveStatus()` | Strict solve status: rigorous convergence, reconciled products, fallback products, failure, or not run. |
| `getLastSolveStatusReason()` | Concise explanation for fallback or rejected candidate states. |
| `getLastAutoSolverSummary()` | Candidate trace from `AUTO`, including the feasibility pre-screen and per-candidate residual metrics. |
| `getLastIterationCount()` | Inner solver iteration count. |
| `getLastSolveTimeSeconds()` | Latest solve wall time. |
| `getLastTemperatureResidual()` | Average tray-temperature residual in Kelvin. |
| `getLastMassResidual()` | Relative mass-balance residual. |
| `getLastEnergyResidual()` | Relative enthalpy-balance residual. |
| `getEnergyBalanceError()` | Maximum tray/column enthalpy imbalance, including external side draws, pumparound draw/return duty, and excluding zero-flow phase templates. |
| `getLastTopSpecificationResidual()`, `getLastBottomSpecificationResidual()` | Endpoint spec errors. |
| `getLastSpecificationResidual()` | Maximum absolute endpoint spec error. |
| `getSpecificationHomotopySteps()` | Configured number of staged continuation targets for adjustable product specifications. |
| `getLastSpecificationHomotopyStepCount()` | Number of specification continuation stages completed by the latest solve. |
| `getLastColumnTearIterationCount()` | Outer side-draw, pumparound, and hydraulic tear iterations. |
| `getLastColumnTearResidual()` | Maximum outer tear residual. |
| `isLastColumnTearConverged()` | Whether active side-draw, pumparound, and hydraulic tear variables met tolerance. |
| `getLastColumnTearCandidateHistory()` | Accepted/rejected single-side-draw attempts, including a guarded continuation retry when a cold solve fails after an accepted state exists. |
| `ColumnPumparound.getDuty()`, `getDuty(String)` | Latest pumparound cooler/heater duty, negative for cooling and positive for heating. |
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

An accepted inner tray solution is not sufficient when an outer tear variable is active. If the
side-draw, pumparound, or hydraulic tear stops or exhausts its iteration budget above tolerance,
`solved()` returns `false`, `getLastSolveStatus()` returns `FAILED`, and
`getLastSolveStatusReason()` reports the outer residual, tolerance, and iteration count. Product
streams remain available for diagnostics but must not be treated as a converged process result.

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
false)`. Explicit `SUM_RATES` and `AUTO` use the native sum-rates path for a fixed-specification
reboiler-only stripper. Condenser-only and full condenser/reboiler columns remain guarded to damped
substitution because reflux and overhead-energy coupling are not represented directly by the
sum-rates accelerator.

Separated terminal products use a canonical trace-phase rule: if the intended gas or liquid phase
contains all but `1e-8` of the product mole inventory, the smaller phase is merged into that intended
outlet while preserving every component mole. This prevents a parts-per-billion phase from changing
the reported product phase count solely because two converged sequential solvers approached a dew-
or bubble-point boundary from opposite sides. Material phase fractions above `1e-8` are retained.

## Troubleshooting

| Symptom | Recommended checks |
|---------|--------------------|
| No convergence | Verify tray numbering, feed condition, endpoint temperatures, pressure profile, and component list. Start with `DIRECT_SUBSTITUTION`, `DAMPED_SUBSTITUTION`, or `AUTO`, then inspect `getConvergenceDiagnostics()`. |
| Oscillation | Reduce aggressive condenser/reboiler specs, set a lower relaxation factor, or use `DAMPED_SUBSTITUTION`. |
| Specification does not close | Check `getLastTopSpecificationResidual()`, `getLastBottomSpecificationResidual()`, feasible product split, and whether the required condenser/reboiler exists. For sharp purity, recovery, or product-flow targets, use `setSpecificationHomotopySteps(steps)` or `AUTO`. |
| Side-draw spec reports non-converged | The target may exceed available tray traffic or feed component inventory. Inspect `getSideDrawStream(...)`, side-draw fraction, and `getLastColumnTearResidual()`. |
| Pumparound fails | Check draw fraction, tray numbers, and return temperature. A return below 0 K is rejected. |
| Hydraulic coupling fails | Run without coupling first, set a positive internal diameter, and verify `calcColumnInternals(...)` succeeds for the selected internals type. |
| Unexpected dynamic result | Confirm the model is acceptable for screening. The current formulation is `EXPERIMENTAL_EULER`, not a rigorous industrial DAE. |

## Related Documentation

- [Distillation column algorithm](../../wiki/distillation_column.md)
- [Reactive distillation](../reactive_distillation.md)
- [Absorbers](absorbers.md)
- [Heat exchangers](heat_exchangers.md)
