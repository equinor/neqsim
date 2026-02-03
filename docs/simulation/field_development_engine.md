---
title: Field Development Engine
description: The Field Development Engine is a rapid concept screening toolkit within NeqSim designed to accelerate early-phase field development decisions. It enables engineers to quickly evaluate multiple develo...
---

# Field Development Engine

The Field Development Engine is a rapid concept screening toolkit within NeqSim designed to accelerate early-phase field development decisions. It enables engineers to quickly evaluate multiple development concepts, comparing technical feasibility, economics, emissions, and safety aspects in hours rather than weeks.

## Overview

### Purpose

Traditional field development concept screening involves:
- Weeks of manual process simulation setup
- Separate tools for flow assurance, economics, and emissions
- Difficulty comparing many alternatives quickly
- Inconsistent assumptions across studies

The Field Development Engine addresses these challenges by providing:
- **Rapid Concept Definition**: Builder-pattern APIs for defining reservoirs, wells, and infrastructure
- **Automated Facility Generation**: Process blocks that auto-configure based on fluid properties
- **Integrated Screening**: Flow assurance, safety, economics, and emissions in one workflow
- **Batch Evaluation**: Run hundreds of concepts with sensitivity analysis

### Architecture

The engine is organized into four packages:

```
neqsim.process.fielddevelopment
├── concept/       # Input data structures (reservoir, wells, infrastructure)
├── facility/      # Process block configuration and facility builder
├── screening/     # Technical screeners (flow assurance, safety, economics, emissions)
└── evaluation/    # Concept evaluation and batch processing
```

## Quick Start

### Basic Concept Definition

```java
import neqsim.process.fielddevelopment.concept.*;
import neqsim.process.fielddevelopment.evaluation.*;

// Define reservoir properties
ReservoirInput reservoir = ReservoirInput.builder()
    .fluidType(FluidType.RICH_GAS)
    .reservoirTempC(85.0)
    .reservoirPressureBara(350.0)
    .co2Percent(3.5)
    .h2sPercent(0.0)
    .waterCutPercent(5.0)
    .gor(5000.0)  // Sm3/Sm3
    .build();

// Define well configuration
WellsInput wells = WellsInput.builder()
    .producerCount(4)
    .injectorCount(2)
    .ratePerWellSm3d(500000.0)  // 0.5 MSm3/d per well
    .tubeheadPressure(120.0)    // bara
    .build();

// Define infrastructure
InfrastructureInput infrastructure = InfrastructureInput.builder()
    .processingLocation(ProcessingLocation.PLATFORM)
    .exportType(ExportType.PIPELINE_GAS)
    .tiebackLengthKm(25.0)
    .waterDepthM(120.0)
    .powerSource(PowerSource.GAS_TURBINE)
    .build();

// Create field concept
FieldConcept concept = FieldConcept.builder()
    .name("Platform Concept A")
    .reservoir(reservoir)
    .wells(wells)
    .infrastructure(infrastructure)
    .build();
```

### Running Concept Evaluation

```java
// Create evaluator and run
ConceptEvaluator evaluator = new ConceptEvaluator();
ConceptKPIs kpis = evaluator.evaluate(concept);

// Access results
System.out.println("Flow Assurance: " + kpis.getFlowAssuranceReport().getSummary());
System.out.println("Total CAPEX: " + kpis.getEconomicsReport().getTotalCapexMUSD() + " MUSD");
System.out.println("CO2 Intensity: " + kpis.getEmissionsReport().getCo2IntensityKgPerBoe() + " kg/boe");
System.out.println("Safety Grade: " + kpis.getSafetyReport().getOverallGrade());
```

## Detailed Usage

### Reservoir Input

The `ReservoirInput` class captures fluid and reservoir properties:

| Property | Type | Description |
|----------|------|-------------|
| `fluidType` | `FluidType` | LEAN_GAS, RICH_GAS, GAS_CONDENSATE, VOLATILE_OIL, BLACK_OIL, HEAVY_OIL |
| `reservoirTempC` | double | Reservoir temperature (°C) |
| `reservoirPressureBara` | double | Initial reservoir pressure (bara) |
| `co2Percent` | double | CO2 content (mol%) |
| `h2sPercent` | double | H2S content (mol%) |
| `waterCutPercent` | double | Initial water cut (%) |
| `gor` | double | Gas-oil ratio (Sm3/Sm3) |

```java
// High CO2 gas field example
ReservoirInput highCO2Gas = ReservoirInput.builder()
    .fluidType(FluidType.LEAN_GAS)
    .reservoirTempC(95.0)
    .reservoirPressureBara(400.0)
    .co2Percent(15.0)  // High CO2 requiring removal
    .h2sPercent(0.5)   // Some H2S
    .waterCutPercent(0.0)
    .gor(Double.POSITIVE_INFINITY)  // Dry gas
    .build();
```

### Wells Input

The `WellsInput` class defines well count and deliverability:

| Property | Type | Description |
|----------|------|-------------|
| `producerCount` | int | Number of production wells |
| `injectorCount` | int | Number of injection wells (water/gas) |
| `ratePerWellSm3d` | double | Production rate per well (Sm3/d) |
| `tubeheadPressure` | double | Wellhead pressure (bara) |

```java
// High-rate gas wells
WellsInput highRateGas = WellsInput.builder()
    .producerCount(6)
    .injectorCount(0)  // No injection
    .ratePerWellSm3d(2000000.0)  // 2 MSm3/d per well
    .tubeheadPressure(150.0)
    .build();
```

### Infrastructure Input

The `InfrastructureInput` class defines facility type and export route:

| Property | Type | Description |
|----------|------|-------------|
| `processingLocation` | `ProcessingLocation` | PLATFORM, FPSO, SUBSEA, ONSHORE |
| `exportType` | `ExportType` | PIPELINE_GAS, PIPELINE_OIL, LNG, SHUTTLE_TANKER |
| `tiebackLengthKm` | double | Distance to host/shore (km) |
| `waterDepthM` | double | Water depth (m) |
| `powerSource` | `PowerSource` | GAS_TURBINE, POWER_FROM_SHORE, HYBRID |

```java
// Deep water FPSO with shuttle tanker
InfrastructureInput deepwaterFPSO = InfrastructureInput.builder()
    .processingLocation(ProcessingLocation.FPSO)
    .exportType(ExportType.SHUTTLE_TANKER)
    .tiebackLengthKm(5.0)  // Short subsea tieback to FPSO
    .waterDepthM(1200.0)   // Deep water
    .powerSource(PowerSource.GAS_TURBINE)
    .build();
```

### Facility Configuration (Optional)

For more detailed estimates, you can define specific process blocks:

```java
import neqsim.process.fielddevelopment.facility.*;

FacilityConfig facility = FacilityBuilder.builder()
    .addBlock(BlockConfig.of(BlockType.INLET_SEPARATION))
    .addBlock(BlockConfig.of(BlockType.THREE_PHASE_SEPARATOR))
    .addBlock(BlockConfig.of(BlockType.CO2_REMOVAL_AMINE)
        .withParameter("capacity_mmscfd", 200.0))
    .addBlock(BlockConfig.of(BlockType.TEG_DEHYDRATION))
    .addBlock(BlockConfig.of(BlockType.COMPRESSION)
        .withParameter("stages", 3))
    .addBlock(BlockConfig.of(BlockType.FLARE_SYSTEM))
    .build();

// Use facility in evaluation
ConceptKPIs kpis = evaluator.evaluate(concept, facility);
```

### Available Block Types

