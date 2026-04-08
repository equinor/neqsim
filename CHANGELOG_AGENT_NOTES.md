# NeqSim API Changelog — Agent Notes

> **Purpose:** Track API changes that affect agent instructions, code patterns,
> and existing examples. Agents read this file to stay aware of breaking changes,
> deprecated methods, and new capabilities.
>
> Format: most recent changes at the top. Include the date, what changed,
> migration steps, and which agents/skills need updating.

---

## 2026-07-07 — Full Bacalhau FPSO Model: Architecture Learnings

### HP Separator Water Routing

When replicating UniSim models in NeqSim, the HP separator at high pressure (90 bar)
may not produce a separate aqueous phase in UniSim. To match this behavior, use
`ThreePhaseSeparator` and then `Mixer` to recombine oil + water:

```java
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Sep", feedStream);
Mixer hpLiqRecombine = new Mixer("HP Liquid Recombine");
hpLiqRecombine.addStream(hpSep.getOilOutStream());
hpLiqRecombine.addStream(hpSep.getWaterOutStream());
// hpLiqRecombine.getOutletStream() now matches UniSim HP oil (includes water)
```

### Import Gas Compression Architecture

Large FPSO models use staged import gas compression matching pressure levels:
- VLP gas (~2 bar) → VRU compressor → ~5 bar → mix with LP gas
- LP+VRU gas (~5 bar) → 1st import compressor → ~22 bar → mix with MP gas
- MP+1st import gas (~22 bar) → 2nd import compressor → ~90 bar → mix with HP gas

Each stage has cooler + flash drum before the compressor (removes condensate).

### Pump API

```java
Pump pump = new Pump("P-100", liquidStream);
pump.setOutletPressure(6.1);          // bara
pump.setIsentropicEfficiency(0.75);
pump.getPower("kW");                  // after run
```

### ComponentSplitter for TEG Dehydration

```java
ComponentSplitter teg = new ComponentSplitter("TEG", wetGasStream);
int nComp = wetGasStream.getFluid().getNumberOfComponents();
double[] sf = new double[nComp];
java.util.Arrays.fill(sf, 1.0);
sf[nComp - 1] = 0.0;  // water is last component
teg.setSplitFactors(sf);
// getSplitStream(0) = dry gas, getSplitStream(1) = removed water
```

### Model Scale: 50+ Equipment Units in Single ProcessSystem

The Bacalhau FPSO model demonstrates ~50 equipment units in a single `ProcessSystem`
covering wellhead → HP/MP/LP/VLP separation → VRU + import gas compression →
gas cooling + TEG → 2-stage export compression → seal gas JT → oil export.
Single `ProcessSystem` converges in ~2 seconds without recycles.

---

## 2026-07-06 — JT Expansion: Use ThrottlingValve, Not PHflash

### Critical Agent Guidance

When modeling isenthalpic (Joule-Thomson) expansion, **always use `ThrottlingValve` in a
`ProcessSystem`**, never manual `PHflash()` on a cloned fluid. Tested on Bacalhau seal gas
(90→48 bar):

| Method | Temperature (°C) | UniSim Reference | Error |
|--------|-----------------|------------------|-------|
| `ThrottlingValve` in ProcessSystem | 16.44 | 18.17 | -1.73°C |
| Manual `PHflash(H/n)` on clone | 33.05 | 18.17 | +14.88°C |

The manual PHflash approach fails because `getEnthalpy('J')` returns total system enthalpy
while `PHflash(double)` expects a specific enthalpy convention (per mole at the system's
reference state). The ThrottlingValve handles the enthalpy bookkeeping internally.

**Pattern:**
```java
// CORRECT: Use process-level valve
ProcessSystem proc = new ProcessSystem();
Stream sg = new Stream("SG", fluid.clone());
proc.add(sg);
ThrottlingValve jt = new ThrottlingValve("JT", sg);
jt.setOutletPressure(48.0);
proc.add(jt);
proc.run();
double T_jt = jt.getOutletStream().getTemperature("C");  // Correct JT temperature

// WRONG: Manual PHflash — gives incorrect JT temperature
// SystemInterface clone = fluid.clone();
// clone.setPressure(48.0);
// new ThermodynamicOperations(clone).PHflash(fluid.getEnthalpy("J") / fluid.getTotalNumberOfMoles());
```

### Bacalhau Model Extension

Extended the NeqSim Bacalhau FPSO replication to include:
- LP/MP gas recompression + mixing with HP gas
- Gas cooling (24HA101, 75°C→36°C) + flash drum (24VG101)
- Seal gas takeoff (5.4% split)
- 2-stage export compression (26KA101: 86→259 bar, 26KA102: 258→554 bar)
- Seal gas JT expansion curve showing 1.35% max condensation at 30 bar

Compressor discharge temperature comparison:
- 26KA101: NeqSim 126.7°C vs UniSim 117.8°C (75% η_is assumed)
- 26KA102: NeqSim 85.9°C vs UniSim 83.6°C
- Suggests UniSim uses ~83-85% isentropic efficiency

---

## 2026-07-05 — EclipseFluidReadWrite Null BIC Fix, UniSim BIP Extraction

### Bug Fix

| Class | Issue | Fix |
|-------|-------|-----|
| `EclipseFluidReadWrite` | `NullPointerException` when E300 file has no BIC section — `kij` array stays `null` | Both `read()` methods now initialize `kij` to zero matrix if BIC section is missing. E300 files without BIC load correctly (all BIPs default to 0.0). |

### Impact on Agents

- **E300 file loading**: Previously required a BIC section or the reader crashed. Now optional (defaults to zero BIPs). However, agents should always include BIC in generated E300 files for accurate results.
- **UniSim → E300 workflow**: BIPs can now be extracted from UniSim via `pp.Kij.Values` (tuple-of-tuples). See `neqsim-unisim-reader` skill Section 1.1 for the COM access pattern.

### Key Discovery

UniSim COM BIP extraction pattern:
```python
kij_obj = pp.Kij          # CDispatch (RealFlexVariable)
raw = kij_obj.Values      # tuple-of-tuples (n×n symmetric matrix)
# Diagonal sentinel = -32767.0, replace with 0.0
```
- `pp.GetInteractionParameter(i,j)` returns 0.0 for PR-LK (correlation BIPs not accessible this way)
- `kij_obj.GetValues()` fails — use `.Values` property instead

---

## 2026-04-08 — IEC 81346 Reference Designation Support

### New Java Package: `neqsim.process.equipment.iec81346`

| Class | Description |
|-------|-------------|
| `IEC81346LetterCode` | Enum for IEC 81346-2 letter codes (A–X). Maps all `EquipmentEnum` values and provides `fromEquipment()` for instanceof-based classification. Now tries `EquipmentEnum.valueOf()` first for faster lookup. |
| `ReferenceDesignation` | Serializable data class holding three IEC 81346 aspects (function `=`, product `-`, location `+`) plus letter code and sequence number. `toReferenceDesignationString()` composes `"=A1-B1+P1"`. Static `parse(String)` factory for round-tripping. |
| `ReferenceDesignationGenerator` | Auto-assigns IEC 81346 designations to a `ProcessSystem` or multi-area `ProcessModel`. Supports hierarchical (`A1.A1`, `A1.A2`) and flat (`A1`, `A2`) function numbering. No-arg constructor + late binding via `generate(ProcessSystem)` / `generate(ProcessModel)`. |

### Modified Interfaces & Classes

| Class | Change |
|-------|--------|
| `ProcessEquipmentInterface` | Added 3 default methods: `getReferenceDesignation()`, `setReferenceDesignation(ReferenceDesignation)`, `getReferenceDesignationString()` |
| `ProcessEquipmentBaseClass` | Added `referenceDesignation` field and overriding getter/setter |
| `ProcessSystem` | Added `generateReferenceDesignations(funcPrefix, locPrefix)` convenience method and `getUnitByReferenceDesignation(String)` for lookup by ref des |
| `ProcessModel` | Added `generateReferenceDesignations(locPrefix)` (flat), `generateReferenceDesignations(funcPrefix, locPrefix)` (hierarchical), and `getUnitByReferenceDesignation(String)` (cross-area lookup) |
| `ProcessConnection` | Added `sourceReferenceDesignation` and `targetReferenceDesignation` fields with getters/setters |
| `ControllerDeviceBaseClass` | Added `referenceDesignation` field with getter/setter/`getReferenceDesignationString()` |
| `ProcessSystemState` | `EquipmentState.fromEquipment()` now captures 6 IEC 81346 properties (`iec81346_referenceDesignation`, `_functionDesignation`, `_productDesignation`, `_locationDesignation`, `_letterCode`, `_sequenceNumber`) |
| `DexpiXmlWriter` (dexpi package) | Writes 5 IEC 81346 `GenericAttribute` elements per equipment when reference designation is set |
| `ProcessAutomation` | `findUnit()` now resolves IEC 81346 reference designation addresses (strings starting with `=` or `-`) |
| `StudyClass` | Added `REFERENCE_DESIGNATION_SCHEDULE` to `DeliverableType` enum (included in CLASS_A and CLASS_B) |
| `EngineeringDeliverablesPackage` | Added `generateReferenceDesignationSchedule()` method and JSON output for ref des schedule |
| `InstrumentScheduleGenerator` | Added `getISAToIEC81346Map()` for ISA-5.1 to IEC 81346 cross-reference |

