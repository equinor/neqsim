# NeqSim — Industrial Agentic Engineering

> **AI Agents for Engineering Task Solving in Industry.**
> Get oriented in 60 seconds. This file is the single entry point for anyone
> (human or AI) solving engineering tasks inside the NeqSim repo.

## Solve a Task (Start Here)

The fastest way to solve an engineering task is the `@solve.task` Copilot agent:

```
@solve.task hydrate formation temperature for wet gas at 100 bara
```

It creates a `task_solve/` folder, researches the topic, builds a simulation,
validates results, and generates a Word report — all in one session. The script
alternative is `neqsim new-task "your task"`. See
`docs/tutorials/solve-engineering-task.md` for a hands-on tutorial or
`docs/development/TASK_SOLVING_GUIDE.md` for the full reference.

**Using OpenAI Codex?** It reads `AGENTS.md` (repo root) automatically and
`.openapi/codex.yaml` sets up the sandbox. Give it a one-shot prompt and it
runs the full workflow including PR creation.

## What Is NeqSim?

Java library for thermodynamic fluid properties and process simulation.
Developed at NTNU, maintained by Equinor. Apache-2.0 license.
`com.equinor.neqsim:neqsim` version 3.10.0 — **must compile with Java 8**.

## Repo Map

```
src/main/java/neqsim/
  thermo/system/         60 EOS classes (SystemSrkEos, SystemPrEos, ...)
  thermo/phase/          Phase implementations
  thermodynamicoperations/  Flash calculations (TP, PH, PS, dew, bubble)
  physicalproperties/    Density, viscosity, conductivity, surface tension
  process/equipment/     33 equipment packages:
    stream/ separator/ compressor/ pump/ valve/ heatexchanger/
    pipeline/ distillation/ mixer/ splitter/ expander/ reactor/
    pipeline/routing/     PipingRouteBuilder — STID/E3D line-list route
                          hydraulics to serial PipeBeggsAndBrills models
    well/ reservoir/ membrane/ ejector/ filter/ flare/
    subsea/              SubseaWell, SubseaTree (subsea equipment)
    heatexchanger/heatintegration/  PinchAnalysis, HeatStream (Linnhoff method)
    powergeneration/     GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem
    MultiPortEquipment   Abstract base for multi-inlet/outlet equipment
  process/               ProcessElementInterface — unified marker for all elements
  process/controllerdevice/  ControllerDeviceInterface (extends ProcessElementInterface)
  process/measurementdevice/ MeasurementDeviceInterface (extends ProcessElementInterface)
  process/mechanicaldesign/
    subsea/              WellMechanicalDesign, WellDesignCalculator,
                         WellCostEstimator, SURFCostEstimator,
                         SubseaCostEstimator (mechanical design & cost)
    heatexchanger/       ShellAndTubeDesignCalculator, ThermalDesignCalculator,
                         BellDelawareMethod, LMTDcorrectionFactor,
                         VibrationAnalysis (TEMA + ASME VIII + thermal-hydraulic)
    (root)               StudyClass, EngineeringDeliverablesPackage,
                         InstrumentScheduleGenerator, ThermalUtilitySummary,
                         AlarmTripScheduleGenerator, SparePartsInventory
  process/processmodel/  ProcessSystem — the flowsheet orchestrator
                         ProcessConnection — typed connection metadata (MATERIAL/ENERGY/SIGNAL)
    lifecycle/           ProcessSystemState, ProcessModelState — JSON lifecycle
                         snapshots with version comparison (ModelDiff), compressed
                         bytes for network transfer, Git-diffable state
  process/automation/    ProcessAutomation — stable string-addressable API for
                         reading/writing simulation variables without Java knowledge.
                         SimulationVariable — INPUT/OUTPUT typed descriptor.
                         AutomationDiagnostics — self-healing with fuzzy name matching,
                         auto-correction, physical bounds validation, operation tracking.
                         Safe accessors: getVariableValueSafe(), setVariableValueSafe()
                         return diagnostic JSON with suggestions instead of throwing.
                         Addresses: "Unit.property", "Area::Unit.stream.property",
                         or IEC 81346 reference designations ("=A1-B1+P1.property")
  process/equipment/iec81346/  IEC 81346 reference designation support:
                         IEC81346LetterCode — enum mapping equipment to IEC 81346-2 codes
                         ReferenceDesignation — data class (function/product/location aspects)
                         ReferenceDesignationGenerator — auto-assigns designations to a
                         ProcessSystem or ProcessModel; exports JSON reports
    dexpi/               DEXPI P&ID import/export/round-trip, topology resolver,
                         equipment factory, simulation builder, cycle detection, column support
  pvtsimulation/         CME, CVD, DL, saturation, GOR, swelling, MMP
  standards/             Gas quality, oil quality, sales contracts
  fluidmechanics/        Pipeline hydraulics
  chemicalreactions/     Reaction equilibrium/kinetics
  statistics/            Parameter fitting, Monte Carlo
  util/                  Validation, exceptions, named interfaces
  util/agentic/          TaskResultValidator, SimulationQualityGate, AgentSession

src/test/java/neqsim/   Mirrors production structure. JUnit 5. Extend NeqSimTest.
src/main/resources/      Component databases, design data CSVs
examples/notebooks/      28+ Jupyter notebooks
docs/                    350+ markdown files, Jekyll site
.github/agents/          19 Copilot Chat agents (router, thermo, process, field development, test, PVT, reaction engineering, control systems, emissions, ...)
.github/skills/          28 reusable knowledge packages (API, Java8, notebooks, field-development, field-economics, subsea-and-wells, eos-regression, reaction-engineering, dynamic-simulation, distillation-design, electrolyte-systems, ...)
community-skills.yaml    Community skill catalog — external skills installable via `neqsim skill install`
devtools/                Unified CLI (`neqsim` command), Jupyter dev setup, task/skill tools, UniSim reader
```

