---
title: "Pipeline Corrosion Integration"
description: "How to run NORSOK M-506 corrosion analysis and M-001 material selection integrated with pipeline mechanical design in NeqSim. Covers automatic stream extraction, convenience methods, and combined wall thickness + corrosion design."
---

# Pipeline Corrosion Integration

The NORSOK corrosion analysis module integrates directly with NeqSim's pipeline mechanical design system. After running a pipeline simulation, you can automatically extract operating conditions from the process stream and run a complete corrosion assessment.

## Architecture

```
Pipeline / PipeBeggsAndBrills / AdiabaticPipe
  └── PipelineMechanicalDesign
        ├── NorsokM506CorrosionRate  ← CO2 corrosion rate
        ├── NorsokM001MaterialSelection  ← Material & CA
        └── PipeMechanicalDesignCalculator  ← Wall thickness with CA
```

### Data Flow

1. `Pipeline.runCorrosionAnalysis()` delegates to `PipelineMechanicalDesign`
2. `PipelineMechanicalDesign.runCorrosionAnalysis()` extracts from the outlet stream:
   - Temperature (°C) and pressure (bara)
   - CO2 and H2S mole fractions from gas phase
   - Liquid density and viscosity (after `initPhysicalProperties()`)
   - Flow velocity from flow rate and pipe diameter
3. Runs `NorsokM506CorrosionRate.calculate()`
4. Feeds results to `NorsokM001MaterialSelection.evaluate()`
5. Applies the resulting corrosion allowance (mm) to the wall thickness calculator

## Usage via Pipeline Convenience Methods

The simplest approach — call methods directly on the pipeline equipment:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.processmodel.ProcessSystem;