### Usage Pattern

```java
// Single system
process.generateReferenceDesignations("A1", "P1");
ProcessEquipmentInterface sep = process.getUnitByReferenceDesignation("=A1-B1+P1");

// Multi-area
plant.generateReferenceDesignations("P1");  // flat
plant.generateReferenceDesignations("A1", "P1");  // hierarchical
ProcessEquipmentInterface comp = plant.getUnitByReferenceDesignation("=A2-K1+P1");

// ProcessAutomation addresses accept IEC 81346 strings
double temp = auto.getVariableValue("=A1-B1+P1.gasOutStream.temperature", "C");
```

### Agent Impact
- `ProcessAutomation` addresses now accept IEC 81346 strings (`=A1-B1+P1`)
- DEXPI exports contain IEC 81346 attributes when designations are generated
- Lifecycle state snapshots preserve IEC 81346 designations for versioning
- Engineering deliverables include reference designation schedule (Class A/B)
- ISA-5.1 to IEC 81346 bridging via `InstrumentScheduleGenerator`
- New documentation: `docs/standards/iec81346-reference-designations.md`

---

## 2026-04-05 — Heat Integration, Power Generation, Agentic QA Gate

### New Java Classes

| Class | Package | Description |
|-------|---------|-------------|
| `PinchAnalysis` | `process.equipment.heatexchanger.heatintegration` | Linnhoff pinch analysis: composite curves, grand composite curve, minimum hot/cold utility targeting, pinch temperature. Accepts hot/cold `HeatStream` objects with MCp and temperature range. |
| `HeatStream` | `process.equipment.heatexchanger.heatintegration` | Data model for hot/cold process streams. Auto-classifies HOT/COLD from supply vs target temperature. Celsius convenience API, Kelvin internal storage. |
| `SteamTurbine` | `process.equipment.powergeneration` | Isentropic steam expansion with configurable efficiency. PS/PH flash for outlet conditions. `getPower("kW")` API. |
| `HRSG` | `process.equipment.powergeneration` | Heat Recovery Steam Generator. Takes hot gas exhaust, calculates steam production rate at specified pressure/temperature using approach temperature and effectiveness. |
| `CombinedCycleSystem` | `process.equipment.powergeneration` | Integrates GasTurbine + HRSG + SteamTurbine. `getTotalPower("MW")`, `getOverallEfficiency()`, `toJson()`. |
| `SimulationQualityGate` | `util.agentic` | Automated QA gate for ProcessSystem validation: physical bounds (T > 0 K, P > 0), stream consistency (no NaN/Inf), composition normalization. Returns JSON report with issues, severity, and remediation hints. |

### New Skills (5)

`neqsim-eos-regression`, `neqsim-reaction-engineering`, `neqsim-dynamic-simulation`,
`neqsim-distillation-design`, `neqsim-electrolyte-systems`.

### New Agents (3)

`reaction.engineering`, `control.system`, `emissions.environmental`.

### Usage — PinchAnalysis

```java
PinchAnalysis pinch = new PinchAnalysis(10.0); // deltaT_min = 10 C
pinch.addHotStream("H1", 180, 80, 30);   // 180→80 C, MCp=30 kW/K
pinch.addColdStream("C1", 30, 140, 20);  // 30→140 C, MCp=20 kW/K
pinch.run();
double Qh = pinch.getMinimumHeatingUtility();  // kW
double Qc = pinch.getMinimumCoolingUtility();   // kW
double Tpinch = pinch.getPinchTemperatureC();   // °C
String json = pinch.toJson();
```

### Usage — SimulationQualityGate

```java
ProcessSystem process = new ProcessSystem();
// ... build and run process ...
process.run();
SimulationQualityGate gate = new SimulationQualityGate(process);
gate.validate();
if (!gate.isPassed()) {
    System.out.println(gate.toJson());
}
```

### Usage — CombinedCycleSystem

```java
CombinedCycleSystem cc = new CombinedCycleSystem("CC-1", fuelGasStream);
cc.setCombustionPressure(15.0);
cc.setSteamPressure(40.0);
cc.setSteamTemperature(400.0, "C");
cc.setSteamTurbineEfficiency(0.85);
cc.run();
double totalMW = cc.getTotalPower("MW");
double efficiency = cc.getOverallEfficiency();
```

---

## 2026-03-31 — GibbsReactor Jacobian Fix & Solver Performance Improvements

### Bug Fix — RT-Corrected Off-Diagonal Jacobian (Always On)

The off-diagonal entries of the Newton-Raphson Jacobian were missing an `RT`
factor. The corrected formula `RT * (-1/n_total + d ln(φ)/dn)` is now the only
code path — the legacy formula has been removed. This fixes convergence issues
for adiabatic and mixed-phase equilibrium. No user action needed (previously
required `setUseConsistentOffDiagonal(true)` which is now a deprecated no-op).

### Performance Improvements

Four algorithmic improvements to the Newton-Raphson solver in `GibbsReactor`:

1. **LU decomposition replaces explicit matrix inverse** — The Newton linear
   system $J \cdot \Delta x = -F$ is now solved via EJML's `solve()` (LU
   decomposition) instead of computing $J^{-1}$ then multiplying. ~3× faster
   and more numerically stable. Falls back to pseudo-inverse if LU fails.

2. **Removed SVD condition number check** — The per-iteration `conditionP2()`
   call (O(n³) SVD) has been removed from the hot path. The legacy
   `calculateJacobianInverse()` method is kept for backward compatibility but
   is only used as a fallback.

3. **NASA CEA-style adaptive step sizing** — New opt-in feature via
   `setUseAdaptiveStepSize(true)`. Computes step size each iteration to limit
   max relative mole change (factor of ~5×). Skips near-zero components so
   they can grow freely. Prevents negative moles.

4. **Configurable minimum iterations** — `setMinIterations(int n)` replaces the
   hardcoded `iteration >= 100` convergence guard. Default unchanged at 100 for
   backward compatibility. Set to 3 for simple isothermal systems.

### New Methods on `GibbsReactor`

| Method | Default | Description |
|--------|---------|-------------|
| `setMinIterations(int)` | 100 | Min iterations before convergence check |
| `getMinIterations()` | — | Get current minimum iterations |
| `setUseAdaptiveStepSize(boolean)` | false | Enable adaptive step sizing |
| `isUseAdaptiveStepSize()` | — | Check if adaptive step sizing is active |

### Deprecated Methods on `GibbsReactor`

| Method | Notes |
|--------|-------|
| `setUseConsistentOffDiagonal(boolean)` | No-op. RT correction is always active. |
| `isUseConsistentOffDiagonal()` | Always returns `true`. |

### Migration Notes

- **No breaking changes** — all defaults preserved, existing code runs identically.
- `setUseConsistentOffDiagonal(true)` calls still compile but are no-ops.
- To opt into faster convergence for isothermal systems:
  ```java
  reactor.setUseAdaptiveStepSize(true);
  reactor.setMinIterations(3);
  ```
- The internal method `solveNewtonSystem(double[])` is private — no public API change.

---

## 2026-03-30 — Serialization Cleanup & ProcessLogic Extends Serializable

### Breaking Change — `ProcessLogic` now extends `Serializable`

- **`ProcessLogic`** (`process.logic.ProcessLogic`) now extends `java.io.Serializable`.
  This was required to eliminate the last SpotBugs SE_BAD_FIELD warning caused by
  a compiler-generated synthetic field in `AlarmActionHandler`'s anonymous inner class
  that captured a `ProcessLogic` reference.
