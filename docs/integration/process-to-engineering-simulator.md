---
title: "Process-to-engineering simulator"
description: "Iterate NeqSim process cases into review-governed equipment, piping, valve, instrument, safety, material, mechanical, and DEXPI design outputs."
---

# Process-to-engineering simulator

The process-to-engineering simulator closes the loop between process physics and preliminary engineering design. It
runs every controlled process case on isolated copies, sizes configured engineering objects, applies selected design
variables to a separate working flowsheet, reruns the cases, and stops only when no design variable changes and all
declared constraints pass.

The original `ProcessSystem` is never modified. After a successful run, DEXPI and engineering-package compilation use
the isolated designed process and embed the calculated design state, governing cases, method evidence, warnings, and
review status.

```text
ProcessSystem / ProcessModel
  -> controlled design cases
  -> governing simulation envelope
  -> discipline design modules
  -> discrete candidate selection
  -> update isolated process geometry and ratings
  -> rerun process cases
  -> constraints and convergence
  -> safety/control dynamic verification
  -> engineering graph, DEXPI 2.0, registers and calculation package
```

## Eight-step workflow coverage

One run now follows the complete preliminary-engineering chain:

1. `ProcessSystem` or one area from a `ProcessModel` is the physical source model.
2. `EngineeringDesignCase` defines normal, maximum, turndown, start-up, shutdown and accidental inputs.
3. `EngineeringCaseRunner` executes isolated copies and creates the governing envelope.
4. Typed family and design-loop modules size equipment, piping, valves, instruments and PSV orifices.
5. Selected separator geometry, pipe diameters and valve Cv values are applied to an isolated design copy and the
   hydraulics are rerun.
6. Governed safety studies connect control, relief, blowdown, flare and dynamic response verification; missing HAZOP
   credibility is a calculation blocker.
7. `EngineeringConvergenceReport` requires stable physical variables, stable process values, converged cases and
   satisfied constraints. Open actions and accountable reviews remain visible.
8. The compiler serializes the canonical graph to DEXPI 2.0 and produces coordinated registers, datasheets,
   calculation evidence and revision/action reports.

The implementation is intentionally preliminary and composable. Detailed company methods can replace a family module
without changing the iteration, provenance, graph or package contracts.

## Configure the first complete vertical slice

Build a governed project, declare at least one executable case, and configure the tagged inlet separator, compressor,
export line, optional control valve, and pressure instrument:

```java
EngineeringProject project = NorsokOffshoreEngineeringBuilder
    .from("Compression project", process)
    .projectId("compression-A")
    .build();

project.addDesignCase(normalCase);
project.addDesignCase(maximumRateCase);
project.addDesignCase(turndownCase);

ProcessToEngineeringDesignBuilder.on(project)
    .separatorBasis(800.0, 0.107, 120.0)
    .exportLineLimits(20.0, 0.5)
    .compressorDrivers(0.10, 3000.0, 5000.0, 7500.0, 10000.0)
    .addInletCompressionExportSlice(
        "20-VA-001", "20-KA-001", "20-PL-001", "20-PV-001", "20-PIT-001");

EngineeringSimulationResult result = ProcessToEngineeringSimulator.run(project, 4);
```

The configured slice performs:

- Souders-Brown gas-capacity and liquid-retention separator sizing;
- discrete export-line diameter selection from velocity and simulated pressure-gradient constraints;
- compressor driver selection from the governing shaft-power case;
- IEC 60534 control-valve Cv selection when a valve tag is supplied;
- proposed design pressure, PSV set pressure, HH trip pressure and blowdown target;
- pressure-instrument range, uncertainty and response-budget screening;
- NORSOK M-001 degradation/material-class screening; and
- preliminary pressure-shell wall thickness and mass.

The selected separator and pipe dimensions are applied to the isolated process and therefore affect the next
simulation iteration. The result records every iteration, applied update, constraint, governing case, and final design
state.

## Add other equipment families

The builder also exposes explicit preliminary modules:

