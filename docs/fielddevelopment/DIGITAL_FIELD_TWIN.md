# NeqSim Digital Field Twin Framework

## Overview

NeqSim provides a comprehensive **Digital Field Twin** capability that links field development planning to detailed thermodynamic, process, and mechanical calculations. This creates consistency throughout the field lifecycle—from **exploration** through **development**, **operation**, and **decommissioning**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     NEQSIM DIGITAL FIELD TWIN ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│   │ EXPLORATION │───▶│ DEVELOPMENT │───▶│  OPERATION  │───▶│   LATE LIFE │  │
│   │   DG0-DG1   │    │   DG2-DG4   │    │  Steady &   │    │  Decommiss. │  │
│   │             │    │             │    │  Transient  │    │             │  │
│   └──────┬──────┘    └──────┬──────┘    └──────┬──────┘    └──────┬──────┘  │
│          │                  │                  │                  │         │
│          ▼                  ▼                  ▼                  ▼         │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                    UNIFIED THERMODYNAMIC ENGINE                      │   │
│   │  • Equations of State (SRK, PR, CPA)                                │   │
│   │  • Flash Calculations (PT, PH, PS, TVn)                             │   │
│   │  • Phase Equilibria & Properties                                    │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│          │                  │                  │                  │         │
│          ▼                  ▼                  ▼                  ▼         │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                    PROCESS SIMULATION ENGINE                         │   │
│   │  • Equipment Models (Separators, Compressors, Heat Exchangers)      │   │
│   │  • Flowsheet Solving (Sequential, Recycle, Adjust)                  │   │
│   │  • Mechanical Design (Sizing, Pressure Rating)                      │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key Integration Points

### 1. Reservoir ↔ Wells ↔ Facilities

```java
// Create unified model from reservoir to export
FieldConcept concept = FieldConcept.builder("Johan Sverdrup Phase 2")
    .reservoir(ReservoirInput.builder()
        .fluidType(FluidType.MEDIUM_OIL)
        .gor(150.0)                    // Sm³/Sm³
        .waterCut(0.0)                 // Initial
        .reservoirPressure(280.0)      // bara
        .reservoirTemperature(90.0)    // °C
        .build())
    .wells(WellsInput.builder()
        .producerCount(8)
        .injectorCount(4)
        .ratePerWell(15000.0)          // Sm³/d oil
        .wellType(WellType.HORIZONTAL)
        .build())
    .infrastructure(InfrastructureInput.builder()
        .processingLocation(ProcessingLocation.PLATFORM)
        .exportType(ExportType.PIPELINE)
        .waterDepth(120.0)             // m
        .build())
    .build();

// Generate detailed process model from concept
ConceptToProcessLinker linker = new ConceptToProcessLinker();
ProcessSystem process = linker.generateProcessSystem(concept, FidelityLevel.CONCEPT);

// Run thermodynamic simulation
process.run();

// Extract results for reservoir coupling
ReservoirCouplingExporter exporter = new ReservoirCouplingExporter(process);
exporter.exportToFile("vfp_tables.inc", ExportFormat.ECLIPSE_100);
```

### 2. PVT ↔ Process ↔ Flow Assurance

```java
// Define fluid with detailed PVT
SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 50.0);
fluid.addComponent("methane", 0.45);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("nC10", 0.35);
fluid.addComponent("water", 0.07);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Create process stream
Stream wellStream = new Stream("Well-1 Stream", fluid);
wellStream.setFlowRate(100000.0, "kg/hr");
wellStream.run();

// Flow assurance check with consistent thermodynamics
MultiphaseFlowIntegrator flowIntegrator = new MultiphaseFlowIntegrator();
PipelineResult result = flowIntegrator.calculateHydraulics(
    wellStream,
    5000.0,     // length (m)
    0.25,       // diameter (m)
    -2.0        // inclination (degrees)
);

// Check flow regime and liquid holdup
System.out.println("Flow Regime: " + result.getFlowRegime());
System.out.println("Pressure Drop: " + result.getPressureDropBar() + " bar");
System.out.println("Liquid Holdup: " + result.getLiquidHoldup());
```

---

## Mathematical Foundations

### Portfolio Optimization

The `PortfolioOptimizer` solves the capital-constrained project selection problem:

$$\max \sum_{i=1}^{n} x_i \cdot NPV_i$$

Subject to:
$$\sum_{i=1}^{n} x_i \cdot CAPEX_{i,t} \leq Budget_t \quad \forall t$$
$$x_i \in \{0, 1\}$$

