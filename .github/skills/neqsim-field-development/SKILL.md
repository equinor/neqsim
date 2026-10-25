---
name: neqsim-field-development
description: "Field development workflows, concept selection, and integrated project evaluation using NeqSim. USE WHEN: performing field development studies, concept screening, tieback analysis, production forecasting, or integrated field planning. Covers the full lifecycle from discovery through operations with NeqSim's field development classes."
last_verified: "2026-07-19"
---

# NeqSim Field Development Skill

Comprehensive reference for oil & gas field development using NeqSim's field
development framework. Covers the full lifecycle from discovery through
decommissioning, with emphasis on concept selection, production forecasting,
economics, and risk assessment.

---

## Field Development Lifecycle (Decision Gates)

| Phase | Decision Gate | Fidelity | Accuracy | NeqSim Focus |
|-------|--------------|----------|----------|--------------|
| Discovery | — | SCREENING | ±50% | Volumetrics, PVT lab, analogs |
| Feasibility | DG1 | SCREENING | ±50% | Flow assurance screening, cost correlations, Arps decline |
| Concept Select | DG2 | CONCEPTUAL | ±30% | EOS tuning, IPR/VLP, process simulation, concept ranking |
| FEED | DG3/DG4 | DETAILED | ±20% | Full process model, reservoir coupling, Monte Carlo NPV |
| Operations | — | DETAILED | ±10% | History matching, debottlenecking, optimization |
| Late Life | — | DETAILED | ±20% | IOR/EOR, recompletion, decommissioning cost |

### Gate Packages: Verdict Versus Full Deliverable Set

- **A gate verdict is not a gate package.** A readiness verdict (ready/review/not_ready) answers
  "can we go to the gate". A full package answers "here is every product the gate requires, with
  an owner and a status". When the user asks to "deliver according to DG1" (or any gate), build a
  product register: one row per required product, a status from a fixed taxonomy (Evidenced /
  Partly evidenced / Draft for review / Owner action / Not applicable - confirm), the evidence
  path and the owning role. Never label the result as gate approval; the readiness label stays
  at most "ready for owner review".
- **Build a concept long list before capacity sizing.** Development concepts (FPSO, tie-back,
  subsea-to-shore, phased, joint development) are different from capacity variants of one concept
  (e.g. 100/150/180 kbbl/d). Screen the long list first, record why concepts are dropped, then size
  the kept concepts. Capacity variants alone do not satisfy a concept-screening product.
- **StudyClass is not an AACE class or gate status.** A screening study class (A/B/C) says how
  deep the work went; the cost estimate carries its own AACE class (5 to 1); the gate carries its
  own readiness. Report all three separately.
- **Placeholder sources stay placeholders.** Fiscal, tariff or price inputs marked as examples
  must be labelled as such in the package and listed as owner actions.
- Company gate product lists and governance live in enterprise skills (for Equinor:
  `enterprise-field-development-decision-gate`, `gate_package.py`); keep them out of this repo.

### Workflow Orchestration

```java
FieldDevelopmentWorkflow workflow = new FieldDevelopmentWorkflow("Field Name");
workflow.setStudyPhase(StudyPhase.FEASIBILITY);
workflow.setFidelityLevel(FidelityLevel.SCREENING);

// Set reservoir
ReservoirInput reservoir = new ReservoirInput();
reservoir.setFluidType("gas_condensate");
reservoir.setGIIP(1.5e9);  // Sm3
reservoir.setReservoirPressure(350.0);  // bara
reservoir.setReservoirTemperature(95.0);  // °C
reservoir.setRecoveryFactor(0.65);
workflow.setReservoirInput(reservoir);

// Set wells
WellsInput wells = new WellsInput();
wells.setNumberOfProducers(4);
wells.setWaterDepth(350.0);
wells.setTotalDepth(3800.0);
workflow.setWellsInput(wells);

// Set infrastructure
InfrastructureInput infra = new InfrastructureInput();
infra.setDevelopmentType("subsea_tieback");
infra.setTiebackDistance(25.0);  // km
workflow.setInfrastructureInput(infra);

// Run
WorkflowResult result = workflow.run();
```

### Key Result Classes

