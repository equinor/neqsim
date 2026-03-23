---
name: neqsim-api-patterns
description: "NeqSim API patterns and code recipes. USE WHEN: writing Java or Python code that uses NeqSim for thermodynamic calculations, process simulation, or property retrieval. Covers EOS selection, fluid creation, flash calculations, property access, equipment patterns, and unit conventions."
---

# NeqSim API Patterns

Copy-paste reference for common NeqSim operations. All Java code must be Java 8 compatible.

## EOS Selection Guide

| Fluid Type | Java Class | Mixing Rule |
|-----------|-----------|-------------|
| Dry/lean gas, simple HC | `SystemSrkEos` | `"classic"` |
| General hydrocarbons, oil | `SystemPrEos` | `"classic"` |
| Water, MEG, methanol, polar | `SystemSrkCPAstatoil` | `10` (numeric) |
| Custody transfer, fiscal metering | `SystemGERG2008Eos` | (none needed) |
| Electrolyte systems | `SystemElectrolyteCPAstatoil` | `10` |
| Volume-corrected SRK | `SystemSrkEosvolcor` | `"classic"` |

## Fluid Creation (Required Sequence)

```java
// 1. Create: temperature in KELVIN, pressure in bara
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);

// 2. Add components (name, mole fraction)
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);

// 3. MANDATORY: set mixing rule — NEVER skip
fluid.setMixingRule("classic");

// 4. Optional: multi-phase check for water/heavy systems
fluid.setMultiPhaseCheck(true);
```

## Oil Characterization (C7+ Fractions)

```java
fluid.addTBPfraction("C7", 0.05, 92.0 / 1000, 0.727);   // name, moleFrac, MW_kg/mol, density
fluid.addTBPfraction("C8", 0.04, 104.0 / 1000, 0.749);
fluid.addPlusFraction("C20+", 0.02, 350.0 / 1000, 0.88);
fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(6);
fluid.getCharacterization().characterisePlusFraction();
```

## Flash Calculations and Property Retrieval

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// CRITICAL: call initProperties() AFTER flash, BEFORE reading properties
// init(3) alone does NOT initialize transport properties — they return ZERO
fluid.initProperties();

// Bulk properties
double density = fluid.getDensity("kg/m3");
double molarMass = fluid.getMolarMass("kg/mol");
double Z = fluid.getZ();

// Phase properties
double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
double gasViscosity = fluid.getPhase("gas").getViscosity("kg/msec");
double gasThermalCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
double gasCp = fluid.getPhase("gas").getCp("J/kgK");

// Phase checks
int numPhases = fluid.getNumberOfPhases();
boolean hasGas = fluid.hasPhaseType("gas");
```

### Other Flash Types

```java
ops.PHflash(enthalpy);                  // Pressure-Enthalpy
ops.PSflash(entropy);                   // Pressure-Entropy
ops.dewPointTemperatureFlash();          // Dew point temperature
ops.bubblePointPressureFlash();          // Bubble point pressure
ops.hydrateFormationTemperature();       // Hydrate T at given P
ops.calcPTphaseEnvelope();              // Phase envelope
```

## Unit Conventions

| Quantity | Constructor default | Setter pattern |
|----------|-------------------|----------------|
| Temperature | **Kelvin** | `setTemperature(25.0, "C")` |
| Pressure | **bara** | `setPressure(50.0, "bara")` |
| Flow rate | — | `setFlowRate(50000.0, "kg/hr")` |
| Getting temp | Returns **Kelvin** | `getTemperature() - 273.15` for °C |

## Process Equipment Patterns

### Stream

```java
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
feed.setPressure(50.0, "bara");
feed.setTemperature(30.0, "C");
```

### Separator

```java
Separator sep = new Separator("HP Sep", feedStream);
Stream gasOut = sep.getGasOutStream();
Stream liqOut = sep.getLiquidOutStream();
```

### Compressor

```java
Compressor comp = new Compressor("Comp", gasStream);
comp.setOutletPressure(120.0);
// comp.setIsentropicEfficiency(0.75);
Stream out = comp.getOutletStream();
// After run: comp.getPower("kW")
```

### Cooler / Heater

```java
Cooler cooler = new Cooler("Cooler", hotStream);
cooler.setOutTemperature(273.15 + 30.0);
Stream out = cooler.getOutletStream();
// After run: cooler.getDuty() — Watts
```

### Valve

```java
ThrottlingValve valve = new ThrottlingValve("JT Valve", stream);
valve.setOutletPressure(20.0);
Stream out = valve.getOutletStream();
```

### Mixer

```java
Mixer mixer = new Mixer("Mix");
mixer.addStream(stream1);
mixer.addStream(stream2);
Stream out = mixer.getOutletStream();
```

### Pipeline

```java
AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", stream);
pipe.setLength(50000.0);   // meters
pipe.setDiameter(0.508);   // meters (20 inch)
Stream out = pipe.getOutletStream();
```

### Recycle and Adjuster

```java
Recycle recycle = new Recycle("Recycle");
recycle.addStream(outletStream);
// Add to process after the equipment loop