## Build & Test (30 seconds)

```powershell
# Full build
.\mvnw.cmd install

# Tests only
.\mvnw.cmd test

# Single test class
.\mvnw.cmd test -Dtest=SeparatorTest

# Single test method
.\mvnw.cmd test -Dtest=SeparatorTest#testTwoPhase

# Skip slow tests (default)
.\mvnw.cmd test                              # excludes @Tag("slow")
.\mvnw.cmd test -DexcludedTestGroups=         # runs everything

# Static analysis
.\mvnw.cmd checkstyle:check spotbugs:check pmd:check

# Package JAR + copy to Python
.\mvnw.cmd package -DskipTests
Copy-Item target\neqsim-3.10.0.jar C:\Users\ESOL\AppData\Roaming\Python\Python312\site-packages\neqsim\lib\java11\ -Force
```

## Code Patterns — Copy-Paste Starters

### Create a Fluid

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0); // T in Kelvin, P in bara
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic"); // NEVER skip this
```

### Run a Flash

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties(); // MANDATORY: initializes thermodynamic + transport properties
// NOTE: init(3) alone does NOT initialize transport properties (viscosity, thermal conductivity)
double density = fluid.getDensity("kg/m3");
double viscosity = fluid.getPhase("gas").getViscosity("kg/msec");
double thermalCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
```

### Build a Process

```java
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.setTemperature(30.0, "C");
feed.setPressure(60.0, "bara");
process.add(feed);

Separator sep = new Separator("HP Sep", feed);
process.add(sep);

Compressor comp = new Compressor("Compressor", sep.getGasOutStream());
comp.setOutletPressure(120.0, "bara");
process.add(comp);

process.run();
System.out.println("Compressor power: " + comp.getPower("kW") + " kW");

// Stream introspection (works on any equipment, no casting needed)
List<StreamInterface> inlets = sep.getInletStreams();   // [feed]
List<StreamInterface> outlets = sep.getOutletStreams();  // [gasOut, liquidOut]

// --- Automation API (PREFERRED for agents — no Java class navigation needed)
ProcessAutomation auto = process.getAutomation();
List<String> units = auto.getUnitList();               // ["feed", "HP Sep", "Compressor"]
auto.getVariableList("HP Sep");                         // SimulationVariable list with INPUT/OUTPUT types
double t = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C");
auto.setVariableValue("Compressor.outletPressure", 120.0, "bara");
process.run();  // Re-run after changing inputs

// --- Lifecycle state: save/restore/compare
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setVersion("1.0.0");
state.saveToFile("model_v1.json");           // Git-diffable JSON
ProcessSystemState loaded = ProcessSystemState.loadFromFile("model_v1.json");

// Named controllers (multiple per equipment)
valve.addController("LC-100", levelController);
valve.addController("PC-200", pressureController);
ControllerDeviceInterface lc = valve.getController("LC-100");

// Explicit connections (metadata for DEXPI/diagrams)
process.connect(feed, sep, feed.getOutletStream(),
    ProcessConnection.ConnectionType.MATERIAL, "Feed");
```