| Class | Key Fields |
|-------|-----------|
| `WorkflowResult` | npvMUSD, irrPercent, paybackYears, totalCapexMUSD, totalOpexMUSD, totalPowerMW, co2IntensityKgPerBoe |
| `ConceptKPIs` | Same as WorkflowResult + concept-level metrics |
| `CashFlowResult` | Year-by-year revenue, opex, capex, tax, net_cash_flow, cumulative_dcf |

---

## Concept Selection (Multi-Concept Comparison)

### Define Concepts

```java
// Concept 1: Subsea tieback to existing platform
FieldConcept tieback = new FieldConcept("Subsea Tieback");
tieback.setReservoirInput(reservoir);
tieback.setWellsInput(wells);
InfrastructureInput tiebackInfra = new InfrastructureInput();
tiebackInfra.setDevelopmentType("subsea_tieback");
tiebackInfra.setTiebackDistance(25.0);
tiebackInfra.setHostCapacity(50000.0);  // boe/d
tieback.setInfrastructureInput(tiebackInfra);

// Concept 2: Standalone FPSO
FieldConcept fpso = new FieldConcept("Standalone FPSO");
fpso.setReservoirInput(reservoir);
fpso.setWellsInput(wells);
InfrastructureInput fpsoInfra = new InfrastructureInput();
fpsoInfra.setDevelopmentType("fpso");
fpso.setInfrastructureInput(fpsoInfra);

// Concept 3: Fixed platform
FieldConcept platform = new FieldConcept("Fixed Platform");
// ... configure ...
```

### Batch Evaluation

```java
BatchConceptRunner runner = new BatchConceptRunner();
runner.addConcept(tieback);
runner.addConcept(fpso);
runner.addConcept(platform);
runner.setFluid(tunedEosFluid);
runner.setOilPrice(70.0);    // USD/bbl
runner.setGasPrice(0.30);    // USD/Sm3
runner.setDiscountRate(0.08);

List<ConceptKPIs> results = runner.runAll();

// Rank by NPV
DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
for (ConceptKPIs kpi : results) {
    ranker.addOption(kpi);
}
List<ConceptKPIs> ranked = ranker.rankByNPV();
```

### Physically Coupled Lifetime Comparison

Use `process.fielddevelopment.lifecycle` after rapid screening when reservoir pressure, well/SURF hydraulics,
processing capacity, gas injection and economics must share one time-dependent state:

```java
FieldLifecycleEvaluator evaluator = new FieldLifecycleEvaluator();
List<FieldLifecycleResult> ranked = evaluator.evaluateAll(
    NorwegianOilFieldCase.createDevelopmentPortfolio());
```

`FieldLifecycleModel` accepts a user-built `ProcessSystem` or a complete multi-area `ProcessModel`, so screening
concepts can be refined without replacing the lifecycle/economic interface. Use
`existingSurfAndFacility(...)` when the new field enters a shared existing SURF area before a host facility; wire the
areas with shared streams and map host feeds at their actual subsea or topsides entry points. Whole-model execution,
power, sizing and `area::equipment` bottlenecks then include both SURF and processing. Each concept must own an
independent mutable reservoir/process model.
Use `FacilityLifecycleStrategy.greenfield(...)` to auto-size a new detailed processing facility from design cases. Use
`FacilityLifecycleStrategy.tieback(...)` with the existing `HostFacility`, `ProductionProfileSeries`,
`CapacityAllocationPolicy`, and `HoldbackPolicy` types for a producing-host tieback. Connect the model's optional host
oil/gas/water feeds to the real shared process so annual `FieldLifecycleResult` records host load, admitted satellite
rate, deliberate holdback, capacity-deferred oil, operating and requested utilization, and their primary bottlenecks.
Set `FieldLifecycleModel.setProductionPotentialProvider(...)` when a detailed NeqSim well/network model or imported
reservoir schedule should replace the reference aggregate PI/water-cut potential calculation.

Use `AreaDevelopmentPortfolio` when one discovery can be routed to several independently modeled producing assets or
to a new facility. Add one `AreaDevelopmentOption` per host/tieback route or greenfield alternative, then use
`AreaDevelopmentEvaluator` to compare route identity, NPV, break-even, recovery, deferment, utilization, bottlenecks
and specification compliance. Each option must own its reservoir, SURF and mutable process state.

