---
title: "Integrated Production Modelling (Reservoir to Market)"
description: "State-of-the-art integrated production modelling in NeqSim: couples reservoir drives, well deliverability, gathering-network hydraulics, facilities and market to balance, forecast, and optimise a field from reservoir to export. Covers WellDeliverabilityCurve, NetworkNewtonSolver, reservoir drives, gas-lift allocation, well-test matching, artificial-lift pumps, and the ReservoirToMarketOptimizer."
keywords: "integrated production modelling, IPM, GAP, PROSPER, MBAL, Pipesim, network solver, deliverability curve, VFP, IPR, VLP, gas lift, well test, jet pump, sucker rod pump, reservoir to market, choke optimization, production network"
---

# Integrated Production Modelling (Reservoir to Market)

NeqSim provides an integrated production-modelling (IPM) stack that couples the four classic
production layers &mdash; **reservoir**, **wells**, **gathering network / flowlines**, and the
**facilities / market boundary** &mdash; into a single solvable system. It plays the same role as
the Petex IPM suite (MBAL + PROSPER + GAP) and Schlumberger Pipesim: it finds the steady-state
operating point of the whole field, marches it forward in time as the reservoir depletes, and
optimises operating decisions (choke openings, lift-gas split) against facility constraints.

All classes live in the package
[`neqsim.process.fielddevelopment.integrated`](API_GUIDE) (artificial-lift pumps live in
`neqsim.process.equipment.pump`).

---

## Why a Deliverability-Curve Architecture

Commercial network solvers do **not** re-run full inflow (IPR) and lift (VLP) thermodynamics inside
the network pressure-flow iteration &mdash; that would be far too slow and fragile. Instead they
precompute a **deliverability curve** (a VFP table reduced to *back pressure vs. surface rate*) for
each well, then balance the network against those cheap surrogates.