### Write a Test

```java
public class MyFeatureTest extends neqsim.NeqSimTest {
    @Test
    void testSomething() {
        // 1. Create fluid
        // 2. Build process
        // 3. process.run()
        // 4. Assert on physical outputs
        assertEquals(expected, actual, tolerance);
    }
}
```

### Jupyter Notebook (Python)

```python
from neqsim import jneqsim
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
# ... see devtools/neqsim_dev_setup.py for local dev workflow
```

### Subsea Well Design (mechanical design + cost)

```java
// Create fluid & stream
SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 200.0);
fluid.addComponent("methane", 0.80);
fluid.setMixingRule("classic");
Stream stream = new Stream("reservoir", fluid);
stream.setFlowRate(50000.0, "kg/hr");

// Create subsea well
SubseaWell well = new SubseaWell("Producer-1", stream);
well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
well.setCompletionType(SubseaWell.CompletionType.CASED_PERFORATED);
well.setRigType(SubseaWell.RigType.SEMI_SUBMERSIBLE);
well.setMeasuredDepth(3800.0);
well.setWaterDepth(350.0);
well.setMaxWellheadPressure(345.0);
well.setReservoirPressure(400.0);

// Casing program (conductor, surface, intermediate, production)
well.setConductorOD(30.0);  well.setConductorDepth(100.0);
well.setSurfaceCasingOD(20.0); well.setSurfaceCasingDepth(800.0);
well.setIntermediateCasingOD(13.375); well.setIntermediateCasingDepth(2500.0);
well.setProductionCasingOD(9.625); well.setProductionCasingDepth(3800.0);
well.setTubingOD(5.5); well.setTubingWeight(23.0); well.setTubingGrade("L80");

// Mechanical design — casing per API 5C3/5CT, barriers per NORSOK D-010
well.initMechanicalDesign();
WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
design.calcDesign();          // burst, collapse, tension, weights, cement, barriers
design.calculateCostEstimate(); // drilling, completion, wellhead, logging, contingency
System.out.println(design.toJson());
```

**Standards used in well design:**
- API 5CT / ISO 11960 — casing & tubing grades and SMYS lookup
- API Bull 5C3 — Barlow burst, yield-strength / plastic / transition / elastic collapse
- NORSOK D-010 — design factors (burst ≥ 1.10, collapse ≥ 1.00, tension ≥ 1.60, VME ≥ 1.25), two-barrier principle, DHSV requirements
- API RP 90 — annular casing pressure management

## EOS Selection Quick Reference

| Fluid Type | Class | Mixing Rule |
|------------|-------|-------------|
| Dry/lean gas | `SystemSrkEos` | `"classic"` |
| Oil systems | `SystemPrEos` | `"classic"` |
| Water/MEG/methanol | `SystemSrkCPAstatoil` | `10` |
| Fiscal metering | `SystemGERG2008Eos` | — |
| Electrolytes | `SystemElectrolyteCPAstatoil` | `10` |

## Equipment Package → Class Cheat Sheet

| Equipment | Package | Key Class |
|-----------|---------|-----------|
| Stream | `stream` | `Stream`, `EquilibriumStream` |
| Separator | `separator` | `Separator`, `ThreePhaseSeparator` |
| Compressor | `compressor` | `Compressor` |
| Pump | `pump` | `Pump` |
| Valve | `valve` | `ThrottlingValve` |
| Heat exchanger | `heatexchanger` | `Cooler`, `Heater`, `HeatExchanger` |
| Heat integration | `heatexchanger.heatintegration` | `PinchAnalysis`, `HeatStream` |
| Power generation | `powergeneration` | `GasTurbine`, `SteamTurbine`, `HRSG`, `CombinedCycleSystem` |
| Bioprocessing reactors | `reactor` | `AnaerobicDigester`, `FermentationReactor`, `BiomassGasifier`, `PyrolysisReactor` |
| Biogas upgrading | `splitter` | `BiogasUpgrader` (4 technologies: membrane, PSA, amine, water scrub) |
| Biorefinery modules | `processmodel.biorefinery` | `BiogasToGridModule`, `GasificationSynthesisModule`, `WasteToEnergyCHPModule` |
| Pipeline | `pipeline` | `AdiabaticPipe`, `PipeBeggsAndBrills` |
| Mixer | `mixer` | `Mixer` |
| Splitter | `splitter` | `Splitter` |
| Distillation | `distillation` | `DistillationColumn` |
| Expander | `expander` | `Expander` |
| Ejector | `ejector` | `Ejector` |
| Well | `well` | `SimpleWell` |
| Subsea Well | `subsea` | `SubseaWell` (casing, tubing, barriers, cost) |
| Recycle | `util` | `Recycle` |
| Adjuster | `util` | `Adjuster` |

