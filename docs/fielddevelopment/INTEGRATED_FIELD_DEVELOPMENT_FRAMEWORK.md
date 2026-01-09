# Integrated Field Development Framework

## Overview

This document describes how NeqSim integrates PVT, reservoir, well, and process simulations
into a unified field development workflow. The framework supports progressive refinement
from early feasibility studies through detailed design, with increasing fidelity at each stage.

## Field Development Lifecycle

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                          FIELD DEVELOPMENT LIFECYCLE                            │
├──────────────┬──────────────┬──────────────┬──────────────┬──────────────────────┤
│  Discovery   │  Feasibility │   Concept    │   FEED/DG2   │  Operations          │
│              │   (DG1)      │   (DG2)      │   (DG3/4)    │                      │
├──────────────┼──────────────┼──────────────┼──────────────┼──────────────────────┤
│ • PVT Lab    │ • Volumetrics│ • EOS Tuning │ • Detailed   │ • History matching   │
│ • GIIP/STOIIP│ • Screening  │ • IPR/VLP    │   reservoir  │ • Production optim.  │
│ • Analogs    │ • Economics  │ • Process    │ • Full CAPEX │ • Debottlenecking    │
│              │   ±50%       │   simulation │   ±20%       │                      │
└──────────────┴──────────────┴──────────────┴──────────────┴──────────────────────┘
```

## NeqSim Classes by Development Phase

### Phase 1: Discovery & Appraisal

**Objective:** Characterize the fluid and estimate volumes

| Task | NeqSim Class | Package |
|------|--------------|---------|
| Create fluid from composition | `SystemSrkEos`, `SystemPrEos`, `SystemSrkCPAstatoil` | `thermo.system` |
| Plus-fraction characterization | `Characterization.Pedersen()` | `thermo.characterization` |
| Saturation pressure | `SaturationPressure` | `pvtsimulation.simulation` |
| CCE/DLE/CVD simulation | `ConstantMassExpansion`, `DifferentialLiberation`, `ConstantVolumeDepletion` | `pvtsimulation.simulation` |
| GOR estimation | `GOR`, `SeparatorTest` | `pvtsimulation.simulation` |
| Fluid type classification | `FluidInput.fluidType()` | `fielddevelopment.concept` |

**Example Workflow:**
```java
// Create reservoir fluid from lab composition
SystemInterface fluid = new SystemSrkEos(373.15, 250.0);
fluid.addComponent("nitrogen", 0.005);
fluid.addComponent("CO2", 0.015);
fluid.addComponent("methane", 0.60);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addTBPfraction("C7+", 0.20, 0.220, 0.85);
fluid.setMixingRule("classic");

// Characterize plus-fraction
fluid.getCharacterization().characterisePlusFraction();

// Calculate bubble point
SaturationPressure satP = new SaturationPressure(fluid);
satP.setTemperature(100.0, "C");
satP.runCalc();
double bubblePoint = satP.getSaturationPressure();

// Run DLE for Bo, Rs, μo
DifferentialLiberation dle = new DifferentialLiberation(fluid);
dle.setTemperature(100.0, "C");
dle.setPressures(new double[]{250, 200, 150, 100, 50, 1.01325}, "bara");
dle.runCalc();
```

---

### Phase 2: Feasibility Study (DG1)

**Objective:** Screen development options and estimate economics (±50%)

| Task | NeqSim Class | Package |
|------|--------------|---------|
| Define concept | `FieldConcept`, `FluidInput`, `WellsInput`, `InfrastructureInput` | `fielddevelopment.concept` |
| Flow assurance screening | `FlowAssuranceScreener` | `fielddevelopment.screening` |
| Hydrate risk | CPA thermodynamics in screener | `fielddevelopment.screening` |
| Wax risk | WAT estimation | `fielddevelopment.screening` |
| Cost estimation (±50%) | `EconomicsEstimator`, `RegionalCostFactors` | `fielddevelopment.screening` |
| Production forecast | `ProductionProfileGenerator` (Arps) | `fielddevelopment.economics` |
| NPV calculation | `CashFlowEngine`, `TaxModel` | `fielddevelopment.economics` |
| Tieback options | `TiebackAnalyzer` | `fielddevelopment.tieback` |
| Batch comparison | `BatchConceptRunner` | `fielddevelopment.evaluation` |

**Example Workflow:**
```java
// Quick concept definition for gas tieback
FieldConcept concept = FieldConcept.quickGasTieback(
    "Satellite Discovery",
    200.0,    // GIIP (GSm3)
    0.02,     // CO2 fraction
    25.0      // Tieback length (km)
);