NeqSim follows the same design. The [`WellDeliverabilityCurve`](#welldeliverabilitycurve) is a
strictly decreasing, monotone surrogate of a well's combined IPR + VLP behaviour. Because the
network solver only evaluates curves and quadratic pressure-drop laws, **no thermodynamic flash runs
inside the Jacobian loop**, so the field-wide solve is fast and numerically robust.

```
┌──────────────────────────────────────────────────────────────────────┐
│             INTEGRATED PRODUCTION MODEL (reservoir → market)           │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  RESERVOIR        WELLS            NETWORK           FACILITY/MARKET    │
│  ─────────        ─────            ───────           ───────────────    │
│                                                                        │
│  ReservoirDrive   WellBranch       FlowlineBranch    Export sink       │
│  (p = f(Np))   →  (deliverability) → (ΔP = a·q+b·q²) → (fixed pressure) │
│       │           curve surrogate        │                  │          │
│       └───────────────┬──────────────────┴──────────────────┘          │
│                       ▼                                                 │
│              NetworkNewtonSolver  (global pressure-flow balance)        │
│                       ▼                                                 │
│       IntegratedProductionModel.solve() / runProfile() / optimize()    │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Class Map

| Class | Layer | Role |
|-------|-------|------|
| `ReservoirDrive` (interface) | Reservoir | Maps cumulative production to average reservoir pressure |
| `MaterialBalanceGasDrive` | Reservoir | Gas *p/z* material balance |
| `AquiferDrive` | Reservoir | Gas drive with Fetkovich aquifer influx |
| `OilTankDrive` | Reservoir | Undersaturated oil compressibility tank |
| `WellDeliverabilityCurve` | Wells | Monotone IPR+VLP surrogate (back pressure vs. rate) |
| `WellTestMatcher` | Wells | Fits an IPR (PI or Vogel) to well-test points |
| `NetworkNode` / `NetworkBranch` | Network | Nodes and branch interface |
| `WellBranch` | Network | Reservoir-to-wellhead branch (curve + choke + lift) |
| `FlowlineBranch` | Network | Quadratic pressure-drop tie-in |
| `NetworkNewtonSolver` | Network | Global Newton pressure-flow solver |
| `IntegratedProductionModel` | Orchestrator | Builds + solves the field; runs profiles |
| `IntegratedSolveResult` / `ProductionProfile` | Results | Steady-state and time-marched results |
| `GasLiftPerformanceCurve` / `GasLiftNetworkOptimizer` | Optimisation | Equal-slope lift-gas allocation |
| `ReservoirToMarketOptimizer` | Optimisation | Choke optimisation vs. facility capacity |
| `JetPump` / `SuckerRodPump` | Artificial lift | Hydraulic jet and beam-pump equipment |

---

## Reservoir Drives

A `ReservoirDrive` converts cumulative produced volume into an average reservoir pressure, providing
the coupling that lets the field march forward in time. The returned pressure is applied as the
boundary pressure of the reservoir nodes, so well deliverability falls naturally as the reservoir
depletes.

### Material-balance gas drive

For a volumetric gas reservoir the *p/z* material balance is

$$
\frac{p}{z} = \frac{p_i}{z_i}\left(1 - \frac{G_p}{G}\right)
$$

where $p_i$ is initial pressure, $z$ the compressibility factor, $G$ the gas initially in place
(GIIP), and $G_p$ cumulative production.

```java
// Initial 250 bara, GIIP 5e9 Sm3, average z = 0.90
MaterialBalanceGasDrive gasRes = new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90);
double p = gasRes.getReservoirPressure();   // 250.0 bara initially
gasRes.produce(1.0e9, 365.0);               // produce 1e9 Sm3 over a year
double pAfter = gasRes.getReservoirPressure(); // depleted pressure
```

### Aquifer drive

`AquiferDrive` adds Fetkovich aquifer influx $W_e$ to the gas material balance, supporting reservoir
pressure as water encroaches. Use `getCumulativeInflux()` to read $W_e$.

### Oil tank drive

`OilTankDrive` models an undersaturated oil tank whose pressure declines linearly with recovery and
is floored at an abandonment pressure:

```java
// 220 bara, STOIIP 8e6 Sm3, 120 bar depletion over the life, abandon at 90 bara
OilTankDrive oilRes = new OilTankDrive(220.0, 8.0e6, 120.0, 90.0);
```

---

## WellDeliverabilityCurve

The deliverability curve stores surface rate as a strictly decreasing function of wellhead (or
sandface) back pressure. Three factory methods are provided.

```java
// 1. From measured/sampled (pressure, rate) points (ascending pressure, non-increasing rate)
WellDeliverabilityCurve a = WellDeliverabilityCurve.fromArrays(
    new double[] {40.0, 90.0, 140.0}, new double[] {3000.0, 1500.0, 0.0});

// 2. From an absolute open-flow potential (AOFP) and shut-in pressure (Vogel shape)
WellDeliverabilityCurve b = WellDeliverabilityCurve.fromVogel(2.0e6, 250.0);

// 3. By sampling a fully configured WellSystem (captures full IPR+VLP physics once)
//    WellDeliverabilityCurve c =
//        WellDeliverabilityCurve.fromWellSystem(wellSystem, 40.0, 240.0, 11);