Full package path: `neqsim.process.equipment.<package>.<Class>`

## Where to Find Things

| I need to... | Look in... |
|-------------|-----------|
| Pick an EOS | `src/main/java/neqsim/thermo/system/` |
| Add equipment | `src/main/java/neqsim/process/equipment/<type>/` |
| Run a PVT experiment | `src/main/java/neqsim/pvtsimulation/simulation/` |
| Check gas quality | `src/main/java/neqsim/standards/gasquality/` |
| See a working example | `examples/notebooks/` or `src/test/java/neqsim/process/` |
| Read documentation | `docs/wiki/` (60+ topics) or `docs/REFERENCE_MANUAL_INDEX.md` |
| Use an AI agent | `.github/agents/` (17 specialist agents), start with `@neqsim.help` or `@capability.scout` or `@field.development` |
| Check API changes | `CHANGELOG_AGENT_NOTES.md` |
| Set up Jupyter dev | `devtools/neqsim_dev_setup.py` |
| Find design data | `src/main/resources/designdata/` |
| Well design & cost | `src/main/java/neqsim/process/mechanicaldesign/subsea/` |
| SURF cost estimation | `src/main/java/neqsim/process/mechanicaldesign/subsea/SURFCostEstimator.java` |
| Engineering deliverables | `src/main/java/neqsim/process/mechanicaldesign/` (StudyClass, InstrumentScheduleGenerator, etc.) |
| HX thermal-hydraulic design | `src/main/java/neqsim/process/mechanicaldesign/heatexchanger/` (ThermalDesignCalculator, BellDelawareMethod, VibrationAnalysis) |
| Heat integration / pinch analysis | `src/main/java/neqsim/process/equipment/heatexchanger/heatintegration/` (PinchAnalysis, HeatStream) |
| Power generation (combined cycle) | `src/main/java/neqsim/process/equipment/powergeneration/` (GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem) |
| Bioprocessing / bioenergy | `src/main/java/neqsim/process/equipment/reactor/` (AnaerobicDigester, FermentationReactor, BiomassGasifier, PyrolysisReactor), `splitter/BiogasUpgrader`, `processmodel/biorefinery/` (BiogasToGridModule, GasificationSynthesisModule, WasteToEnergyCHPModule), `util/fielddevelopment/SustainabilityMetrics`, `thermo/characterization/BiomassCharacterization` |
| Agentic QA & validation | `src/main/java/neqsim/util/agentic/` (TaskResultValidator, SimulationQualityGate, AgentSession) |
| Automation API (string-addressed variables) | `src/main/java/neqsim/process/automation/` (ProcessAutomation, SimulationVariable) |
| Lifecycle state / save-restore | `src/main/java/neqsim/process/processmodel/lifecycle/` (ProcessSystemState, ProcessModelState) |
| Component database | `src/main/resources/` |

## Key Constraints

- **Java 8 only** — no `var`, `List.of()`, `Map.of()`, `String.repeat()`, text blocks, records
- **Temperature in Kelvin** — constructors take `(T_kelvin, P_bara)`
- **Always set mixing rule** — simulations fail silently without it
- **Unique equipment names** — `ProcessSystem` enforces unique names
- **Clone before branching** — `fluid.clone()` to avoid shared state
- **Google Java Style** — 2-space indent, checkstyle enforced

## Deeper Context

| Topic | File |
|-------|------|
| Full AI coding instructions | `.github/copilot-instructions.md` |
| Task-solving workflow | `docs/development/TASK_SOLVING_GUIDE.md` |
| Solved task history | `docs/development/TASK_LOG.md` |
| Developer setup | `docs/development/DEVELOPER_SETUP.md` |
| Module architecture | `docs/modules.md` |
| Contributing guide | `CONTRIBUTING.md` |
| All documentation index | `docs/REFERENCE_MANUAL_INDEX.md` |
