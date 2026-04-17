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
| **Matched to commercial simulator PR-LK** | **`SystemPrLeeKeslerEos`** | `"classic"` |
| Water, MEG, methanol, polar | `SystemSrkCPAstatoil` | `10` (numeric) |
| Custody transfer, fiscal metering | `SystemGERG2008Eos` | (none needed) |
| Electrolyte systems | `SystemElectrolyteCPAstatoil` | `10` |
| Volume-corrected SRK | `SystemSrkEosvolcor` | `"classic"` |

**PR-LK vs PR78**: `SystemPrLeeKeslerEos` uses PR76 alpha for ALL ω:
`m = 0.37464 + 1.54226ω − 0.26992ω²`. Standard `SystemPrEos1978` uses a modified
cubic for ω > 0.49. Use PR-LK when matching commercial simulator models that use
this EOS label.

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

## Loading Fluids from E300 Files

NeqSim can read Eclipse E300-format fluid files with full component properties
and binary interaction parameters:

```java
// Load fluid from E300 file (returns SystemInterface with PR-EOS)
SystemInterface fluid = EclipseFluidReadWrite.read("path/to/fluid.e300");
// Returns a PR-EOS fluid with all components, properties, and BIPs set
```

**Required E300 sections**: `CNAMES`, `TCRIT`, `PCRIT`, `ACF`, `MW`, `TBOIL`,
`VCRIT`, `PARACHOR`, `SSHIFT`, `BIC`, `ZI`.

**Optional E300 sections** (NeqSim parses and applies these):
- `OMEGAA` / `OMEGAB` — per-component OmegaA/B overrides (applied after `init(0)`)
- `BICS` — surface-condition BICs (parsed, same lower-triangular format as `BIC`)
- `SSHIFTS` — surface-condition volume shift
- `PEDERSEN` — activates Pedersen viscosity model

**EOS keyword determines fluid class:**
- `EOS\nSRK /` → `SystemSrkEos`
- `EOS\nPR /\nPRCORR` → `SystemPrEos1978`
- `EOS\nPR /\nPRLKCORR` → `SystemPrLeeKeslerEos` ← use for PR-LK matching
- `EOS\nPR /` → `SystemPrEos`

**CRITICAL**: The `BIC` section must ALWAYS be present. If omitted, NeqSim
defaults to zero BIPs (no crash, but results may differ significantly from the
source simulator). The `PARACHOR` section is also required — estimate unknown
values with `4.0 * MW^0.77`.

**Component name mapping**: `C1`→methane, `C2`→ethane, `C3`→propane,
`iC4`→i-butane, `C4`→n-butane, `iC5`→i-pentane, `C5`→n-pentane,
`C6`→n-hexane, `N2`→nitrogen, `CO2`→CO2, `H2O`→water. All other names are
treated as TBP pseudo-fractions via `addTBPfraction()` — including aromatics
(Benzene, Toluene, etc.).

```python
# Python usage — auto-detects EOS from file
from neqsim import jneqsim
EclipseFluidReadWrite = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite
fluid = EclipseFluidReadWrite.read("path/to/fluid.e300")

# Force a specific EOS regardless of what's in the file
SystemPrLeeKeslerEos = jneqsim.thermo.system.SystemPrLeeKeslerEos
target_fluid = SystemPrLeeKeslerEos(288.15, 1.01325)
fluid = EclipseFluidReadWrite.read("path/to/fluid.e300", target_fluid)
```

**JSON process builder also supports PR-LK** via `"model": "PR_LK"`:
```json
{ "fluid": { "model": "PR_LK", "temperature": 288.15, "pressure": 50.0, ... } }
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

### Separator Mechanical Design (Physical Configuration)

Physical dimensions, internals, and design parameters are configured through
`SeparatorMechanicalDesign` — NOT directly on `Separator`. The `Separator`
class handles process simulation (flash, entrainment); `SeparatorMechanicalDesign`
owns the physical vessel design.

```java
// After process.run():
sep.initMechanicalDesign();
SeparatorMechanicalDesign design =
    (SeparatorMechanicalDesign) sep.getMechanicalDesign();

// Design envelope
design.setMaxOperationPressure(85.0);           // bara
design.setMaxOperationTemperature(273.15 + 80); // K

// Vessel sizing parameters (configured via MechanicalDesign)
design.setGasLoadFactor(0.107);       // K-factor [m/s]
design.setRetentionTime(120.0);       // Liquid retention [s]
design.setFg(0.5);                    // Gas area fraction

// Nozzle diameters (set via MechanicalDesign, NOT on Separator)
design.setInletNozzleID(0.254);       // 10-inch inlet nozzle [m]
design.setGasOutletNozzleID(0.20);    // Gas outlet [m]
design.setOilOutletNozzleID(0.15);    // Oil outlet [m]

