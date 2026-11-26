---
title: "Simulator-Derived Reservoir State with Well and Network Models"
description: "Use Eclipse or OPM Flow reservoir-state tables with the canonical WellSystem and FieldWellNetworkCoupler path, or with the reduced-order hybrid tree model for fast profile studies."
keywords: "hybrid model, reservoir proxy, Eclipse, OPM Flow, production profile, IPR, VLP, VFPPROD, lift curve, gathering network, choke, back-pressure, scenario interpolation, molar feed, facility capacity"
---

A production profile replayed from a reservoir simulator gives the same rate at the same cumulative production
whatever the production network does. It cannot say what happens to the other wells when one well is choked, when
the separator pressure changes or when a well is routed to another manifold.

The reservoir-state proxy keeps the reservoir **state** from the simulator and lets a NeqSim well/network model solve
the **rates**:

```
simulator results -> reservoir-state proxy -> WellSystem -> canonical field topology
                  -> well operating point -> component molar rates -> NeqSim network and process
```

Reservoir pressure, water cut, gas-oil ratio and productivity index are read from tables at the current cumulative
volumes. Each well then produces what its inflow performance and tubing lift curve allow against the pressure of the
network node it is tied to, and the node pressures follow from the commingled rates through the flowlines and the
riser. Wells and network are solved together, so a change anywhere moves the operating point of every well.

For production/injection fields, mixed hydraulic fidelity, loops, typed subsea equipment and host/process coupling,
use `ReservoirStateWellBinding` with the existing `WellSystem`, `FieldNetworkTopology`, `LoopedPipeNetwork` and
`FieldWellNetworkCoupler`. This is the canonical path. `GatheringNetwork` and `HybridProductionModel` remain a
reduced-order, tree-only compatibility model for rapid profile studies; they are not a second canonical SURF graph.

All reservoir-state and reduced-order classes are in `neqsim.process.fielddevelopment.hybrid`, except the flowline lift model
`neqsim.process.fielddevelopment.reservoir.FlowlineVlpProvider`. The model is a proxy of the simulator runs it was
built from; it does not replace the reservoir simulator.

## Layers

| Layer | Class | What it does |
|---|---|---|
| Reservoir state, one run | `ReservoirStateProxy` | Pressure, water cut, GOR, productivity index and optional composition against one cumulative volume. Linear interpolation; values are held outside the table and `isOutsideValidity()` reports it |
| Reservoir state, several runs | `ScenarioReservoirState` | Blends the tables of several simulator runs on a second coordinate: cumulative injection or elapsed time |
| Canonical well binding | `ReservoirStateWellBinding` | Applies pressure, linear oil PI and optional proxy composition to an existing `WellSystem` without replacing its inlet identity |
| Canonical field solve | `FieldWellNetworkCoupler` + `FieldNetworkTopology` | Solves live production/injection wells on the same typed `LoopedPipeNetwork`, including per-edge hydraulic model selection |
| Well | `HybridWell` | Inflow performance, tubing lift curve, rate target, fixed choke, minimum bottom-hole pressure, optional drainage unit shared with other wells |
| Lift curve lookup | `VfpTableLookup` | Reads a METRIC `VFPPROD` table the way Eclipse and OPM Flow do: multilinear, linear extrapolation outside the axes; oil, liquid or gas rate basis |
| Fast and full fidelity | `CorrectedVlpProvider` | A fast lift curve plus a pressure offset calibrated against a live model |
| Live branch model | `FlowlineVlpProvider` | Chain of `PipeBeggsAndBrills` segments solved for the inlet pressure |
| Reduced-order network | `GatheringNetwork` | Compatibility tree of manifolds, flowlines and riser to one fixed-pressure boundary |
| Fluid | `MolarFeedAdapter` | Well rates to component molar rates, per well and commingled |
| Capacities | `FacilityConstraints` | Oil, water, gas and liquid capacities of the receiving facility |
| Solver | `HybridProductionModel` | Coupled well and network solve, time stepping, fidelity modes, capacity allocation |
| Result | `HybridStepResult` | Well operating points, node pressures, field totals and solver diagnostics |