// Create fluid with CO2
SystemSrkEos fluid = new SystemSrkEos(273.15 + 60.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("H2S", 0.001);
fluid.addComponent("water", 0.069);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("export pipeline", feed);
pipe.setLength(50000.0);      // 50 km
pipe.setDiameter(0.254);      // 10 inch
pipe.setAngle(0.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(pipe);
process.run();

// Configure corrosion parameters
pipe.setDesignLifeYears(25);
pipe.setInhibitorEfficiency(0.0);  // No inhibitor
pipe.setGlycolWeightFraction(0.0);

// Run corrosion analysis
pipe.runCorrosionAnalysis();

// Read results
System.out.println("Corrosion rate: " + pipe.getCorrosionRate() + " mm/yr");
System.out.println("Material: " + pipe.getRecommendedMaterial());
System.out.println("CA: " + pipe.getRecommendedCorrosionAllowanceMm() + " mm");
```

### Pipeline Convenience Methods

| Method | Description |
|--------|-------------|
| `runCorrosionAnalysis()` | Run full M-506 + M-001 analysis from stream data |
| `setDesignLifeYears(double)` | Set design life (default 25 years) |
| `setInhibitorEfficiency(double)` | Set inhibitor efficiency (0.0 – 1.0) |
| `setGlycolWeightFraction(double)` | Set glycol fraction (0.0 – 1.0) |
| `getCorrosionRate()` | Get calculated rate (mm/yr) |
| `getRecommendedMaterial()` | Get material recommendation |
| `getRecommendedCorrosionAllowanceMm()` | Get CA (mm) |

## Usage via PipelineMechanicalDesign

For more control, access the mechanical design directly:

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipeline", feed);
// ... configure and run pipeline ...

pipe.initMechanicalDesign();
PipelineMechanicalDesign design =
    (PipelineMechanicalDesign) pipe.getMechanicalDesign();

// Configure corrosion parameters
design.setDesignLifeYears(25);
design.setInhibitorEfficiency(0.80);
design.setGlycolWeightFraction(0.0);

// Run corrosion analysis
design.runCorrosionAnalysis();

// Access the models directly for detailed results
NorsokM506CorrosionRate corrosionModel = design.getCorrosionModel();
NorsokM001MaterialSelection materialSelector = design.getMaterialSelector();

System.out.println("pH: " + corrosionModel.getCalculatedPH());
System.out.println("Fugacity: " + corrosionModel.getCO2FugacityBar() + " bar");
System.out.println("Sour: " + corrosionModel.isSourService());
System.out.println("SCC risk: " + materialSelector.getChlorideSCCRisk());

// JSON reports
System.out.println(corrosionModel.toJson());
System.out.println(materialSelector.toJson());
```

### PipelineMechanicalDesign Methods

| Method | Description |
|--------|-------------|
| `getCorrosionModel()` | Access `NorsokM506CorrosionRate` instance |
| `getMaterialSelector()` | Access `NorsokM001MaterialSelection` instance |
| `runCorrosionAnalysis()` | Auto-populate from stream and run both models |
| `setDesignLifeYears(double)` | Design life for CA calculation |
| `setInhibitorEfficiency(double)` | Inhibitor efficiency (0–1) |
| `setGlycolWeightFraction(double)` | Glycol weight fraction (0–1) |
| `getCorrosionRate()` | Corrected rate (mm/yr) |
| `getRecommendedMaterial()` | Material recommendation |
| `getRecommendedCorrosionAllowanceMm()` | CA (mm) |

## Combined Mechanical + Corrosion Design

Run corrosion analysis alongside wall thickness sizing:

```java
pipe.initMechanicalDesign();
PipelineMechanicalDesign design =
    (PipelineMechanicalDesign) pipe.getMechanicalDesign();

// Set mechanical design parameters
design.setMaxOperationPressure(100.0);
design.setMaterialGrade("X65");
design.setDesignStandardCode("DNV-OS-F101");

// Run corrosion analysis first (sets CA on the calculator)
design.setDesignLifeYears(25);
design.runCorrosionAnalysis();

// Now run mechanical design (uses the corrosion-derived CA)
design.calcDesign();

// Wall thickness includes corrosion allowance
String report = design.toJson();
```

## Effect on Inhibitor

Compare uninhibited vs. inhibited corrosion rates:

```java
// Without inhibitor
pipe.setInhibitorEfficiency(0.0);
pipe.runCorrosionAnalysis();
double uninhibitedRate = pipe.getCorrosionRate();

// With 80% inhibitor
pipe.setInhibitorEfficiency(0.80);
pipe.runCorrosionAnalysis();
double inhibitedRate = pipe.getCorrosionRate();

System.out.println("Uninhibited: " + uninhibitedRate + " mm/yr");
System.out.println("Inhibited: " + inhibitedRate + " mm/yr");
System.out.println("Reduction: " + (1 - inhibitedRate/uninhibitedRate)*100 + " %");
```

## Python Example

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create fluid
fluid = SystemSrkEos(273.15 + 60.0, 100.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("CO2", 0.03)
fluid.addComponent("water", 0.12)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

feed = Stream("feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")

pipe = PipeBeggsAndBrills("pipeline", feed)
pipe.setLength(50000.0)
pipe.setDiameter(0.254)

process = ProcessSystem()
process.add(feed)
process.add(pipe)
process.run()

# Run corrosion analysis
pipe.setDesignLifeYears(25)
pipe.runCorrosionAnalysis()

print(f"Rate: {pipe.getCorrosionRate():.2f} mm/yr")
print(f"Material: {pipe.getRecommendedMaterial()}")
print(f"CA: {pipe.getRecommendedCorrosionAllowanceMm():.1f} mm")
```

## Related

- [Corrosion Module Overview](index) — Package overview
- [NorsokM506CorrosionRate](norsok_m506_corrosion_rate) — Standalone corrosion rate API
- [NorsokM001MaterialSelection](norsok_m001_material_selection) — Standalone material selection API
- [Pipeline Mechanical Design](../pipeline_mechanical_design) — Wall thickness, stress analysis, cost estimation