```java
ProcessToEngineeringDesignBuilder builder = ProcessToEngineeringDesignBuilder.on(project);

builder.addPumpDesign("30-PA-001", 0.10, 1.0,
    75.0, 110.0, 160.0, 250.0, 400.0);

builder.addHeatExchangerDesign("30-HA-001", 500.0, 25.0, 0.15,
    25.0, 50.0, 75.0, 100.0, 150.0, 250.0);

builder.addInventoryDesign("30-TK-001", 600.0, 0.70,
    10.0, 25.0, 50.0, 100.0, 250.0);

builder.addRatedCapacity("40-DA-001",
    EngineeringMetric.equipmentInletMassFlow("40-DA-001"),
    "ratedFeedCapacity", "kg/s", 0.10,
    5.0, 10.0, 20.0, 40.0, 80.0);
```

`EngineeringDesignModule` is the extension point for detailed equipment or company methods. A module reads the
governing case report and current design state, then returns proposed updates, process appliers, constraints, evidence,
and review warnings. Continuous calculations and discrete vendor or schedule candidates use the same update contract.

Typed `EngineeringCalculationModule` implementations are available for independently executable and benchmarkable
calculations:

- `EquipmentDesignCalculations.Separator`, `.Compressor`, `.Pump`, `.HeatExchanger`, `.Column` and `.Tank`;
- `PipingNetworkDesignCalculation` with a versioned `PipingRulePack`;
- `ValveInstrumentDesignCalculations.Valve` and `.Instrument`;
- `SafetyScenarioEngineCalculation` and `ReliefSizingCalculation`; and
- `MaterialsMechanicalDesignCalculations.MaterialSelection` and `.PreliminaryMechanical`.

Every typed result carries a method identifier/version, input snapshot, design-case context, readiness findings,
uncertainty where applicable, constraints, warnings and `engineeringApprovalRequired=true`.

### Network-level piping

`PipingNetworkDesignCalculation` selects the smallest nominal-size/schedule candidate satisfying velocity, minimum
velocity, simulated pressure-gradient, pressure rating and relief-inlet-loss limits across every supplied case. It
accounts for equivalent fitting length, elevation evidence, multiphase pressure-drop multipliers and simultaneous
header demand. Use `PipingNetworkDesignModule` when the selected inside diameters must participate in the common
process/design convergence loop.

The built-in offshore rule pack identifies itself as `NORSOK P-002:2023+AC:2024`; numerical limits are
project-overridable and the selected project edition remains traceable.

## Safety and scenario integration

The design loop establishes pressure and response proposals. The existing coupled relief/blowdown/flare calculation
and `DynamicSafetyScenarioRunner` then evaluate the configured hazard cases against the designed process copy.
`ProcessSafetyScenario.generateFromTopology` can prepare blocked-outlet, loss-of-drive, utility-loss and stuck-valve
screening cases for engineering review.

Scenario credibility, simultaneous-event groups, fire zones and final safeguards must be supplied from the project
hazard review. The simulator does not invent a HAZOP conclusion or SIL target.

`SafetyScenarioEngineCalculation` provides the controlled scenario taxonomy for blocked outlet, control-valve
failure, utility/cooling failure, compressor trip and settle-out, fire, tube rupture, thermal expansion, gas blow-by,
abnormal heat input, check-valve failure, simultaneous blowdown and loss of containment. It calculates only scenarios
marked credible with a hazard-review reference. Outputs include gas/liquid/steam/two-phase method selection, API
orifice selection, inlet loss, built-up/superimposed backpressure, concurrency-group load, knockout-drum liquid
hold-up and depressurization minimum-temperature checks.

## Engineering graph and DEXPI

After the loop runs:

- process geometry in the isolated designed copy is used for DEXPI serialization;
- every scalar design value is attached to the matching canonical engineering graph node;
- legacy DEXPI exports receive calculated-design attributes with units;
- `engineering-calculations.json` contains the complete loop and discipline evidence;
- equipment, line and instrument registers use the designed process; and
- the package retains `REVIEW_REQUIRED`, `engineeringApprovalRequired=true`, and
  `fitnessForConstruction=false` boundaries.

DEXPI remains the exchange representation. The canonical engineering graph and controlled calculation evidence are
the source of engineering identity, provenance and revision impact.