// Demister/mist eliminator parameters
design.setDemisterType("wire_mesh");  // "wire_mesh", "vane_pack", "cyclone"
design.setDemisterPressureDrop(1.5);  // [mbar]
design.setDemisterThickness(150.0);   // [mm]
design.setFoamAllowanceFactor(1.0);   // 1.0 = no foam

// Run design calculation
design.readDesignSpecifications();
design.calcDesign();
String report = design.toJson();

// Results: design.getInnerDiameter(), design.getTantanLength(),
//          design.getWallThickness(), design.getInletNozzleID(), etc.
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

### HeatExchanger (Two-Sided)

`HeatExchanger` has two feed/outlet sides indexed 0 and 1. Use
`setFeedStream(int, StreamInterface)` to connect both sides and
`getOutStream(int)` to retrieve the outlet for each side.

**IMPORTANT:** Do NOT use `getOutletStream()` when you need a specific
side — it only returns side 0. Always use `getOutStream(int)`.

```java
HeatExchanger hx = new HeatExchanger("E-100");
hx.setFeedStream(0, shellSideFeed);   // side 0 = shell
hx.setFeedStream(1, tubeSideFeed);    // side 1 = tube
// Optional: hx.setUAvalue(35000.0);  // W/K

// After run: retrieve each side's outlet
Stream shellOut = (Stream) hx.getOutStream(0);
Stream tubeOut  = (Stream) hx.getOutStream(1);
double duty = hx.getDuty();  // Watts
```

```python
# Python
hx = HeatExchanger("E-100")
hx.setFeedStream(0, shell_feed)
hx.setFeedStream(1, tube_feed)
# Downstream connections:
cooler = Cooler("C-100", hx.getOutStream(int(0)))   # shell side out
valve  = ThrottlingValve("VLV-100", hx.getOutStream(int(1)))  # tube side out
```

### Valve (JT / Isenthalpic Expansion)

```java
ThrottlingValve valve = new ThrottlingValve("JT Valve", stream);
valve.setOutletPressure(20.0);
Stream out = valve.getOutletStream();
```

**CRITICAL:** Always use `ThrottlingValve` inside a `ProcessSystem` for Joule-Thomson
cooling calculations. Manual `PHflash()` on a cloned fluid gives wrong JT temperatures
(tested: 14.9°C error vs 1.7°C with ThrottlingValve). The valve handles the isenthalpic
enthalpy bookkeeping internally.

```python
# Python — Correct JT expansion pattern
proc = ProcessSystem()
feed = Stream('SG', fluid.clone())
feed.setFlowRate(flow, 'kg/hr')
feed.setTemperature(T_in, 'C')
feed.setPressure(P_in, 'bara')
proc.add(feed)
valve = ThrottlingValve('JT', feed)
valve.setOutletPressure(P_out)
proc.add(valve)
proc.run()
T_jt = float(valve.getOutletStream().getTemperature('C'))
```

### Mixer

```java
Mixer mixer = new Mixer("Mix");
mixer.addStream(stream1);
mixer.addStream(stream2);
Stream out = mixer.getOutletStream();
```

### ComponentSplitter (TEG / Glycol Contactor — Water Removal)

Used to model TEG dehydration contactors as simple water-removal units.
Splits a stream per-component: `splitFactor[k] = 1.0` keeps the component in
stream 0 (dry gas), `0.0` removes it to stream 1 (water).

**TEG dehydration pattern**: water is always the last component added,
so use `[1.0] * (N-1) + [0.0]` to remove only water.

```java
// Java
ComponentSplitter dehydrator = new ComponentSplitter("TEG contactor", wetGasStream);
int nComp = wetGasStream.getFluid().getNumberOfComponents();
double[] sf = new double[nComp];
Arrays.fill(sf, 1.0);
sf[nComp - 1] = 0.0;  // last component = water
dehydrator.setSplitFactors(sf);
// After run:
Stream dryGas = dehydrator.getSplitStream(0);   // all components except water
Stream water  = dehydrator.getSplitStream(1);   // removed water
```

```python
# Python
water_dehydration = neqsim.process.equipment.splitter.ComponentSplitter(
    "dehyd", wet_gas_stream)
complen = wet_gas_stream.getFluid().getNumberOfComponents()
water_dehydration.setSplitFactors([1.0] * (complen - 1) + [0.0])
water_dehydration.run()
dry_gas = water_dehydration.getSplitStream(0)
```

> **When to use**: Any absorber with a glycol-related name ("glyc", "teg",
> "dehydrat") should be modeled as a ComponentSplitter rather than a
> DistillationColumn. This avoids solver convergence issues and is the
> standard pattern for production platform models.

### Pump

```java
Pump pump = new Pump("P-100", liquidStream);
pump.setOutletPressure(20.0);           // bara
pump.setIsentropicEfficiency(0.75);     // 0-1
Stream out = pump.getOutletStream();
// After run: pump.getPower("kW")
```