Attach `FieldProductSpecifications` to each configuration to check live export-gas CO2/H2S/O2, ISO 6976 GCV/Wobbe,
dew points, stabilized-oil RVP/BS&W and treated-water oil-in-water. The built-in evaluator uses NeqSim stream
analysers and standards; connect
`FieldProductQualityProvider` where a real facility has a dedicated water-treatment or online-analyser model. Choose
`REPORT_ONLY` for diagnostics or `REJECT_OPTION` to exclude non-compliant options from the area recommendation.

For brownfield studies, preserve actual equipment limits (`autoSizeDetailedProcess(false)`). For greenfield studies,
run the process at the simultaneous design case, auto-size equipment with the selected design margin, and use explicit
oil/gas/water/liquid nameplates for non-coincident component peak cases. Put any debottleneck capacity change in
`capacityFromYear(...)` and its CAPEX in `FieldLifecycleConfiguration`.
Use `FacilityModificationPlanner.analyse(result, targetUtilization)` to create traceable screening candidates from
annual requested utilization and deferred oil. Implement, size and cost each candidate in a cloned detailed process
before comparing its rerun lifecycle; the planner's multiplier is not a substitute for that engineering model.
See `docs/fielddevelopment/FIELD_LIFECYCLE_SIMULATION.md` for the connection points and fidelity boundaries.

---

## Reservoir & Well Modeling

### Material Balance (SimpleReservoir)

```java
SimpleReservoir reservoir = new SimpleReservoir("Main Reservoir");
reservoir.setReservoirFluid(fluid.clone(), giipSm3, reservoirThickness, reservoirArea);
reservoir.addOilProducer("P1");
reservoir.addWaterInjector("I1");

// Depletion with time steps
for (int year = 0; year < 20; year++) {
    reservoir.setProductionRate(annualRate[year]);
    reservoir.run();
    pressureProfile[year] = reservoir.getReservoirPressure();
}
```

### Inverse Material Balance / Reserves Surveillance

Regress OGIP/OOIP, drive mechanism and aquifer support directly from a measured
pressure-vs-cumulative-production history (the "inverse" of the forward
`SimpleReservoir` model). Package
`neqsim.pvtsimulation.reservoirproperties.materialbalance`:

```java
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GasMaterialBalance;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.OilMaterialBalance;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;

// Gas: P/Z straight line → OGIP (+ Cole plot aquifer diagnostic)
GasMaterialBalance.Result g = GasMaterialBalance.fitVolumetric(pressure, z, gp);
double ogip = g.getOgip();

// Oil: Havlena-Odeh depletion / gas-cap / water drive + Pirson drive indices
OilMaterialBalance.Result o = OilMaterialBalance.fitGasCapDrive(f, eo, eg);

// Aquifer: Van Everdingen-Hurst / Carter-Tracy cumulative influx + ECLIPSE AQUTAB
double u = VanEverdingenHurstAquifer.aquiferConstant(phi, ct, h, re, angleDeg);
double[] we = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(tD, deltaP, u, reD);
```

See the `neqsim-production-optimization` skill for the full surveillance workflow.

### Injection Strategy (Voidage Replacement)

```java
InjectionStrategy strategy = InjectionStrategy.waterInjection(1.0);  // VRR = 1.0
InjectionResult injection = strategy.calculateInjection(
    reservoir, oilRate, gasRate, waterRate
);
double requiredInjection = injection.waterInjectionRate;  // Sm3/d
```

### Well Performance (IPR/VLP Nodal Analysis)

```java
WellSystem well = new WellSystem("Producer-1", reservoirStream);
well.setIPRModel(WellSystem.IPRModel.VOGEL);
well.setVogelParameters(qTest, pwfTest, pRes);
well.setTubingLength(2500.0, "m");
well.setTubingDiameter(4.0, "in");
well.setPressureDropCorrelation(
    TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
well.setWellheadPressure(50.0, "bara");
well.run();

double operatingRate = well.getOperatingFlowRate("Sm3/day");
double operatingBHP = well.getOperatingBHP("bara");
```

### Production Network