// Add well details
concept.getWells()
    .setWellCount(4)
    .setInitialRate(2.5e6, "Sm3/day")  // Per well
    .setTHP(80.0, "bara");

// Flow assurance screening
FlowAssuranceScreener faScreener = new FlowAssuranceScreener();
FlowAssuranceResult faResult = faScreener.screen(concept);
System.out.println("Hydrate risk: " + faResult.getHydrateRisk());
System.out.println("Wax risk: " + faResult.getWaxRisk());

// Economics screening
EconomicsEstimator estimator = new EconomicsEstimator("NO");
EconomicsReport costs = estimator.quickEstimate(concept);
System.out.println("CAPEX: " + costs.getTotalCapexMUSD() + " MUSD");

// Production profile (Arps decline)
ProductionProfileGenerator gen = new ProductionProfileGenerator();
Map<Integer, Double> gasProfile = gen.generateFullProfile(
    10.0e6,                       // Peak rate (Sm3/d)
    1,                            // Ramp-up years
    5,                            // Plateau years
    0.12,                         // Decline rate
    ProductionProfileGenerator.DeclineType.EXPONENTIAL,
    2027,                         // First production
    25                            // Field life
);

// Cash flow analysis
CashFlowEngine engine = new CashFlowEngine("NO");
engine.setCapex(costs.getTotalCapexMUSD(), 2025);
engine.setOpexPercentOfCapex(0.04);
engine.setGasPrice(0.30);  // USD/Sm3
engine.setProductionProfile(null, gasProfile, null);

CashFlowResult result = engine.calculate(0.08);
System.out.println("NPV@8%: " + result.getNpv() + " MUSD");
System.out.println("IRR: " + result.getIrr() * 100 + "%");
```

---

### Phase 3: Concept Selection (DG2)

**Objective:** Select preferred concept with EOS-tuned fluid and well models

| Task | NeqSim Class | Package |
|------|--------------|---------|
| EOS tuning to lab data | `PVTRegression` | `pvtsimulation.regression` |
| PVT report generation | `PVTReportGenerator` | `pvtsimulation.util` |
| IPR modeling | `WellFlow` | `process.equipment.reservoir` |
| VLP modeling | `TubingPerformance` | `process.equipment.reservoir` |
| Integrated well model | `WellSystem` | `process.equipment.reservoir` |
| Nodal analysis | `WellSystem.findOperatingPoint()` | `process.equipment.reservoir` |
| Material balance | `SimpleReservoir` | `process.equipment.reservoir` |
| Process simulation | `ProcessSystem` | `process.processmodel` |
| Facility builder | `FacilityBuilder` | `fielddevelopment.facility` |
| Concept evaluation | `ConceptEvaluator` | `fielddevelopment.evaluation` |
| Sensitivity analysis | `SensitivityAnalyzer` | `fielddevelopment.economics` |

**Example Workflow:**
```java
// === EOS TUNING ===
// Start with base fluid
SystemInterface baseFluid = createBaseFluid();

// Add lab data and run regression
PVTRegression regression = new PVTRegression(baseFluid);
regression.addCCEData(ccePressures, cceRelativeVolumes, 100.0);
regression.addDLEData(dlePressures, dleRs, dleBo, dleViscosity, 100.0);
regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10);
regression.addRegressionParameter(RegressionParameter.C7PLUS_MW_MULTIPLIER, 0.9, 1.1);

