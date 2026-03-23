---
title: "CO2 Injection Well Analysis Module"
description: "Classes for CO2 injection well safety analysis: wellbore flow simulation with formation temperature gradients, impurity monitoring, shutdown transient simulation, CO2-specific flow corrections, and integrated safety assessment."
---

# CO2 Injection Well Analysis Module

Five new classes provide an integrated analysis workflow for CO2 injection wells
where impurity enrichment (hydrogen, nitrogen, methane) in the gas phase during
two-phase conditions is a safety concern.

## Class Overview

| Class | Package | Purpose |
|-------|---------|---------|
| `CO2InjectionWellAnalyzer` | `process.equipment.pipeline` | High-level orchestrator — runs all analyses and returns safety verdict |
| `ImpurityMonitor` | `process.measurementdevice` | Phase-partitioned composition tracking with alarm thresholds |
| `TransientWellbore` | `process.equipment.pipeline` | Shutdown cooling simulation (exponential decay to geothermal profile) |
| `CO2FlowCorrections` | `process.equipment.pipeline` | Static utility — CO2-specific holdup and friction correction factors |
| `PipeBeggsAndBrills` | `process.equipment.pipeline` | Enhanced with formation temperature gradient support (NIP-1) |

## Architecture

```
CO2InjectionWellAnalyzer (orchestrator)
├── PipeBeggsAndBrills (steady-state wellbore flow)
│   ├── Formation Temperature Gradient (NIP-1)
│   └── CO2FlowCorrections (NIP-4)
├── ImpurityMonitor (NIP-2)
│   └── Phase-partitioned K-value tracking
├── TransientWellbore (NIP-3)
│   └── Shutdown cooling + phase evolution
└── ThrottlingValve (choke JT analysis)
```

---

## CO2InjectionWellAnalyzer

**Package:** `neqsim.process.equipment.pipeline`

Combines steady-state wellbore simulation, phase boundary scanning,
impurity enrichment mapping, shutdown assessment, and choke JT analysis
into a single analysis tool.

### API

```java
CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("InjectionWell-1");

// Configure
analyzer.setFluid(co2Fluid);
analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5);        // depth, ID, roughness [m]
analyzer.setOperatingConditions(90.0, 25.0, 150000.0);    // WHP [bara], WHT [C], flow [kg/hr]
analyzer.setFormationTemperature(4.0, 43.0);               // top C, bottom C
analyzer.addTrackedComponent("hydrogen", 0.04);            // name, alarm mol frac

// Run
analyzer.runFullAnalysis();

// Results
boolean safe = analyzer.isSafeToOperate();
Map<String, Object> results = analyzer.getResults();
```

### Results Map Keys

| Key | Type | Description |
|-----|------|-------------|
| `safe_to_operate` | Boolean | Overall safety verdict |
| `design_BHP_bara` | Double | Bottom-hole pressure at design rate |
| `design_BHT_C` | Double | Bottom-hole temperature at design rate |
| `min_safe_WHP_bara` | Double | Minimum wellhead pressure for single-phase |
| `two_phase_conditions` | List | P-T points where two phases form |
| `max_impurity_concentrations` | Map | Peak gas-phase concentrations per component |
| `shutdown_safe` | Boolean | Whether shutdown at design WHP is safe |

---

## ImpurityMonitor

**Package:** `neqsim.process.measurementdevice`

A measurement device that reads from a connected stream and tracks the
phase-partitioned composition of selected impurity components. Reports
enrichment factors (K-values) and triggers alarms when gas-phase
concentrations exceed configurable thresholds.

### API

