---
title: "Compressor Design Feasibility Report"
description: "Guide to assessing whether a centrifugal gas compressor design is realistic to build and operate, including mechanical design, cost estimation, supplier matching, and performance curve generation."
---

# Compressor Design Feasibility Report

The `CompressorDesignFeasibilityReport` class provides a unified assessment that answers: **"Is this compressor realistic to build and operate?"**

It orchestrates four subsystems into a single JSON-based report:

1. **Mechanical design** (API 617) — staging, impeller sizing, tip speed, shaft dynamics, weights
2. **Cost estimation** — purchased equipment, driver, installation, OPEX, 10-year lifecycle
3. **Supplier matching** — which OEMs can build this machine (15 manufacturers in the database)
4. **Performance curves** — realistic compressor maps generated from templates

## Quick Start

```java
// 1. Create and run a compressor
SystemInterface gas = new SystemSrkEos(303.15, 30.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000.0, "kg/hr");

Compressor comp = new Compressor("Export Compressor", feed);
comp.setOutletPressure(120.0);
comp.setPolytropicEfficiency(0.80);
comp.setSpeed(9000);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(comp);
process.run();

// 2. Generate feasibility report
CompressorDesignFeasibilityReport report =
    new CompressorDesignFeasibilityReport(comp);
report.setDriverType("gas-turbine");
report.setCompressorType("centrifugal");
report.setAnnualOperatingHours(8000);
report.generateReport();

// 3. Check results
String verdict = report.getVerdict();  // FEASIBLE, FEASIBLE_WITH_WARNINGS, or NOT_FEASIBLE
boolean feasible = report.isFeasible();
String json = report.toJson();

// 4. Check which suppliers can build it
List<SupplierMatch> suppliers = report.getMatchingSuppliers();
for (SupplierMatch s : suppliers) {
    System.out.println(s.getManufacturer() + " - " + s.getApplications());
}

// 5. Apply generated curves to the compressor for further simulation
report.applyChartToCompressor();
```

## Report Sections

### Operating Point

Captured automatically from the compressor after it has been run:

| Parameter | JSON Key | Unit |
|-----------|----------|------|
| Inlet pressure | `inletPressure_bara` | bara |
| Outlet pressure | `outletPressure_bara` | bara |
| Pressure ratio | `pressureRatio` | - |
| Inlet temperature | `inletTemperature_C` | °C |
| Outlet temperature | `outletTemperature_C` | °C |
| Mass flow | `massFlow_kghr` | kg/hr |
| Volume flow | `volumeFlow_m3hr` | m3/hr |
| Shaft power | `shaftPower_kW` | kW |
| Polytropic head | `polytropicHead_kJkg` | kJ/kg |
| Polytropic efficiency | `polytropicEfficiency` | - |
| Gas molecular weight | `gasMolecularWeight_kgkmol` | kg/kmol |
| Rotational speed | `speed_rpm` | rpm |

### Mechanical Design

Runs `CompressorMechanicalDesign.calcDesign()` to compute API 617-based sizing:

| Parameter | JSON Key | Unit |
|-----------|----------|------|
| Number of stages | `numberOfStages` | - |
| Head per stage | `headPerStage_kJkg` | kJ/kg |
| Impeller diameter | `impellerDiameter_mm` | mm |
| Shaft diameter | `shaftDiameter_mm` | mm |
| Tip speed | `tipSpeed_ms` | m/s |
| Bearing span | `bearingSpan_mm` | mm |
| Casing type | `casingType` | BARREL / HORIZONTALLY_SPLIT / VERTICALLY_SPLIT |
| Driver power (with margin) | `driverPower_kW` | kW |
| Design pressure | `designPressure_bara` | bara |
| Design temperature | `designTemperature_C` | °C |
| Max continuous speed | `maxContinuousSpeed_rpm` | rpm |
| Trip speed | `tripSpeed_rpm` | rpm |
| First critical speed | `firstCriticalSpeed_rpm` | rpm |