| Block Type | Description | Typical CAPEX (MUSD) |
|------------|-------------|---------------------|
| `INLET_SEPARATION` | Inlet slug catcher/separator | 20 |
| `TWO_PHASE_SEPARATOR` | Gas-liquid separation | 20 |
| `THREE_PHASE_SEPARATOR` | Oil-water-gas separation | 20 |
| `COMPRESSION` | Gas compression (per stage) | 40 |
| `TEG_DEHYDRATION` | Glycol dehydration | 35 |
| `CO2_REMOVAL_AMINE` | Amine-based CO2 removal | 120 |
| `CO2_REMOVAL_MEMBRANE` | Membrane CO2 removal | 80 |
| `H2S_REMOVAL` | Sulfur recovery/scavenging | 60 |
| `NGL_RECOVERY` | NGL extraction | 100 |
| `OIL_STABILIZATION` | Crude stabilization | 30 |
| `WATER_TREATMENT` | Produced water treatment | 25 |
| `SUBSEA_BOOSTING` | Subsea multiphase pumping | 150 |
| `POWER_GENERATION` | Gas turbine power generation | 100 |
| `FLARE_SYSTEM` | Emergency flare system | 20 |

## Screening Reports

### Flow Assurance Report

Evaluates hydrate, wax, corrosion, and other flow assurance risks:

```java
FlowAssuranceReport fa = kpis.getFlowAssuranceReport();

// Check hydrate risk
if (fa.getHydrateResult() == FlowAssuranceResult.FAIL) {
    System.out.println("Hydrate formation temp: " + fa.getHydrateFormationTemp() + "°C");
    System.out.println("Margin to operating temp: " + fa.getHydrateMargin() + "°C");
}

// Get mitigation recommendations
fa.getRecommendations().forEach((category, recommendation) -> {
    System.out.println(category + ": " + recommendation);
});

// Get mitigation options
fa.getMitigationOptions().forEach((id, description) -> {
    System.out.println("  Option: " + description);
});
```

### Economics Report

Provides CAPEX/OPEX estimates with ±40% accuracy (AACE Class 5):

```java
EconomicsEstimator.EconomicsReport econ = kpis.getEconomicsReport();

System.out.println("Total CAPEX: " + econ.getTotalCapexMUSD() + " MUSD");
System.out.println("  Range: " + econ.getCapexLowMUSD() + " - " + econ.getCapexHighMUSD());
System.out.println("Annual OPEX: " + econ.getAnnualOpexMUSD() + " MUSD/year");
System.out.println("CAPEX per boe: " + econ.getCapexPerBoeUSD() + " USD/boe");

// CAPEX breakdown
econ.getCapexBreakdown().forEach((category, cost) -> {
    System.out.println("  " + category + ": " + cost + " MUSD");
});
```

### Emissions Report

Tracks CO2 emissions and intensity:

```java
EmissionsTracker.EmissionsReport emissions = kpis.getEmissionsReport();

System.out.println("Annual CO2: " + emissions.getAnnualCO2TonnesPerYear() + " tonnes/year");
System.out.println("CO2 Intensity: " + emissions.getCo2IntensityKgPerBoe() + " kg/boe");
System.out.println("Power Source: " + emissions.getPowerSource());

// Emissions breakdown
emissions.getEmissionsBreakdown().forEach((source, tonnes) -> {
    System.out.println("  " + source + ": " + tonnes + " tonnes/year");
});
```

### Safety Report

Assesses safety considerations:

```java
SafetyScreener.SafetyReport safety = kpis.getSafetyReport();

System.out.println("Overall Grade: " + safety.getOverallGrade());
System.out.println("ESD Complexity: " + safety.getEsdComplexity());
System.out.println("Fire Protection Grade: " + safety.getFireProtectionGrade());
System.out.println("Manned Status: " + (safety.isNormallyManned() ? "Manned" : "Unmanned"));

// Safety recommendations
safety.getRecommendations().forEach(rec -> {
    System.out.println("  - " + rec);
});
```

## Batch Processing

### Comparing Multiple Concepts

