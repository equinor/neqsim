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

For multi-area plants, use `ProcessModel` to combine multiple `ProcessSystem` instances (see below).

## ProcessModel — Combining Multiple Process Areas (MANDATORY for Large Plants)

For large process plants (platforms, refineries, gas plants), split the model into
separate `ProcessSystem` objects per process area, then combine them into a single
`ProcessModel`. **NEVER try to add a ProcessModule or ProcessSystem to another
ProcessSystem** — use `ProcessModel` as the top-level container.

### Architecture Pattern (from Oseberg/Snorre field models)

```
ProcessModel ("Grane Platform")              ← TOP-LEVEL CONTAINER
  ├── ProcessSystem ("well process")          ← Well feed & manifold
  ├── ProcessSystem ("separation train A")    ← HP/LP separation
  ├── ProcessSystem ("separation train B")    ← HP/LP separation
  ├── ProcessSystem ("TEX process A")         ← Turbo-expander
  ├── ProcessSystem ("TEX process B")         ← Turbo-expander
  ├── ProcessSystem ("export compressor A")   ← Gas compression
  ├── ProcessSystem ("export gas")            ← Gas export pipeline
  └── ProcessSystem ("export oil")            ← Oil export
```

### Java Example

```java
// Each area is its own ProcessSystem
ProcessSystem wellProcess = new ProcessSystem();
wellProcess.add(wellFeed);
wellProcess.add(manifold);
wellProcess.add(splitter);

ProcessSystem separationA = new ProcessSystem();
separationA.add(new Heater("HP heater", splitter.getSplitStream(0)));
separationA.add(new ThreePhaseSeparator("1st stage", ...));
// ... more equipment

ProcessSystem compressionA = new ProcessSystem();
compressionA.add(new Compressor("export comp",
    separationA.getUnit("gas mixer").getOutletStream()));  // cross-ref

// Combine into ProcessModel
ProcessModel plant = new ProcessModel();
plant.add("well process", wellProcess);
plant.add("separation train A", separationA);
plant.add("export compressor A", compressionA);
plant.run();  // Iterates until all converge

// Access equipment by process area
plant.get("separation train A").getUnit("1st stage separator");

// Convergence info
System.out.println(plant.getConvergenceSummary());
System.out.println(plant.getMassBalanceReport());
```

### Python Example (Recommended Pattern)

The Oseberg model uses **functions** that return ProcessSystem objects:

```python
def create_well_feed_model(inp):
    well_process = neqsim.process.processmodel.ProcessSystem()
    feed = Stream("feed", fluid)
    feed.setFlowRate(inp.flow_rate, "kg/hr")
    well_process.add(feed)
    splitter = Splitter("manifold", feed)
    splitter.setSplitFactors([0.5, 0.5])
    well_process.add(splitter)
    return well_process

def create_separation_process(inp, feed_stream):
    sep_process = neqsim.process.processmodel.ProcessSystem()
    separator = ThreePhaseSeparator("1st stage", feed_stream)  # cross-ref!
    sep_process.add(separator)
    # ... more equipment
    return sep_process

# Build and run each area
well_model = create_well_feed_model(params)
well_model.run()

sep_train_A = create_separation_process(params,
    well_model.getUnit("manifold").getSplitStream(0))  # cross-system stream
sep_train_A.run()

# Combine into ProcessModel
ProcessModel = jneqsim.process.processmodel.ProcessModel
plant = ProcessModel()
plant.add("well process", well_model)
plant.add("separation train A", sep_train_A)
plant.run()  # Iterates until convergence

print(plant.getConvergenceSummary())
print(plant.getMassBalanceReport())
```

### ProcessModel Key Features