RegressionResult regResult = regression.runRegression();
SystemInterface tunedFluid = regResult.getTunedFluid();

// === WELL MODELING ===
// Create reservoir stream
Stream reservoirStream = new Stream("Reservoir", tunedFluid);
reservoirStream.setFlowRate(5000.0, "Sm3/day");
reservoirStream.setTemperature(100.0, "C");
reservoirStream.setPressure(250.0, "bara");
reservoirStream.run();

// Integrated IPR + VLP model
WellSystem well = new WellSystem("Producer-1", reservoirStream);
well.setIPRModel(WellSystem.IPRModel.VOGEL);
well.setVogelParameters(8000.0, 180.0, 250.0);  // qTest, pwfTest, pRes
well.setTubingLength(2500.0, "m");
well.setTubingDiameter(4.0, "in");
well.setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
well.setWellheadPressure(50.0, "bara");
well.run();

double operatingRate = well.getOperatingFlowRate("Sm3/day");
double operatingBHP = well.getOperatingBHP("bara");

// === MATERIAL BALANCE RESERVOIR ===
SimpleReservoir reservoir = new SimpleReservoir("Main Field");
reservoir.setReservoirFluid(tunedFluid, 200e6, 10.0, 10.0);  // GIIP, thickness, area
Stream wellStream = reservoir.addOilProducer("Well-1");
wellStream.setFlowRate(operatingRate, "Sm3/day");

// === PROCESS SIMULATION ===
ProcessSystem process = new ProcessSystem("FPSO");
process.add(reservoir);

// HP Separator
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellStream);
hpSep.setInletPressure(50.0, "bara");
process.add(hpSep);

// Compressor
Compressor compressor = new Compressor("Export Comp", hpSep.getGasOutStream());
compressor.setOutletPressure(150.0, "bara");
process.add(compressor);

process.run();

// === CONCEPT EVALUATION ===
ConceptEvaluator evaluator = new ConceptEvaluator();
ConceptKPIs kpis = evaluator.evaluate(concept, tunedFluid);
System.out.println(kpis.getSummaryReport());
```

---

### Phase 4: FEED / Detailed Design (DG3/DG4)

**Objective:** Finalize design with detailed reservoir coupling and process simulation

| Task | NeqSim Class | Package |
|------|--------------|---------|
| Black oil tables | `BlackOilConverter` | `blackoil` |
| Eclipse PVT export | `EclipseEOSExporter` | `blackoil.io` |
| VFP table generation | `WellSystem.generateLiftCurves()` | `process.equipment.reservoir` |
| Reservoir depletion study | `SimpleReservoir.runDepletion()` | `process.equipment.reservoir` |
| Production scheduling | `FieldProductionScheduler` | `process.util.fielddevelopment` |
| Well scheduling | `WellScheduler` | `process.util.fielddevelopment` |
| Capacity analysis | `FacilityCapacity` | `process.util.fielddevelopment` |
| Monte Carlo | `SensitivityAnalyzer.monteCarloAnalysis()` | `fielddevelopment.economics` |
| MMP calculation | `MMPCalculator` | `pvtsimulation.simulation` |

**Example Workflow:**
```java
// === EXPORT TO RESERVOIR SIMULATOR ===
// Generate black oil tables
BlackOilConverter converter = new BlackOilConverter(tunedFluid);
converter.setReservoirTemperature(373.15);
converter.setPressureRange(1.01325, 300.0, 20);
BlackOilPVTTable pvtTable = converter.convert();

// Export to Eclipse format
EclipseEOSExporter.ExportConfig config = new EclipseEOSExporter.ExportConfig()
    .setUnits(EclipseEOSExporter.Units.METRIC)
    .setIncludePVTO(true)
    .setIncludePVTG(true)
    .setIncludePVTW(true)
    .setIncludeDensity(true);
    