```java
import neqsim.process.fielddevelopment.evaluation.BatchConceptRunner;

// Create multiple concepts
List<FieldConcept> concepts = Arrays.asList(
    createPlatformConcept(),
    createFPSOConcept(),
    createSubseaConcept()
);

// Run batch evaluation
BatchConceptRunner runner = new BatchConceptRunner();
Map<String, ConceptKPIs> results = runner.runAll(concepts);

// Compare results
results.forEach((name, kpis) -> {
    System.out.printf("%s: CAPEX=%.0f MUSD, CO2=%.1f kg/boe%n",
        name,
        kpis.getEconomicsReport().getTotalCapexMUSD(),
        kpis.getEmissionsReport().getCo2IntensityKgPerBoe());
});

// Get ranked results
List<ConceptKPIs> rankedByCAPEX = runner.rankBy(results, 
    kpis -> kpis.getEconomicsReport().getTotalCapexMUSD());
```

### Sensitivity Analysis

```java
// Create base concept
FieldConcept baseConcept = createBaseConcept();

// Define parameter ranges
double[] waterDepths = {100, 300, 500, 800, 1200};
double[] co2Levels = {2.0, 5.0, 10.0, 15.0};

// Run sensitivities
for (double depth : waterDepths) {
    for (double co2 : co2Levels) {
        FieldConcept variant = baseConcept.toBuilder()
            .infrastructure(baseConcept.getInfrastructure().toBuilder()
                .waterDepthM(depth)
                .build())
            .reservoir(baseConcept.getReservoir().toBuilder()
                .co2Percent(co2)
                .build())
            .name("Depth=" + depth + "m, CO2=" + co2 + "%")
            .build();
        
        ConceptKPIs kpis = evaluator.evaluate(variant);
        // Store/analyze results...
    }
}
```

## Integration with NeqSim Process Simulation

The Field Development Engine integrates with NeqSim's full process simulation capabilities:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;

// Generate facility from concept
FacilityBuilder facilityBuilder = new FacilityBuilder();
ProcessSystem processSystem = facilityBuilder.buildProcessSystem(concept);

// Run detailed simulation
processSystem.run();

// Access detailed results
Stream exportStream = (Stream) processSystem.getUnit("export");
double exportRate = exportStream.getFlowRate("MSm3/day");
double exportPressure = exportStream.getPressure("bara");
```

## Cost Estimation Methodology

### CAPEX Basis

The economics estimator uses screening-level cost factors:

| Category | Basis | Notes |
|----------|-------|-------|
| Platform | 400 MUSD base | Adjusted for water depth |
| FPSO | 800 MUSD base | Adjusted for water depth |
| Subsea template | 100 MUSD each | Per wellhead cluster |
| Platform wells | 50 MUSD each | Includes completions |
| Subsea wells | 100 MUSD each | Includes trees and controls |
| Pipeline | 2 MUSD/km | Varies with diameter |
| Umbilical | 1.5 MUSD/km | For subsea systems |

### Depth Factor

Water depth increases costs according to:
```
depthFactor = 1.0 + (waterDepth / 500m) × 0.5
```

For example:
- 100m depth: factor = 1.1
- 500m depth: factor = 1.5
- 1000m depth: factor = 2.0

### Accuracy

All cost estimates carry ±40% accuracy (AACE Class 5), appropriate for:
- Concept screening
- Alternative comparison
- Order-of-magnitude budgeting

For FEED-level estimates (±20%), use detailed process simulation and vendor quotes.

## Emissions Methodology

### Sources Tracked

1. **Fuel gas combustion**: Based on power demand and turbine efficiency
2. **Flaring**: Calculated from upset/safety flaring estimates
3. **Fugitive emissions**: 0.01% of hydrocarbon throughput (industry typical)
4. **Venting**: Based on process configuration

### CO2 Intensity Calculation

```
CO2 Intensity (kg/boe) = Annual CO2 (tonnes) × 1000 / Annual Production (boe)
```

### Power Source Impact

| Power Source | Emission Factor |
|--------------|-----------------|
| Gas turbine | ~50 kg CO2/MWh (depends on efficiency) |
| Power from shore | 50 kg CO2/MWh (Norwegian grid) |
| Hybrid | Weighted average |

## Best Practices

### 1. Start Simple

Begin with basic concept definition and add detail as needed:

```java
// Minimal concept for initial screening
FieldConcept simple = FieldConcept.builder()
    .name("Quick Screen")
    .reservoir(ReservoirInput.builder()
        .fluidType(FluidType.LEAN_GAS)
        .co2Percent(5.0)
        .build())
    .build();