#### Optimization Strategies

| Strategy | Ranking Function | Use Case |
|----------|-----------------|----------|
| **GREEDY_NPV_RATIO** | $\frac{NPV_i}{CAPEX_i}$ | Capital-constrained portfolios |
| **RISK_WEIGHTED** | $P_i \cdot NPV_i$ | High-uncertainty environments |
| **EMV_MAXIMIZATION** | $EMV_i = P_i \cdot NPV_i - (1-P_i) \cdot Cost_{dry}$ | Exploration portfolios |
| **BALANCED** | Weighted mix ensuring type diversity | Strategic balance |

#### Usage Example

```java
PortfolioOptimizer optimizer = new PortfolioOptimizer();

// Add candidate projects
optimizer.addProject("Field A", 800.0, 1200.0, ProjectType.DEVELOPMENT, 0.85);
optimizer.addProject("Field B IOR", 150.0, 280.0, ProjectType.IOR, 0.92);
optimizer.addProject("Exploration C", 200.0, 800.0, ProjectType.EXPLORATION, 0.35);
optimizer.addProject("Tieback D", 300.0, 450.0, ProjectType.TIEBACK, 0.88);

// Set budget constraints
optimizer.setAnnualBudget(2025, 500.0);
optimizer.setAnnualBudget(2026, 600.0);
optimizer.setAnnualBudget(2027, 400.0);
optimizer.setTotalBudget(1500.0);

// Optimize and compare strategies
Map<OptimizationStrategy, PortfolioResult> results = optimizer.compareStrategies();

// Generate report
String report = optimizer.generateComparisonReport();
```

---

### Multi-Criteria Decision Analysis (MCDA)

The `DevelopmentOptionRanker` uses weighted sum normalization:

$$Score_i = \sum_{j=1}^{m} w_j \cdot \tilde{s}_{ij}$$

Where normalized scores are:

**For "higher is better" criteria:**
$$\tilde{s}_{ij} = \frac{s_{ij} - s_j^{min}}{s_j^{max} - s_j^{min}}$$

**For "lower is better" criteria:**
$$\tilde{s}_{ij} = \frac{s_j^{max} - s_{ij}}{s_j^{max} - s_j^{min}}$$

#### Criterion Categories

| Category | Criteria | Direction |
|----------|----------|-----------|
| **Economic** | NPV, IRR, Capital Efficiency, Breakeven Price | ↑↓ mixed |
| **Technical** | Complexity, Risk, Reservoir Uncertainty | ↓ lower is better |
| **Environmental** | CO₂ Intensity, Total Emissions | ↓ lower is better |
| **Strategic** | Strategic Fit, Synergies, Optionality | ↑ higher is better |
| **Risk** | HSE Risk, Execution Risk, Commercial Risk | ↓ lower is better |

#### Weight Profiles

```java
DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();

// Pre-defined profiles
ranker.setWeightProfile("economic");       // NPV/IRR focused
ranker.setWeightProfile("sustainability"); // CO2/environment focused
ranker.setWeightProfile("balanced");       // Equal weights
ranker.setWeightProfile("risk_averse");    // Risk minimization

// Or custom weights
ranker.setWeight(Criterion.NPV, 0.25);
ranker.setWeight(Criterion.CO2_INTENSITY, 0.20);
ranker.setWeight(Criterion.TECHNICAL_RISK, 0.15);
ranker.setWeight(Criterion.STRATEGIC_FIT, 0.15);
ranker.setWeight(Criterion.EXECUTION_RISK, 0.25);
```

---

### Multiphase Flow Correlations

The `MultiphaseFlowIntegrator` implements Beggs & Brill correlation for pipeline hydraulics:

#### Liquid Holdup Calculation

$$H_L(\theta) = H_L(0) \cdot \psi$$

Where horizontal holdup:
$$H_L(0) = \frac{a \cdot \lambda_L^b}{Fr^c}$$

Inclination correction:
$$\psi = 1 + C \cdot [\sin(1.8\theta) - \frac{1}{3}\sin^3(1.8\theta)]$$

#### Froude Number

$$Fr = \frac{v_m^2}{g \cdot D}$$

Where:
- $v_m$ = mixture velocity (m/s)
- $g$ = gravitational acceleration (9.81 m/s²)
- $D$ = pipe diameter (m)

#### Flow Regime Determination