double rate  = b.rateAt(120.0);                 // Sm3/day at 120 bara back pressure
double slope = b.slopeAt(120.0);                // d(rate)/d(pressure) < 0
double aofp  = b.getAbsoluteOpenFlowPotential(); // Sm3/day at zero back pressure
double psi   = b.getShutInPressure();           // no-flow pressure
```

The Vogel inflow shape used by `fromVogel` is

$$
\frac{q}{q_{\max}} = 1 - 0.2\,\frac{p}{p_s} - 0.8\left(\frac{p}{p_s}\right)^2
$$

with $p_s$ the shut-in pressure and $q_{\max}$ the AOFP.

---

## The Network Solver

A network is a set of `NetworkNode` objects connected by `NetworkBranch` objects. Some node
pressures are fixed boundary conditions (reservoir datum, export header); the remaining free node
pressures are solved so that volumetric mass balance holds at every free node:

$$
R_n = Q_{\text{ext}} + \sum_{\text{in}} q - \sum_{\text{out}} q = 0
$$

`NetworkNewtonSolver` solves this with a damped global Newton&ndash;Raphson iteration using a
finite-difference Jacobian and self-contained Gaussian elimination with partial pivoting. If Newton
stalls it falls back to successive substitution.

```java
WellDeliverabilityCurve curve = WellDeliverabilityCurve.fromVogel(1.0e6, 200.0);

NetworkNewtonSolver solver = new NetworkNewtonSolver();
solver.addNode(NetworkNode.reservoir("RES", 200.0));   // fixed boundary
solver.addNode(NetworkNode.manifold("WH", 80.0));      // solved-for
solver.addNode(NetworkNode.sink("EXP", 40.0));         // fixed boundary
solver.addBranch(new WellBranch("well", "RES", "WH", curve, 200.0));
solver.addBranch(new FlowlineBranch("line", "WH", "EXP", 0.0, 1.0e-9, 0.0));
solver.setTolerance(10.0);                              // Sm3/day residual

NetworkNewtonSolver.NetworkSolutionResult r = solver.solve();
boolean ok = r.isConverged();
double q   = r.getBranchFlows().get("line");           // Sm3/day
double whp = r.getNodePressures().get("WH");           // bara
String how = r.getMethod();                            // "newton" or "successive-substitution"
```

### Branch pressure-drop law

A `FlowlineBranch` uses a quadratic pressure-drop model:

$$
\Delta P = \Delta P_{\text{static}} + a\,q + b\,q^2
$$

Build it from explicit coefficients, from a single reference operating point
(`FlowlineBranch.fromReferencePoint`), or from a Beggs&ndash;Brill sample
(`FlowlineBranch.fromBeggsBrillSample`).

A `WellBranch` scales its deliverability curve by a choke factor (0&ndash;1) and an optional lift
factor (&ge;1), and rescales for the current reservoir pressure relative to the pressure at which the
curve was built.

---

## IntegratedProductionModel

The orchestrator wires reservoir drives, well branches, flowlines, and an export sink into one
model, builds the solver, and exposes a steady-state `solve()` plus a time-marched `runProfile()`.

```java
IntegratedProductionModel model = new IntegratedProductionModel("DemoField");
model.setExportPressure(40.0);          // bara at the export boundary
model.setHydrocarbonPrice(0.5);         // currency per Sm3
model.setEnergyIntensity(0.05);         // kWh per Sm3
model.setEmissionIntensity(0.18);       // kg CO2 per Sm3

model.addWell("GAS-1", new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90),
    WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));
model.addWell("OIL-1", new OilTankDrive(220.0, 8.0e6, 120.0, 90.0),
    WellDeliverabilityCurve.fromVogel(4000.0, 220.0));

// Steady-state field balance
IntegratedSolveResult res = model.solve();
double fieldRate = res.getFieldRate();              // Sm3/day
double revenue   = res.getRevenue();                // /day
double co2       = res.getEmissionsKgPerDay();      // kg CO2/day
Map<String, Double> perWell = res.getWellRates();   // Sm3/day by well