Basis of the model: oil rate in Sm3/day, productivity index in Sm3 oil/(day bar), water cut as fraction of the liquid,
gas-oil ratio in Sm3/Sm3, pressures in bara. Every lift model handed to a well or a branch uses this basis
(`VfpTableLookup.onOilBasis()` converts a table on another basis).

## Canonical topology workflow

Build the typed production/injection topology as described in [Well and SURF Network Integration](SURF_NETWORK_INTEGRATION),
then apply each proxy before the coupled solve. The binding owns no graph, solver or time integration:

```java
Stream reservoir = new Stream("W1 reservoir", reservoirFluid);
WellSystem well = new WellSystem("W1", reservoir);
well.setTubingLength(1800.0, "m");
well.setTubingDiameter(0.12, "m");

ReservoirStateWellBinding stateBinding =
    new ReservoirStateWellBinding(stateA, well, new MolarFeedAdapter(recombinationFactory));
FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
coupler.bindProductionWell("W1", well);

stateBinding.apply();
FieldWellNetworkCoupler.CouplingResult result = coupler.run();
if (!result.isConverged()) {
  throw new IllegalStateException(result.getMessage());
}

// Advance cumulative state explicitly, then reapply it before the next canonical solve.
stateA.produce(oilSm3, waterSm3, gasSm3);
stateA.inject(injectedWaterSm3, injectedGasSm3);
stateA.elapse(days);
stateBinding.apply();
```

The proxy PI is in Sm3 oil/(day bar). The binding selects the linear `WellSystem` production-index law; the older
`Sm3/day/bar2` unit continues to select the squared-pressure law. With a `MolarFeedAdapter`, black-oil WCT/GOR or
compositional proxy data updates the fluid on the same inlet `Stream` object. Without it, composition is retained.
Check `isOutsideValidity()` separately; an internally converged network outside the simulator envelope is not a
qualified prediction. The canonical binding and coupled-solve workflow is exercised by
`ReservoirStateWellBindingTest`.

## Reduced-order compatibility quick start

```java
// Reservoir-state proxies post-processed from a reservoir simulator run (here: typed in).
double[] cumulativeOil = {0.0, 1.0e6, 2.0e6, 3.0e6}; // Sm3
ReservoirStateProxy stateA = new ReservoirStateProxy("A", cumulativeOil, new double[] {250.0, 225.0, 205.0, 190.0})
    .setWaterCut(new double[] {0.0, 0.10, 0.25, 0.40}).setGasOilRatio(new double[] {150.0, 150.0, 180.0, 240.0})
    .setProductivityIndex(new double[] {20.0, 18.0, 16.0, 14.0});
ReservoirStateProxy stateB = new ReservoirStateProxy("B", cumulativeOil, new double[] {250.0, 230.0, 212.0, 198.0})
    .setWaterCut(new double[] {0.0, 0.05, 0.15, 0.30}).setGasOilRatio(new double[] {150.0, 150.0, 160.0, 190.0})
    .setProductivityIndex(new double[] {15.0, 14.0, 13.0, 12.0});

// Lift models on the oil rate, water cut and gas-oil ratio basis. In a study these are VFPPROD tables
// (VfpTableLookup.fromFile(path).onOilBasis()) or live models (TubingVlpProvider, FlowlineVlpProvider).
VlpProvider tubing = (rate, wellhead, waterCut, gor, alq) -> wellhead + 90.0 + 40.0 * waterCut
    + 3.0e-5 * rate * rate;
VlpProvider flowline = (rate, outlet, waterCut, gor, alq) -> outlet + 3.0e-6 * Math.pow(rate / (1.0 - waterCut), 2);
VlpProvider riser = (rate, outlet, waterCut, gor, alq) -> outlet + 1.5e-6 * Math.pow(rate / (1.0 - waterCut), 2);

// Network: wells -> manifold -> riser base -> separator at 30 bara.
GatheringNetwork network = new GatheringNetwork("SEPARATOR", 30.0);
network.addBranch("RISER", "RISER_BASE", "SEPARATOR", riser);
network.addBranch("FLOWLINE", "MANIFOLD", "RISER_BASE", flowline);
HybridWell wellA = new HybridWell("A", stateA, tubing);
HybridWell wellB = new HybridWell("B", stateB, tubing);
network.connectWell(wellA, "MANIFOLD");
network.connectWell(wellB, "MANIFOLD");

HybridProductionModel model = new HybridProductionModel("example", network);
HybridStepResult start = model.solve();

// What happens to well B when well A is choked to half its rate?
wellA.setOilRateTarget(0.5 * start.getWell("A").getOilRate());
HybridStepResult choked = model.solve(); // well B now produces more: the manifold pressure fell

// Two years in monthly steps with a gas-handling capacity.
model.setFacilityConstraints(
    new FacilityConstraints().setCapacity(FacilityConstraints.Quantity.GAS, 0.9 * choked.getFieldGasRate()));
List<HybridStepResult> profile = model.run(730.0, 30.4);
```