| Regime | $L_1$ | $L_2$ | Condition |
|--------|-------|-------|-----------|
| Segregated | $316 \lambda_L^{0.302}$ | $0.0009252 \lambda_L^{-2.4684}$ | $\lambda_L < 0.01$ and $Fr < L_1$ |
| Intermittent | - | - | $0.01 \leq \lambda_L \leq 0.4$ and $L_3 < Fr \leq L_1$ |
| Distributed | - | - | $\lambda_L \geq 0.4$ and $Fr \geq L_1$ |

```java
MultiphaseFlowIntegrator integrator = new MultiphaseFlowIntegrator();

// Single calculation
PipelineResult result = integrator.calculateHydraulics(
    stream, length, diameter, inclination);

// Generate hydraulic curve
List<PipelineResult> curve = integrator.calculateHydraulicsCurve(
    stream, length, diameter, inclination,
    minFlowRate, maxFlowRate, numPoints);

// Pipe sizing
double optimalDiameter = integrator.sizePipeline(
    stream, length, inclination, maxPressureDrop, minVelocity, maxVelocity);
```

---

### Norwegian Petroleum Tax Model

The `NorwegianTaxModel` implements the 2022+ tax regime:

#### Tax Calculation

$$Tax_{total} = Tax_{corporate} + Tax_{special}$$

Where:
$$Tax_{corporate} = 0.22 \times (Revenue - OPEX - DD\&A - Interest)$$
$$Tax_{special} = 0.56 \times (Revenue - OPEX - Uplift \times CAPEX - Special DD\&A)$$

#### Key Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| Corporate Rate | 22% | Standard Norwegian corporate tax |
| Special Petroleum Tax | 56% | Additional petroleum sector tax |
| Marginal Rate | 78% | Combined marginal rate |
| Uplift | 20.8% | CAPEX deduction for special tax |
| Depreciation Period | 6 years | Linear depreciation |

```java
NorwegianTaxModel taxModel = new NorwegianTaxModel();

// Configure parameters
taxModel.setOilPrice(75.0);         // USD/bbl
taxModel.setGasPrice(8.0);          // USD/MMBtu
taxModel.setExchangeRate(10.5);     // NOK/USD

// Calculate for a production year
TaxResult result = taxModel.calculateTax(
    oilProductionSm3,
    gasProductionSm3,
    opexMNOK,
    capexMNOK,
    previousCapex  // for depreciation
);

// Results
System.out.println("Corporate Tax: " + result.getCorporateTax() + " MNOK");
System.out.println("Special Tax: " + result.getSpecialTax() + " MNOK");
System.out.println("Net Cash Flow: " + result.getNetCashFlow() + " MNOK");
```

---

### Tieback Analysis

The `TiebackAnalyzer` performs comprehensive feasibility screening:

#### Distance-Based Pressure Drop

$$\Delta P_{tieback} = \frac{f \cdot L \cdot \rho \cdot v^2}{2D} + \rho \cdot g \cdot \Delta h$$

Where Darcy friction factor from Colebrook-White:
$$\frac{1}{\sqrt{f}} = -2 \log_{10}\left(\frac{\epsilon/D}{3.7} + \frac{2.51}{Re \sqrt{f}}\right)$$

#### Screening Criteria

| Criterion | Threshold | Notes |
|-----------|-----------|-------|
| Maximum Distance | 50 km (typical) | Depends on fluid type |
| Pressure Availability | ΔP > tieback losses | Wellhead to host |
| Water Depth Compatibility | ±20% host capability | Subsea equipment limits |
| Flow Assurance | Hydrate, wax, scale | Temperature-dependent |
| Capacity Availability | Host spare capacity | Processing constraints |

```java
TiebackAnalyzer analyzer = new TiebackAnalyzer();

// Define satellite field
analyzer.setSatelliteLocation(61.2, 2.1);  // lat/lon
analyzer.setWaterDepth(320.0);
analyzer.setProductionRateSm3d(8000.0);
analyzer.setFluidType(FluidType.LIGHT_OIL);
analyzer.setGOR(200.0);

// Add potential hosts
HostFacility host1 = HostFacility.builder("Troll C")
    .location(60.8, 3.5)
    .facilityType(FacilityType.PLATFORM)
    .waterDepth(340.0)
    .processingCapacity(150000.0)
    .currentThroughput(120000.0)
    .maxWaterCut(0.90)
    .build();

analyzer.addHost(host1);

// Quick screening
TiebackScreeningResult screening = analyzer.quickScreen(
    host1, 25000.0, 320.0, 8000.0, FluidType.LIGHT_OIL, 200.0);

// Full analysis
TiebackReport report = analyzer.analyze(host1);
```

