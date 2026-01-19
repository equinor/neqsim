# Field Development Framework - API Guide

This guide provides detailed usage examples for all components added in the field development framework PR.

---

## Table of Contents

1. [Core Concepts](#1-core-concepts)
2. [Economics Module](#2-economics-module)
3. [Evaluation Module](#3-evaluation-module)
4. [Reservoir Integration](#4-reservoir-integration)
5. [Facility Design](#5-facility-design)
6. [Network & Hydraulics](#6-network--hydraulics)
7. [Tieback Analysis](#7-tieback-analysis)
8. [Screening Tools](#8-screening-tools)

---

## 1. Core Concepts

### FieldConcept - Central Configuration Object

The `FieldConcept` class represents a complete field development configuration:

```java
import neqsim.process.fielddevelopment.concept.*;

// Builder pattern for complex configurations
FieldConcept concept = FieldConcept.builder("Barents Sea Discovery")
    .reservoir(ReservoirInput.builder()
        .fluidType(ReservoirInput.FluidType.LIGHT_OIL)
        .gor(250.0)                    // Sm³/Sm³
        .apiGravity(38.0)              // °API
        .waterCut(0.0)                 // Initial fraction
        .reservoirPressure(320.0)      // bara
        .reservoirTemperature(95.0)    // °C
        .reservoirDepth(2800.0)        // m TVD
        .permeability(150.0)           // mD
        .netPay(45.0)                  // m
        .stoiip(180.0)                 // MSm³
        .build())
    .wells(WellsInput.builder()
        .producerCount(12)
        .injectorCount(6)
        .ratePerWell(8000.0)           // Sm³/d oil
        .wellType(WellsInput.WellType.HORIZONTAL)
        .completionType(WellsInput.CompletionType.OPEN_HOLE)
        .lateralLength(1500.0)         // m
        .productivityIndex(25.0)       // Sm³/d/bar
        .build())
    .infrastructure(InfrastructureInput.builder()
        .processingLocation(InfrastructureInput.ProcessingLocation.FPSO)
        .exportType(InfrastructureInput.ExportType.SHUTTLE_TANKER)
        .waterDepth(380.0)             // m
        .distanceToShore(220.0)        // km
        .powerSupply(InfrastructureInput.PowerSupply.GAS_TURBINE)
        .build())
    .startYear(2028)
    .productionLifeYears(25)
    .build();

// Quick factory methods for common configurations
FieldConcept oilField = FieldConcept.oilDevelopment("Simple Oil", 100.0, 8, 5000);
FieldConcept gasField = FieldConcept.gasDevelopment("Simple Gas", 50.0, 4, 15.0);
```

### ReservoirInput - Reservoir Properties

```java
ReservoirInput reservoir = ReservoirInput.builder()
    .fluidType(FluidType.GAS_CONDENSATE)
    .gor(5000.0)                   // High GOR for condensate
    .cgrSm3PerMSm3(150.0)          // Condensate-gas ratio
    .reservoirPressure(450.0)      // bara - high pressure
    .reservoirTemperature(140.0)   // °C - HPHT
    .h2sContent(0.0)               // ppm
    .co2Content(3.5)               // mol%
    .n2Content(1.2)                // mol%
    .waxAppearanceTemp(25.0)       // °C
    .asphalteneStability(0.8)      // 0-1 scale
    .build();
```

### WellsInput - Well Configuration

```java
WellsInput wells = WellsInput.builder()
    .producerCount(8)
    .injectorCount(4)
    .ratePerWell(6000.0)           // Sm³/d per well
    .wellType(WellType.DEVIATED)
    .completionType(CompletionType.FRAC_PACK)
    .tubing(4.5)                   // inches
    .casingDepth(3200.0)           // m
    .kickoffPoint(800.0)           // m
    .maxDogleg(6.0)                // °/30m
    .artificialLift(ArtificialLift.ESP)
    .build();
```

### InfrastructureInput - Facilities Configuration

```java
InfrastructureInput infra = InfrastructureInput.builder()
    .processingLocation(ProcessingLocation.PLATFORM)
    .platformType(PlatformType.STEEL_JACKET)
    .exportType(ExportType.PIPELINE)
    .exportPipelineDiameter(0.6)   // m (24")
    .exportPipelineLength(85.0)    // km
    .waterDepth(120.0)             // m
    .distanceToShore(95.0)         // km
    .powerSupply(PowerSupply.SHORE_POWER)
    .powerFromShoreMW(50.0)
    .build();
```

---

## 2. Economics Module

### PortfolioOptimizer - Multi-Project Selection

```java
import neqsim.process.fielddevelopment.economics.*;

PortfolioOptimizer optimizer = new PortfolioOptimizer();

// Add candidate projects with: name, CAPEX, NPV, type, probability of success
Project projectA = optimizer.addProject("Field Alpha", 800.0, 1400.0, 
    ProjectType.DEVELOPMENT, 0.85);
projectA.setStartYear(2026);

Project projectB = optimizer.addProject("Beta IOR", 120.0, 220.0, 
    ProjectType.IOR, 0.95);
projectB.setMandatory(true);  // Must be included if budget allows

Project projectC = optimizer.addProject("Gamma Exploration", 250.0, 900.0, 
    ProjectType.EXPLORATION, 0.30);

Project projectD = optimizer.addProject("Delta Tieback", 350.0, 480.0, 
    ProjectType.TIEBACK, 0.88);

// Set budget constraints
optimizer.setTotalBudget(1200.0);  // Total MUSD available
optimizer.setAnnualBudget(2026, 400.0);
optimizer.setAnnualBudget(2027, 500.0);
optimizer.setAnnualBudget(2028, 450.0);

// Optional: Set allocation constraints by type
optimizer.setMinAllocation(ProjectType.IOR, 100.0);      // At least 100 MUSD to IOR
optimizer.setMaxAllocation(ProjectType.EXPLORATION, 300.0);  // At most 300 MUSD to exploration

// Run optimization with different strategies
PortfolioResult greedyResult = optimizer.optimize(OptimizationStrategy.GREEDY_NPV_RATIO);
PortfolioResult riskResult = optimizer.optimize(OptimizationStrategy.RISK_WEIGHTED);
PortfolioResult emvResult = optimizer.optimize(OptimizationStrategy.EMV_MAXIMIZATION);

// Access results
System.out.println("Selected Projects: " + greedyResult.getSelectedProjects());
System.out.println("Total NPV: " + greedyResult.getTotalNpv() + " MUSD");
System.out.println("Total CAPEX: " + greedyResult.getTotalCapex() + " MUSD");
System.out.println("Capital Efficiency: " + greedyResult.getCapitalEfficiency());

// Compare all strategies
Map<OptimizationStrategy, PortfolioResult> comparison = optimizer.compareStrategies();
String report = optimizer.generateComparisonReport();
System.out.println(report);
```

### NorwegianTaxModel - Petroleum Taxation

```java
import neqsim.process.fielddevelopment.economics.*;

NorwegianTaxModel taxModel = new NorwegianTaxModel();

// Configure economic parameters
taxModel.setOilPrice(80.0);          // USD/bbl
taxModel.setGasPrice(9.0);           // USD/MMBtu
taxModel.setExchangeRate(10.8);      // NOK/USD
taxModel.setDiscountRate(0.08);      // 8% real

// Calculate tax for a single year
TaxResult yearResult = taxModel.calculateTax(
    5_000_000.0,    // Oil production (Sm³)
    2_000_000_000.0, // Gas production (Sm³)
    1_500.0,         // OPEX (MNOK)
    800.0,           // CAPEX this year (MNOK)
    3_000.0          // Cumulative previous CAPEX for depreciation
);

System.out.println("Revenue: " + yearResult.getRevenue() + " MNOK");
System.out.println("Corporate Tax (22%): " + yearResult.getCorporateTax() + " MNOK");
System.out.println("Special Tax (56%): " + yearResult.getSpecialTax() + " MNOK");
System.out.println("Total Tax: " + yearResult.getTotalTax() + " MNOK");
System.out.println("Net Cash Flow: " + yearResult.getNetCashFlow() + " MNOK");
System.out.println("Effective Tax Rate: " + yearResult.getEffectiveTaxRate() * 100 + "%");

// Full lifecycle calculation
List<TaxResult> lifecycle = taxModel.calculateLifecycle(
    productionProfile,   // List<Double> annual oil production
    gasProfile,          // List<Double> annual gas production
    opexProfile,         // List<Double> annual OPEX
    capexProfile         // List<Double> annual CAPEX
);

double npv = taxModel.calculateNPV(lifecycle);
double irr = taxModel.calculateIRR(lifecycle);
```

### SensitivityAnalyzer - Tornado Analysis

```java
import neqsim.process.fielddevelopment.economics.*;

SensitivityAnalyzer sensitivity = new SensitivityAnalyzer();

// Define base case
sensitivity.setBaseCase(concept);

// Define parameters to vary
sensitivity.addParameter("oilPrice", 60.0, 80.0, 100.0);      // low, base, high
sensitivity.addParameter("capexMultiplier", 0.85, 1.0, 1.25);
sensitivity.addParameter("opexMultiplier", 0.9, 1.0, 1.2);
sensitivity.addParameter("recoveryFactor", 0.35, 0.45, 0.55);
sensitivity.addParameter("firstOilDelay", -6, 0, 12);          // months

// Run sensitivity
SensitivityResults results = sensitivity.runTornado();

// Get sorted impact on NPV
List<ParameterImpact> impacts = results.getSortedImpacts("npv");
for (ParameterImpact impact : impacts) {
    System.out.printf("%s: %.1f to %.1f MUSD swing%n", 
        impact.getParameter(), impact.getLowValue(), impact.getHighValue());
}

// Spider plot data
Map<String, List<Point>> spiderData = results.getSpiderPlotData("npv", 5);
```

---

## 3. Evaluation Module

### DevelopmentOptionRanker - MCDA Ranking

```java
import neqsim.process.fielddevelopment.evaluation.*;

DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();

// Add development options with scores
DevelopmentOption fpso = ranker.addOption("FPSO Development");
fpso.setDescription("New-build FPSO with full processing");
fpso.setScore(Criterion.NPV, 1200.0);           // MUSD
fpso.setScore(Criterion.IRR, 0.18);             // 18%
fpso.setScore(Criterion.CAPITAL_EFFICIENCY, 1.5);
fpso.setScore(Criterion.CO2_INTENSITY, 12.0);   // kg CO2/boe
fpso.setScore(Criterion.TECHNICAL_RISK, 0.4);   // 0-1
fpso.setScore(Criterion.EXECUTION_RISK, 0.5);
fpso.setScore(Criterion.STRATEGIC_FIT, 0.9);

DevelopmentOption tieback = ranker.addOption("Tieback to Existing Platform");
tieback.setScore(Criterion.NPV, 650.0);
tieback.setScore(Criterion.IRR, 0.28);
tieback.setScore(Criterion.CAPITAL_EFFICIENCY, 2.1);
tieback.setScore(Criterion.CO2_INTENSITY, 7.0);
tieback.setScore(Criterion.TECHNICAL_RISK, 0.2);
tieback.setScore(Criterion.EXECUTION_RISK, 0.25);
tieback.setScore(Criterion.STRATEGIC_FIT, 0.7);

DevelopmentOption subsea = ranker.addOption("Subsea to Shore");
subsea.setScore(Criterion.NPV, 900.0);
subsea.setScore(Criterion.IRR, 0.15);
subsea.setScore(Criterion.CAPITAL_EFFICIENCY, 1.2);
subsea.setScore(Criterion.CO2_INTENSITY, 5.0);
subsea.setScore(Criterion.TECHNICAL_RISK, 0.6);
subsea.setScore(Criterion.EXECUTION_RISK, 0.55);
subsea.setScore(Criterion.STRATEGIC_FIT, 0.85);

// Set weights - can use profiles or individual weights
ranker.setWeightProfile("balanced");  // or "economic", "sustainability", "risk_averse"

// Or set individual weights
ranker.setWeight(Criterion.NPV, 0.25);
ranker.setWeight(Criterion.CO2_INTENSITY, 0.20);
ranker.setWeight(Criterion.TECHNICAL_RISK, 0.15);
ranker.setWeight(Criterion.EXECUTION_RISK, 0.15);
ranker.setWeight(Criterion.STRATEGIC_FIT, 0.15);
ranker.setWeight(Criterion.CAPITAL_EFFICIENCY, 0.10);

// Perform ranking
RankingResult result = ranker.rank();

// Get ranked list
List<DevelopmentOption> ranked = result.getRankedOptions();
for (int i = 0; i < ranked.size(); i++) {
    DevelopmentOption opt = ranked.get(i);
    System.out.printf("%d. %s (Score: %.3f)%n", 
        i+1, opt.getName(), result.getWeightedScore(opt));
}

// Generate detailed report
String report = result.generateReport();

// Rank by single criterion
List<DevelopmentOption> byNpv = ranker.rankByCriterion(Criterion.NPV);
List<DevelopmentOption> byCo2 = ranker.rankByCriterion(Criterion.CO2_INTENSITY);
```

### MonteCarloRunner - Probabilistic Analysis

```java
import neqsim.process.fielddevelopment.evaluation.*;

MonteCarloRunner mc = new MonteCarloRunner(10000);  // 10,000 iterations

// Define uncertain parameters with distributions
mc.addUniformParameter("oilPrice", 50.0, 120.0);
mc.addTriangularParameter("recoveryFactor", 0.30, 0.45, 0.55);
mc.addNormalParameter("capexMultiplier", 1.0, 0.15);
mc.addLognormalParameter("opexMultiplier", 0.0, 0.20);  // mean of log, std of log
mc.addDiscreteParameter("delayMonths", new double[]{0, 6, 12}, new double[]{0.6, 0.3, 0.1});

// Optional: Add correlations
mc.addCorrelation("oilPrice", "gasPrice", 0.7);

// Define the model to evaluate
mc.setEvaluationFunction((params) -> {
    FieldConcept concept = createConceptWithParams(params);
    ConceptEvaluator evaluator = new ConceptEvaluator();
    ConceptKPIs kpis = evaluator.evaluate(concept);
    
    Map<String, Double> results = new HashMap<>();
    results.put("npv", kpis.getNpv());
    results.put("irr", kpis.getIrr());
    results.put("payback", kpis.getPaybackYears());
    results.put("co2", kpis.getCo2Intensity());
    return results;
});

// Run simulation
MonteCarloResults results = mc.run();

// Statistical analysis
System.out.println("NPV Statistics:");
System.out.println("  Mean: " + results.getMean("npv") + " MUSD");
System.out.println("  Std Dev: " + results.getStdDev("npv") + " MUSD");
System.out.println("  P10: " + results.getPercentile("npv", 10) + " MUSD");
System.out.println("  P50: " + results.getPercentile("npv", 50) + " MUSD");
System.out.println("  P90: " + results.getPercentile("npv", 90) + " MUSD");
System.out.println("  P(NPV > 0): " + results.probabilityAbove("npv", 0.0) * 100 + "%");

// Sensitivity from Monte Carlo
Map<String, Double> sensitivities = results.computeRankCorrelations("npv");
for (Map.Entry<String, Double> entry : sensitivities.entrySet()) {
    System.out.printf("  %s: %.3f%n", entry.getKey(), entry.getValue());
}

// Export for visualization
results.exportToCsv("monte_carlo_results.csv");
```

### ConceptEvaluator - Integrated Evaluation

```java
import neqsim.process.fielddevelopment.evaluation.*;

ConceptEvaluator evaluator = new ConceptEvaluator();

// Configure evaluation parameters
evaluator.setOilPrice(75.0);
evaluator.setGasPrice(8.0);
evaluator.setDiscountRate(0.08);
evaluator.setTaxModel(new NorwegianTaxModel());

// Evaluate a concept
ConceptKPIs kpis = evaluator.evaluate(concept);

// Access all KPIs
System.out.println("=== Economic KPIs ===");
System.out.println("NPV: " + kpis.getNpv() + " MUSD");
System.out.println("IRR: " + kpis.getIrr() * 100 + "%");
System.out.println("Payback: " + kpis.getPaybackYears() + " years");
System.out.println("PI: " + kpis.getProfitabilityIndex());
System.out.println("Breakeven: " + kpis.getBreakevenPrice() + " USD/bbl");
System.out.println("CAPEX: " + kpis.getTotalCapex() + " MUSD");
System.out.println("Peak CAPEX Year: " + kpis.getPeakCapexYear());

System.out.println("\n=== Production KPIs ===");
System.out.println("Plateau Rate: " + kpis.getPlateauRate() + " Sm³/d");
System.out.println("Ultimate Recovery: " + kpis.getUltimateRecovery() + " MSm³");
System.out.println("Recovery Factor: " + kpis.getRecoveryFactor() * 100 + "%");
System.out.println("First Oil: " + kpis.getFirstOilYear());

System.out.println("\n=== Environmental KPIs ===");
System.out.println("CO2 Intensity: " + kpis.getCo2Intensity() + " kg/boe");
System.out.println("Total Emissions: " + kpis.getTotalEmissions() + " kt CO2");
System.out.println("Flaring Rate: " + kpis.getFlaringRate() + "%");
```

### BatchConceptRunner - Parallel Evaluation

```java
import neqsim.process.fielddevelopment.evaluation.*;

BatchConceptRunner runner = new BatchConceptRunner();

// Add multiple concepts
runner.addConcept(FieldConcept.oilDevelopment("Concept A", 100, 8, 5000));
runner.addConcept(FieldConcept.oilDevelopment("Concept B", 80, 6, 6000));
runner.addConcept(FieldConcept.oilDevelopment("Concept C", 120, 10, 4500));
runner.addConcept(FieldConcept.oilDevelopment("Concept D", 90, 7, 5500));

// Configure evaluation
runner.setOilPrice(75.0);
runner.setDiscountRate(0.08);

// Run in parallel (4 threads)
BatchResults results = runner.runParallel(4);

// Access results
for (String name : results.getConceptNames()) {
    ConceptKPIs kpis = results.getKpis(name);
    System.out.printf("%s: NPV=%.0f, IRR=%.1f%%, CO2=%.1f%n",
        name, kpis.getNpv(), kpis.getIrr()*100, kpis.getCo2Intensity());
}

// Get best by criterion
String bestNpv = results.getBestConcept("npv");
String lowestCo2 = results.getBestConcept("co2Intensity", false);  // false = minimize

// Export comparison table
String table = results.generateComparisonTable();
results.exportToCsv("batch_results.csv");
```

---

## 4. Reservoir Integration

### EclipseLiftCurveGenerator - VFP Tables from Beggs & Brill Pipelines

Generate Eclipse VFPPROD tables using rigorous Beggs and Brill pipeline calculations:

```java
import neqsim.process.fielddevelopment.reservoir.*;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create reservoir fluid
SystemInterface fluid = new SystemSrkEos(353.15, 150.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-heptane", 0.10);
fluid.addComponent("water", 0.05);
fluid.setMixingRule("classic");

// Create inlet stream
Stream inlet = new Stream("wellstream", fluid);
inlet.setFlowRate(50000, "kg/hr");
inlet.run();

// Create pipeline (e.g., vertical riser)
PipeBeggsAndBrills riser = new PipeBeggsAndBrills("riser", inlet);
riser.setDiameter(0.1524);      // 6 inch
riser.setLength(1500.0);        // 1500 m
riser.setElevation(1500.0);     // Vertical
riser.setNumberOfIncrements(30);

// Create lift curve generator
EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(riser, fluid);
generator.setPipelineParameters(0.1524, 1500.0, 1500.0);
generator.setInletTemperature(80.0, "C");

// Configure VFP table dimensions
generator.setFlowRateRange(500, 8000, 8);       // Sm3/day
generator.setThpRange(20, 80, 7);               // bara
generator.setWaterCutRange(0.0, 0.8, 5);        // fraction
generator.setGorRange(100, 500, 5);             // Sm3/Sm3

// Generate VFP table
VfpTableData vfp = generator.generateVfpTable(1, "PROD-A1");

// Export to Eclipse INCLUDE file
generator.exportToFile("include/vfp_riser.inc");

// Also available as CSV or JSON
String csv = generator.exportToCsv();
String json = generator.toJson();
```

### ProcessSystemLiftCurveGenerator - VFP Tables for Full Process Plants

Generate Eclipse VFPPROD tables for complete oil and gas separation facilities using full process simulation:

```java
import neqsim.process.fielddevelopment.reservoir.*;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create base fluid with typical oil/gas composition
SystemInterface fluid = new SystemSrkEos(330.0, 35.0);
fluid.addComponent("methane", 62.0);
fluid.addComponent("ethane", 9.0);
fluid.addComponent("propane", 6.0);
fluid.addComponent("n-butane", 3.0);
fluid.addComponent("n-heptane", 6.0);
fluid.addComponent("n-octane", 4.0);
fluid.addComponent("nC10", 5.0);
fluid.setMixingRule("classic");

// Build separation process
ProcessSystem process = new ProcessSystem("Offshore Platform");

Stream wellStream = new Stream("well stream", fluid);
wellStream.setFlowRate(100000, "kg/hr");
wellStream.setPressure(35.0, "bara");
process.add(wellStream);

ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellStream);
process.add(hpSep);

// ... add MP separator, LP separator, compressors, coolers, etc.

Stream exportGas = new Stream("export gas", ...);
Stream stableOil = new Stream("stable oil", ...);
process.add(exportGas);
process.add(stableOil);

// Create lift curve generator
ProcessSystemLiftCurveGenerator generator = 
    new ProcessSystemLiftCurveGenerator(process, fluid);

// Configure stream names
generator.setInletStreamName("well stream");
generator.setExportGasStreamName("export gas");
generator.setExportOilStreamName("stable oil");

// Set process description for documentation
generator.setProcessDescription("Three-Stage Offshore Separation - North Sea");

// Configure VFP table dimensions
generator.setFlowRateRange(500, 8000, 8);        // Sm3/day
generator.setThpRange(20, 60, 5);                // bara
generator.setWaterCutRange(0.0, 0.6, 4);         // fraction
generator.setGorRange(100, 400, 4);              // Sm3/Sm3

// Optional: Set well/datum depth for hydrostatic calculation
generator.setDatumDepth(2500.0);                 // meters
generator.setInletTemperature(57.0, "C");

// Generate VFP table (runs process simulation for each combination)
VfpTableData vfp = generator.generateVfpTable(1, "PLATFORM-A");

// Export to Eclipse INCLUDE file
generator.exportToFile("include/vfp_platform.inc");

// Also available as CSV with extended process data
// (includes export gas/oil rates, compression power per point)
String csv = generator.exportToCsv();
String json = generator.toJson();
```

#### Key Features:

- **Full Process Simulation**: Runs actual NeqSim process simulation for each VFP point
- **Multi-Stage Separation**: Captures behavior of HP/MP/LP separator trains
- **Process Outputs**: Tracks export gas/oil rates and compression power
- **Standard Eclipse Format**: VFPPROD tables compatible with Eclipse 100 and E300
- **Extended Reporting**: CSV export includes process-level data for analysis

### ReservoirCouplingExporter - VFP Table Generation

```java
import neqsim.process.fielddevelopment.reservoir.*;

// Create from a process system
ProcessSystem process = createProcessModel();
process.run();

ReservoirCouplingExporter exporter = new ReservoirCouplingExporter(process);

// Configure VFP table parameters
exporter.setWellName("PROD-A1");
exporter.setThpValues(new double[]{10, 20, 30, 40, 50, 60, 70, 80});  // bara
exporter.setWaterCutValues(new double[]{0, 0.2, 0.4, 0.6, 0.8, 0.9, 0.95});
exporter.setGorValues(new double[]{50, 100, 150, 200, 300, 400, 500});  // Sm³/Sm³
exporter.setRateValues(new double[]{1000, 2000, 4000, 6000, 8000, 10000, 12000});  // Sm³/d

// Generate production well VFP
VfpTable vfpProd = exporter.generateVfpProd(1, "PROD-A1");

// Generate injection well VFP  
exporter.setInjectionType(InjectionType.WATER);
VfpTable vfpInj = exporter.generateVfpInj(2, "INJ-A1");

// Add schedule keywords
exporter.addWellConstraint("PROD-A1", "BHP", 150.0);
exporter.addWellConstraint("PROD-A1", "ORAT", 8000.0);
exporter.addGroupConstraint("FIELD", "ORAT", 50000.0);
exporter.addGroupConstraint("FIELD", "WRAT", 100000.0);

// Get ECLIPSE keywords
String eclipseKeywords = exporter.getEclipseKeywords();

// Export to file
exporter.exportToFile("include/vfp_wells.inc", ExportFormat.ECLIPSE_100);
exporter.exportToFile("include/vfp_wells_e300.inc", ExportFormat.E300_COMPOSITIONAL);
```

### TransientWellModel - Well Performance

```java
import neqsim.process.fielddevelopment.reservoir.*;

TransientWellModel well = new TransientWellModel();

// Configure well and reservoir properties
well.setReservoirPressure(280.0);    // bara
well.setReservoirTemperature(90.0);   // °C
well.setPermeability(100.0);          // mD
well.setNetPay(30.0);                 // m
well.setPorosity(0.22);               // fraction
well.setCompressibility(15e-6);       // 1/bar
well.setViscosity(0.8);               // cP
well.setFormationVolumeFactor(1.25);  // rm³/Sm³
well.setWellRadius(0.108);            // m
well.setDrainageRadius(500.0);        // m
well.setSkinFactor(5.0);

// Drawdown analysis
DrawdownResult dd = well.analyzeDrawdown(6000.0, 24.0);  // rate, duration hours
System.out.println("Final BHP: " + dd.getFinalPressure() + " bara");
System.out.println("Productivity Index: " + dd.getProductivityIndex() + " Sm³/d/bar");

// Buildup analysis
BuildupResult bu = well.analyzeBuildup(48.0);  // shut-in duration hours
System.out.println("Extrapolated Pressure: " + bu.getExtrapolatedPressure() + " bara");
System.out.println("Derived Permeability: " + bu.getDerivedPermeability() + " mD");
System.out.println("Derived Skin: " + bu.getDerivedSkin());

// IPR curve
List<Point> ipr = well.generateIPR(20);  // 20 points
for (Point p : ipr) {
    System.out.printf("BHP=%.1f bara -> Rate=%.0f Sm³/d%n", p.x, p.y);
}
```

### InjectionWellModel - Injection Optimization

```java
import neqsim.process.fielddevelopment.reservoir.*;

InjectionWellModel injector = new InjectionWellModel();

// Configure injection parameters
injector.setInjectionType(InjectionType.WATER);
injector.setReservoirPressure(280.0);
injector.setFracturePressure(420.0);
injector.setFormationPermeability(80.0);
injector.setInjectionTemperature(40.0);

// Calculate injection performance
InjectionWellResult result = injector.calculatePerformance(15000.0);  // Sm³/d
System.out.println("Required BHP: " + result.getRequiredBhp() + " bara");
System.out.println("Surface Pressure: " + result.getSurfacePressure() + " bara");
System.out.println("Max Sustainable Rate: " + result.getMaxRate() + " Sm³/d");

// Pattern analysis (for multiple injectors)
InjectionPattern pattern = new InjectionPattern(PatternType.FIVE_SPOT);
pattern.setWellSpacing(600.0);  // m
double sweepEfficiency = pattern.calculateSweepEfficiency(0.5);  // at 50% WC
```

---

## 5. Facility Design

### ConceptToProcessLinker - Auto-generation

```java
import neqsim.process.fielddevelopment.facility.*;

ConceptToProcessLinker linker = new ConceptToProcessLinker();

// Configure design parameters
linker.setHpSeparatorPressure(45.0);     // bara
linker.setLpSeparatorPressure(4.0);      // bara
linker.setExportGasPressure(180.0);      // bara
linker.setExportOilTemperature(40.0);    // °C
linker.setCompressionEfficiency(0.78);   // polytropic

// Generate process model from concept
ProcessSystem process = linker.generateProcessSystem(
    concept, 
    FidelityLevel.PRE_FEED
);

// Run simulation
process.run();

// Get utility summary
double powerMW = linker.getTotalPowerMW(process);
double heatingMW = linker.getTotalHeatingMW(process);
double coolingMW = linker.getTotalCoolingMW(process);

System.out.println("Total Power: " + powerMW + " MW");
System.out.println("Total Heating: " + heatingMW + " MW");
System.out.println("Total Cooling: " + coolingMW + " MW");

// Access individual equipment
ThreePhaseSeparator hpSep = (ThreePhaseSeparator) process.getUnit("HP-Separator");
Compressor exportComp = (Compressor) process.getUnit("Export-Compressor");

System.out.println("HP Sep Gas Rate: " + hpSep.getGasOutStream().getFlowRate("MSm3/day"));
System.out.println("Compressor Power: " + exportComp.getPower("MW") + " MW");
```

### FacilityBuilder - Custom Configurations

```java
import neqsim.process.fielddevelopment.facility.*;

FacilityBuilder builder = new FacilityBuilder();

// Configure facility
FacilityConfig config = FacilityConfig.builder()
    .facilityType(FacilityType.FPSO)
    .processingCapacity(120000.0)    // Sm³/d oil
    .gasCapacity(15.0e6)             // Sm³/d gas
    .waterCapacity(150000.0)         // Sm³/d water
    .exportPressure(180.0)           // bara gas
    .oilStorageCapacity(1.0e6)       // bbls
    .build();

// Add processing blocks
builder.addBlock(BlockType.INLET_SEPARATION, BlockConfig.twoStage());
builder.addBlock(BlockType.GAS_COMPRESSION, BlockConfig.threeStage(180.0));
builder.addBlock(BlockType.GAS_DEHYDRATION, BlockConfig.tegDehy());
builder.addBlock(BlockType.PRODUCED_WATER, BlockConfig.hydrocyclone());

// Build process model
ProcessSystem facility = builder.build(concept.getFluid());

// Size equipment
SeparatorSizingCalculator sizing = new SeparatorSizingCalculator();
sizing.setSeparator((Separator) facility.getUnit("HP-Separator"));
sizing.calculateDimensions();
System.out.println("HP Sep Diameter: " + sizing.getDiameter() + " m");
System.out.println("HP Sep Length: " + sizing.getLength() + " m");
```

---

## 6. Network & Hydraulics

### MultiphaseFlowIntegrator - Pipeline Calculations

```java
import neqsim.process.fielddevelopment.network.*;

MultiphaseFlowIntegrator flow = new MultiphaseFlowIntegrator();

// Calculate hydraulics for a pipeline segment
Stream inlet = createWellStream();
inlet.run();

PipelineResult result = flow.calculateHydraulics(
    inlet,
    8000.0,    // length (m)
    0.30,      // diameter (m)
    -5.0       // inclination (degrees, negative = downhill)
);

System.out.println("Flow Regime: " + result.getFlowRegime());
System.out.println("Pressure Drop: " + result.getPressureDropBar() + " bar");
System.out.println("Temperature Drop: " + result.getTemperatureDropC() + " °C");
System.out.println("Liquid Holdup: " + result.getLiquidHoldup());
System.out.println("Mixture Velocity: " + result.getMixtureVelocity() + " m/s");
System.out.println("Erosional Velocity Ratio: " + result.getErosionalVelocityRatio());

// Generate hydraulics curve (varying flow rate)
List<PipelineResult> curve = flow.calculateHydraulicsCurve(
    inlet, 8000.0, 0.30, -5.0,
    5000.0,    // min flow (kg/hr)
    50000.0,   // max flow (kg/hr)
    10         // number of points
);

// Pipe sizing
double optimalDiameter = flow.sizePipeline(
    inlet,
    8000.0,    // length
    -5.0,      // inclination
    15.0,      // max pressure drop (bar)
    2.0,       // min velocity (m/s)
    15.0       // max velocity (m/s)
);
System.out.println("Recommended Diameter: " + optimalDiameter * 1000 + " mm");
```

### NetworkSolver - Full Network

```java
import neqsim.process.fielddevelopment.network.*;

NetworkSolver network = new NetworkSolver();

// Add wells
network.addWell("Well-1", ipr1, vlp1);
network.addWell("Well-2", ipr2, vlp2);
network.addWell("Well-3", ipr3, vlp3);

// Add flowlines
network.addFlowline("Well-1", "Manifold-A", 3000.0, 0.15);
network.addFlowline("Well-2", "Manifold-A", 4500.0, 0.15);
network.addFlowline("Well-3", "Manifold-B", 2500.0, 0.15);

// Add risers
network.addRiser("Manifold-A", "Platform", 350.0, 0.25);
network.addRiser("Manifold-B", "Platform", 380.0, 0.25);

// Set boundary conditions
network.setSeparatorPressure("Platform", 45.0);  // bara

// Solve network
NetworkResult result = network.solve();

// Get well rates
for (String well : network.getWellNames()) {
    System.out.printf("%s: %.0f Sm³/d oil, %.1f MSm³/d gas%n",
        well, result.getOilRate(well), result.getGasRate(well)/1e6);
}

System.out.println("Total Field Rate: " + result.getTotalOilRate() + " Sm³/d");
```

---

## 7. Tieback Analysis

### TiebackAnalyzer - Feasibility Screening

```java
import neqsim.process.fielddevelopment.tieback.*;

TiebackAnalyzer analyzer = new TiebackAnalyzer();

// Configure satellite discovery
analyzer.setSatelliteLocation(62.1, 3.2);      // lat/lon
analyzer.setWaterDepth(350.0);                  // m
analyzer.setProductionRate(6000.0);             // Sm³/d oil
analyzer.setFluidType(FluidType.MEDIUM_OIL);
analyzer.setGor(180.0);                         // Sm³/Sm³
analyzer.setWaterCut(0.15);                     // initial
analyzer.setReservoirPressure(320.0);           // bara

// Add potential host facilities
HostFacility host1 = HostFacility.builder("Platform Alpha")
    .location(61.8, 3.0)
    .facilityType(FacilityType.PLATFORM)
    .waterDepth(120.0)
    .processingCapacity(80000.0)
    .currentThroughput(55000.0)
    .maxWaterCut(0.85)
    .maxGor(300.0)
    .availableGasLift(2.0e6)
    .endOfLife(2045)
    .build();

HostFacility host2 = HostFacility.builder("FPSO Beta")
    .location(62.3, 3.5)
    .facilityType(FacilityType.FPSO)
    .waterDepth(380.0)
    .processingCapacity(120000.0)
    .currentThroughput(95000.0)
    .maxWaterCut(0.90)
    .build();

analyzer.addHost(host1);
analyzer.addHost(host2);

// Quick screening of all hosts
List<TiebackScreeningResult> screenings = analyzer.screenAllHosts();
for (TiebackScreeningResult result : screenings) {
    System.out.printf("%s: %s - %s%n", 
        result.getHostName(),
        result.isPassed() ? "FEASIBLE" : "NOT FEASIBLE",
        result.isPassed() ? "" : result.getFailureReason());
}

// Detailed analysis for best candidates
TiebackReport report = analyzer.analyze(host1);

System.out.println("=== Tieback Report: " + host1.getName() + " ===");
System.out.println("Distance: " + report.getDistance() + " km");
System.out.println("Pressure Drop: " + report.getPressureDrop() + " bar");
System.out.println("Temperature Arrival: " + report.getArrivalTemperature() + " °C");
System.out.println("Flow Regime: " + report.getFlowRegime());
System.out.println("Hydrate Risk: " + report.getHydrateRisk());
System.out.println("Wax Risk: " + report.getWaxRisk());
System.out.println("Estimated CAPEX: " + report.getCapexMusd() + " MUSD");
System.out.println("NPV: " + report.getNpv() + " MUSD");

// Get tieback options ranked
List<TiebackOption> options = analyzer.rankOptions();
```

---

## 8. Screening Tools

### FlowAssuranceScreener

```java
import neqsim.process.fielddevelopment.screening.*;

FlowAssuranceScreener fa = new FlowAssuranceScreener();

// Configure fluid
fa.setFluid(concept.getFluid());
fa.setWaterCut(0.3);
fa.setGor(200.0);

// Configure flowline
fa.setFlowlineLength(15000.0);
fa.setFlowlineDiameter(0.25);
fa.setAmbientTemperature(4.0);
fa.setInsulationThickness(0.05);

// Run screening
FlowAssuranceReport report = fa.screen();

System.out.println("=== Flow Assurance Screening ===");
System.out.println("Hydrate Formation Temperature: " + report.getHydrateFormationTemp() + " °C");
System.out.println("Wax Appearance Temperature: " + report.getWaxAppearanceTemp() + " °C");
System.out.println("Arrival Temperature: " + report.getArrivalTemperature() + " °C");
System.out.println("Hydrate Margin: " + report.getHydrateMargin() + " °C");
System.out.println("Wax Margin: " + report.getWaxMargin() + " °C");
System.out.println("Scale Risk: " + report.getScaleRisk());
System.out.println("Corrosion Risk: " + report.getCorrosionRisk());

// Get mitigation recommendations
List<String> mitigations = report.getRecommendations();
```

### ArtificialLiftScreener

```java
import neqsim.process.fielddevelopment.screening.*;

ArtificialLiftScreener lift = new ArtificialLiftScreener();

// Configure well conditions
lift.setReservoirPressure(180.0);     // Depleted reservoir
lift.setWaterCut(0.70);
lift.setGor(100.0);
lift.setProductivityIndex(15.0);
lift.setWellDepth(2800.0);
lift.setDeviation(45.0);               // degrees
lift.setTemperature(95.0);
lift.setGasAvailable(true);
lift.setSandProduction(false);
lift.setH2sPresent(false);

// Screen all methods
List<MethodResult> results = lift.screenAllMethods();

for (MethodResult method : results) {
    System.out.printf("%s: %s%n", 
        method.getMethod().name(),
        method.isFeasible() ? 
            String.format("Feasible (Score: %.0f/100)", method.getScore()) :
            "Not feasible - " + method.getRationale());
}

// Get recommended method
LiftMethod recommended = lift.getRecommendedMethod();
System.out.println("Recommended: " + recommended);
```

### EmissionsTracker

```java
import neqsim.process.fielddevelopment.screening.*;

EmissionsTracker emissions = new EmissionsTracker();

// Configure sources
emissions.addPowerGeneration("Gas Turbine", 25.0, 0.35);  // MW, efficiency
emissions.addFlaring(0.5, 0.98);                          // % of gas, combustion eff
emissions.addFugitives(150, 0.001);                       // equipment count, EF
emissions.addVenting(100.0);                               // Sm³/d

// Calculate for production
EmissionsReport report = emissions.calculate(
    50000.0,    // oil rate Sm³/d
    8.0e6,      // gas rate Sm³/d
    365         // days
);

System.out.println("=== Annual Emissions ===");
System.out.println("Power Generation: " + report.getPowerEmissions() + " kt CO2");
System.out.println("Flaring: " + report.getFlaringEmissions() + " kt CO2");
System.out.println("Fugitives: " + report.getFugitiveEmissions() + " kt CO2");
System.out.println("Venting: " + report.getVentingEmissions() + " kt CO2");
System.out.println("Total: " + report.getTotalEmissions() + " kt CO2");
System.out.println("CO2 Intensity: " + report.getCo2Intensity() + " kg/boe");
```

---

## See Also

- [Digital Field Twin Architecture](DIGITAL_FIELD_TWIN.md)
- [Mathematical Reference](MATHEMATICAL_REFERENCE.md)
- [TPG4230 Course Integration](TPG4230_COURSE_INTEGRATION.md)