| Feature | Method |
|---------|--------|
| Add named sub-process | `add("name", processSystem)` |
| Get sub-process | `get("name")` |
| Remove sub-process | `remove("name")` |
| Run all (iterates to convergence) | `run()` |
| Run single step | `runStep()` |
| Run in background thread | `runAsTask()` returns `Future` |
| Check convergence | `isModelConverged()`, `getConvergenceSummary()` |
| Mass balance report | `getMassBalanceReport()`, `getFailedMassBalanceReport()` |
| Validation | `validateSetup()`, `validateAll()`, `getValidationReport()` |
| Execution analysis | `getExecutionPartitionInfo()` |
| Set convergence tolerance | `setTolerance(1e-4)` or individual `setFlowTolerance()` etc. |
| Save/load model | `saveToNeqsim("file.neqsim")`, `loadFromNeqsim("file.neqsim")` |
| JSON report | `getReport_json()` |

### Cross-System Stream Sharing

Streams cross sub-system boundaries by **direct object reference**:
- Equipment in System B takes an outlet stream from System A as a constructor argument
- `ProcessModel.run()` executes systems in insertion order
- System A populates its outlet streams BEFORE System B reads from them
- **Order of `add()` calls matters** — add upstream systems first

### ProcessModel vs ProcessModule vs ProcessSystem

| Class | Purpose | Use When |
|-------|---------|----------|
| `ProcessSystem` | Single process area with equipment | Always — the basic building block |
| `ProcessModel` | **Named** collection of ProcessSystems with convergence tracking | Multi-area plants (platforms, gas plants) |
| `ProcessModule` | Legacy container for ProcessSystems | Backward compatibility only — prefer ProcessModel |

**NEVER** add a `ProcessModule` or `ProcessModel` to a `ProcessSystem` — it will throw `TypeError`.

## Key Rules

- **Clone fluids** before branching: `fluid.clone()` to avoid shared-state bugs
- Equipment constructors take `(String name, StreamInterface inlet)`
- Connect equipment via outlet streams — don't create separate streams
- Add equipment to `ProcessSystem` in topological order
- Call `process.run()` only ONCE after building the entire flowsheet
- **For multi-area plants**: use `ProcessModel` to combine `ProcessSystem` objects — never nest them

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

## Heat Exchanger Thermal-Hydraulic Design

TEMA-level shell-and-tube thermal design with tube/shell-side HTCs, pressure drops,
LMTD correction, vibration screening, and full mechanical design.

### Standalone Thermal Calculation

```java
ThermalDesignCalculator calc = new ThermalDesignCalculator();
calc.setTubeODm(0.01905);    // 3/4" OD
calc.setTubeIDm(0.01483);
calc.setTubeLengthm(6.0);
calc.setTubeCount(200);
calc.setTubePasses(2);
calc.setTubePitchm(0.0254);
calc.setTriangularPitch(true);
calc.setShellIDm(0.489);
calc.setBaffleSpacingm(0.15);
calc.setBaffleCount(30);
calc.setBaffleCut(0.25);

// Tube-side fluid (density, viscosity, cp, conductivity, massFlow, isHeating)
calc.setTubeSideFluid(995.0, 0.0008, 4180.0, 0.62, 5.0, true);
// Shell-side fluid
calc.setShellSideFluid(820.0, 0.003, 2200.0, 0.13, 8.0);

calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
calc.calculate();
String json = calc.toJson();  // Full results: U, dP, HTCs, zone analysis
```

### LMTD Correction Factor

```java
double ft = LMTDcorrectionFactor.calcFt(tHotIn, tHotOut, tColdIn, tColdOut, 1);  // 1 shell pass
int minShells = LMTDcorrectionFactor.requiredShellPasses(tHotIn, tHotOut, tColdIn, tColdOut);
// MIN_ACCEPTABLE_FT = 0.75
```

### Vibration Screening

```java
VibrationAnalysis.VibrationResult vib = VibrationAnalysis.performScreening(
    tubeOD, tubeID, unsupportedSpan, tubeMaterialE, tubeDensity,
    fluidDensityTube, fluidDensityShell, "fixed-fixed",
    crossflowVelocity, tubePitch, true, shellID, sonicVelocity);
if (!vib.passed) {
    // Check vib.vortexSheddingCritical, vib.fluidElasticCritical, vib.acousticCritical
}
```