- Any class implementing `ProcessLogic` is now implicitly `Serializable`.
- Non-serializable fields in `ProcessLogic` implementations (`ESDLogic`, `HIPPSLogic`,
  `ShutdownLogic`, `StartupLogic`, `SafetyInstrumentedFunction`) have been marked
  `transient`.

### Serialization Audit — 56 SE_BAD_FIELD Warnings Fixed

All SpotBugs SE_BAD_FIELD warnings have been resolved by adding `transient` to
non-serializable fields across 40+ classes. Categories fixed:

- **Thermo phases**: `doubleW[]`, `doubleW[][]`, GERG EOS objects in phase classes
- **Database classes**: JDBC `Connection` and `Statement` fields (6 database classes)
- **Process equipment**: Inner class types (`NetworkNode`, `GibbsComponent`,
  `ReservoirLayer`, `ValveSkid`, `UmbilicalElement`, `TransientWallHeatTransfer`, etc.)
- **Functional interfaces**: `Function`, `BiConsumer`, `Consumer` fields in
  `Adjuster`, `SetPoint`, `Calculator`, `SpreadsheetBlock`, `EquipmentStateAdapter`,
  `BatchStudy`, `SensitivityAnalysis`, `ProcessSafetyScenario`
- **Util/optimizer**: `ProductionOptimizer`, `ProcessLinearizer`, `ProgressCallback`
- **Mechanical design**: `SubseaCostEstimator`, `ShellAndTubeDesignCalculator`,
  `TorgManager`, `MechanicalDesignDataSource`
- **Standards**: Apache Commons Math interpolators in `Standard_ISO6578`
- **Core**: `Thread` in `ThermodynamicOperations`, `BicubicInterpolator` in
  `OLGApropertyTableGeneratorWater`

**Pattern for new code:** When adding fields to any class that extends
`ProcessEquipmentBaseClass`, `MeasurementDeviceBaseClass`, `MechanicalDesign`,
or any other `Serializable` class, mark non-serializable fields `transient`:

```java
// Correct modifier order:
private transient MyNonSerializableType field;
private final transient List<NonSerializableInner> items = new ArrayList<>();
transient SomeType packagePrivateField;  // package-private
```

**Agents/skills updated:** `neqsim-java8-rules/SKILL.md`, `copilot-instructions.md`.

---

## 2026-03-27 — UniSimToNeqSim Python Code Generation

### New Method — `to_python()` on `UniSimToNeqSim`

- **`UniSimToNeqSim.to_python(include_subflowsheets=True)`** generates a self-contained,
  **human-readable Python script** that recreates the entire UniSim process using
  explicit `jneqsim` API calls — instead of the opaque JSON intermediate format.
- The generated script includes: all imports, fluid/EOS definition with components,
  feed streams with T/P/flow, every equipment item in topological order wired through
  outlet stream references (`getGasOutStream()`, `getLiquidOutStream()`,
  `getSplitStream(int)`, `getOutletStream()`), and `process.run()`.
- Handles all supported equipment types: Separator, ThreePhaseSeparator, Mixer,
  Splitter, Compressor, ThrottlingValve, Cooler, Heater, HeatExchanger, Pump,
  Expander, AdiabaticPipe, Recycle, DistillationColumn, StreamSaturatorUtil.
- Sanitizes variable names (spaces, hyphens, special chars → underscores; numeric
  prefixes get `_` prefix; uniqueness guaranteed).
- Located in `devtools/unisim_reader.py`.

**Usage:**
```python
from devtools.unisim_reader import UniSimReader, UniSimToNeqSim

reader = UniSimReader(visible=False)
model = reader.read(r"path\to\file.usc")
reader.close()

converter = UniSimToNeqSim(model)
python_code = converter.to_python()

with open("my_process.py", "w") as f:
    f.write(python_code)
```

**Agents/skills updated:** `unisim.reader.agent.md`, `neqsim-unisim-reader/SKILL.md`,
`PR_DESCRIPTION_PROCESS_EXTRACTION.md`, `devtools/README.md`.

---

## 2026-03-27 — Distillation Column Internals, Air Cooler, PVF Flash, Amine Framework

### New Classes — Distillation Internals

- **`PackedColumn`** (`process.equipment.distillation`) — Extends `DistillationColumn`
  for packed absorption/distillation columns (absorbers, strippers, contactors).
  Wraps rigorous VLE column solver and adds packing-specific functionality:
  - HETP calculation from packed bed height
  - Packing hydraulics via `PackingHydraulicsCalculator`
  - Built-in presets (Pall Ring, Mellapak, IMTP, etc.)
  - API: `setPackedHeight()`, `setPackingType()`, `setStructuredPacking()`,
    `addSolventStream()`, `getHETP()`, `getPercentFlood()`, `toJson()`

- **`ShortcutDistillationColumn`** (`process.equipment.distillation`) — Rapid conceptual
  design using Fenske-Underwood-Gilliland (FUG) method:
  - Fenske: minimum stages from relative volatility
  - Underwood: minimum reflux ratio
  - Gilliland: actual stages (Molokanov correlation)
  - Kirkbride: optimal feed tray location
  - API: `setLightKey()`, `setHeavyKey()`, `setLightKeyRecoveryDistillate()`,
    `setRefluxRatioMultiplier()`, `getMinimumNumberOfStages()`,
    `getActualRefluxRatio()`, `getResultsJson()`

- **`ColumnInternalsDesigner`** (`process.equipment.distillation.internals`) — High-level
  internals sizing facade. Evaluates hydraulic performance on every tray of a converged
  `DistillationColumn`, identifies controlling tray, sizes column diameter.
  Supports tray (sieve, valve, bubble-cap) and packed modes.
  API: `calculate()`, `getRequiredDiameter()`, `isDesignOk()`, `toJson()`

- **`TrayHydraulicsCalculator`** (`process.equipment.distillation.internals`) — Per-tray
  hydraulic evaluation for sieve, valve, and bubble-cap trays. Correlations: Fair
  (flooding, entrainment), Sinnott (weeping), Francis weir (downcomer backup),
  O'Connell (tray efficiency). References: Kister (1992), Ludwig (2001), Sinnott (2005).

- **`PackingHydraulicsCalculator`** (`process.equipment.distillation.internals`) — Packing
  hydraulics engine with Eckert GPDC (flooding), Leva (pressure drop), Onda 1968
  (mass transfer coefficients), HTU/HETP. Built-in presets for 10 random packings
  and 7 structured packings (Mellapak 125Y–500Y, Flexipac 1Y–3Y).

### New Class — AirCooler Rewrite

- **`AirCooler`** (`process.equipment.heatexchanger`) — Complete rewrite from simple
  air flow calculator to full API 661 thermal design model (~960 lines):
  - Briggs-Young fin-tube correlation for air-side HTC
  - Schmidt annular fin efficiency
  - Robinson-Briggs air-side pressure drop
  - LMTD with F-correction for cross-flow
  - Fan model with cubic polynomial fan curve (dP vs Q)
  - Ambient temperature correction (ITD ratio method)
  - Bundle sizing (tubes per row, total tubes, face area, fin area)
  - Comprehensive `toJson()` report
  - API: `setDesignAmbientTemperature(T, "C")`, `setNumberOfTubeRows()`,
    `setTubeLength()`, `getFanPower("kW")`, `getOverallU()`, `toJson()`

### New Class — PVF Flash

- **`PVFflash`** (`thermodynamicoperations.flashops`) — Pressure-Vapor Fraction flash.
  Given P + target vapor fraction β → find temperature. Uses Illinois method
  (accelerated regula falsi). Integrated into `ThermodynamicOperations` via
  `ops.PVFflash(beta)`. β=0.0 → bubble point, β=1.0 → dew point.

### New Classes — Amine Framework

- **`AmineSystem`** (`thermo.util.amines`) — Convenience wrapper for creating
  electrolyte-CPA amine systems. Supports MEA, DEA, MDEA, aMDEA. Auto-configures
  species (neutral + ionic + carbamate), mixing rules, reactions, physical properties.
  - Enum: `AmineType` (`MEA`, `DEA`, `MDEA`, `AMDEA`)
  - API: `new AmineSystem(AmineType, T_K, P_bara, amineMolFraction, co2Loading)`,
    `getSystem()`, `getAmineType()`