```java
NetworkSolver network = new NetworkSolver("Gathering System");
network.addWell(well1, 3.0);   // 3 km flowline
network.addWell(well2, 5.5);   // 5.5 km flowline
network.setSolutionMode(SolutionMode.FIXED_MANIFOLD_PRESSURE);
network.setManifoldPressure(60.0);
NetworkResult result = network.solve();
```

---

## Production Forecasting

### Decline Curves (Arps)

```java
ProductionProfile profile = new ProductionProfile();
profile.setDeclineModel(ProductionProfile.DeclineModel.EXPONENTIAL);
profile.setPeakRate(25000.0);          // boe/d
profile.setDeclineRate(0.15);          // 15% per year
profile.setPlateauDuration(3);         // years
profile.setProjectLife(25);            // years
double[] annualRates = profile.generateProfile();
```

### Decline-Curve History Matching (Arps + Duong)

Fit decline parameters to a measured rate-time history and estimate EUR with
`neqsim.pvtsimulation.util.DeclineCurveAnalysis` (static, unit-agnostic):

```java
import neqsim.pvtsimulation.util.DeclineCurveAnalysis;

Map<String, Double> arps = DeclineCurveAnalysis.fitArps(t, q);       // qi, di, b, rSquared
double eur = DeclineCurveAnalysis.eurFromFit(arps, qEconLimit);
Map<String, Double> duong = DeclineCurveAnalysis.fitDuong(t, q);     // q1, a, m for tight/shale wells
```

### Production Scheduling

```java
FieldProductionScheduler scheduler = new FieldProductionScheduler();
scheduler.setNumberOfWells(6);
scheduler.setDrillingInterval(6);      // months between wells
scheduler.setFirstOil(2027);
scheduler.setWellProductivity(5000.0); // boe/d/well
scheduler.setDeclineRate(0.12);
scheduler.setFacilityCapacity(28000.0);  // boe/d plateau constraint
double[][] schedule = scheduler.generateSchedule(25);
```

### Well Scheduling

```java
WellScheduler wellScheduler = new WellScheduler();
wellScheduler.setDrillingDaysPerWell(45);
wellScheduler.setCompletionDaysPerWell(25);
wellScheduler.setMobDemobDays(30);
wellScheduler.setRigs(1);
wellScheduler.setWells(6);
wellScheduler.setStartDate(2026, 1);
List<WellSchedule> schedule = wellScheduler.generate();
```

---

## Facility Design Integration

### Building a Process System from Concept

```java
FacilityBuilder builder = new FacilityBuilder();
FacilityConfig config = new FacilityConfig();
config.setInletPressure(85.0);     // bara
config.setExportPressure(150.0);   // bara
config.setDesignRate(50000.0);     // boe/d

// Add processing blocks
config.addBlock(BlockType.INLET_SEPARATION, new BlockConfig()
    .set("stages", 2)
    .set("hp_pressure", 70.0)
    .set("lp_pressure", 5.0));
config.addBlock(BlockType.GAS_COMPRESSION, new BlockConfig()
    .set("stages", 3)
    .set("export_pressure", 150.0));
config.addBlock(BlockType.DEHYDRATION, new BlockConfig()
    .set("type", "TEG"));

ProcessSystem process = builder.build(config, fluid);
process.run();
```

### Bottleneck Analysis

```java
BottleneckAnalyzer analyzer = new BottleneckAnalyzer(process);
analyzer.setReservoirDecline(reservoir);
analyzer.setRateRange(10000, 60000, 5000);  // min, max, step (boe/d)
Map<String, Double> bottlenecks = analyzer.findBottlenecks();
```

---

## Subsea Production Systems

### Tieback Analysis

```java
TiebackAnalyzer analyzer = new TiebackAnalyzer();

// Define host
HostFacility host = new HostFacility("Platform A");
host.setAvailableCapacity(30000.0);  // boe/d
host.setProcessingPressure(70.0);    // bara

// Define tieback options
TiebackOption opt1 = new TiebackOption("Direct Tieback");
opt1.setFlowlineLength(15.0);       // km
opt1.setFlowlineDiameter(10.0);     // inches
opt1.setWaterDepth(350.0);          // m

TiebackOption opt2 = new TiebackOption("Via Manifold");
opt2.setFlowlineLength(25.0);
opt2.setFlowlineDiameter(12.0);
opt2.setWaterDepth(450.0);
opt2.setHasManifold(true);
opt2.setHasBooster(true);

analyzer.setHost(host);
analyzer.addOption(opt1);
analyzer.addOption(opt2);
analyzer.setFluid(reservoirFluid);

TiebackReport report = analyzer.analyze();
```