#### Weights

| Component | JSON Key | Unit |
|-----------|----------|------|
| Casing | `casingWeight_kg` | kg |
| Rotor | `rotorWeight_kg` | kg |
| Bundle (rotor + internals) | `bundleWeight_kg` | kg |
| Total skid weight | `totalSkidWeight_kg` | kg |

#### Module Dimensions

| Dimension | JSON Key | Unit |
|-----------|----------|------|
| Length | `length_m` | m |
| Width | `width_m` | m |
| Height | `height_m` | m |

### Cost Estimation

Runs `CompressorCostEstimate` with CEPCI-indexed correlations:

| Parameter | JSON Key | Unit |
|-----------|----------|------|
| Purchased equipment cost | `purchasedEquipmentCost_USD` | USD |
| Bare module cost | `bareModuleCost_USD` | USD |
| Total module cost | `totalModuleCost_USD` | USD |
| Specific cost | `specificCost_USDperKW` | USD/kW |
| Annual energy cost | `annualEnergyCost_USD` | USD/yr |
| Annual maintenance cost | `annualMaintenanceCost_USD` | USD/yr |
| Total annual OPEX | `totalAnnualOPEX_USD` | USD/yr |
| 10-year lifecycle cost | `tenYearLifecycleCost_USD` | USD |

Supported driver types: `electric-motor`, `gas-turbine`, `steam-turbine`, `gas-engine`.

Supported compressor types: `centrifugal`, `reciprocating`, `screw`, `axial`.

### Supplier Matching

The report matches the compressor specification against a database of 15 OEMs stored in `CompressorSuppliers.csv`. A supplier is considered a match when its capabilities cover the required power, flow, discharge pressure, number of stages, and pressure ratio per stage.

**Supplier Database (centrifugal manufacturers):**

| Manufacturer | Power Range (kW) | Max Pressure (bara) | Typical Applications |
|-------------|-----------------|--------------------|--------------------|
| Siemens Energy | 500 - 100,000 | 700 | Pipeline, Export, Injection, Refrigeration |
| Baker Hughes | 200 - 80,000 | 600 | Pipeline, Export, Injection, Gas Lift |
| Atlas Copco | 100 - 50,000 | 500 | Process, Booster, Refrigeration |
| MAN Energy Solutions | 300 - 70,000 | 650 | Pipeline, Export, Process, Refrigeration |
| Elliott Group | 150 - 40,000 | 500 | Process, Booster, Refrigeration, Gas Lift |
| MHI | 500 - 120,000 | 800 | LNG, Pipeline, Export, Injection |
| Solar Turbines | 1,000 - 30,000 | 300 | Pipeline, Booster, Gas Lift |
| GE Vernova | 500 - 100,000 | 700 | LNG, Pipeline, Export, Injection |
| Hanwha Power Systems | 200 - 50,000 | 500 | Pipeline, Export, Process |

**Reciprocating/screw manufacturers:** Ariel, Burckhardt Compression, Kobelco, Aerzen.

If no supplier matches, a WARNING issue is raised suggesting a custom or engineered-to-order solution.

### Performance Curves

When `generateCurves` is enabled (default: true), the report generates realistic compressor performance maps using `CompressorChartGenerator` and the selected template.

Available curve templates:

| Template | Use Case |
|----------|----------|
| `CENTRIFUGAL_STANDARD` | General-purpose centrifugal (default) |
| `PIPELINE` | Pipeline/export compressors |
| `EXPORT` | Export gas compressors |
| `INJECTION` | Gas injection compressors |
| `REFRIGERATION` | Refrigeration duty |
| `HIGH_PRESSURE` | High-pressure service |
| `LOW_FLOW` | Low-flow/high-head applications |

After generating the report, you can apply the curves to the compressor for further simulation:

```java
// Apply generated curves
report.applyChartToCompressor();

// Now the compressor uses chart-based calculations
// Useful for simulating varying conditions (e.g. declining reservoir pressure)
comp.run();
```

## Feasibility Checks

The report runs eight automated feasibility checks. Each check can produce issues at three severity levels:

| Severity | Meaning | Effect |
|----------|---------|--------|
| `BLOCKER` | Design is not feasible as specified | Sets verdict to `NOT_FEASIBLE` |
| `WARNING` | Feasible but should be reviewed | Sets verdict to `FEASIBLE_WITH_WARNINGS` |
| `INFO` | Informational note | No impact on verdict |

### Check Details

| Check | Category | Blocker Condition | Warning Condition |
|-------|----------|-------------------|-------------------|
| Discharge temperature | `TEMPERATURE` | Exceeds max (API 617 limit) | Within 10% of max |
| Pressure ratio/stage | `PRESSURE_RATIO` | Exceeds max per stage | - |
| Impeller tip speed | `TIP_SPEED` | Exceeds 350 m/s (steel limit) | Above 315 m/s |
| Power range | `POWER` | - | Below 50 kW or above 100 MW |
| Flow range | `FLOW` | - | Below 100 m3/hr or above 500,000 m3/hr |
| Rotor dynamics | `ROTOR_DYNAMICS` | - | Operating speed within 15% of critical |
| Efficiency | `EFFICIENCY` | - | Above 90% or below 65% |
| Cost reasonableness | `COST` | - | Specific cost above 5,000 or below 100 USD/kW |

## Verdicts

| Verdict | Meaning |
|---------|---------|
| `FEASIBLE` | No blockers and no warnings — machine can be built as specified |
| `FEASIBLE_WITH_WARNINGS` | No blockers but some issues to review |
| `NOT_FEASIBLE` | One or more blockers — design changes needed |

## Configuration (Fluent API)

All setters return `this` for method chaining:

```java
CompressorDesignFeasibilityReport report =
    new CompressorDesignFeasibilityReport(comp)
        .setDriverType("gas-turbine")
        .setCompressorType("centrifugal")
        .setAnnualOperatingHours(8000)
        .setElectricityRate(0.12)
        .setFuelRate(6.0)
        .setGenerateCurves(true)
        .setCurveTemplate("PIPELINE")
        .setNumberOfSpeedCurves(7);

report.generateReport();
```

| Method | Default | Description |
|--------|---------|-------------|
| `setDriverType(String)` | `"electric-motor"` | Driver type for cost estimation |
| `setCompressorType(String)` | `"centrifugal"` | Compressor type for supplier matching and cost |
| `setAnnualOperatingHours(double)` | `8000` | Hours/year for OPEX calculation |
| `setElectricityRate(double)` | `0.10` | USD/kWh for electric motor operating cost |
| `setFuelRate(double)` | `5.0` | USD/GJ for gas turbine/engine fuel cost |
| `setGenerateCurves(boolean)` | `true` | Whether to generate performance curves |
| `setCurveTemplate(String)` | `"CENTRIFUGAL_STANDARD"` | Curve template to use |
| `setNumberOfSpeedCurves(int)` | `5` | Number of speed curves in the chart |

## JSON Output Structure

The `toJson()` method returns a comprehensive JSON report:

```json
{
  "verdict": "FEASIBLE_WITH_WARNINGS",
  "feasible": true,
  "operatingPoint": {
    "inletPressure_bara": 30.0,
    "outletPressure_bara": 120.0,
    "pressureRatio": 4.0,
    "shaftPower_kW": 5230.5,
    "polytropicEfficiency": 0.80
  },
  "mechanicalDesign": {
    "numberOfStages": 3,
    "impellerDiameter_mm": 450,
    "tipSpeed_ms": 285.3,
    "casingType": "BARREL",
    "weights": {
      "casingWeight_kg": 4200,
      "rotorWeight_kg": 380,
      "bundleWeight_kg": 520,
      "totalSkidWeight_kg": 12500
    },
    "moduleDimensions": {
      "length_m": 8.5,
      "width_m": 4.2,
      "height_m": 3.8
    }
  },
  "costEstimation": {
    "totalModuleCost_USD": 4850000,
    "specificCost_USDperKW": 927,
    "annualOperatingCost": {
      "totalAnnualOPEX_USD": 625000
    },
    "tenYearLifecycleCost_USD": 11100000
  },
  "matchingSuppliers": [
    {
      "manufacturer": "Siemens Energy",
      "applications": "Pipeline;Export;Injection;Refrigeration"
    }
  ],
  "numberOfMatchingSuppliers": 5,
  "compressorCurves": {
    "generated": true,
    "template": "CENTRIFUGAL_STANDARD",
    "numberOfSpeedCurves": 5
  },
  "issues": [
    {
      "severity": "WARNING",
      "category": "TIP_SPEED",
      "message": "Impeller tip speed 320 m/s is near material limit."
    }
  ]
}
```

## Python (Jupyter Notebook) Usage

```python
from neqsim import jneqsim

# Create fluid and compressor
fluid = jneqsim.thermo.system.SystemSrkEos(303.15, 30.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

feed = jneqsim.process.equipment.stream.Stream("feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")

comp = jneqsim.process.equipment.compressor.Compressor("Export Comp", feed)
comp.setOutletPressure(120.0)
comp.setPolytropicEfficiency(0.80)
comp.setSpeed(9000)

process = jneqsim.process.processmodel.ProcessSystem()
process.add(feed)
process.add(comp)
process.run()

# Generate feasibility report
Report = jneqsim.process.mechanicaldesign.compressor.CompressorDesignFeasibilityReport
report = Report(comp)
report.setDriverType("gas-turbine")
report.setCompressorType("centrifugal")
report.generateReport()

print("Verdict:", report.getVerdict())
print("Feasible:", report.isFeasible())
print("Matching suppliers:", report.getMatchingSuppliers().size())

# Parse JSON for detailed results
import json
results = json.loads(report.toJson())
print(json.dumps(results, indent=2))
```

## Related Documentation

- [Compressor Mechanical Design](CompressorMechanicalDesign.md) — API 617 staging, impeller sizing, weights
- [Compressor Models](equipment/compressors.md) — Basic compressor simulation
- [Compressor Performance Curves](equipment/compressor_curves.md) — Chart-based operation
- [Cost Estimation Framework](COST_ESTIMATION_FRAMEWORK.md) — CEPCI-indexed cost correlations
- [Compressor Optimization Guide](optimization/COMPRESSOR_OPTIMIZATION_GUIDE.md) — Anti-surge, power minimization

## Key Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `CompressorDesignFeasibilityReport` | `process.mechanicaldesign.compressor` | Unified feasibility assessment |
| `CompressorDesignFeasibilityReport.SupplierMatch` | (inner class) | Supplier capability data |
| `CompressorDesignFeasibilityReport.FeasibilityIssue` | (inner class) | Issue with severity |
| `CompressorDesignFeasibilityReport.IssueSeverity` | (inner enum) | INFO, WARNING, BLOCKER |
| `CompressorMechanicalDesign` | `process.mechanicaldesign.compressor` | API 617 mechanical design |
| `CompressorCostEstimate` | `process.costestimation.compressor` | Cost estimation with CEPCI |
| `CompressorChartGenerator` | `process.equipment.compressor` | Chart generation from templates |

## References

1. API 617, 8th Edition — Axial and Centrifugal Compressors
2. Bloch, H.P. — "A Practical Guide to Compressor Technology"
3. Japikse, D. — "Centrifugal Compressor Design and Performance"
4. Lüdtke, K.H. — "Process Centrifugal Compressors"