---

### Reservoir Coupling (VFP Tables)

The `ReservoirCouplingExporter` generates ECLIPSE-compatible VFP tables:

#### VFPPROD Format

$$BHP = f(THP, WFR, GFR, ALQ, Q_{oil})$$

Tubing performance:
$$BHP = THP + \Delta P_{friction} + \Delta P_{gravity} - \Delta P_{acceleration}$$

```java
ReservoirCouplingExporter exporter = new ReservoirCouplingExporter(processSystem);

// Configure VFP generation parameters
exporter.setThpRange(10.0, 100.0, 10);      // bara
exporter.setWaterCutRange(0.0, 0.95, 10);   // fraction
exporter.setGorRange(50.0, 500.0, 10);      // Sm³/Sm³
exporter.setRateRange(1000.0, 20000.0, 15); // Sm³/d

// Generate VFP tables
VfpTable vfpProd = exporter.generateVfpProd(1, "WELL-1");
VfpTable vfpInj = exporter.generateVfpInj(2, "INJECTOR-1");

// Add schedule constraints
exporter.addGroupConstraint("FIELD", "ORAT", 50000.0);
exporter.addGroupConstraint("FIELD", "GRAT", 10e6);

// Export to file
exporter.exportToFile("include/vfp_tables.inc", ExportFormat.ECLIPSE_100);
```

---

## Lifecycle Integration Examples

### Example 1: Concept Screening (DG0-DG1)

```java
// Quick screening of multiple concepts
BatchConceptRunner runner = new BatchConceptRunner();

// Add concepts to evaluate
runner.addConcept(FieldConcept.oilDevelopment("Concept A - FPSO", 120.0, 12, 5000));
runner.addConcept(FieldConcept.oilDevelopment("Concept B - Tieback", 80.0, 6, 4000));
runner.addConcept(FieldConcept.oilDevelopment("Concept C - Platform", 150.0, 15, 6000));

// Run parallel evaluation
BatchResults results = runner.runParallel(4);

// Compare KPIs
for (ConceptKPIs kpis : results.getAllKpis()) {
    System.out.println(kpis.getConceptName() + ": NPV = " + kpis.getNpv() 
        + " MUSD, CO2 = " + kpis.getCo2Intensity() + " kg/boe");
}

// Rank by multiple criteria
DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
ranker.setWeightProfile("balanced");

for (ConceptKPIs kpis : results.getAllKpis()) {
    DevelopmentOption opt = ranker.addOption(kpis.getConceptName());
    opt.setScore(Criterion.NPV, kpis.getNpv());
    opt.setScore(Criterion.CO2_INTENSITY, kpis.getCo2Intensity());
    opt.setScore(Criterion.CAPITAL_EFFICIENCY, kpis.getNpv() / kpis.getCapex());
}

RankingResult ranking = ranker.rank();
System.out.println(ranking.generateReport());
```

### Example 2: Detailed Development Design (DG2-DG3)

```java
// From concept to detailed process design
FieldConcept selectedConcept = FieldConcept.builder("Selected Development")
    .reservoir(ReservoirInput.builder()
        .fluidType(FluidType.MEDIUM_OIL)
        .gor(180.0)
        .apiGravity(32.0)
        .h2sContent(50.0)  // ppm
        .co2Content(2.5)   // mol%
        .build())
    .wells(WellsInput.builder()
        .producerCount(10)
        .injectorCount(5)
        .ratePerWell(12000.0)
        .wellType(WellType.DEVIATED)
        .completionType(CompletionType.FRAC_PACK)
        .build())
    .infrastructure(InfrastructureInput.builder()
        .processingLocation(ProcessingLocation.FPSO)
        .exportType(ExportType.SHUTTLE_TANKER)
        .waterDepth(380.0)
        .distanceToShore(180.0)
        .powerSupply(PowerSupply.GAS_TURBINE)
        .build())
    .build();

// Generate FEED-level process model
ConceptToProcessLinker linker = new ConceptToProcessLinker();
linker.setHpSeparatorPressure(45.0);
linker.setLpSeparatorPressure(4.0);
linker.setExportGasPressure(180.0);
linker.setCompressionEfficiency(0.78);

ProcessSystem process = linker.generateProcessSystem(
    selectedConcept, FidelityLevel.PRE_FEED);

// Run simulation
process.run();

// Extract utility requirements
double powerMW = linker.getTotalPowerMW(process);
double heatingMW = linker.getTotalHeatingMW(process);
double coolingMW = linker.getTotalCoolingMW(process);

System.out.println("Power Demand: " + powerMW + " MW");
System.out.println("Heating Duty: " + heatingMW + " MW");
System.out.println("Cooling Duty: " + coolingMW + " MW");

// Flow assurance analysis
FlowAssuranceScreener faScreener = new FlowAssuranceScreener();
FlowAssuranceReport faReport = faScreener.screen(process);

// Detailed emissions calculation
DetailedEmissionsCalculator emissions = new DetailedEmissionsCalculator();
emissions.setPowerSource("gas_turbine");
emissions.setFlaringRate(0.5);  // % of gas
DetailedEmissionsReport emReport = emissions.calculate(process);
```