Adjuster adjuster = new Adjuster("Adj");
adjuster.setAdjustedVariable(equipment, "methodName");
adjuster.setTargetVariable(stream, "methodName", targetValue);
```

## ProcessSystem Assembly

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.add(cooler);
process.run();  // Run ONCE after adding all equipment
```

## Key Rules

- **Clone fluids** before branching: `fluid.clone()` to avoid shared-state bugs
- Equipment constructors take `(String name, StreamInterface inlet)`
- Connect equipment via outlet streams — don't create separate streams
- Add equipment to `ProcessSystem` in topological order
- Call `process.run()` only ONCE after building the entire flowsheet

## Design Feasibility Reports

After running equipment in a process simulation, generate a feasibility report to answer:
"Is this machine realistic to build? What will it cost? Who can supply it?"

### Compressor Feasibility

```java
// After process.run():
CompressorDesignFeasibilityReport report =
    new CompressorDesignFeasibilityReport(compressor);
report.setDriverType("gas-turbine");
report.setCompressorType("centrifugal");
report.setAnnualOperatingHours(8000);
report.generateReport();

String verdict = report.getVerdict();  // FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE
String json = report.toJson();         // Full JSON with mech design, cost, suppliers, curves
List<SupplierMatch> suppliers = report.getMatchingSuppliers();

// Apply generated performance curves back to compressor
report.applyChartToCompressor();
```

### Heat Exchanger / Cooler / Heater Feasibility

```java
// After process.run():
HeatExchangerDesignFeasibilityReport hxReport =
    new HeatExchangerDesignFeasibilityReport(heatExchanger);
hxReport.setExchangerType("shell-and-tube");
hxReport.setDesignStandard("TEMA-R");
hxReport.setAnnualOperatingHours(8000);
hxReport.generateReport();

String verdict = hxReport.getVerdict();
String json = hxReport.toJson();
List<HXSupplierMatch> suppliers = hxReport.getMatchingSuppliers();
```

**Key points:**
- Equipment must have been `run()` before generating the report
- Verdicts: `FEASIBLE`, `FEASIBLE_WITH_WARNINGS`, `NOT_FEASIBLE`
- Issues have severity: `BLOCKER` (not feasible), `WARNING` (review), `INFO` (note)
- Supplier matching uses built-in OEM databases (`CompressorSuppliers.csv`, `HeatExchangerSuppliers.csv`)
- Reports include: operating point, mechanical design, cost estimation, supplier list, issues
- For compressors: also generates performance curves from templates

**When to run feasibility checks:**
- Any task involving equipment sizing or selection
- Process design tasks where cost or buildability matters
- Field development or FEED-level studies
- When the user asks "is this realistic?", "can this be built?", "what will it cost?"

## CO2 Injection Well Analysis