`engineering-diagram-layout.json` is generated deterministically from canonical graph identities and flow edges. It
contains stable PFD/P&ID placements and orthogonal routes; coordinates are serialization data, not the engineering
database. Internal round-trip qualification compares stable equipment identity, references and semantic inventories
for native DEXPI 2.0, Proteus and pyDEXPI representations. Named commercial-tool qualification remains a separate
external gate.

## Coordinated package outputs

In addition to DEXPI XML, registers, the case matrix, governing envelope and calculation graph, compilation writes:

- `process-design-basis.json`;
- `equipment-datasheets.json`;
- `valve-list.json` and `io-list.json`;
- `alarm-trip-schedule.json` and `shutdown-narratives.json`;
- `psv-datasheets.json` and `flare-blowdown-report.json`;
- `utility-summary.json`;
- `materials-selection-report.json`;
- `engineering-diagram-layout.json`;
- `engineering-qualification-plan.json`;
- `unresolved-engineering-actions.json`; and
- `revision-impact-report.json`.

The existing cause-and-effect matrix, equipment/line/instrument registers, native DEXPI PFD/P&ID representations,
calculation DAG, approval
ledger and graph difference complete the package. Each calculated value retains its governing case, method or module,
unit and graph/evidence relationship.

## Standards profile

The supplied methods are designed to be used with version-controlled project rule packs, including:

- DEXPI 2.0 for exchange serialization;
- NORSOK P-002 for offshore process-system and line-design requirements;
- ANSI/ISA-5.1:2024, NORSOK I-001:2025+AC:2026, NORSOK I-002:2021 and IEC 61511 for instrumentation and supplied SIF
  requirements;
- NORSOK M-001 for materials and degradation review;
- IEC 60534 for control-valve sizing; and
- API 520/521 for supplied relief and depressurization cases.

The edition stored in the project design basis controls traceability. Numerical limits remain project inputs because
jurisdiction, service, company requirements, standard revisions and project phase can change the applicable rule.

## Production-readiness qualification

Convergence and package validity are necessary, but they are not evidence that a calculation method or project is
qualified for production engineering. Attach an `EngineeringProductionReadinessBasis` to the project to evaluate the
additional controlled gates:

- independently reviewed, non-regression benchmark evidence for every method/version executed by the final design
  iteration;
- project-qualified method records with standards, applicability limits, evidence and accountable approval;
- complete automatic configuration from a revision-controlled policy with no hidden numerical defaults;
- import/export and zero-open-semantic-difference evidence from a named DEXPI-capable product and version;
- approved HAZOP evidence plus required LOPA/SRS, SIF and shutdown evidence;
- accepted independent pilots covering separation/compression, pumping/heat exchange and relief/blowdown/flare; and
- release evidence for the full CI/Java matrix, deterministic convergence, performance, compatibility, migration and
  security checks.

The explicit automatic-configuration path is:

```java
EngineeringAutoConfigurationPolicy policy = new EngineeringAutoConfigurationPolicy("offshore-gas", "A")
    .addInletCompressionExportSlice(
        "20-VA-001", "20-KA-001", "20-PL-001", "20-PV-001", "20-PIT-001",
        800.0, 0.107, 120.0, // liquid density, Souders-Brown K, liquid retention time
        20.0, 0.5,           // maximum line velocity and pressure gradient
        0.10,                // compressor driver margin
        3000.0, 5000.0, 7500.0, 10000.0);

EngineeringAutoConfigurator.Result configured = EngineeringAutoConfigurator.configure(project, policy);
project.setProductionReadinessBasis(
    new EngineeringProductionReadinessBasis().autoConfigurationResult(configured));

// Or configure, record the coverage evidence, and run in one call:
EngineeringSimulationResult result = ProcessToEngineeringSimulator.run(project, policy, 4);
```

### Automatic discipline orchestration and invalidation

`EngineeringAutoConfigurator` discovers recognized equipment in the process, applies only the explicit rules in the
revision-controlled policy, and records both configured and unconfigured items. It deliberately does not infer design
limits, relief causes, SIL, valve failure action or shared-system concurrency from topology. The simulator overload
that accepts a policy now fails before the design loop when any recognized item is unconfigured, a hidden screening
default was selected, no executable case exists, or no discipline module was created.