```java
ImpurityMonitor monitor = new ImpurityMonitor("WH-H2-Monitor", stream);
monitor.addTrackedComponent("hydrogen", 0.04);   // alarm at 4 mol%
monitor.addTrackedComponent("nitrogen", 0.10);   // alarm at 10 mol%
monitor.setPrimaryComponent("hydrogen");          // for getMeasuredValue()

// After process.run():
double h2InGas   = monitor.getGasPhaseMoleFraction("hydrogen");    // y_H2
double h2InLiq   = monitor.getLiquidPhaseMoleFraction("hydrogen");  // x_H2
double h2Bulk    = monitor.getBulkMoleFraction("hydrogen");         // z_H2
double enrichment = monitor.getEnrichmentFactor("hydrogen");       // y/z
boolean alarm     = monitor.isAlarmExceeded("hydrogen");           // y > 0.04?
int nPhases       = monitor.getNumberOfPhases();
double gasFrac    = monitor.getGasPhaseFraction();                 // gas beta

Map<String, Map<String, Double>> report = monitor.getFullReport();
```

### Supported Units for `getMeasuredValue(String unit)`

| Unit | Description |
|------|-------------|
| `"mol%"` | Mole percent (default) |
| `"ppm"` | Parts per million |
| `"mole fraction"` | Raw mole fraction |

---

## TransientWellbore

**Package:** `neqsim.process.equipment.pipeline`

Simulates the thermal evolution of a wellbore after injection stops.
The model divides the wellbore into vertical segments and applies
exponential temperature decay toward the formation temperature at
each depth. At each time step, a TP flash determines if two-phase
conditions develop and tracks impurity enrichment.

### API

```java
TransientWellbore well = new TransientWellbore("InjWell", feedStream);
well.setWellDepth(1300.0);                              // meters
well.setTubingDiameter(0.1571);                         // meters
well.setFormationTemperature(277.15, 316.15);           // top K, bottom K
well.setShutdownCoolingRate(5.0);                       // time constant [hours]
well.setNumberOfSegments(20);
well.setRadialHeatTransferCoefficient(25.0);            // W/(m2 K)
well.setDepressurizationRate(5.0);                      // bar/hr (0 = constant P)

// Run
well.runShutdownSimulation(24.0, 1.0);  // 24 hours, 1-hour steps

// Results
List<TransientSnapshot> snapshots = well.getSnapshots();
List<double[]> tempProfiles = well.getTemperatureProfiles();
double[] times = well.getTimePoints();
double maxH2 = well.getMaxGasPhaseConcentration("hydrogen");
```

### TransientSnapshot Fields

| Field | Type | Description |
|-------|------|-------------|
| `timeHours` | double | Simulation time |
| `depths[]` | double[] | Depth at each segment [m] |
| `temperatures[]` | double[] | Temperature at each segment [K] |
| `pressures[]` | double[] | Pressure at each segment [bara] |
| `numberOfPhases[]` | int[] | Phase count at each segment |
| `gasFraction[]` | double[] | Gas mole fraction at each segment |
| `gasCompositions` | Map | Component gas-phase compositions at each segment |

---

## CO2FlowCorrections

**Package:** `neqsim.process.equipment.pipeline`

Static utility class providing CO2-specific correction factors for the
Beggs and Brill (1973) multiphase flow correlation. The standard correlation
was developed for oil-gas-water systems and may not accurately represent
CO2-dominated streams where the liquid phase is dense-phase CO2.

### API

```java
// Check applicability
boolean isCO2 = CO2FlowCorrections.isCO2DominatedFluid(system);   // >50 mol% CO2
double xCO2   = CO2FlowCorrections.getCO2MoleFraction(system);

// Correction factors
double holdupFactor  = CO2FlowCorrections.getLiquidHoldupCorrectionFactor(system);   // 0.70-0.85
double frictionFactor = CO2FlowCorrections.getFrictionCorrectionFactor(system);      // 0.85-0.95

// Phase state
boolean dense = CO2FlowCorrections.isDensePhase(system);           // T>Tc AND P>Pc
double Tr     = CO2FlowCorrections.getReducedTemperature(system);  // T/Tc
double Pr     = CO2FlowCorrections.getReducedPressure(system);     // P/Pc

// Surface tension
double sigma  = CO2FlowCorrections.estimateCO2SurfaceTension(system);  // N/m
```

### Correction Factor Behaviour