- **`AmineViscosity`** (`physicalproperties.methods.liquidphysicalproperties.viscosity`) —
  Correlations for CO₂-loaded amine solution viscosity:
  - Weiland et al. (1998) for MEA, DEA, aMDEA
  - Teng et al. (1994) for MDEA
  - Auto-detects amine type from fluid composition

### Updated Classes

- **`DistillationColumn`** — Column specification framework with `ColumnSpecification`,
  secant-method outer adjustment loop (+531 lines)

- **`ProcessSystem`** — Three new UniSim/HYSYS-style stream summary methods:
  - `getStreamSummaryTable()` — formatted text table with T, P, flow, composition
  - `getStreamSummaryJson()` — JSON output for programmatic access
  - `getAllStreams()` — collects all unique `StreamInterface` objects

- **`ThermodynamicOperations`** — Added `PVFflash(double vaporFraction)` entry point

- **`ThermalDesignCalculator`** — Added `toJson()` method for JSON reporting

### New Database Entries

- **COMP.csv**: MEA+ (ID 1259, charge=+1) and MEACOO- (ID 1260, charge=-1)
- **REACTIONDATA.csv**: MEA/DEA equilibrium reactions (Austgen 1989)
- **STOCCOEFDATA.csv**: Updated stoichiometric coefficients for amine reactions

### Usage Examples

```java
// Packed column absorber
PackedColumn absorber = new PackedColumn("CO2 Absorber", 10, feed);
absorber.setPackedHeight(15.0);
absorber.setPackingType("Mellapak 250Y");
absorber.setStructuredPacking(true);
absorber.addSolventStream(leanAmine, 1);
absorber.run();

// Shortcut design
ShortcutDistillationColumn shortcut = new ShortcutDistillationColumn("Deprop", feed);
shortcut.setLightKey("propane");
shortcut.setHeavyKey("n-butane");
shortcut.setLightKeyRecoveryDistillate(0.98);
shortcut.setHeavyKeyRecoveryDistillate(0.02);
shortcut.run();

// Air cooler
AirCooler cooler = new AirCooler("Gas Cooler", hotStream);
cooler.setOutTemperature(40.0, "C");
cooler.setDesignAmbientTemperature(15.0, "C");
cooler.run();
double fanPower = cooler.getFanPower("kW");

// PVF flash
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.PVFflash(0.5);  // Find T where β = 0.5

// Amine system
AmineSystem amine = new AmineSystem(AmineSystem.AmineType.MEA,
    273.15 + 40.0, 1.0, 0.30, 0.40);
SystemInterface fluid = amine.getSystem();

// Stream summary
process.run();
System.out.println(process.getStreamSummaryTable());
String json = process.getStreamSummaryJson();
```

### New Tests

| Test | Methods |
|------|---------|
| `PackedColumnTest` | 4 tests: basic absorber, setters/getters, condenser/reboiler, JSON |
| `ShortcutDistillationColumnTest` | 3 tests: deethanizer, depropanizer, JSON |
| `ColumnInternalsDesignerTest` | 4 tests: sieve tray, convenience, packed, structured |
| `PackingHydraulicsCalculatorTest` | 6 tests: Pall Ring, structured, diameter, presets, mass transfer, dP |
| `TrayHydraulicsCalculatorTest` | 6 tests: sieve, diameter, valve, liquid rate, weeping, O'Connell |
| `ProcessSystemStreamSummaryTest` | 3 tests: text table, JSON, getAllStreams |
| `PVFflashTest` | 4 tests: mid-fraction, bubble point, dew point, consistency |
| `AirCoolerTest` | 14 new tests: LMTD, U, fin efficiency, fan, bundle, ITD, JSON |
| `ColumnSpecificationTest` | Column spec purity/recovery/flow rate tests |

### New Documentation

- `docs/development/NEQSIM_VS_UNISIM_COMPARISON.md` — NeqSim vs UniSim feature comparison
- `docs/process/process-simulation-enhancements.md` — User guide for all new capabilities
- `examples/notebooks/air_cooler_and_packed_column.ipynb` — Jupyter notebook example

### Agents/Skills to Update

- `neqsim-capability-map` — Add PackedColumn, ShortcutDistillationColumn, ColumnInternalsDesigner,
  TrayHydraulicsCalculator, PackingHydraulicsCalculator, PVFflash, AmineSystem, AirCooler
- `neqsim-api-patterns` — Add packed column, shortcut distillation, air cooler, PVF flash,
  amine system, stream summary patterns
- `CONTEXT.md` — Add distillation internals, amine framework to repo map
- `docs/development/CODE_PATTERNS.md` — Add packed column, shortcut, air cooler, amine patterns

---

## 2026-03-27 — Column Specification Flexibility

### New Classes

- **`ColumnSpecification`** (`process.equipment.distillation`) — Represents one
  degree-of-freedom specification for a distillation column. Five specification
  types via `SpecificationType` enum:
  - `PRODUCT_PURITY` — mole-fraction purity target for a product stream
  - `REFLUX_RATIO` — condenser reflux ratio (L/D)
  - `COMPONENT_RECOVERY` — fractional recovery of a named component (0–1)
  - `PRODUCT_FLOW_RATE` — molar flow rate target (kmol/h)
  - `DUTY` — condenser or reboiler duty (W)
  - `ProductLocation` enum: `TOP`, `BOTTOM`
  - Configurable tolerance (default 1e-4) and max iterations (default 20)
  - Full input validation, serializable

### Updated Classes

- **`DistillationColumn`** — Integrated `ColumnSpecification` support:
  - New convenience methods: `setTopProductPurity(component, target)`,
    `setBottomProductPurity(component, target)`, `setCondenserRefluxRatio(ratio)`,
    `setReboilerBoilupRatio(ratio)`, `setTopComponentRecovery(component, fraction)`,
    `setBottomComponentRecovery(component, fraction)`, `setTopProductFlowRate(rate)`,
    `setBottomProductFlowRate(rate)`, `getTopSpecification()`, `getBottomSpecification()`
  - Outer secant-method adjustment loop (`solveWithSpecifications()`) iterates
    condenser/reboiler temperatures to satisfy purity, recovery, or flow-rate specs.
    Safeguards: max step 50 K, temperature bounds 100–1000 K.
  - Direct-set specs (reflux ratio, duty) applied before inner solve without outer loop.
  - Builder pattern extended: `topSpecification()`, `bottomSpecification()`,
    `topProductPurity()`, `bottomProductPurity()` methods.

### Usage

```java
// Product purity specification
DistillationColumn column = new DistillationColumn("T-100", 25, true, true);
column.addFeedStream(feed, 12);
column.setTopPressure(25.0, "bara");
column.setTopProductPurity("ethane", 0.95);      // 95 mol% ethane overhead
column.setBottomProductPurity("propane", 0.98);   // 98 mol% propane bottoms
column.run();

// Component recovery specification
column.setTopComponentRecovery("ethane", 0.99);   // 99% ethane recovery overhead
column.run();

// Reflux ratio specification (applied directly, no outer loop)
column.setCondenserRefluxRatio(3.5);
column.run();

// Builder pattern with specs
DistillationColumn col = DistillationColumn.builder()
    .name("Deethanizer")
    .numberOfTrays(25)
    .hasCondenser(true)
    .hasReboiler(true)
    .topPressure(25.0)
    .topProductPurity("ethane", 0.95)
    .bottomProductPurity("propane", 0.98)
    .build();
```

### Agents/Skills to Update

- `neqsim-api-patterns` — Add column specification pattern
- `docs/process/equipment/distillation.md` — Add Column Specifications section
- `docs/development/CODE_PATTERNS.md` — Add distillation specification pattern

---

## 2026-03-26 — Heat Exchanger Thermal-Hydraulic Design Toolkit

### New Classes

- **`ThermalDesignCalculator`** (`process.mechanicaldesign.heatexchanger`) — Central
  calculator for tube-side and shell-side heat transfer coefficients, overall U,
  pressure drops, and zone-by-zone analysis. Supports Gnielinski (tube-side) and
  Kern or Bell-Delaware (shell-side) methods.
  - Inner enum: `ShellSideMethod` (`KERN`, `BELL_DELAWARE`)

- **`BellDelawareMethod`** (`process.mechanicaldesign.heatexchanger`) — Static utility
  for industry-standard Bell-Delaware shell-side HTC and pressure drop with J-factor
  correction factors (Jc, Jl, Jb, Js, Jr) and Zhukauskas correlation for tube banks.