The result supplies a deterministic configuration fingerprint, conservative module dependencies and executable
blockers:

```java
EngineeringAutoConfigurator.Result orchestration =
    project.getProductionReadinessBasis().getAutoConfigurationResult();

if (!orchestration.isExecutionReady()) {
  throw new IllegalStateException(orchestration.getExecutionBlockers().toString());
}

Map<String, List<String>> dependencyGraph = orchestration.getModuleDependencies();
EngineeringAutoConfigurator.RevisionImpact impact = orchestration.compareWith(previousOrchestration);
```

A changed policy, process topology, case set or module graph changes the fingerprint. `compareWith` then invalidates
the affected discipline chain conservatively and lists the package artifacts that must be regenerated. Compilation
writes this evidence to `engineering-discipline-orchestration.json`; it also remains embedded in
`engineering-production-readiness.json`.

### Coordinated multi-area ProcessModel execution

Use `ProcessModelEngineeringSimulator` when the source is a `ProcessModel`. One explicit policy and controlled case
set is required per area:

```java
Map<String, ProcessModelEngineeringSimulator.AreaConfiguration> areas = new LinkedHashMap<>();
areas.put("inlet", new ProcessModelEngineeringSimulator.AreaConfiguration(inletPolicy)
    .addDesignCase(inletNormal).addDesignCase(inletMaximum));
areas.put("compression", new ProcessModelEngineeringSimulator.AreaConfiguration(compressionPolicy)
    .addDesignCase(compressionNormal).addDesignCase(compressionMaximum));

ProcessModelEngineeringSimulator.Result plant =
    ProcessModelEngineeringSimulator.run("Field A", processModel, areas, 4);
if (!plant.isComplete()) {
  throw new IllegalStateException(plant.getBlockers().toString());
}
Path manifest = plant.compile(Paths.get("build/field-a-engineering"));
```

The compiler creates an area package below each area name and a
`process-model-engineering-manifest.json` at the root. Stream objects shared between area equipment are registered as
cross-area dependencies; a change invalidates all connected areas. Utility demand simultaneity, flare/blowdown group
concurrency and safety scenario credibility still require explicit project inputs and review.

Compilation always writes the schema-registered `engineering-production-readiness.json` artifact. Its maturity levels
are `NOT_READY`, `EXPERIMENTAL`, `VALIDATED_PRELIMINARY` and `QUALIFIED_FEED_SUPPORT`. A green result means only that
the supplied preliminary/FEED-support evidence gates have passed. The artifact deliberately fixes
`fitnessForConstruction=false` and `finalEngineeringApprovalGranted=false`; neither can be changed through the API.

Do not use internal regression values as independent validation. `EngineeringValidationBenchmark` distinguishes
regression, published, independent-calculation and vendor/CAE sources, and requires a separate review record before a
benchmark can qualify a method. Method keys are the exact final-loop `method@version` values, preventing a benchmark
for one revision from silently qualifying another.

The current built-in calculation modules remain preliminary screening methods until their exact method versions have
been benchmarked and project-qualified for the stated service. The readiness framework exposes that gap instead of
silently promoting the results.

### Execute the qualification workflows

The production-readiness gates are backed by executable, serializable workflows. They derive evidence from supplied
measurements and fail closed when an output, review, tool result or acceptance record is missing:

- `EngineeringBenchmarkDataset` executes versioned scalar reference cases with units and absolute/relative
  tolerances. A qualifying source must be published, independently calculated or vendor/CAE evidence and must have an
  independent review record. Every case registered for a method version must qualify; one passing case cannot mask a
  failed, regression-only or unreviewed case for the same method.
- `DexpiToolQualificationRunner` compares persistent objects, properties and relationships after import and after
  export/reimport in a named product/version. Any semantic difference prevents qualification.
- `EngineeringPilotQualificationRunner` compares simulator values with an independently reviewed project package.
  An out-of-tolerance material comparison prevents acceptance.