ConceptKPIs kpis = evaluator.quickEvaluate(simple);
```

### 2. Use Consistent Assumptions

When comparing concepts, ensure consistent:
- Reservoir properties (same fluid for all concepts)
- Economic assumptions (same cost year basis)
- Operating philosophy (same manning assumptions)

### 3. Document Deviations

Track any manual overrides or custom assumptions:

```java
FieldConcept concept = FieldConcept.builder()
    .name("Concept A - Modified")
    .description("Base case with reduced compression due to high reservoir pressure")
    // ... other properties
    .build();
```

### 4. Validate Against Benchmarks

Compare screening results against:
- Recent project sanctions in similar environments
- Industry cost databases (IHS, Wood Mackenzie)
- Operator internal benchmarks

## Troubleshooting

### Common Issues

**1. "Table COMP not found" error**

Ensure the thermodynamic system has database initialized:
```java
fluid.setMixingRule("classic");
fluid.createDatabase(true);  // Required!
```

**2. Hydrate calculation fails**

This typically occurs with unusual compositions. The screener falls back to correlation-based estimates and flags for detailed analysis.

**3. Negative margins in flow assurance**

A negative margin indicates operating conditions are within the risk envelope. This is flagged as FAIL with mandatory mitigation.

### Debug Mode

Enable detailed logging for troubleshooting:

```java
// Set log level for field development package
Logger logger = LogManager.getLogger("neqsim.process.fielddevelopment");
Configurator.setLevel(logger.getName(), Level.DEBUG);
```

## API Reference

### Package: `neqsim.process.fielddevelopment.concept`

| Class | Description |
|-------|-------------|
| `FieldConcept` | Main concept container with reservoir, wells, infrastructure |
| `ReservoirInput` | Fluid and reservoir properties |
| `WellsInput` | Well count and deliverability |
| `InfrastructureInput` | Facility type and export route |

### Package: `neqsim.process.fielddevelopment.facility`

| Class | Description |
|-------|-------------|
| `FacilityBuilder` | Constructs facility configurations |
| `FacilityConfig` | Immutable facility configuration |
| `BlockConfig` | Individual process block configuration |
| `BlockType` | Enumeration of available process blocks |

### Package: `neqsim.process.fielddevelopment.screening`

| Class | Description |
|-------|-------------|
| `FlowAssuranceScreener` | Hydrate, wax, corrosion screening |
| `FlowAssuranceReport` | Flow assurance results and recommendations |
| `FlowAssuranceResult` | PASS/MARGINAL/FAIL classification |
| `EconomicsEstimator` | CAPEX/OPEX estimation |
| `EmissionsTracker` | CO2 emissions calculation |
| `SafetyScreener` | Safety assessment |

### Package: `neqsim.process.fielddevelopment.evaluation`

| Class | Description |
|-------|-------------|
| `ConceptEvaluator` | Main evaluation orchestrator |
| `ConceptKPIs` | Aggregated KPIs from all screeners |
| `BatchConceptRunner` | Parallel batch processing |

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12 | Initial release with core screening capabilities |

## References

- AACE International Recommended Practice No. 18R-97: Cost Estimate Classification System
- NORSOK P-002: Process System Design
- NACE MR0175/ISO 15156: Materials for Sour Service
- API RP 14C: Recommended Practice for Analysis, Design, Installation, and Testing of Safety Systems

## Contributing

See [CONTRIBUTING.md](../development/contributing-structure.md) for guidelines on contributing to the Field Development Engine.

For questions or feature requests, open an issue on the [NeqSim GitHub repository](https://github.com/equinor/neqsim).