- **`VibrationAnalysis`** (`process.mechanicaldesign.heatexchanger`) — Flow-induced
  vibration screening per TEMA RCB-4.6. Evaluates vortex shedding (Von Karman),
  fluid-elastic instability (Connors), and acoustic resonance.
  - Inner class: `VibrationResult` with pass/fail, natural frequency, critical velocity

- **`LMTDcorrectionFactor`** (`process.mechanicaldesign.heatexchanger`) — LMTD correction
  factor F_t for multi-pass configurations using Bowman-Mueller-Nagle (1940) method.
  Supports 1-N shell passes, calculates R and P parameters, recommends minimum shell
  passes needed.

- **`InterfacialFriction`** (`process.equipment.pipeline.twophasepipe.closure`) —
  Interfacial friction correlations for two-fluid pipe model. Flow regime-dependent:
  Taitel-Dukler (stratified smooth), Andritsos-Hanratty (stratified wavy), Wallis
  (annular), Oliemans (slug).
  - Inner class: `InterfacialFrictionResult` with shear, friction factor, slip velocity

### Updated Classes

- **`ShellAndTubeDesignCalculator`** — Major expansion: now includes ASME VIII Div.1
  pressure design (UHX-13 tubesheet, UG-27 MAWP, UG-37 nozzle reinforcement, UG-99
  hydro test), NACE MR0175/ISO 15156 sour service assessment, thermal-hydraulic
  integration (auto-runs `ThermalDesignCalculator` + `VibrationAnalysis` when fluid
  properties are provided), weight/cost estimation with Bill of Materials.

- **`HeatExchangerMechanicalDesign`** — New high-level orchestrator auto-selecting
  exchanger type (shell-and-tube, plate, air-cooled) based on configurable criteria
  (`MIN_AREA`, `MIN_WEIGHT`, `MIN_PRESSURE_DROP`). Handles TEMA class (R/C/B),
  shell types (E/F/G/H/J/K/X), fouling resistances, velocity limits, materials,
  and NACE sour service.

- **`HeatExchanger`** — Added `getRatingCalculator()` returning `ThermalDesignCalculator`
  for rating mode. Added `getThermalEffectiveness()` and `calcThermalEffectivenes(NTU, Cr)`.

- **`TwoFluidPipe`** — Enhanced with boundary condition API (STREAM_CONNECTED,
  CONSTANT_FLOW, CONSTANT_PRESSURE, CLOSED), elevation profile support, temperature
  profile output (K and °C), liquid inventory calculation, cooldown time estimation.

- **`TwoFluidConservationEquations`** — Extended to 7 conservation equations for
  three-phase (gas/oil/water) with separate oil and water momentum. Uses AUSM+ flux
  scheme and MUSCL reconstruction.

- **`Pump`** — Added pump curve support with affinity law scaling, cavitation detection
  (NPSH available vs required), operating status monitoring, outlet temperature mode.

### Usage

```java
// Standalone thermal design
ThermalDesignCalculator calc = new ThermalDesignCalculator();
calc.setTubeODm(0.01905);
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
calc.setTubeSideFluid(995.0, 0.0008, 4180.0, 0.62, 5.0, true);
calc.setShellSideFluid(820.0, 0.003, 2200.0, 0.13, 8.0);
calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
calc.calculate();
String json = calc.toJson();

// Vibration screening
VibrationAnalysis.VibrationResult result = VibrationAnalysis.performScreening(
    tubeOD, tubeID, unsupportedSpan, tubeMaterialE, tubeDensity,
    fluidDensityTube, fluidDensityShell, endCondition,
    crossflowVelocity, tubePitch, triangularPitch, shellID, sonicVelocity
);
boolean safe = result.passed;

// LMTD correction factor
double ft = LMTDcorrectionFactor.calcFt(tHotIn, tHotOut, tColdIn, tColdOut, shellPasses);
int minShells = LMTDcorrectionFactor.requiredShellPasses(tHotIn, tHotOut, tColdIn, tColdOut);

// Full mechanical design with thermal-hydraulic
ShellAndTubeDesignCalculator stCalc = new ShellAndTubeDesignCalculator();
stCalc.setTubeSideFluidProperties(density, viscosity, cp, k, massFlow, isHeating);
stCalc.setShellSideFluidProperties(density, viscosity, cp, k, massFlow);
stCalc.calculate();  // runs mech + thermal + vibration
String report = stCalc.toJson();
```

### Agents/Skills to Update

- `neqsim-capability-map` — Add ThermalDesignCalculator, BellDelawareMethod, VibrationAnalysis, LMTDcorrectionFactor, InterfacialFriction
- `neqsim-api-patterns` — Add HX thermal design pattern
- `CONTEXT.md` — Add HX thermal design to repo map
- `docs/REFERENCE_MANUAL_INDEX.md` — Add thermal_hydraulic_design.md entry

---

## 2026-03-26 — InstrumentScheduleGenerator and Updated Engineering Deliverables

### New Classes

- **`InstrumentScheduleGenerator`** (`process.mechanicaldesign`) — ISA-5.1 tagged
  instrument schedule generator that bridges engineering deliverables and dynamic
  simulation. Walks a `ProcessSystem`, creates `MeasurementDeviceInterface` objects
  (PT, TT, LT, FT) with `AlarmConfig` (HH/H/L/LL thresholds) and SIL ratings.
  With `setRegisterOnProcess(true)`, live devices are registered on the ProcessSystem.

### Updated Classes

- **`StudyClass`** — Added `INSTRUMENT_SCHEDULE` to `DeliverableType` enum.
  CLASS_A now produces 7 deliverables (was 6), CLASS_B produces 4 (was 3).
- **`EngineeringDeliverablesPackage`** — Added `generateInstrumentSchedule()` and
  `getInstrumentSchedule()`. The `INSTRUMENT_SCHEDULE` case is handled in `generate()`.

### StudyClass Deliverable Counts (IMPORTANT for tests)

| Study Class | Count | Deliverables |
|-------------|-------|-------------|
| CLASS_A | 7 | PFD, Thermal, Alarm/Trip, Spares, Fire, Noise, Instrument Schedule |
| CLASS_B | 4 | PFD, Thermal, Fire, Instrument Schedule |
| CLASS_C | 1 | PFD |

### Usage

```java
InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
gen.setRegisterOnProcess(true);  // creates live MeasurementDevice objects
gen.generate();
List<InstrumentScheduleGenerator.InstrumentEntry> entries = gen.getEntries();
String json = gen.toJson();

// Through package
EngineeringDeliverablesPackage pkg =
    new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
pkg.generate();  // includes instrument schedule
InstrumentScheduleGenerator instrSchedule = pkg.getInstrumentSchedule();
```

### Agents/Skills Updated

- `neqsim-capability-map` SKILL — Expanded Measurement Devices table, added Engineering Deliverables subsection
- `neqsim-api-patterns` SKILL — Added Engineering Deliverables section with instrument schedule pattern
- `engineering.deliverables.agent.md` — Added instrument schedule deliverable section and code examples
- `field.development.agent.md` — Added item 17 (instrument schedule), updated StudyClass table and class map
- `AGENTS.md` — Updated key paths table
- `CONTEXT.md` — Updated repo map and key locations table

---

## 2026-03-25 — TwoFluidPipe Boundary Condition API

### New API

Added public setters for configuring inlet and outlet boundary conditions during
transient `TwoFluidPipe` simulations. Includes CLOSED BC for shut-in/surge scenarios.

### New Methods

```java
// Set boundary condition types
pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);  // default
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
pipe.setInletBoundaryCondition(BoundaryCondition.CLOSED);            // NEW: blocked
pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE); // default
pipe.setOutletBoundaryCondition(BoundaryCondition.CLOSED);            // NEW: blocked

// Query boundary condition types
BoundaryCondition inletBC = pipe.getInletBoundaryCondition();
BoundaryCondition outletBC = pipe.getOutletBoundaryCondition();

// Set explicit values for CONSTANT_FLOW / CONSTANT_PRESSURE BCs
pipe.setInletMassFlow(50.0);             // kg/s
pipe.setInletMassFlow(180000, "kg/hr");  // with unit
pipe.setInletPressure(60.0, "bara");     // with unit

// Convenience methods for shut-in scenarios
pipe.closeOutlet();                       // Set outlet BC to CLOSED
pipe.openOutlet();                        // Restore to CONSTANT_PRESSURE
pipe.openOutlet(30.0, "bara");            // Open with specified pressure
pipe.closeInlet();                        // Set inlet BC to CLOSED
pipe.openInlet();                         // Restore to STREAM_CONNECTED
boolean closed = pipe.isOutletClosed();   // Check if outlet is blocked
boolean closed = pipe.isInletClosed();    // Check if inlet is blocked
```