### Host impact of a tie-in on a benchmarked host model

When the host already has a benchmarked NeqSim `ProcessModel`, assess the tie-in on a
reproducible copy of that model:

1. Record the baseline model version, operating window and constraint basis. If the host package
   and workspace require incompatible NeqSim JARs, use separate Python processes with explicit
   classpaths and exchange inputs/results; do not mix those JARs in one JVM.
2. Use the tie-in fluid's own composition and PVT evidence with compatible EOS, mixing rules and
   component definitions. If only host-fluid data are available, label that fluid as a surrogate
   and quantify the sensitivity. Recombine oil, gas, water and lift gas on a molar basis; document
   standard-volume reference conditions, GOR and water-cut definitions, and verify them by flashing
   the reconstructed fluid. Initialize compositions before reading mole fractions.
3. Connect the additional `Stream` to the intended inlet `Mixer` using the host model's supported
   extension point. Include each external feed exactly once in total and component balances and
   check the energy balance. An unaccounted feed requires a boundary-accounting correction, not a
   change that hides a residual. Preserve the baseline for comparison.
4. Run plateau, mid-life and late-life points over the relevant benchmark windows. Report both
   absolute and incremental compressor power, separator utilisation, export oil and produced water
   against documented limits. Identify constraints already exceeded in the baseline separately
   from those introduced by the tie-in; do not assume a universal utilisation threshold.

### Evaluate tubing and line back-pressure during depletion

Use the coupled reservoir inflow and tubing/flowline outflow response to determine feasible rates
as reservoir pressure, water cut, GOR, gas lift and host pressure change. Bubble point alone does
not define a minimum flowing bottom-hole pressure or a recovery ceiling. Establish flow/no-flow
conditions from the available reservoir pressure and the required pressure at the same datum.