- `EngineeringReleaseQualificationRunner` derives release evidence from CI state, the supported Java matrix,
  repeated deterministic fingerprints, measured runtime, API fingerprints, serialization migration and security
  findings. It also requires an accountable reviewer.

For example, a controlled benchmark case is run against actual calculation outputs rather than copying the expected
value into the evidence object:

```java
EngineeringBenchmarkDataset dataset = new EngineeringBenchmarkDataset("separator-reference", "A")
    .add(new EngineeringBenchmarkDataset.Case(
        "SEP-CASE-1", "separator-scrubber-preliminary-design", "2.0",
        EngineeringValidationBenchmark.SourceClass.INDEPENDENT_CALCULATION,
        "CALC-SEP-001", "A", "Independent checker / CALC-SEP-001-A")
        .expect("insideDiameterM", 2.0, "m", 0.02, 0.01));

Map<String, Map<String, Double>> actualOutputs = runControlledCases();
EngineeringBenchmarkDataset.RunResult benchmarkRun = dataset.run(actualOutputs);
```

Compilation also writes `engineering-qualification-plan.json`. It is an executable backlog keyed to the exact methods
used in the final loop. It identifies missing independent benchmarks, project qualifications, automatic configuration,
named-tool DEXPI evidence, pilot scopes, release evidence and safety-lifecycle evidence. It never fabricates external
acceptance and always reports `fitnessForConstruction=false`.

### Fail-closed production calculation mode

Set `productionQualification=true` in an `EngineeringCalculationContext` when a typed calculation is being exercised
for qualification:

```java
EngineeringCalculationContext context = EngineeringCalculationContext.builder()
    .designCaseId("maximum")
    .simulationFingerprint("controlled-run-sha256")
    .addStandardReference("PROJECT-CALCULATION-BASIS-A")
    .addEvidenceReference("INDEPENDENT-CHECK-001")
    .attribute("productionQualification", "true")
    .build();
```

In this mode the equipment modules reject screening defaults, piping rejects reference-diameter pressure-drop scaling,
unresolved valve failure position becomes a blocker, and standards/evidence references are mandatory. Instrument,
materials and mechanical modules additionally require controlled installation, corrosion-assessment and design-code
records. Two-phase relief cannot be inferred from a normal operating point: supply a controlled specialist mass-flux
method and reference. The built-in relief calculation selects from the project device table for gas, steam and liquid
cases, but certified coefficients, stability, inlet/outlet losses and disposal-network interaction remain separate
review gates.

See `examples/notebooks/engineering_production_qualification_workflow.ipynb` for benchmark, DEXPI, pilot, release and
relief workflow examples. The notebook uses synthetic references only to demonstrate the API; replace every such value
and reviewer string with controlled project evidence before assessing readiness.

See the executable
[production-readiness notebook](../../examples/notebooks/engineering_production_readiness.ipynb) for explicit
auto-configuration, executed-method discovery, a regression-evidence rejection, and the compiled gate report.
The companion
[discipline-orchestration notebook](../../examples/notebooks/engineering_discipline_orchestration.ipynb) focuses on
execution blockers, dependency fingerprints, revision invalidation and the multi-area `ProcessModel` API.

## Required engineering review

The simulator produces calculated or proposed engineering suitable for concept and pre-FEED development. It does not
replace accountable discipline work for:

- HAZOP/LOPA scenario credibility, SIL selection or SRS approval;
- vendor compressor, pump, exchanger, valve or instrument guarantees;
- final two-phase relief method and disposal-system network verification;
- code mechanical design, fatigue, external loads, nozzle reinforcement or fabrication;
- corrosion assessment, welding and material approval;
- plot plan, maintainability, escape, access, fire and gas coverage; or
- construction, commissioning and operating approval.

See the executed
[process-to-engineering notebook](../../examples/notebooks/process_to_engineering_simulator.ipynb) for the full
vertical slice, process/design convergence plots and coordinated package. The executed
[engineering-roadmap workbench](../../examples/notebooks/engineering_roadmap_steps_1_to_8.ipynb) demonstrates every
new typed equipment, piping, valve/instrument, safety, materials and preliminary mechanical calculation family.