**Three operating modes:**
1. **Isentropic (default):** PS flash → isentropic enthalpy → divide by efficiency → PH flash
2. **Fixed outlet temperature:** `pump.setOutletTemperature(40.0, "C")` → back-calculates power
3. **Pump chart:** `pump.getPumpChart()` → head, efficiency, NPSH curves

### Pipeline

```java
AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", stream);
pipe.setLength(50000.0);   // meters
pipe.setDiameter(0.508);   // meters (20 inch)
Stream out = pipe.getOutletStream();
```

### Recycle (Detailed)

Recycles enable iterative convergence of process loops. The `ProcessSystem`
automatically detects and iterates recycles up to 100 times.

```java
// 1. Create placeholder stream with estimated conditions
Stream placeholder = new Stream("recycle estimate", fluidGuess.clone());
placeholder.setFlowRate(estimatedFlow, "kg/hr");
placeholder.setTemperature(estimatedT, "C");
placeholder.setPressure(estimatedP, "bara");
process.add(placeholder);

// 2. Build downstream equipment using the placeholder as input
Mixer mixer = new Mixer("recycle mixer");
mixer.addStream(mainFeed);
mixer.addStream(placeholder);       // ← placeholder used here
process.add(mixer);
// ... more equipment in the loop ...

// 3. Create Recycle that connects actual outlet back to placeholder
Recycle recycle = new Recycle("RCY-1");
recycle.addStream(actualOutletStream);    // downstream end of loop
recycle.setOutletStream(placeholder);      // connects back to start
recycle.setTolerance(1e-3);               // tighter than default 1e-2
process.add(recycle);
```

**Convergence tuning:**
```java
recycle.setFlowTolerance(1e-3);          // flow convergence (default 1e-2)
recycle.setTemperatureTolerance(1e-3);   // temperature convergence
recycle.setCompositionTolerance(1e-3);   // composition convergence
recycle.setPriority(50);                 // lower = solved first (default 100)
recycle.setAccelerationMethod("Wegstein"); // or "Direct Substitution", "Broyden"
```

**Priority-based nesting:** Set lower priority numbers on inner recycle loops.
The `RecycleController` solves lower-priority recycles first, then higher.
ProcessSystem hard cap: 100 iterations (not user-configurable).

### Adjuster

```java
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

### Architecture Pattern (from reference platform models)

```
ProcessModel ("Gas Platform")                ← TOP-LEVEL CONTAINER
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

The reference model uses **functions** that return ProcessSystem objects:

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
| Automation facade | `getAutomation()` returns `ProcessAutomation` (string-addressable variables) |
| Lifecycle state | `ProcessModelState.fromProcessModel(plant)`, `.saveToFile()`, `.compare(v1, v2)` |

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

## Automation API (String-Addressable Variables)

Use `ProcessAutomation` for agent-friendly variable access — no Java class navigation needed.

### Setup and Discovery

```java
ProcessAutomation auto = process.getAutomation();   // or plant.getAutomation()
List<String> units = auto.getUnitList();             // ["Feed Gas", "HP Sep", ...]
List<SimulationVariable> vars = auto.getVariableList("HP Sep");
// Each variable: address, name, type (INPUT/OUTPUT), defaultUnit, description
String eqType = auto.getEquipmentType("HP Sep");     // "Separator"
```

### Read / Write Variables

```java
// Read with unit conversion (dot-notation addressing)
double temp = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C");
double flow = auto.getVariableValue("HP Sep.gasOutStream.flowRate", "kg/hr");

// Write INPUT variables, then re-run
auto.setVariableValue("Compressor.outletPressure", 150.0, "bara");
process.run();
```

### Multi-Area Addressing

```java
ProcessAutomation plantAuto = plant.getAutomation();
List<String> areas = plantAuto.getAreaList();
// Area-qualified: "Area::Unit.property"
double t = plantAuto.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C");
```

## Lifecycle State (Save / Restore / Compare)

JSON snapshots for reproducibility and version tracking.

```java
// Save
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setName("Gas Processing"); state.setVersion("1.0.0");
state.saveToFile("model_v1.json");

// Load and validate
ProcessSystemState loaded = ProcessSystemState.loadFromFile("model_v1.json");
assert loaded.validate().isValid();

// Multi-area
ProcessModelState ms = ProcessModelState.fromProcessModel(plant);
ms.saveToFile("plant_v1.json");

// Version diff
ProcessModelState.ModelDiff diff = ProcessModelState.compare(v1, v2);
// diff.getModifiedParameters(), diff.getAddedEquipment(), diff.getRemovedEquipment()

// Compressed bytes for API transfer
byte[] bytes = ms.toCompressedBytes();
ProcessModelState restored = ProcessModelState.fromCompressedBytes(bytes);
```

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