Reuse the current VFP generation and import/export APIs (see `MultiScenarioVFPGenerator` and
`neqsim-subsea-and-wells`), checking units, datum depth, axis definitions, feasible points and the
reservoir simulator's table format. Compare gas-lift and injection scenarios on the same basis.
If a VFP table causes convergence trouble, inspect interpolation, operating branches and control
switching. Do not force BHP versus rate to be monotonic just to obtain convergence. The
[OPM VFP consistency guidance](https://opm.github.io/opm-flow-editor-support/keywords/VFPCHK.html)
discusses crossing curves at adjacent tubing-head pressures, a different check from monotonicity
with rate. Preserve the original table and validate any justified conditioning against the
hydraulic model before using it for recovery or economic conclusions.

### Subsea System Configuration

```java
SubseaProductionSystem subsea = new SubseaProductionSystem("Subsea System");
subsea.setNumberOfWells(4);
subsea.setWaterDepth(350.0);
subsea.setFlowlineLength(25.0);
subsea.setUmbilicalLength(27.0);
subsea.setRiserType("flexible");
subsea.setTreeType("vertical");
subsea.setHasManifold(true);
subsea.setManifoldWells(4);
```

---

## Key NeqSim Classes for Field Development

| Package | Class | Purpose |
|---------|-------|---------|
| `process.fielddevelopment.workflow` | `FieldDevelopmentWorkflow` | Master orchestrator |
| `process.fielddevelopment.concept` | `FieldConcept`, `ReservoirInput`, `WellsInput`, `InfrastructureInput` | Concept definition |
| `process.fielddevelopment.evaluation` | `ConceptEvaluator`, `BatchConceptRunner`, `DevelopmentOptionRanker` | Concept comparison |
| `process.fielddevelopment.evaluation` | `BottleneckAnalyzer`, `MonteCarloRunner`, `ScenarioAnalyzer` | Analysis tools |
| `process.fielddevelopment.screening` | `FlowAssuranceScreener`, `SafetyScreener`, `EconomicsEstimator`, `EmissionsTracker` | Screening |
| `process.fielddevelopment.economics` | `CashFlowEngine`, `NorwegianTaxModel`, `SensitivityAnalyzer` | Economics |
| `process.fielddevelopment.facility` | `FacilityBuilder`, `FacilityConfig`, `ConceptToProcessLinker` | Process facility |
| `process.fielddevelopment.tieback` | `TiebackAnalyzer`, `TiebackOption`, `HostFacility` | Tieback screening |
| `process.fielddevelopment.subsea` | `SubseaProductionSystem` | Subsea systems |
| `process.fielddevelopment.network` | `NetworkSolver`, `NetworkResult` | Production network |
| `process.fielddevelopment.reservoir` | `InjectionStrategy`, `TransientWellModel` | Reservoir support |
| `process.equipment.reservoir` | `SimpleReservoir`, `WellFlow`, `WellSystem`, `TubingPerformance` | Reservoir/well equipment |
| `process.equipment.subsea` | `SubseaWell`, `SubseaManifold`, `SimpleFlowLine`, etc. | Subsea equipment |
| `process.util.fielddevelopment` | `ProductionProfile`, `WellScheduler`, `FacilityCapacity`, `DCFCalculator` | Utilities |
| `process.mechanicaldesign.subsea` | `WellMechanicalDesign`, `SURFCostEstimator` | Well & SURF cost |

---

## Common Patterns & Pitfalls

### Always Verify API Before Use
NeqSim's field development classes are under active development. Before using any
class, search the source to confirm:
1. The class exists and compiles
2. Constructor signatures match expected parameters
3. Methods have the expected return types

### Temperature Units
- `ReservoirInput.setReservoirTemperature()` — **Celsius** (field development classes)
- `SystemSrkEos(T, P)` constructor — **Kelvin**
- Always confirm with source which convention each class uses

### Production Rate Units
- NeqSim process equipment: `setFlowRate(value, "kg/hr")` or `"Sm3/hr"`
- Field development classes: typically boe/d or Sm3/d
- Always specify units explicitly

### EOS Selection for Field Development
| Fluid Type | EOS | Mixing Rule | Notes |
|-----------|-----|-------------|-------|
| Dry gas | `SystemSrkEos` | `"classic"` | Simplest, fastest |
| Gas condensate | `SystemSrkEos` or `SystemPrEos` | `"classic"` | PR better for liquid densities |
| Black oil | `SystemPrEos` | `"classic"` | Need C7+ characterization |
| Oil + water + MEG | `SystemSrkCPAstatoil` | `10` | CPA for polar systems |
| CO2-rich (CCS) | `SystemSrkCPAstatoil` | `10` | CPA for CO2-water |
| HP/HT reservoir | `SystemPrEos` or `SystemSrkEos` | `"classic"` | Validate against PVT data |

### Fiscal Regime
- Norwegian NCS: 22% corporate + 56% special petroleum tax = 78% marginal rate
- UK UKCS: 30% ring-fence CT + 10% supplementary charge = 40% marginal rate
- Generic: Configurable via `GenericTaxModel`
- Always use `CashFlowEngine` with the correct country code

### Staged-option VPbo / DG1 screening for an unappraised HPHT discovery (lessons from Afrodite PL293)

- Value the decision as a staged option (stop / commit now / mature to DG2 then develop only if positive); an at-break-even project (NPV about 0 at P50) still has positive EMV when maturation is cheap, so state EMV of maturing, not just P50 NPV.
- Host life is a cost, not a constraint: price the years the profile outlives the host plan (host-alive cost, PV) and report a COP-shift curve; ullage rarely discriminates, host life and cost do.
- Reservoir-model need is a value-of-information question: EVPI on volume vs EVSI of a sidetrack vs its net cost, with the well-count rule (about 45 % of volume over a 4-year plateau).
- Cluster upside: report the share of nearby discovered gas that must be tied in for NPV > 0, not a single NPV.
- Report break-even gas price from the model by bisection, never a rounded literal; keep literals out of narrative strings (derive them from the results dict).
- HPHT dew point: do not trust `dewPointPressureFlash` (wrong root at 168 C); use a TP-flash bisection and CCE dropout. `TiebackAnalyzer` rejects shallow hosts through its water-depth gate; screen those hosts with the explicit pipe model.