// Multi-year profile (depletes each drive every step)
ProductionProfile profile = model.runProfile(10.0, 1.0);   // 10 years, 1-year steps
double peak = profile.getPeakRate();
double cum  = profile.getCumulativeProduction();
for (ProductionProfile.Point p : profile.getPoints()) {
  // p.getTimeYears(), p.getRateSm3PerDay(), p.getReservoirPressureBara() ...
}
```

`IntegratedProductionModel.toJson()` returns a compact JSON summary suitable for agent hand-off.

---

## Gas-Lift Allocation

When a fixed lift-gas budget must be shared across several gas-lifted wells, the optimum follows the
**equal-slope (Lagrangian) rule**: at the optimum every producing well has the same marginal oil
response per unit lift gas, $\partial q_o / \partial q_{\text{lift}} = \lambda$.

`GasLiftPerformanceCurve` models a single well's gas-lift performance curve (GLPC):

$$
q_o = q_{o,\text{base}} + a\sqrt{q_{\text{lift}}} - b\,q_{\text{lift}}
$$

with peak response at $q_{\text{lift}}^{*} = (a / 2b)^2$.

```java
GasLiftNetworkOptimizer lift = new GasLiftNetworkOptimizer();
lift.addWell("A", new GasLiftPerformanceCurve(800.0, 30.0, 0.02, 200000.0));
lift.addWell("B", new GasLiftPerformanceCurve(500.0, 45.0, 0.04, 200000.0));
lift.addWell("C", new GasLiftPerformanceCurve(1200.0, 20.0, 0.015, 200000.0));

GasLiftNetworkOptimizer.AllocationResult alloc = lift.allocate(150000.0); // Sm3/day budget
double totalOil  = alloc.getTotalOil();
double totalLift = alloc.getTotalLift();             // <= budget
Map<String, Double> split = alloc.getLiftRates();    // Sm3/day per well
```

The optimiser brackets $\lambda$ and finds it by bisection so the total allocated gas matches the
budget; it never injects a well past its GLPC peak.

---

## Well-Test Matching

`WellTestMatcher` fits an IPR model to measured (rate, flowing-pressure) points and returns a
ready-to-use `WellDeliverabilityCurve`.

```java
WellTestMatcher matcher = new WellTestMatcher();
matcher.addTestPoint(1500.0, 180.0)
       .addTestPoint(2600.0, 150.0)
       .addTestPoint(3400.0, 120.0);

WellTestMatcher.MatchResult m = matcher.fitVogel();        // or fitProductivityIndex()
WellDeliverabilityCurve curve = m.getCurve();
double pr   = m.getReservoirPressure();      // fitted reservoir pressure (bara)
double parm = m.getDeliverabilityParameter();// AOFP (Vogel) or PI (linear)
double rms  = m.getRmsError();               // Sm3/day
String name = m.getModel();                  // "Vogel" or "PI"
```

For a linear productivity index the IPR is $q = J\,(p_r - p_{wf})$; `fitProductivityIndex()`
grid-searches the reservoir pressure $p_r$ and uses the closed-form least-squares $J$ at each
candidate.

---

## Reservoir-to-Market Optimisation

`ReservoirToMarketOptimizer` chooses per-well choke openings (0&ndash;1) to maximise revenue or
field rate subject to a facility throughput capacity. The capacity is enforced with a steep penalty
whose slope exceeds the marginal objective value, so the optimiser drives the field rate down onto
the capacity bound rather than overshooting it. `optimize()` **never throws** &mdash; on failure it
returns an infeasible result.

```java
ReservoirToMarketOptimizer optimizer = new ReservoirToMarketOptimizer(model)
    .setObjective(ReservoirToMarketOptimizer.Objective.REVENUE)
    .setFacilityCapacity(500000.0);          // Sm3/day