This compatibility example is run by `HybridReservoirNetworkDocExamplesTest`.

## Proxy tables from the simulator

Reading Eclipse or OPM Flow summary files is done in Python with `resdata`; the community skill
`neqsim-simulator-state-proxy` builds the tables and writes them as comma-separated files that
`ReservoirStateProxy.fromCsv(name, CumulativeQuantity.OIL_PRODUCED, path)` reads:

```
cumulative,pressure,waterCut,gor,pi,ELAPSED_TIME,WATER_INJECTED
0.0,180.0,0.0,174.4,41.2,0.0,0.0
52257.4,173.9,0.004,174.4,40.8,30.4375,0.0
```

Columns: `cumulative` and `pressure` are required; `waterCut`, `gor`, `pi` and `molesPerSm3` are optional; a column
named after a `CumulativeQuantity` records how that quantity developed along the run; `z:<component>` columns carry a
wellstream composition.

The productivity index is not the simulator's `WPI`. It is derived as oil rate divided by (reference pressure minus
flowing bottom-hole pressure), so it is consistent with the pressure the table uses. Two references are in use:

- **Drainage unit.** The region pressure of a pressure-connected volume, keyed on the cumulative oil of all its
  wells. Give the wells of the unit the same `ReservoirStateProxy` with `HybridWell.setDrainageUnit(unit)`.
- **Well.** The well block pressure, keyed on the well's own cumulative oil.

To start part-way through production, use `setCumulative` on the proxy or scenario state. Setting
`OIL_PRODUCED` or `WATER_PRODUCED` replaces that phase total and recomputes `LIQUID_PRODUCED` as
their sum in Sm³. If only total liquid is known, set `LIQUID_PRODUCED` directly. This leaves the
phase split unspecified. Subsequent production adds the oil and water increments to the reported
total. Saving and restoring a cumulative-state snapshot preserves all coordinates exactly.

## Solving and time stepping

`solve()` finds the well rates and node pressures at the current cumulative volumes by fixed-point iteration on the
node pressures with Aitken relaxation. The well operating point is the highest rate at which the bottom-hole pressure
offered by the reservoir is at least the pressure the tubing needs, so a U-shaped lift curve gives the stable
intersection. Check `HybridStepResult.isConverged()` before using a result.

`step(dt)` advances the cumulative volumes and solves the new state. Changes made between steps (rate targets, well
status, routing with `GatheringNetwork.connectWell`, separator pressure with `setBoundaryPressure`) act from the start
of the step, like a keyword after a `TSTEP` in a simulator schedule.

| `TimeIntegration` | Rates held over the step | Order | Use |
|---|---|---|---|
| `EXPLICIT_EULER` | start of step | first | quick estimates |
| `PREDICTOR_CORRECTOR` (default) | average of start and predicted end | second | profiles with long steps |
| `IMPLICIT_EULER` | end of step, by iteration | first | reproducing a simulator run: an implicit simulator reports `cumulative[n] = cumulative[n-1] + rate[n] * dt` |

## Fidelity modes