### Boundary Condition Types

| Type | Description |
|------|-------------|
| `STREAM_CONNECTED` | Flow rate, T, composition from connected stream (default inlet) |
| `CONSTANT_FLOW` | Fixed mass flow via `setInletMassFlow()` |
| `CONSTANT_PRESSURE` | Fixed pressure (default outlet, optional inlet) |
| `CLOSED` | Zero velocity (blocked/shut-in) — pressure floats |

### Common Configurations

| Config | Inlet BC | Outlet BC | Inlet P | Flow |
|--------|----------|-----------|---------|------|
| Default | STREAM_CONNECTED | CONSTANT_PRESSURE | Computed | From stream |
| Explicit flow | CONSTANT_FLOW | CONSTANT_PRESSURE | Computed | Fixed |
| Both P fixed | CONSTANT_PRESSURE | CONSTANT_PRESSURE | Fixed | Computed |
| Shut-in | STREAM_CONNECTED | CLOSED | Computed | From stream |
| Blowdown | CLOSED | CONSTANT_PRESSURE | Floats | Zero |
| Blocked pipe | CLOSED | CLOSED | Floats | Zero |

### Python Usage

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe
BoundaryCondition = TwoFluidPipe.BoundaryCondition

pipe = TwoFluidPipe("Pipeline", feed)
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW)
pipe.setInletMassFlow(50.0)
pipe.setOutletPressure(30.0, "bara")

# Shut-in scenario
pipe.closeOutlet()
for t in range(60):
    pipe.runTransient(1.0)
pipe.openOutlet(30.0, "bara")  # Reopen
```

### Documentation

- Updated [Pipeline Recipes](docs/cookbook/pipeline-recipes.md) with Boundary Conditions section

### Migration

No breaking changes. Existing code using default BCs continues to work unchanged.

---

## 2026-06-18 — TwoFluidPipe Transient & Pressure Gradient Improvements

### Bug Fixes

- **Transient inlet pressure override (FIXED):** `applyBoundaryConditions()` was
  overwriting the inlet pressure from the stream during transient runs, preventing
  the pressure profile from evolving. Added `isTransientMode` boolean flag; when
  `true` (set automatically by `runTransient()`), inlet pressure comes from
  `reconstructPressureProfile()` (backward march from fixed outlet BC) instead of
  from the inlet stream.

- **Outlet pressure captured before convergence (FIXED):** `outletPressure` was
  being captured before `runSteadyState()` converged, recording the initial guess
  (~54 bar) rather than the converged value (~59 bar). Now captured after
  steady-state convergence.

### Updated Files

- **`TwoFluidPipe.java`** (`process.equipment.pipeline`):
  - New field: `private boolean isTransientMode = false;`
  - New method: `reconstructPressureProfile()` — backward marches from fixed outlet
    boundary condition to compute inlet pressure from the local pressure gradient.
  - New method: `calcDarcyFrictionFactor(rho, velocity, D, mu)` — extracted Haaland
    equation (turbulent), 64/Re (laminar), linear interpolation (transitional
    Re 2300–4000). Used in `estimatePressureGradient()`.
  - Updated `estimatePressureGradient()`: replaced holdup-weighted viscosity
    (`αG*μG + αL*μL`) with McAdams quality-based harmonic averaging
    (`1/(x/μG + (1-x)/μL)`) where x is vapor mass fraction. Density remains
    holdup-weighted (`αG*ρG + αL*ρL`).
  - Updated `applyBoundaryConditions()`: inlet pressure only set from stream when
    `!isTransientMode`.
  - Updated `runTransient()`: sets `isTransientMode = true` at entry, calls
    `reconstructPressureProfile()` for inlet pressure.

### New Test File

- **`TwoFluidPipeBenchmarkTest.java`** (`test/.../pipeline/`) — 19 benchmark tests
  in 8 categories:
  1. **SinglePhaseTests** (2): Gas and liquid horizontal flow
  2. **TwoPhaseHorizontalTests** (3): Gas-dominated, liquid-dominated, intermediate GOR
  3. **InclinedFlowTests** (3): Uphill 5°, downhill 5°, vertical riser
  4. **ThreePhaseTests** (2): Moderate and high water cut
  5. **ConsistencyTests** (3): dP monotonicity, smooth pressure profile, holdup sum = 1
  6. **TransientTests** (1): 200 m pipe, 100% flow rate step-change, holdup evolution
  7. **CrossValidationTests** (1): GLR sweep 0.50–0.95 vs PipeBeggsAndBrills
  8. **LiteratureValidationTests** (4): Moody chart, holdup vs gas velocity, gravity in
     vertical riser, diameter D⁻⁵ scaling

### Benchmark Results Summary

| Test | TwoFluidPipe / BeggsAndBrill | Notes |
|------|-----|-------|
| Single-phase gas | 0.98 | Excellent agreement |
| Two-phase GLR 0.50–0.95 | 0.81–1.33 | Within engineering accuracy |
| Vertical riser | 1.04 bar gravity dP | Matches ρgH calculation |
| Diameter scaling (6"/12") | 33.7× | Close to theoretical ~32× (D⁻⁵) |
| Transient holdup evolution | 0.19 → 0.09 | Holdup decreases after flow increase |

### Migration

No breaking API changes. Existing code calling `run()` and `runTransient()` will
behave identically (steady-state) or more correctly (transient now evolves).

---

## 2026-03-24 — Field Development Agent and Skills

### New Agent

- **`@field.development`** (`.github/agents/field.development.agent.md`) — Expert agent for oil & gas field development workflows: concept selection, subsea tieback, production forecasting, and project economics (NPV/IRR). Orchestrates concept screening through final investment decision.

### New Skills (4)

- **`neqsim-field-development`** — Field development lifecycle (DG1→Operations), concept selection, reservoir/well/facility API patterns
- **`neqsim-field-economics`** — NPV, IRR, cash flow engines, Norwegian NCS and UK UKCS tax models, cost estimation
- **`neqsim-subsea-and-wells`** — Subsea equipment APIs, well casing design (API 5C3/NORSOK D-010), SURF cost estimation, tieback analysis
- **`neqsim-production-optimization`** — Decline curves, bottleneck analysis, gas lift optimization, IOR/EOR screening, emissions tracking

### Updated Files

- `AGENTS.md` — Added field development paths and skills references
- `CONTEXT.md` — Updated agent/skill counts (16 agents, 14 skills)
- `.github/agents/router.agent.md` — Added field development routing
- `.github/agents/README.md` — Added field development section
- `.github/agents/solve.task.agent.md` — Added `@field.development` to delegation table
- `docs/integration/ai_agents_reference.md` — Added agent entry, 4 skill entries, updated cross-reference tables
- `docs/integration/ai_agentic_programming_intro.md` — Updated count and added agent to catalog
- `docs/integration/ai_workflow_examples.md` — Added Example 8: Field Development Concept Selection
- `docs/fielddevelopment/README.md` — Added AI Agent & Skills section
- `docs/REFERENCE_MANUAL_INDEX.md` — Updated description

### Migration

No code changes needed. Use `@field.development` for field development tasks that were previously handled by `@solve.task`.

---

## 2026-03-23 — CO2 Injection Well Analysis Module (NIP-1 to NIP-6)

### New Classes

- **`CO2InjectionWellAnalyzer`** (`process.equipment.pipeline`) — High-level safety orchestrator for CO2 injection wells:
  - Steady-state wellbore flow via PipeBeggsAndBrills
  - Phase boundary scanning (P-T space flash grid)
  - Impurity enrichment mapping in two-phase region
  - Shutdown safety assessment at various trapped WHPs
  - Returns `isSafeToOperate()` boolean and comprehensive `getResults()` map
  - API: `setFluid()`, `setWellGeometry()`, `setOperatingConditions()`, `setFormationTemperature()`, `addTrackedComponent()`, `runFullAnalysis()`

- **`ImpurityMonitor`** (`process.measurementdevice`) — Phase-partitioned composition tracking device:
  - Extends `StreamMeasurementDeviceBaseClass`
  - Tracks gas/liquid/bulk mole fractions and enrichment factors (K-values = y/z)
  - Configurable alarm thresholds per component
  - API: `addTrackedComponent(name, alarmThreshold)`, `getGasPhaseMoleFraction()`, `getEnrichmentFactor()`, `isAlarmExceeded()`, `getFullReport()`

- **`TransientWellbore`** (`process.equipment.pipeline`) — Shutdown cooling transient model:
  - Extends `Pipeline`
  - Exponential temperature decay toward formation temperature (geothermal gradient)
  - Vertical segmentation with TP flash at each depth and time step
  - Tracks phase evolution and impurity enrichment over time
  - Inner class `TransientSnapshot` stores per-timestep depth profiles
  - API: `setWellDepth()`, `setFormationTemperature(topK, bottomK)`, `setShutdownCoolingRate(tau_hr)`, `runShutdownSimulation(hours, dt)`

- **`CO2FlowCorrections`** (`process.equipment.pipeline`) — Static utility for CO2-specific flow corrections:
  - `isCO2DominatedFluid()` — checks >50 mol% CO2
  - `getLiquidHoldupCorrectionFactor()` — returns 0.70-0.85 based on reduced temperature
  - `getFrictionCorrectionFactor()` — returns 0.85-0.95
  - `estimateCO2SurfaceTension()` — Sugden correlation
  - `isDensePhase()`, `getReducedTemperature()`, `getReducedPressure()`

### Modified Classes

- **`PipeBeggsAndBrills`** — Added formation temperature gradient support (NIP-1):
  - New method: `setFormationTemperatureGradient(double inletTemp, double gradient, String unit)`
  - Enables depth-dependent heat transfer with geothermal gradient
  - Sign convention: negative gradient = temperature increases with depth

### Test Coverage

- 19 tests in `CO2InjectionNIPsTest.java` covering all NIP classes

### Documentation

- New doc: `docs/process/co2_injection_well_analysis.md`
- Updated: `REFERENCE_MANUAL_INDEX.md`, `docs/process/README.md`

---

## 2026-03-22 — Motor Mechanical Design and Combined Equipment Design Report

### New Classes

- **`MotorMechanicalDesign`** (`process.mechanicaldesign.motor`) — Physical/mechanical design of electric motors:
  - Foundation loads (static + dynamic) and mass per IEEE 841 (3:1 ratio)
  - Cooling classification per IEC 60034-6 (IC411/IC611/IC81W)
  - Bearing selection and L10 life per ISO 281 (ball vs roller, lubrication)
  - Vibration limits per IEC 60034-14 Grade A and ISO 10816-3 zone classification
  - Noise assessment per IEC 60034-9 and NORSOK S-002 (83 dB(A) at 1m)
  - Enclosure/IP rating per IEC 60034-5, Ex marking per IEC 60079 (Zone 0/1/2)
  - Environmental derating per IEC 60034-1 (altitude: 1%/100m above 1000m; temperature: 2.5%/°C above 40°C)
  - Motor weight and dimensional estimation
  - Constructors: `MotorMechanicalDesign(double shaftPowerKW)`, `MotorMechanicalDesign(ElectricalDesign)`

- **`EquipmentDesignReport`** (`process.mechanicaldesign`) — Combined design report for any process equipment:
  - Orchestrates mechanical design + electrical design + motor mechanical design
  - Produces FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE verdict
  - Checks: motor undersizing, excessive derating, noise exceedance, low bearing life
  - `toJson()` — comprehensive JSON with all three design disciplines
  - `toLoadListEntry()` — summary for electrical load list integration
  - Works with any `ProcessEquipmentInterface` (compressor, pump, separator, etc.)

### Key API Methods

```java
// Motor mechanical design — standalone
MotorMechanicalDesign motorDesign = new MotorMechanicalDesign(250.0);
motorDesign.setPoles(4);
motorDesign.setAmbientTemperatureC(45.0);
motorDesign.setAltitudeM(500.0);
motorDesign.setHazardousZone(1);
motorDesign.calcDesign();
motorDesign.toJson();