ReservoirToMarketOptimizer.OptimizationResult opt = optimizer.optimize();
boolean feasible = opt.isFeasible();
double objective = opt.getObjectiveValue();
double rate      = opt.getFieldRate();        // <= capacity
Map<String, Double> chokes = opt.getChokeSettings(); // 0..1 per well
String json      = opt.toJson();
```

---

## Artificial-Lift Pumps

Two downhole artificial-lift pumps extend the standard `Pump` equipment. Both set the outlet stream
pressure and then delegate the thermodynamic boost to the base `Pump.run()`.

### Jet pump

`JetPump` implements the Cunningham / Gosline&ndash;O'Brien dimensionless head-ratio model. The head
ratio is

$$
H = \frac{p_d - p_s}{p_n - p_d}
$$

where $p_s$, $p_d$, $p_n$ are suction, discharge and nozzle (power-fluid) pressures, and the
discharge pressure follows $p_d = (p_s + H\,p_n)/(1 + H)$.

```java
JetPump jp = new JetPump("JP-1", suctionStream);
jp.setAreaRatio(0.25);             // nozzle/throat area ratio R
jp.setPowerFluidPressure(220.0);   // bara
jp.setOperatingFlowRatio(0.8);     // M = q_suction / q_nozzle
jp.run(null);
double pd  = jp.getDischargePressure();   // bara
double eff = jp.getEfficiency();          // M * H
```

### Sucker-rod (beam) pump

`SuckerRodPump` models positive-displacement beam pumping. The theoretical displacement is

$$
q_{th} = A_{\text{plunger}}\,S\,N\,\eta_v
$$

with plunger area $A$, stroke length $S$, strokes per minute $N$, and volumetric efficiency
$\eta_v$. It also estimates the polished-rod load.

```java
SuckerRodPump sr = new SuckerRodPump("SR-1", suctionStream);
sr.setPlungerDiameter(0.0381);     // m
sr.setStrokeLength(1.5);           // m
sr.setStrokesPerMinute(8.0);
sr.setVolumetricEfficiency(0.80);
sr.setPumpDepth(1500.0);           // m
sr.setDischargePressure(60.0);     // bara
sr.run(null);
double qActual = sr.getActualDisplacement("m3/day");
double load    = sr.getPolishedRodLoad();   // N
```

---

## End-to-End Example

A complete runnable demonstration that wires reservoir drives &rarr; matched/Vogel wells &rarr;
flowline tie-ins &rarr; export, then runs a steady-state solve, a 10-year decline profile, gas-lift
allocation, and choke optimisation, is provided at
`examples/neqsim/process/fielddevelopment/ReservoirToMarketExample.java`.

```java
IntegratedProductionModel model = new IntegratedProductionModel("DemoField");
model.setExportPressure(40.0).setHydrocarbonPrice(0.5);
model.addWell("GAS-1", new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90),
    WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));
model.addWell("OIL-1", new OilTankDrive(220.0, 8.0e6, 120.0, 90.0),
    WellDeliverabilityCurve.fromVogel(4000.0, 220.0));

IntegratedSolveResult res = model.solve();
ProductionProfile profile = model.runProfile(10.0, 1.0);
ReservoirToMarketOptimizer.OptimizationResult opt =
    new ReservoirToMarketOptimizer(model).setFacilityCapacity(1.0e6).optimize();
```

---

## Validation

The stack is covered by JUnit tests asserting monotonic deliverability curves, network convergence
(Newton vs. successive substitution), reservoir depletion, budget-honouring gas-lift allocation,
well-test round-trip accuracy, profile decline, and capacity-constrained optimisation:

- `src/test/java/neqsim/process/fielddevelopment/integrated/IntegratedProductionTest.java`
- `src/test/java/neqsim/process/equipment/pump/ArtificialLiftPumpTest.java`

---

## Related Documentation

- [Field Development Framework](README) &mdash; lifecycle overview and digital field twin
- [API Guide](API_GUIDE) &mdash; detailed usage for every field-development class
- [Mathematical Reference](MATHEMATICAL_REFERENCE) &mdash; EoS, economics, and flow foundations
- [Host Tie-In Capacity](HOST_TIE_IN_CAPACITY) &mdash; brownfield capacity and debottlenecking
- [Multi-Scenario Production Optimization](MULTI_SCENARIO_PRODUCTION_OPTIMIZATION) &mdash; VFP and scenario sweeps