EclipseEOSExporter.toFile(tunedFluid, Path.of("PVT_TABLES.INC"), config);

// === VFP TABLE GENERATION ===
double[] whPressures = {30, 40, 50, 60, 70, 80};  // bara
double[] waterCuts = {0, 0.2, 0.4, 0.6, 0.8};
WellSystem.VFPTable vfp = well.generateLiftCurves(whPressures, waterCuts);
vfp.exportToEclipse("VFP_WELL1.INC");

// === PRODUCTION SCHEDULING ===
FieldProductionScheduler scheduler = new FieldProductionScheduler();
scheduler.addReservoir(reservoir);
scheduler.setFacilityModel(process);
scheduler.setPlateauTarget(10.0e6, "Sm3/day");
scheduler.setEconomicLimit(0.5e6, "Sm3/day");
scheduler.setGasPrice(0.30);
scheduler.setDiscountRate(0.08);

ScheduleResult schedule = scheduler.runScheduling(2027, 2052);
System.out.println(schedule.getProductionForecast());
System.out.println(schedule.getEconomicSummary());

// === MONTE CARLO ANALYSIS ===
SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);
analyzer.setOilPriceDistribution(50.0, 100.0);
analyzer.setCapexDistribution(800, 1200);
analyzer.setProductionFactorDistribution(0.8, 1.2);

MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);
System.out.println("P10: " + mc.getNpvP10() + " MUSD");
System.out.println("P50: " + mc.getNpvP50() + " MUSD");
System.out.println("P90: " + mc.getNpvP90() + " MUSD");
```

---

## Key Integration Points

### 1. PVT → Reservoir Coupling

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Lab PVT Data    │────▶│  PVTRegression   │────▶│  Tuned EOS       │
│  (CCE/DLE/CVD)   │     │  (Parameter fit) │     │  SystemInterface │
└──────────────────┘     └──────────────────┘     └────────┬─────────┘
                                                           │
                         ┌─────────────────────────────────┼─────────────────────┐
                         │                                 │                     │
                         ▼                                 ▼                     ▼
               ┌──────────────────┐           ┌──────────────────┐    ┌──────────────────┐
               │ BlackOilConverter│           │  SimpleReservoir │    │  Process Streams │
               │ PVTO/PVTG tables │           │  Material Balance│    │  Compositional   │
               └────────┬─────────┘           └────────┬─────────┘    └────────┬─────────┘
                        │                              │                       │
                        ▼                              ▼                       ▼
               ┌──────────────────┐           ┌──────────────────┐    ┌──────────────────┐
               │ Eclipse/OPM      │           │  WellSystem      │    │  ProcessSystem   │
               │ Reservoir Sim    │           │  IPR/VLP Nodal   │    │  Facility Model  │
               └──────────────────┘           └──────────────────┘    └──────────────────┘
```

### 2. Well → Reservoir → Facility Loop

```java
// Nodal analysis loop with reservoir depletion
for (int year = 2027; year <= 2050; year++) {
    // Update reservoir pressure
    reservoir.runDepletion(365.0);  // 1 year
    double pRes = reservoir.getAveragePressure("bara");
    
    // Update IPR with new reservoir pressure
    well.setReservoirPressure(pRes, "bara");
    well.run();
    
    double newRate = well.getOperatingFlowRate("Sm3/day");
    
    // Check facility constraints
    if (newRate > facilityCapacity.getMaxGasRate()) {
        newRate = facilityCapacity.getMaxGasRate();
        // Back-calculate required choke setting
        well.setTargetRate(newRate, "Sm3/day");
        well.run();
    }
    
    schedule.addYear(year, newRate, pRes);
}
```

### 3. Economics → Decision Support