// Combined report — from any equipment
EquipmentDesignReport report = new EquipmentDesignReport(compressor);
report.setUseVFD(true);
report.setRatedVoltageV(6600);
report.setHazardousZone(1);
report.generateReport();
report.getVerdict();   // "FEASIBLE" / "FEASIBLE_WITH_WARNINGS" / "NOT_FEASIBLE"
report.toJson();
```

### Bug Fix
- Fixed IP rating override in Zone 0 hazardous areas — IEEE 841 IP55 minimum no longer overrides Zone 0 IP66 requirement

### Test Coverage
- 22 new tests in `MotorMechanicalDesignTest`: standalone design, small/large motors, altitude/temperature derating, hazardous area enclosure, vibration zones, NORSOK noise compliance, bearing L10 life, VFD notes, applied standards, compressor integration, JSON/Map output, combined reports

### Documentation
- New doc: `docs/process/motor-mechanical-design.md`
- Updated: `REFERENCE_MANUAL_INDEX.md`, capability map, `mechanical_design.md`, `electrical-design.md`

---

## 2026-03-22 — Heat Exchanger Mechanical Design Standards Expansion

### New Data Files
- **`HeatExchangerTubeMaterials.csv`** — 22 material grades for tubes and shells with
  SMYS, SMTS, allowable stress, thermal conductivity, NACE compliance, and temperature limits.
  Covers SA-179, SA-213 (T11/T22/TP304/304L/316/316L/321), duplex/super-duplex, Cu-Ni,
  titanium, Inconel, Hastelloy, Incoloy, and shell plate materials.

### Standards Database Additions
| Standard | Equipment Types | New Entries |
|----------|----------------|-------------|
| API-660 9th Ed | HeatExchanger | 21 entries (design margins, velocity limits, hydro test, joint efficiency, vibration) |
| API-661 7th Ed | HeatExchanger/Cooler | 9 entries (air cooler fins, face velocity, fan efficiency) |
| API-662 1st Ed | HeatExchanger | 10 entries (plate HX gasketed/welded pressure/temp limits) |
| NORSOK-P-002 Rev 5 | HeatExchanger/Cooler/Heater | 14 entries (duty/area/pressure margins, velocity limits) |
| NORSOK-M-001 Rev 6 | HeatExchanger/Cooler/Heater | 7 entries (min/max design temp, hardness, H2S limits) |
| ASME VIII Div.1 | HeatExchanger | 19 entries (UG-27, UHX-13, UG-37, UG-99, allowable stresses, joint efficiencies, flange ratings) |
| ISO-16812 | HeatExchanger | 12 entries (velocity, fouling resistance, baffle cut range) |
| ISO-15547 | HeatExchanger | 3 entries (plate-fin aluminium HX) |
| EN-13445 | HeatExchanger | 3 entries (pressure, joint efficiency, corrosion allowance) |
| PD-5500 | HeatExchanger | 3 entries (pressure, joint efficiency, corrosion allowance) |

### `ShellAndTubeDesignCalculator` Expanded
| New Capability | Standard | Method |
|----------------|----------|--------|
| Tubesheet thickness per UHX-13 | ASME VIII | `calculateTubesheetThicknessUHX()` |
| Nozzle reinforcement per UG-37 | ASME VIII | `calculateNozzleReinforcement()` |
| MAWP back-calculation per UG-27 | ASME VIII | `calculateMAWP()` |
| Hydrostatic test pressure per UG-99 | ASME VIII | `calculateHydroTestPressure()` |
| Material property lookup from DB | HeatExchangerTubeMaterials | `loadMaterialProperties()` |
| NACE MR0175 sour service assessment | NACE MR0175 / NORSOK M-001 | `performNACEAssessment()` |
| Shell/tube material grade tracking | — | `setShellMaterialGrade()`, `setTubeMaterialGrade()` |

### `HeatExchangerMechanicalDesign` Integration
- New fields: `shellMaterialGrade`, `tubeMaterialGrade`, `h2sPartialPressure`,
  `sourServiceAssessment`, `shellJointEfficiency`
- `calcDesign()` now runs `ShellAndTubeDesignCalculator` with ASME VIII and NACE
- `getShellAndTubeCalculator()` provides access to detailed calculator results
- `HeatExchangerMechanicalDesignResponse` updated with MAWP, hydro test, NACE fields

### Migration Notes
- Existing `calcDesign()` calls work unchanged — new calculator runs automatically
- To access ASME/NACE results: `design.getShellAndTubeCalculator().getMawpShellSide()`
- For sour service: set `design.setSourServiceAssessment(true)` and `setH2sPartialPressure(pp)`
- Material grades default to SA-516-70 (shell) and SA-179 (tubes) if not set

---

## 2026-03-22 — Compressor Casing Mechanical Design (API 617 / ASME VIII)

### New Class
- **`CompressorCasingDesignCalculator`** — Standalone calculator for compressor casing
  pressure containment design per API 617 and ASME Section VIII Div. 1.

### Capabilities Added
| Feature | Standard |
|---------|----------|
| Casing wall thickness (UG-27 formula) | ASME VIII Div. 1 |
| Material selection with SMYS/SMTS (9 grades) | ASME II Part D |
| Temperature derating of allowable stress | ASME II Part D Table 1A |
| Nozzle load analysis (force/moment scaling) | API 617 Table 3 |
| Flange rating verification with temp derating | ASME B16.5 / B16.47 |
| Hydrostatic test pressure | ASME VIII UG-99 |
| Corrosion allowance integration | API 617 |
| NACE MR0175 / ISO 15156 sour service check | NACE MR0175 |
| Thermal growth & differential expansion | API 617 |
| Split-line bolt sizing (horizontally-split) | API 617 |
| Barrel casing outer/inner/end-cover sizing | ASME VIII UG-34 |
| MAWP back-calculation | ASME VIII |
| Automatic material recommendation | — |

### Integration
- `CompressorMechanicalDesign.calcDesign()` now automatically runs the casing
  calculator after process sizing and populates
  `getCasingDesignCalculator()` with results.
- New configuration methods on `CompressorMechanicalDesign`:
  `setCasingMaterialGrade(String)`, `setCasingCorrosionAllowanceMm(double)`,
  `setH2sPartialPressureKPa(double)`.
- `CompressorMechanicalDesignResponse` includes full casing design data in
  the `casingDesign` section of JSON output.

### New Data Files
| File | Content |
|------|---------|
| `designdata/CompressorCasingMaterials.csv` | 20 material grades with mechanical properties |
| `designdata/standards/api_standards.csv` | +22 API-617 compressor entries |
| `designdata/standards/asme_standards.csv` | +18 ASME VIII / B16.5 compressor entries |

### Agent Migration
- When writing compressor casing design code, use `CompressorCasingDesignCalculator`
  directly or via `comp.getMechanicalDesign().getCasingDesignCalculator()`.
- For sour service: set `design.setNaceCompliance(true)` and
  `design.setH2sPartialPressureKPa(value)` before calling `calcDesign()`.
- For automatic material selection: call `casingCalc.recommendMaterial()`.

---

## 2026-03-21 — Capability Scout Agent and Capability Map Skill

### New Agent
- **`@capability.scout`** — Analyzes engineering tasks, identifies required capabilities,
  checks NeqSim coverage, identifies gaps, writes NIPs, recommends skills and agent pipelines.
  Use before starting complex multi-discipline tasks.

### New Skill
| Skill | Purpose |
|-------|---------|
| `neqsim-capability-map` | Structured inventory of all NeqSim capabilities by discipline (EOS, equipment, PVT, standards, mechanical design, flow assurance, safety, economics) |

### Updated Files
- `solve.task.agent.md` — Phase 1.5 Section 7b.3 now recommends invoking `@capability.scout` for comprehensive tasks
- `router.agent.md` — Added capability scout to routing table and Pattern 6 (Capability Assessment + Implementation)
- `README.md` — Added capability scout to Routing & Help section and capability-map to Skills table
- `AGENTS.md` — Added capability scout to Key Paths and capability-map to Skills Reference
- `CONTEXT.md` — Updated agent count to 14, skill count to 9
- `copilot-instructions.md` — Added Capability Assessment bullet point

---

## 2026-03-21 — Agent Ecosystem v2: Router, Skills, Validation

### New Agents
- **`@neqsim.help` (router agent)** — Routes requests to specialist agents. Use when unsure which agent to pick.

### New Skills (6 added)
| Skill | Purpose |
|-------|---------|
| `neqsim-troubleshooting` | Recovery strategies for convergence failures, zero values, phase issues |
| `neqsim-input-validation` | Pre-simulation input checks (T, P, composition, component names, order of operations) |
| `neqsim-regression-baselines` | Baseline management for preventing silent accuracy drift |
| `neqsim-agent-handoff` | Structured schemas for agent-to-agent result passing |
| `neqsim-physics-explanations` | Plain-language explanations of thermodynamic and process phenomena |
| `neqsim-performance-guide` | Simulation time estimates and optimization strategies (in notebook-patterns skill) |

### Updated Files
- `solve.task.agent.md` — Added auto-search past solutions (Phase 0, Step 1.5) and cross-discipline consistency gate
- `README.md` — Updated with new router agent, skills table, and cross-references
- `neqsim-notebook-patterns/SKILL.md` — Added performance estimation table and optimization tips

---

## 2026-03-14 — Fix IEC 60534 Gas Valve Sizing

### Changed
- `ControlValveSizing_IEC_60534.java` — Gas valve Cv now uses standard volumetric flow instead of actual
- `ControlValveSizing_IEC_60534_full.java` — Same fix applied to full version

### Migration
- If you have code using `sizeControlValveGas()`, results will now be correct (previously ~98% too low at 50 bara)
- No API change — same methods, corrected internal calculations

---

## 2026-03-10 — Process Architecture Improvements

### New APIs
| API | Class | Description |
|-----|-------|-------------|
| `getInletStreams()` | `ProcessEquipmentInterface` | Returns list of inlet streams for any equipment |
| `getOutletStreams()` | `ProcessEquipmentInterface` | Returns list of outlet streams for any equipment |
| `addController(tag, ctrl)` | `ProcessEquipmentBaseClass` | Attach named controller to equipment |
| `getController(tag)` | `ProcessEquipmentBaseClass` | Retrieve controller by tag name |
| `getControllers()` | `ProcessEquipmentBaseClass` | Get all named controllers as map |
| `connect(src, dst, type, label)` | `ProcessSystem` | Record typed connection metadata |
| `getConnections()` | `ProcessSystem` | Query all recorded connections |
| `getAllElements()` | `ProcessSystem` | Get all equipment, controllers, and measurements |

### New Classes
| Class | Package | Purpose |
|-------|---------|---------|
| `ProcessElementInterface` | `process` | Unified supertype for equipment, controllers, measurements |
| `MultiPortEquipment` | `process.equipment` | Abstract base for multi-inlet/outlet equipment |
| `ProcessConnection` | `process.processmodel` | Typed connection metadata (MATERIAL/ENERGY/SIGNAL) |

### Migration
- **Backward compatible** — all existing code continues to work
- Legacy `setController()`/`getController()` still work alongside named controllers
- `getInletStreams()`/`getOutletStreams()` default to empty lists for classes that don't override

---

## 2026-03-09 — CO2 Corrosion Analyzer

### New Classes
| Class | Package | Purpose |
|-------|---------|---------|
| `CO2CorrosionAnalyzer` | `pvtsimulation.flowassurance` | Couples electrolyte CPA EOS with de Waard-Milliams corrosion model |

### Important Note
Must call `chemicalReactionInit()` before `createDatabase(true)` and `setMixingRule(10)` to enable aqueous chemical equilibrium. Without this, pH returns 7.0 (neutral) because H3O+ is not generated.

---

## Pre-2026 — Stable API Reference

### Key Methods (Unchanged)
These core methods have been stable for years and are safe to use:
- `SystemInterface.addComponent(name, moleFraction)`
- `SystemInterface.setMixingRule(rule)`
- `SystemInterface.initProperties()`
- `ThermodynamicOperations.TPflash()` / `PHflash()` / `PSflash()`
- `Stream.setFlowRate(value, unit)` / `setTemperature(value, unit)` / `setPressure(value, unit)`
- `ProcessSystem.add(equipment)` / `run()`
- `Separator.getGasOutStream()` / `getLiquidOutStream()`
- `Compressor.setOutletPressure(value)` / `getPower(unit)`

### Known Method Name Corrections
| Wrong Name (Don't Use) | Correct Name |
|-------------------------|-------------|
| `getUnitOperation("name")` | `getUnit("name")` |
| `characterise()` | `characterisePlusFraction()` |
| `characterize()` | `characterisePlusFraction()` |
| `Optional.isEmpty()` | `!optional.isPresent()` (Java 8) |
