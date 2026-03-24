---
name: neqsim-field-development
description: "Field development workflows, concept selection, and integrated project evaluation using NeqSim. USE WHEN: performing field development studies, concept screening, tieback analysis, production forecasting, or integrated field planning. Covers the full lifecycle from discovery through operations with NeqSim's field development classes."
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