```java
// Compare multiple development options
BatchConceptRunner batch = new BatchConceptRunner();

// Option A: Direct tieback to platform
batch.addConcept(FieldConcept.quickGasTieback("Tieback-A", 200, 0.02, 15));

// Option B: Standalone FPSO
batch.addConcept(FieldConcept.quickOilDevelopment("FPSO-B", 50, 0.03));

// Option C: Subsea to shore
batch.addConcept(FieldConcept.quickGasTieback("S2S-C", 200, 0.02, 80));

// Run parallel evaluation
batch.evaluateAll();

// Get ranked results
List<ConceptKPIs> ranked = batch.getRankedResults();
for (ConceptKPIs kpi : ranked) {
    System.out.printf("%s: NPV=%.0f MUSD, Score=%.2f%n",
        kpi.getConceptName(), kpi.getNpv(), kpi.getOverallScore());
}
```

---

## Study Fidelity Levels

### Level 1: Screening (±50% accuracy)

```java
// Minimal input - analog-based
FieldConcept concept = FieldConcept.quickGasTieback(name, giip, co2, distance);
ConceptKPIs kpis = new ConceptEvaluator().quickScreen(concept);
```

**Inputs:** Fluid type, volumes (GIIP/STOIIP), distance, water depth  
**Models:** Correlations, analog-based costs, Arps decline  
**Outputs:** Order-of-magnitude CAPEX/OPEX, screening-level NPV

### Level 2: Conceptual (±30% accuracy)

```java
// EOS fluid, IPR/VLP, process blocks
SystemInterface fluid = createFluidFromComposition(labData);
WellSystem well = new WellSystem("Well-1", reservoirStream);
FacilityConfig facility = FacilityBuilder.forConcept(concept).autoGenerate().build();
ConceptKPIs kpis = new ConceptEvaluator().evaluate(concept, fluid, facility);
```

**Inputs:** Composition, lab PVT, well test data, facility configuration  
**Models:** EOS thermodynamics, Vogel/Fetkovich IPR, Beggs-Brill VLP  
**Outputs:** Detailed CAPEX breakdown, production forecast, flow assurance risk

### Level 3: Detailed (±20% accuracy)

```java
// Tuned EOS, full process simulation, Monte Carlo
PVTRegression regression = new PVTRegression(baseFluid);
regression.addCCEData(ccePressures, cceRelativeVolumes, temp);
regression.addDLEData(dlePressures, dleRs, dleBo, dleViscosity, temp);
SystemInterface tunedFluid = regression.runRegression().getTunedFluid();

ProcessSystem process = new ProcessSystem();
// ... detailed equipment configuration
process.run();

SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, discountRate);
MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);
```

**Inputs:** Full PVT report, well test interpretation, vendor quotes  
**Models:** Tuned EOS, mechanistic correlations, rigorous process simulation  
**Outputs:** P10/P50/P90 economics, FEED-level design

---

## Topics from TPG4230 Course Mapped to NeqSim

Based on the NTNU course "Field Development and Operations" (TPG4230), here is how
each topic maps to NeqSim capabilities:

| Course Topic | NeqSim Implementation | Status |
|--------------|----------------------|--------|
| **Life cycle of hydrocarbon field** | `FieldProductionScheduler`, `CashFlowEngine` | ✅ Complete |
| **Field development workflow** | `ConceptEvaluator`, `BatchConceptRunner` | ✅ Complete |
| **Probabilistic reserve estimation** | `SensitivityAnalyzer.monteCarloAnalysis()` | ✅ Complete |
| **Project economic evaluation** | `CashFlowEngine`, `TaxModel`, NPV/IRR | ✅ 15+ countries |
| **Offshore field architectures** | `FieldConcept`, `InfrastructureInput` | ✅ Complete |
| **Production systems** | `WellSystem` (IPR+VLP), `TubingPerformance` | ✅ Complete |
| **Injection systems** | `WellFlow` with injection mode | ✅ Basic |
| **Reservoir depletion** | `SimpleReservoir`, material balance | ✅ Tank model |
| **Field performance** | `ProductionProfile`, decline curves | ✅ Complete |
| **Production scheduling** | `FieldProductionScheduler`, `WellScheduler` | ✅ Complete |
| **Flow assurance** | `FlowAssuranceScreener`, hydrate/wax/corrosion | ✅ Complete |
| **Boosting (ESP, gas lift)** | `WellsInput.artificialLift()` | ✅ Screening |
| **Field processing** | `ProcessSystem`, separators, compressors | ✅ Complete |
| **Export product control** | `ProcessSystem` export streams | ✅ Complete |
| **Integrated asset modeling** | `SimpleReservoir` + `ProcessSystem` | ✅ Complete |
| **Energy efficiency** | `EmissionsTracker` CO2 intensity | ✅ Screening |
| **Emissions to air/sea** | `EmissionsTracker` | ✅ Screening |