| Condition | Holdup Factor | Friction Factor |
|-----------|:------------:|:---------------:|
| Near-critical ($T_r > 0.95$) | 0.70 | 0.85 |
| Intermediate ($0.85 < T_r < 0.95$) | 0.70 - 0.85 (linear) | 0.95 |
| Subcritical ($T_r < 0.85$) | 0.85 | 0.95 |

**Constants:** $T_c = 304.13$ K, $P_c = 73.77$ bara, CO2 threshold = 50 mol%.

**Reference:** Peletiri, S.P., Rahmanian, N. and Mujtaba, I.M. (2018). CO2 Pipeline Design: A Review. *Energies*, 11(9), 2184.

---

## PipeBeggsAndBrills — Formation Temperature Gradient (NIP-1)

**Package:** `neqsim.process.equipment.pipeline`

The existing `PipeBeggsAndBrills` class was enhanced with a formation
temperature gradient model. Previously, only a constant surface temperature
could be specified. Now the heat transfer calculation uses a depth-dependent
formation temperature profile for realistic geothermal gradient modelling.

### New Methods

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(inletStream);

// Set formation temperature gradient
// For a well with 4 C at wellhead and 30 C/km geothermal gradient:
pipe.setFormationTemperatureGradient(4.0, -0.03, "C");
//                                  ^inlet ^gradient ^unit
// Sign convention: negative gradient = temperature INCREASES with depth
//                  (negative elevation change in injection wells)

double grad = pipe.getFormationTemperatureGradient();   // returns K/m
```

### Impact on Results (Example)

| Parameter | Without Gradient | With Gradient | Difference |
|-----------|:----------------:|:-------------:|:----------:|
| BHT | 4.1 °C | 42.8 °C | +38.7 °C |
| BHP | 200.5 bara | 183.7 bara | -16.8 bar |

The formation temperature gradient significantly affects the fluid density
along the wellbore, which changes the hydrostatic pressure contribution.
Warmer fluid is less dense, resulting in lower bottom-hole pressure.

---

## Integration Workflow

These classes are designed to work together for CO2 injection well safety
analysis. The typical workflow is:

1. **Create fluid** — Define CO2-rich injection composition
2. **Steady-state** — Run `PipeBeggsAndBrills` with formation gradient
3. **Phase scan** — Map the two-phase region with TP flash
4. **Enrichment** — Use `ImpurityMonitor` to track H2/N2 in gas phase
5. **Shutdown** — Run `TransientWellbore` for thermal transient
6. **Safety** — Use `CO2InjectionWellAnalyzer` for integrated assessment

### Python / Jupyter Example

```python
from neqsim import jneqsim
import jpype

# Load classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
CO2InjectionWellAnalyzer = jpype.JClass(
    "neqsim.process.equipment.pipeline.CO2InjectionWellAnalyzer")

# Create fluid
fluid = SystemSrkEos(273.15 + 25.0, 90.0)
fluid.addComponent("CO2", 0.96225)
fluid.addComponent("nitrogen", 0.01975)
fluid.addComponent("methane", 0.008)
fluid.addComponent("hydrogen", 0.0075)
fluid.addComponent("argon", 0.00175)
fluid.addComponent("CO", 0.00075)
fluid.setMixingRule("classic")

# Run integrated analysis
analyzer = CO2InjectionWellAnalyzer("InjectionWell-1")
analyzer.setFluid(fluid)
analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5)
analyzer.setOperatingConditions(90.0, 25.0, 150000.0)
analyzer.setFormationTemperature(4.0, 43.0)
analyzer.addTrackedComponent("hydrogen", 0.04)
analyzer.runFullAnalysis()

print("Safe:", analyzer.isSafeToOperate())
results = dict(analyzer.getResults())
print("Min safe WHP:", results.get("min_safe_WHP_bara"), "bara")
```

---

## Related Documentation

- [Beggs & Brill Pipe Flow](PipeBeggsAndBrills.md) — Base multiphase flow correlation
- [Pipeline Equipment](equipment/pipelines.md) — Pipeline and riser models
- [Well Mechanical Design](well_mechanical_design.md) — Casing/tubing design per NORSOK D-010
- [Process Design Guide](process_design_guide.md) — Complete process design workflow
