---
title: "Gas Network Operations and Optimization"
description: "Conservative composition mixing, coupled thermal hydraulics, point quality, whole-network optimization, and EOS linepack for gas networks."
---

This guide describes the `LoopedPipeNetwork` workflow for heterogeneous gas
sources, contractual handover points, constrained allocation, and multi-period
linepack. The examples use public NCS context and synthetic data. They are
engineering demonstrations, not capacity forecasts or contractual
specifications.

The executed companion notebook is
[`norwegian_ncs_gas_network_optimization.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/process/norwegian_ncs_gas_network_optimization.ipynb).

## Calculation sequence

Use the capabilities in this order:

1. Define compatible source fluids and network topology.
2. Enable edge-local composition and thermal coupling where the global-template
   screening mode is insufficient.
3. Assign a typed quality profile to each governed point.
4. Register bounded decisions, constraints, and objectives.
5. Solve a steady candidate or a discrete planning horizon.
6. Inspect hydraulic, composition, quality, and inventory residuals before
   accepting the result.

## Conservative component mixing

Junction mixing conserves component molar flow. For inlet \(j\) with mass rate
\(\dot m_j\), molar mass \(M_j\), and component mole fraction \(z_{i,j}\):

\[
\dot n_i = \sum_j \frac{\dot m_j}{M_j} z_{i,j},
\qquad
z_{i,\mathrm{mix}} =
\frac{\dot n_i}{\sum_k \dot n_k}.
\]

Components are aligned by component name, not array position. Compatible
slates are unioned. The mixed state is flashed at solved node pressure using
the inlet enthalpy-flow sum. The implementation iterates synchronously, so
loops and flow reversals do not depend on edge insertion order.

```java
network.setNodeFluid("rich source", richGas);
network.setNodeFluid("lean source", leanGas);

NetworkCompositionConvergenceReport mixing =
    network.updateCompositionalMixingWithReport();

if (!mixing.isConverged()
    || mixing.getMaxComponentMassBalanceResidualKgS() > 1.0e-9) {
  throw new IllegalStateException(mixing.getMessage());
}
```

Mixing rejects incompatible EOS classes, mixing rules, and conflicting
TBP/plus-fraction definitions. Re-characterize unlike assays to a declared
common slate before mixing.

## Coupled composition, heat transfer, and hydraulics

Legacy mode uses one `fluidTemplate` on every edge. It remains the default for
backward compatibility and fast screening. Enable coupled mode when local
composition, condensation, compression temperature, or heat transfer can
change capacity:

```java
network.setCompositionalHydraulicsEnabled(true);
network.setThermalHydraulicsEnabled(true);
network.setCouplingMaxIterations(20);
network.setCouplingTolerances(1.0e-5, 100.0); // kg/s, Pa

LoopedPipeNetwork.NetworkPipe export =
    network.addPipe("hub", "delivery", "export", 120000.0, 1.0);
export.setElevationProfile(
    new double[] {0.0, 40000.0, 120000.0},
    new double[] {-100.0, -320.0, -20.0});
export.setAmbientTemperatureProfile(
    new double[] {0.0, 120000.0},
    new double[] {278.15, 283.15});
export.setHeatTransferProfile(
    new double[] {0.0, 120000.0},
    new double[] {4.0, 7.0});

network.run();
NetworkCouplingReport coupling = network.getNetworkCouplingReport();
```

Profile distances are measured from `fromNode` in metres. Elevation is metres,
ambient temperature is kelvin, and overall heat-transfer coefficient is
W/(m² K). On flow reversal the physical inlet switches to `toNode`; route
profiles are traversed in the opposite direction.

The outer report separates:

- final hydraulic convergence;
- maximum edge-flow change in kg/s;
- maximum node-pressure change in Pa;
- maximum node-temperature change in K;
- conservative composition residuals.

## Point-specific gas quality

`NetworkQualityProfile` is assigned to a named node. Different points can have
different metrics, references, versions, provenance, and named exceptions.

```java
NetworkQualityProfile delivery =
    new NetworkQualityProfile("Synthetic Area D handover")
        .withEffectivePeriod("education-v1", "2026-01-01", null)
        .withProvenance("Synthetic limits for the public example");

delivery.addUpperLimit(
    GasQualityMetric.CO2_MOLE_PERCENT, 2.5, "mol%");
delivery.addRange(
    GasQualityMetric.WOBBE_INDEX, 40.0, 60.0, "MJ/Sm3",
    new QualityReference().withIso6976Reference(15.0, 15.0));
delivery.addUpperLimit(
    GasQualityMetric.HYDROCARBON_DEW_POINT_TEMPERATURE,
    -10.0, "C", QualityReference.atPressure(50.0, "barg"));

network.setQualityProfile("delivery", delivery);
network.run();
NetworkQualityComplianceReport report =
    network.getQualityComplianceReport("delivery");
```

Each result includes value, unit, lower/upper limit, signed nearest-limit
margin, status, method, provenance, and reference conditions.
`NOT_CALCULABLE` is non-compliant. Use measured attributes with an explicit
method, provenance, effective date, and blending rule when a contaminant is not
represented rigorously in the EOS fluid.

Pressure references distinguish `bara` and `barg`. ISO 6976 volume and
combustion reference temperatures are stored separately.

## Whole-network optimization

`NetworkOptimizer` supports registered decisions, objective terms, and typed
hard/soft constraints. Candidate state is restored after every evaluation.

```java
NetworkOptimizer optimizer = new NetworkOptimizer(network);
optimizer.addDecisionVariable(new NetworkDecisionVariable(
    "source.rich.rate",
    NetworkDecisionVariable.Type.SOURCE_RATE,
    "rich source", "kg/hr",
    NetworkDecisionVariable.RateBasis.MASS,
    10000.0, 250000.0));
optimizer.addDecisionVariable(new NetworkDecisionVariable(
    "edge.export.availability",
    NetworkDecisionVariable.Type.EDGE_AVAILABILITY,
    "export", "-", NetworkDecisionVariable.RateBasis.NONE,
    0.1, 1.0));
optimizer.addObjective(NetworkObjectives.maximizeThroughput(1.0));
optimizer.addConstraint(NetworkConstraints.convergence());
optimizer.addConstraint(NetworkConstraints.qualityCompliance(true));

NetworkOptimizer.OptimizationResult optimum = optimizer.optimize();
```

BOBYQA is a bounded local derivative-free method. CMA-ES is a deterministic
seeded population method for less smooth cases. Neither proves global
optimality. Scale soft constraints explicitly, use finite bounds, and verify
the selected candidate independently.

`ProcessAutomation` exposes addresses such as:

```text
network.node.delivery.pressure
network.source.rich source.rate
network.sink.delivery.nomination
network.edge.export.flowRate
network.edge.export.availability
network.compressor.export station.speed
```

Rate-bearing decisions carry an explicit mass, molar, standard-volume,
actual-volume, or energy basis.

## Multi-period linepack

`NetworkPlanningHorizon` uses a discrete inventory balance:

\[

L_{p,t+1} = L_{p,t} + \Delta t
(\dot m_{p,t}^{in} - \dot m_{p,t}^{out}
\quad - \dot m_{p,t}^{fuel} - \dot m_{p,t}^{loss}).

\]

Initial linepack is calculated from pipe volume, average absolute pressure,
temperature, local composition, and EOS \(Z\). Component inventories are
carried in moles and reported together with mass and standard volume.

```java
NetworkPlanningHorizon horizon =
    new NetworkPlanningHorizon(network);
horizon.addHourlyPeriods("2026-01-01T00:00:00Z", 24);
horizon.setInitialLinepackFromSolvedState();
horizon.addNomination("delivery", demand, "kg/hr");
horizon.derateElement("export compressor", 8, 14, 0.0);
horizon.setFuelSchedule("export", fuel, "kg/s");
horizon.setLinepackBounds("export", minimumKg, maximumKg);
horizon.setTerminalLinepackTarget("export", targetKg);

NetworkScheduleResult schedule = horizon.optimize();
```

The planning layer is a steady-period screening model. It does not replace a
high-frequency transient pipeline simulation for rapid valve actions, thermal
fronts, surge, or control-system verification.

## Conservative transient species transport

`TransientCompositionalPipeNetwork` propagates a finite composition event
through source branches, conservative junction mixing, edge linepack, and a
delivery point. It is a separate high-frequency species model for prescribed
flows; it does not alter the steady hydraulics or planning APIs above.

For physical cell $j$, fixed gas mass $M_j$, component mass fraction
$Y_{i,j}$, positive edge mass rate $q$, and timestep $\Delta t$, the
implicit upwind balance is

$$
M_j\left(Y_{i,j}^{n+1}-Y_{i,j}^{n}\right)
=\Delta t\,q\left(Y_{i,j-1}^{n+1}-Y_{i,j}^{n+1}\right).
$$

The inlet of cell zero is the accepted upstream node state. At a junction,
incoming integrated component masses are combined by canonical NeqSim
component name:

$$
Y_{i,\mathrm{mix}}=
\frac{\sum_e m_{i,e}^{out}}{\sum_k\sum_e m_{k,e}^{out}}.
$$

The mixed mass rate and composition become the downstream edge boundary in the
same timestep. Consequently internal edge boundaries cancel from the
whole-network balance, while every edge retains its own distributed inventory
and residence-time delay.

The synthetic Åsgard/Kristin-to-Kårstø teaching topology is:

```java
TransientCompositionalPipeNetwork transientNetwork =
    new TransientCompositionalPipeNetwork("Norwegian export teaching case");
transientNetwork.addNode("asgard");
transientNetwork.addNode("kristin");
transientNetwork.addNode("junction");
transientNetwork.addNode("karsto");

// All fluids in this example are one-phase gases at 300 K and 70 bara.
transientNetwork.addPipe(
    "asgardBranch", "asgard", "junction", 2000.0, 0.4, 12, asgardGas);
transientNetwork.addPipe(
    "kristinBranch", "kristin", "junction", 2000.0, 0.4, 12, kristinGas);
transientNetwork.addPipe(
    "export", "junction", "karsto", 4000.0, 0.4, 12, mixedGas);

transientNetwork.setSourceSchedule(
    "asgard",
    new double[] {0.0},
    new SystemInterface[] {asgardGas},
    new double[] {20.0}); // kg/s
transientNetwork.setSourceSchedule(
    "kristin",
    new double[] {0.0, 600.0, 1800.0},
    new SystemInterface[] {kristinGas, kristinHighCo2, kristinGas},
    new double[] {20.0, 18.0, 20.0});

transientNetwork.run(5400.0, 60.0);
TransientCompositionalPipeNetworkHistory species =
    transientNetwork.getSpeciesHistory();
double[] junctionCo2 =
    species.getNodeMassFractionHistory("junction", "CO2");
double[] karstoCo2 =
    species.getNodeMassFractionHistory("karsto", "CO2");

if (species.getFinalNetworkReport()
    .getMaximumRelativeInventoryResidual() > 1.0e-8) {
  throw new IllegalStateException(
      species.getFinalNetworkReport().getMessage());
}
```

`getNodeMassFractionHistory` returns **mass fraction**, not mole fraction.
`getEdgeReports`, `getJunctionReports`, and `getNetworkReports` expose immutable,
time-aligned inventory, cumulative boundary-mass, cumulative residual,
boundedness, and profile data.
All array getters return defensive copies. `toJson()` on the history or an
individual report is the stable Python/JPype capture path.

With `neqsim_dev_setup.py`, Python can use the same API:

```python
import jpype

SystemInterface = jpype.JClass("neqsim.thermo.system.SystemInterface")
DoubleArray = jpype.JArray(jpype.JDouble)
FluidArray = jpype.JArray(SystemInterface)

network = ns.TransientCompositionalPipeNetwork("transient export")
# Add the four nodes and three pipes as in the Java example.
network.setSourceSchedule(
    "kristin",
    DoubleArray([0.0, 600.0, 1800.0]),
    FluidArray([kristin_gas, kristin_high_co2, kristin_gas]),
    DoubleArray([20.0, 18.0, 20.0]),
)
network.run(5400.0, 60.0)
history = network.getSpeciesHistory()
karsto_co2_mass_fraction = list(
    history.getNodeMassFractionHistory("karsto", "CO2")
)
history_json = str(history.toJson())
```

Validated scope and required diagnostics:

- directed acyclic gathering topology with at most one outgoing edge per node;
- strictly positive prescribed source flows and conservative mixing flow
  continuity;
- one gas phase and one common temperature across initial and scheduled states;
- no hydraulic coupling, flow splitting, reverse flow, recirculation, thermal
  transport, dispersion, or phase appearance;
- edge mass initialized from EOS density and geometric volume, then held fixed;
- default fail-loud relative component-balance tolerance $10^{-8}$.

Use a joint grid/timestep refinement study for each engineering case. Flow
reversal, a two-phase flash, a temperature mismatch, an unsupported topology,
or a failed conservation/boundedness criterion raises an explicit exception
before the state is accepted.

## Coupled transient hydraulics with scheduled source rates

Use `TransientGasNetwork` when source mass rates and compositions are scheduled,
the delivery pressure is fixed, and source pressure must be solved together
with linepack. This boundary-value problem differs from both APIs above:

- `TransientCompositionalPipeNetwork` prescribes edge flow and holds each cell's
  gas mass fixed; it transports species without solving pressure.
- `TransientGasNetwork` prescribes positive source rates, fixes one sink
  pressure, and solves source/junction pressure plus edge inlet, average, and
  outlet flow. Source pressure is an output.
- This is not deliverability control. Source rates do not fall when a pressure
  limit is reached; an infeasible pressure or edge capacity raises an exception.

For an edge with quasi-steady Darcy flow \(\bar q_e\) and EOS-linearized
linepack \(M_e\), the implicit storage split is

$$
\dot M_e = \frac{M_e^{n+1}-M_e^n}{\Delta t},\qquad
q_e^{in}=\bar q_e+\frac{\dot M_e}{2},\qquad
q_e^{out}=\bar q_e-\frac{\dot M_e}{2}.
$$

Each solved node closes its face-flow balance. For a scheduled source \(s\),

$$
q_s^{schedule}+\sum_e q_e^{out}-\sum_e q_e^{in}=0.
$$

The conservative finite-volume species update uses those same face flows and
the new cell masses, so packing, unpacking, and composition residence time share
one mass ledger.

The synthetic Åsgard/Kristin-to-Kårstø rate event can be configured as follows.
The gas objects use a common component slate and 288.15 K; `kristinEventGas`
contains 4 mol% CO2.

```java
TransientGasNetwork hydraulic =
    new TransientGasNetwork("Synthetic Asgard and Kristin to Karsto");
hydraulic.addNode("asgard");
hydraulic.addNode("kristin");
hydraulic.addNode("junction");
hydraulic.addNode("karsto");
hydraulic.addPipe(
    "asgardBranch", "asgard", "junction", 1.0, 1.0, 50.0e-6, 1, asgardGas);
hydraulic.addPipe(
    "kristinBranch", "kristin", "junction", 1.0, 1.0, 50.0e-6, 1, kristinGas);
hydraulic.addPipe(
    "export", "junction", "karsto", 700000.0, 0.987, 50.0e-6, 12, mixedGas);

hydraulic.setSourceSchedule(
    "asgard", new double[] {0.0}, new SystemInterface[] {asgardGas},
    new double[] {343.125});
hydraulic.setSourceSchedule(
    "kristin", new double[] {0.0, 6.0 * 3600.0, 18.0 * 3600.0},
    new SystemInterface[] {kristinGas, kristinEventGas, kristinGas},
    new double[] {114.375, 142.96875, 114.375});
hydraulic.setFixedPressureBoundary("karsto", 110.0, "bara");
hydraulic.setSourcePressureLimits("asgard", 110.0, 240.0, "bara");
hydraulic.setSourcePressureLimits("kristin", 110.0, 240.0, "bara");

hydraulic.run(36.0 * 3600.0, 1800.0);
TransientGasNetworkHistory history = hydraulic.getHistory();
double[] sourcePressure =
    history.getSourcePressureBaraHistory("asgard");
double[] exportInlet =
    history.getEdgeInletMassFlowKgSHistory("export");
double[] exportOutlet =
    history.getEdgeOutletMassFlowKgSHistory("export");
double[] exportLinepack =
    history.getEdgeLinepackKgHistory("export");
```

The regression uses the approximately 200 bara baseline and 207 bara
high-rate quasi-steady notebook results as comparison anchors, not exact
transient targets. During packing, export inlet flow exceeds outlet flow and
the solved source pressure rises; after the event it relaxes toward baseline.

Python receives the same time-aligned arrays through `neqsim_dev_setup.py`:

```python
network = ns.TransientGasNetwork("transient export")
# Configure nodes, pipes, schedules, and the fixed sink as in the Java example.
network.run(36.0 * 3600.0, 1800.0)
history = network.getHistory()
times_s = list(history.getElapsedTimeSeconds())
source_pressure_bara = list(
    history.getSourcePressureBaraHistory("asgard")
)
karsto_co2_mass_fraction = list(
    history.getNodeMassFractionHistory("karsto", "CO2")
)
capture = str(history.toJson())
```

Every accepted step exposes hydraulic, total-mass, component, and junction
residuals through `getStepReports()`. Edge and network species ledgers are
available through `getEdgeSpeciesReports(...)` and
`getNetworkSpeciesReports()`. Configure pressure bounds with
`setSourcePressureLimits(...)` and a fail-loud velocity capacity with
`setMaximumEdgeVelocity(...)`.

The initial validated scope is a directed acyclic gathering tree with one
outgoing edge per source or junction, strictly positive flow, one fixed-pressure
sink, a common component slate and temperature, and exactly one gas phase.
Momentum is quasi-steady and isothermal with a local EOS linearization for
compressibility. Flow splits, recirculation, reverse flow, acoustic waves,
thermal transport, compressor/control logic, and phase appearance are not
silently approximated.

## JSON and reproducibility

Quality profiles, compliance reports, candidate evaluations, and planning
results support JSON serialization. `LoopedPipeNetwork.fromJson(...)` restores
topology and element configuration. Thermodynamic fluids and chart-backed
equipment delegates are not embedded; reattach them before solving.

For reproducible studies, record:

- NeqSim revision and EOS/mixing rule;
- component and pseudo-component definitions;
- route/thermal profiles and units;
- optimizer seed, bounds, scales, and tolerances;
- quality profile version and provenance;
- initial/terminal linepack and period duration.

## Public context and limitations

- [Norsk Petroleum: the oil and gas pipeline system](https://www.norskpetroleum.no/en/production-and-exports/the-oil-and-gas-pipeline-system/)
- [Original public NCS gas notebook](https://github.com/EvenSol/NeqSim-Colab/blob/master/notebooks/process/norwegian_ncs_gas_network_optimization.ipynb)

Named facilities and corridors provide public context only. The repository
examples use synthetic compositions, capacities, commercial limits, and
nominations. Current contracts, approved operator models, metering standards,
and asset data remain controlling.