### Full Shell-and-Tube Mechanical + Thermal Design

```java
ShellAndTubeDesignCalculator stCalc = new ShellAndTubeDesignCalculator();
stCalc.setTemaDesignation("AES");
stCalc.setTemaClass(TEMAClass.R);
stCalc.setRequiredArea(50.0);           // m²
stCalc.setShellSidePressure(30.0);      // bara
stCalc.setTubeSidePressure(10.0);       // bara
stCalc.setDesignTemperature(200.0);     // °C
stCalc.setShellMaterialGrade("SA-516-70");
stCalc.setTubeMaterialGrade("SA-179");
stCalc.setSourServiceAssessment(true);
stCalc.setH2sPartialPressure(0.01);     // bar

// Provide fluid properties for thermal + vibration analysis
stCalc.setTubeSideFluidProperties(995.0, 0.0008, 4180.0, 0.62, 5.0, true);
stCalc.setShellSideFluidProperties(820.0, 0.003, 2200.0, 0.13, 8.0);
stCalc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);

stCalc.calculate();  // Runs mechanical + thermal + vibration
String json = stCalc.toJson();  // MAWP, wall thickness, U, dP, vibration, cost, BOM
```

**Standards:** TEMA R/C/B, ASME VIII Div.1 (UHX-13, UG-27, UG-37, UG-99),
NACE MR0175/ISO 15156, Bell-Delaware, Gnielinski, Von Karman, Connors criterion.

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

## Engineering Deliverables

Generate study-class-appropriate engineering documents from a converged ProcessSystem.

### StudyClass and Package

```java
// Standalone — generates all deliverables for the selected study class
EngineeringDeliverablesPackage pkg =
    new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
pkg.generate();
String json = pkg.toJson();

// Through orchestrator
orchestrator.setStudyClass(StudyClass.CLASS_A);
orchestrator.runCompleteDesignWorkflow();
EngineeringDeliverablesPackage pkg = orchestrator.getEngineeringDeliverables();
```

| Study Class | Deliverables |
|-------------|-------------|
| **CLASS_A** (FEED/Detail) | PFD, Thermal Utilities, Alarm/Trip, Spare Parts, Fire Scenarios, Noise, Instrument Schedule |
| **CLASS_B** (Concept/Pre-FEED) | PFD, Thermal Utilities, Fire Scenarios, Instrument Schedule |
| **CLASS_C** (Screening) | PFD only |

### Instrument Schedule Generator (with Live Device Bridge)

Creates ISA-5.1 tagged instruments and optionally registers real `MeasurementDeviceInterface`
objects on the ProcessSystem for dynamic simulation:

```java
InstrumentScheduleGenerator instrGen = new InstrumentScheduleGenerator(process);
instrGen.setRegisterOnProcess(true);  // bridge: creates live MeasurementDevice objects
instrGen.generate();

// Query instruments
List<InstrumentScheduleGenerator.InstrumentEntry> all = instrGen.getEntries();
List<InstrumentScheduleGenerator.InstrumentEntry> pts =
    instrGen.getEntriesByType(InstrumentScheduleGenerator.MeasuredVariable.PRESSURE);

// Each entry has: tag, equipmentName, service, measuredVariable, rangeMin/Max, unit,
//                 alarmHH/H/L/LL, silRating, liveDevice (if registerOnProcess=true)
for (InstrumentScheduleGenerator.InstrumentEntry e : all) {
    System.out.println(e.getTag() + " " + e.getEquipmentName()
        + " SIL=" + e.getSilRating());
    if (e.getLiveDevice() != null) {
        // Real MeasurementDevice registered on ProcessSystem
        System.out.println("  Live: " + e.getLiveDevice().getMeasuredValue());
    }
}

String instrJson = instrGen.toJson();
```

Tag numbering convention: PT-100+, TT-200+, LT-300+, FT-400+ (ISA-5.1).

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