---

## Recommended Workflow by Project Phase

### Feasibility (Week 1-4)

1. **Define fluid type and volumes**
   ```java
   FluidInput fluid = new FluidInput().fluidType(FluidType.GAS_CONDENSATE).gor(3000);
   ```

2. **Screen concepts with `BatchConceptRunner`**
   ```java
   batch.addConcept(concept1);
   batch.addConcept(concept2);
   batch.evaluateAll();
   ```

3. **Generate economics comparison**
   ```java
   batch.generateComparisonReport("concepts_comparison.md");
   ```

### Concept Select (Week 5-12)

1. **Tune EOS to lab data**
   ```java
   PVTRegression regression = new PVTRegression(fluid);
   regression.addCCEData(...);
   SystemInterface tuned = regression.runRegression().getTunedFluid();
   ```

2. **Build well model (IPR + VLP)**
   ```java
   WellSystem well = new WellSystem("Producer", stream);
   well.setVogelParameters(qTest, pwfTest, pRes);
   well.setTubingLength(2500, "m");
   ```

3. **Run process simulation**
   ```java
   ProcessSystem process = new ProcessSystem();
   process.add(separator);
   process.add(compressor);
   process.run();
   ```

4. **Sensitivity analysis**
   ```java
   SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);
   TornadoResult tornado = analyzer.tornadoAnalysis(0.20);
   ```

### FEED (Week 13-26)

1. **Export to reservoir simulator**
   ```java
   EclipseEOSExporter.toFile(tunedFluid, Path.of("PVT.INC"));
   well.exportVFPToEclipse("VFP.INC");
   ```

2. **Full production scheduling**
   ```java
   FieldProductionScheduler scheduler = new FieldProductionScheduler();
   scheduler.runScheduling(2027, 2052);
   ```

3. **Monte Carlo economics**
   ```java
   MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);
   double probPositiveNPV = mc.getProbabilityPositiveNpv();
   ```

---

## Future Enhancements

### Near-term (Priority)

1. **Network solver** - Multi-well gathering network pressure balance
2. **Transient well model** - Time-dependent IPR with pressure buildup/drawdown
3. **Water injection support** - Full injection well modeling
4. **Gas lift optimization** - Optimal gas allocation across wells

### Medium-term

1. **Eclipse/OPM coupling** - Direct reservoir simulator integration
2. **Real-time data integration** - OSDU/WITSML connection
3. **Machine learning** - Decline curve prediction from analogs
4. **Optimization** - Portfolio optimization across multiple fields

### Long-term

1. **Digital twin** - Live field model with data assimilation
2. **Carbon storage** - CO2 injection field development
3. **Hydrogen/ammonia** - Energy transition applications

---

## See Also

- [Economics Module](../process/economics/README.md) - NPV, tax models, cash flow
- [Well Simulation Guide](../simulation/well_simulation_guide.md) - IPR/VLP details
- [PVT Workflow](../pvtsimulation/pvt_workflow.md) - EOS tuning guide
- [Reservoir Modeling](../process/equipment/reservoirs.md) - SimpleReservoir API
- [Field Development Strategy](FIELD_DEVELOPMENT_STRATEGY.md) - Architecture overview