Full-stack safety analysis for CO2 injection wells covering steady-state flow,
phase boundary mapping, impurity enrichment, shutdown transients, and flow corrections.

### CO2InjectionWellAnalyzer (High-Level Orchestrator)

```java
CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("InjectionWell-1");
analyzer.setFluid(co2Fluid);
analyzer.setWellGeometry(1300.0, 0.1571, 5e-5);      // depth_m, tubingID_m, roughness_m
analyzer.setOperatingConditions(90.0, 25.0, 150000.0); // WHP_bara, WHT_C, flow_kg/hr
analyzer.setFormationTemperature(4.0, 43.0);            // top_C, bottom_C
analyzer.addTrackedComponent("hydrogen", 0.10);         // name, alarm mol fraction
analyzer.addTrackedComponent("nitrogen", 0.05);
analyzer.runFullAnalysis();

boolean safe = analyzer.isSafeToOperate();
Map<String, Object> results = analyzer.getResults();
```

### ImpurityMonitor (Measurement Device)

```java
ImpurityMonitor monitor = new ImpurityMonitor("H2-Monitor", stream);
monitor.addTrackedComponent("hydrogen", 0.10);   // alarm at 10 mol%
monitor.setPrimaryComponent("hydrogen");

// After process.run():
double gasH2 = monitor.getGasPhaseMoleFraction("hydrogen");
double enrichment = monitor.getEnrichmentFactor("hydrogen"); // y_gas / z_feed
boolean alarm = monitor.isAlarmExceeded("hydrogen");
Map<String, Map<String, Double>> report = monitor.getFullReport();
```

### TransientWellbore (Shutdown Cooling)

```java
TransientWellbore wellbore = new TransientWellbore("Shutdown", stream);
wellbore.setWellDepth(1300.0);
wellbore.setTubingDiameter(0.1571);
wellbore.setFormationTemperature(273.15 + 4.0, 273.15 + 43.0);
wellbore.setShutdownCoolingRate(6.0);   // tau = 6 hours
wellbore.setNumberOfSegments(10);

wellbore.runShutdownSimulation(48.0, 1.0);  // 48 hours, 1-hour steps
List<TransientSnapshot> snaps = wellbore.getSnapshots();
double maxH2 = wellbore.getMaxGasPhaseConcentration("hydrogen");
```

### PipeBeggsAndBrills (Formation Temperature Gradient)

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Wellbore", feed);
pipe.setLength(1300.0);
pipe.setElevation(-1300.0);    // downward
pipe.setDiameter(0.1571);
pipe.setFormationTemperatureGradient(4.0, -0.03, "C"); // 4°C top, -30°C/km (increases with depth)
pipe.run();
```

### CO2FlowCorrections (Static Utility)

```java
boolean co2Dominant = CO2FlowCorrections.isCO2DominatedFluid(system);      // > 50 mol% CO2
double holdupCorr = CO2FlowCorrections.getLiquidHoldupCorrectionFactor(system);  // 0.70–0.85
double frictionCorr = CO2FlowCorrections.getFrictionCorrectionFactor(system);    // 0.85–0.95
boolean dense = CO2FlowCorrections.isDensePhase(system);
double Tr = CO2FlowCorrections.getReducedTemperature(system);
```

## Documentation Code Verification

When writing code examples for documentation (markdown guides, cookbook recipes, tutorials):

1. **Read the source class first** — verify every method signature, constructor, inner class
2. **Write a JUnit test** that calls every documented API method (append to `DocExamplesCompilationTest.java`)
3. **Run the test** and confirm it passes before publishing the doc
4. **Common pitfalls**:
   - Plus fraction names: use `"C20"` not `"C20+"` (the `+` character breaks parsing)
   - Set mixing rule BEFORE calling `characterisePlusFraction()`
   - `getUnit("name")` not `getUnitOperation("name")`
   - `setDepreciationYears` takes `double`, not `int`
   - Risk thresholds: always read source for actual comparison logic (subcooling direction, enum ordering)