### Example 3: Production Operations

```java
// Real-time production optimization
ProcessSystem operations = loadOperationalModel("field_model.json");

// Update with current conditions
Stream wellStream = (Stream) operations.getUnit("Well-1");
wellStream.setFlowRate(getCurrentFlowRate(), "Sm3/hr");
wellStream.setTemperature(getCurrentWellheadTemp(), "C");
wellStream.setPressure(getCurrentWellheadPressure(), "bara");

// Run updated model
operations.run();

// Production allocation
ProductionAllocator allocator = new ProductionAllocator();
allocator.setTestSeparatorData(testSepData);
Map<String, Double> allocation = allocator.allocateProduction(operations);

// Bottleneck analysis
BottleneckAnalyzer bottleneck = new BottleneckAnalyzer();
bottleneck.setCapacities(equipmentCapacities);
String constrainingEquipment = bottleneck.findBottleneck(operations);

// Gas lift optimization
GasLiftOptimizer glOptimizer = new GasLiftOptimizer();
glOptimizer.setAvailableGas(5.0);  // MSm³/d
glOptimizer.setWellPerformance(iprCurves);
Map<String, Double> optimalAllocation = glOptimizer.optimize();
```

### Example 4: Late-Life and Decommissioning

```java
// Late-life screening
ArtificialLiftScreener liftScreener = new ArtificialLiftScreener();
liftScreener.setCurrentConditions(
    reservoirPressure,
    waterCut,
    gor,
    productivityIndex
);

List<MethodResult> liftOptions = liftScreener.screenAllMethods();
for (MethodResult option : liftOptions) {
    System.out.println(option.getMethod() + ": " 
        + (option.isFeasible() ? "Feasible" : "Not feasible")
        + " - " + option.getRationale());
}

// IOR/EOR evaluation
ScenarioAnalyzer scenarios = new ScenarioAnalyzer();
scenarios.setBaseCase(currentProduction);

// Water injection scenario
scenarios.addScenario("Water Injection", () -> {
    InjectionWellModel injector = new InjectionWellModel();
    injector.setInjectionType(InjectionType.WATER);
    injector.setInjectionRate(15000.0);  // Sm³/d
    return injector.simulateResponse(reservoirModel);
});

// Gas injection scenario
scenarios.addScenario("Gas Injection", () -> {
    InjectionWellModel injector = new InjectionWellModel();
    injector.setInjectionType(InjectionType.GAS);
    injector.setInjectionRate(3.0e6);  // Sm³/d
    return injector.simulateResponse(reservoirModel);
});

ScenarioResults results = scenarios.runAll();
System.out.println(results.generateComparisonTable());

// Decommissioning cost estimation
DecommissioningEstimator decom = new DecommissioningEstimator();
decom.setFacilityType(FacilityType.FPSO);
decom.setWellCount(15);
decom.setWaterDepth(380.0);
decom.setSubseaEquipment(subseaInventory);

double decomCost = decom.estimateTotalCost();
Map<String, Double> breakdown = decom.getCostBreakdown();
```

---

## Monte Carlo Uncertainty Analysis

The framework supports probabilistic analysis across all lifecycle phases:

```java
MonteCarloRunner mc = new MonteCarloRunner(1000);

// Define uncertain parameters
mc.addParameter("oilPrice", Distribution.triangular(50.0, 75.0, 120.0));
mc.addParameter("recoveryFactor", Distribution.normal(0.45, 0.05));
mc.addParameter("capexMultiplier", Distribution.lognormal(1.0, 0.15));
mc.addParameter("opexMultiplier", Distribution.triangular(0.9, 1.0, 1.3));
mc.addParameter("firstOilDelay", Distribution.discrete(0, 0.7, 6, 0.2, 12, 0.1));

// Define model function
mc.setModel((params) -> {
    FieldConcept concept = createConcept(params);
    ConceptEvaluator evaluator = new ConceptEvaluator();
    return evaluator.evaluate(concept);
});

// Run simulation
MonteCarloResults results = mc.run();

// Statistical analysis
System.out.println("NPV P10: " + results.getPercentile("npv", 10));
System.out.println("NPV P50: " + results.getPercentile("npv", 50));
System.out.println("NPV P90: " + results.getPercentile("npv", 90));
System.out.println("Probability NPV > 0: " + results.probabilityAbove("npv", 0.0));

// Sensitivity (tornado) analysis
Map<String, Double> sensitivities = results.computeSensitivities("npv");
```

---

## Integration with External Systems

### ECLIPSE/E300 Reservoir Coupling

```java
// Export VFP tables for reservoir simulation
ReservoirCouplingExporter exporter = new ReservoirCouplingExporter(process);

// Production well VFP
VfpTable vfpProd = exporter.generateVfpProd(1, "PROD-1");

// Injection well VFP
VfpTable vfpInj = exporter.generateVfpInj(2, "INJ-1");

// Schedule keywords
exporter.addGroupConstraint("FIELD", "ORAT", 50000.0);
exporter.addWellConstraint("PROD-1", "BHP", 150.0);

// Export
String eclipseKeywords = exporter.getEclipseKeywords();
exporter.exportToFile("vfp_wells.inc", ExportFormat.ECLIPSE_100);
```

### Python Integration

```python
import jpype
import jpype.imports
from jpype.types import *

# Start JVM with NeqSim
jpype.startJVM(classpath=['neqsim.jar'])

from neqsim.process.fielddevelopment.concept import FieldConcept
from neqsim.process.fielddevelopment.evaluation import ConceptEvaluator
from neqsim.process.fielddevelopment.economics import PortfolioOptimizer

# Create and evaluate concept
concept = FieldConcept.oilDevelopment("Python Field", 100.0, 8, 5000)
evaluator = ConceptEvaluator()
kpis = evaluator.evaluate(concept)

print(f"NPV: {kpis.getNpv()} MUSD")
print(f"IRR: {kpis.getIrr() * 100:.1f}%")
print(f"CO2 Intensity: {kpis.getCo2Intensity():.1f} kg/boe")
```

---

## Best Practices

### 1. Maintain Thermodynamic Consistency

Always use the same equation of state throughout the workflow:

```java
// Create master fluid definition
SystemInterface masterFluid = new SystemSrkEos(273.15 + 60, 50.0);
masterFluid.addComponent("methane", 0.45);
// ... add all components
masterFluid.setMixingRule("classic");
masterFluid.setMultiPhaseCheck(true);

// Clone for different uses
SystemInterface wellFluid = masterFluid.clone();
SystemInterface separatorFluid = masterFluid.clone();
```

### 2. Progressive Fidelity

Start with screening-level models and increase fidelity as project progresses:

```java
// DG0-DG1: Screening
ProcessSystem screening = linker.generateProcessSystem(concept, FidelityLevel.SCREENING);

// DG2: Concept
ProcessSystem conceptModel = linker.generateProcessSystem(concept, FidelityLevel.CONCEPT);

// DG3: Pre-FEED
ProcessSystem preFeed = linker.generateProcessSystem(concept, FidelityLevel.PRE_FEED);

// DG4: FEED
ProcessSystem feed = linker.generateProcessSystem(concept, FidelityLevel.FEED);
```

### 3. Document Assumptions

Use the built-in logging and reporting:

```java
FieldConcept concept = FieldConcept.builder("My Field")
    .notes("Based on 2024 CPR. Assumes analog reservoir performance.")
    .dataSource("Exploration Well EXP-1, 2023")
    .confidenceLevel(ConfidenceLevel.MEDIUM)
    // ... configuration
    .build();
```

---

## See Also

- [Field Development Strategy](FIELD_DEVELOPMENT_STRATEGY.md)
- [Integrated Field Development Framework](INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md)
- [Process Simulation Guide](../process/README.md)
- [PVT Simulation Guide](../pvtsimulation/README.md)