| `Mode` | Lift and branch models | Cost |
|---|---|---|
| `SURROGATE` | fast lift curves | a profile of 100 steps runs in a fraction of a second |
| `FULL_NETWORK` | the reference models in every iteration | one live pipe solve per branch and iteration |
| `HYBRID` | fast lift curves, corrected against the reference models every `setHybridUpdateInterval(n)` steps | a few reference evaluations per correction |

A reference model is attached with `GatheringNetwork.setReferenceModel(branch, provider)` or
`HybridWell.getTubing().setReference(provider)`. At a correction the reference network is evaluated at the current
rates and each branch gets the offset that makes its fast curve reproduce the reference inlet pressure there.

## Facility capacities

With `setFacilityConstraints(...)` the model holds wells back until the field rates fit the capacities. For the most
utilised capacity it restricts the flowing well that uses most of that capacity per oil volume (highest gas-oil ratio
for a gas capacity, highest water cut for water, liquid and oil). Wells whose intensity lies within the tie tolerance
of the highest (`setAllocationTieTolerance`, default 2 %) are restricted together, to a common fraction of their
rates, so that identical wells share a restriction instead of taking turns. The fraction is found so that the
capacity is just met while the other wells respond through the network. If closing the group is not enough it stays
closed and the next group is restricted. Wells held back report the control mode `FACILITY_LIMIT`. This is a rule,
not an optimiser: use the fast mode inside an optimiser when the best allocation is the question.

## Scenarios and the validity of the proxy

A table from one simulator run is exact for that run. It stays close while the depletion path is similar and departs
when the rate history changes, because water cut and productivity index at the same cumulative oil depend on how fast
the oil was produced (coning, aquifer influx, voidage replacement). Three tools handle this:

- `ScenarioReservoirState` blends the tables of several runs. With `CumulativeQuantity.WATER_INJECTED` it follows an
  injection strategy between a low and a high injection run (set the rate with
  `HybridProductionModel.setInjectionRate`). With `CumulativeQuantity.ELAPSED_TIME` it follows the depletion speed
  between a fast and a slow run.
- `HybridStepResult.isOutsideProxyValidity()` is true when a table is evaluated beyond its last row or a blend is
  outside the envelope of its scenarios. Run the source cases longer than the cases they are used for.
- `HybridWell.refreshState` and `HybridProductionModel.refreshDrainageUnit` replace a proxy with one from a new
  simulator run and keep the cumulative volumes.

Blend only on a coordinate that the strategy drives. A coordinate that the proxy itself predicts (cumulative water or
gas produced) never leaves the first scenario.

## Measured accuracy and how many simulator runs are needed

Measured in November 2026 against OPM Flow 2026.04 on a synthetic two-compartment black-oil field (four producers,
two manifolds, two flowlines, one riser, 8 years on monthly steps). The simulator and the model read the same
`VFPPROD` tables, the simulator balanced its network in every Newton iteration, and the model used
`IMPLICIT_EULER` on the simulator's time steps. The error is the mean absolute error of the well oil rates; the
cumulative oil error at 8 years is given in brackets. A replay of the base profile (rate against cumulative oil) is
the comparison.

| Strategy predicted | Simulator runs behind the proxy | Hybrid model | Profile replay |
|---|---|---|---|
| The base case itself | 1 | 0.03 % (0.00 %) | 1.5 % (+0.6 %) |
| Choke swap: one well to 50 %, another +50 % | 1, well block pressure | 0.7 % (-0.06 %) | 1.8 % (+0.4 %) |
| Choke swap | 1, drainage-unit pressure | 2.5 % (-0.08 %) | 1.8 % (+0.4 %) |
| Separator pressure +10 bar after one year | 1 | 17 % (-9.3 %) | 27 % (+18 %) |
| Separator pressure +10 bar after one year | 2, blended on elapsed time | 1.5 % (-0.6 %) | 27 % (+18 %) |
| Water injection at half the rate of the high scenario | 2, blended on cumulative injection | 16 % (+3.0 %) | 34 % (-25 %) |
| Water injection starting in year 2 | 3, blended on cumulative injection | 3.6 % (-0.6 %) | 31 % (-24 %) |

What the measurement supports:

- **One run** is enough for a change that moves production between wells for a limited time (choke changes, rate
  targets). It is not enough for a change that alters the depletion rate for the rest of field life. For
  separator-pressure rises of 2, 5, 10 and 15 bar the one-run model overstated the loss of cumulative oil by 58 to 73 %
  of the true effect at every step size; the profile replay shows no loss at all. With one run, treat the two as
  bounds.
- **Two runs** that bracket the change, blended on `ELAPSED_TIME`, held the cumulative oil within 0.6 % for all four
  pressure steps.
- **Three injection scenarios** are needed when gas rates matter: with two, the oil total was within 3 % but the gas
  rate was 74 % off, because the gas-oil ratio switches at the bubble point.
- **Which variable fails** depends on the strategy. Replacing one state variable at a time by the table of the true
  run showed that water cut and productivity index carry the error of the back-pressure case (pressure against
  cumulative oil holds), and that reservoir pressure carries the error of the injection case.
- **Pressure form.** Well block pressure was better for the choke swap (0.7 % against 2.5 %); the drainage-unit form
  was slightly better for the back-pressure case (17 % against 20 %). Test both on the base run and one blind run.
- **Fidelity.** The riser table needed monotonicity repairs of several bar and overstated the back-pressure by up to
  4.1 bar at low rates; `SURROGATE` mode was then 17 % below `FULL_NETWORK` on field oil rate. `HYBRID` mode with a
  correction every 12 steps was within 1.2 % on average (largest 3.6 %) using 60 reference evaluations instead of
  about 1400, and a profile took 39 s instead of an estimated 76 minutes. `SURROGATE` mode took 0.3 s.

These figures are for one synthetic field whose wells sit near their lift limit late in life. Repeat the base-case
test and one blind test on the model in hand before quoting accuracy for it.

Before scoring a model against a simulator run, check the tables themselves: the tubing table evaluated at the
simulator's rates and tubing-head pressure must return its bottom-hole pressure (here within 0.08 bar), and the
branch tables must return its node pressures (here within 0.05 bar). If they do not, the simulator balanced its
network less often than every iteration, or the tables differ.

## Molar feed

`MolarFeedAdapter` turns the solved rates into fluids with component molar flow, for one well or commingled. With a
black-oil simulation it recombines surface gas, stock-tank oil and water at the well's gas-oil ratio and water cut
(`RecombinationFluidFactory` on the OIL, WCT, GOR basis). With a compositional simulation the proxy carries the
wellstream composition and its moles per stock-tank oil volume. The fluid goes straight into a NeqSim `Stream`.

## Reduced-order model limitations

- `GatheringNetwork` is tree-shaped. Use the canonical `FieldNetworkTopology`/`LoopedPipeNetwork` path for production
  and injection systems, loops, typed equipment, per-edge hydraulic fidelity and host coupling.
- Oil-rate basis. Gas-condensate wells need a gas-rate basis.
- One blending coordinate per proxy.
- The capacity allocation is a ranking rule, not an optimum.
- The model inherits the limits of the simulator runs: it cannot represent a strategy outside their envelope.

## Related

- [Integrated Production Modelling](INTEGRATED_PRODUCTION_MODELLING): the deliverability-curve model with tank
  drives; `ReservoirStateProxy` also works there as a `ReservoirDrive`.
- [Well and SURF Network Integration](SURF_NETWORK_INTEGRATION): full network hydraulics with `LoopedPipeNetwork`.
- `VfpTable` and `VfpPipeline` (package `neqsim.process.equipment.pipeline`) use a gas-basis `VFPPROD` table as a
  pipeline unit in a `ProcessSystem`, without extrapolation. `VfpTableLookup` is the lift-model form used here: oil,
  liquid or gas rate basis with WCT/WOR/WGR and GOR/GLR/OGR ratios, linear extrapolation outside the axes, behind
  the `VlpProvider` interface. Both parse the keyword with `VfpDeckReader`.
- Skills: `neqsim-integrated-production-and-lifecycle`, `neqsim-vfp-lift-curves`, and the community skill
  `neqsim-simulator-state-proxy`.
