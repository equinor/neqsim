---
title: "Standards-based DEXPI engineering generation"
description: "Generate governed DEXPI packages with instrumentation, safeguarding requirements, validation, and compressor maps from NeqSim processes."
---

# Standards-based DEXPI engineering generation

`EngineeringProject` adds a governed engineering layer to a runnable `ProcessSystem`. It records the
design basis, applicable standards, deterministic control and safeguarding proposals, approval state,
and validation findings without treating automatically generated safeguards as approved design.

The initial profile targets Norwegian offshore gas-processing facilities. It references DEXPI 2.0,
ISO 10628, IEC 62424, IEC 61511, ISO 10418, API 521, API 617/API 670, and the applicable
NORSOK process, instrumentation, automation, safety, drawing, and risk standards.

## Generate an engineering package

Run and configure the process, including vendor or design compressor maps, before building the
engineering project:

```java
EngineeringProject project =
    NorsokOffshoreEngineeringBuilder.from("Gas compression engineering model", process)
        .registerProposedInstruments(true)
        .build();

EngineeringValidationReport validation = project.validate();
if (validation.hasErrors()) {
  throw new IllegalStateException("Engineering model contains blocking validation errors");
}

DexpiEngineeringExporter.ExportResult result =
    DexpiEngineeringExporter.export(project, Paths.get("build/engineering-package"));
```

## Compile a coordinated engineering package

`EngineeringDeliverableCompiler` is the higher-level entry point when the package must include the canonical
engineering graph, executable design-case envelope and consistent equipment, line and instrument registers in addition
to the DEXPI artifacts. Assign a persistent project id and controlled revision so snapshots remain comparable across
sessions:

```java
EngineeringProject project = NorsokOffshoreEngineeringBuilder
    .from("Gas compression engineering model", process)
    .projectId("GAS-COMPRESSION-PROJECT")
    .registerProposedInstruments(true)
    .build()
    .setRevision("A");

project.addDesignCase(new EngineeringDesignCase(
    "CASE-MAX", "Maximum production", EngineeringDesignCase.Type.MAXIMUM_PRODUCTION,
    scenario -> ((Stream) scenario.getUnit("20-FEED-001")).setFlowRate(1.15e6, "kg/hr"))
    .addInput(new EngineeringDesignCase.Input(
        "feed rate", 1.15e6, "kg/hr", "PROCESS-DESIGN-BASIS-REV-A")));

project.addEngineeringMetric(EngineeringMetric.equipmentPressure("20-VG-001"));
project.addEngineeringMetric(EngineeringMetric.equipmentTemperature("20-VG-001"));
project.addEngineeringMetric(EngineeringMetric.equipmentInletMassFlow("20-VG-001"));

EngineeringDeliverableCompiler.CompilationResult compiled =
    EngineeringDeliverableCompiler.compile(
        project, Paths.get("build/engineering-package"));
```

Every case runs on `ProcessSystem.copy()`, so scenario configuration and simulation results cannot alter the base
process. A failed or incomplete case remains explicit and is excluded from governing-value selection. Each governing
value records its metric, unit and source case and is materialized as an auditable calculation node.

The compiler adds these coordinated artifacts:

| Artifact | Purpose |
|---|---|
| `engineering-model.json` | Canonical nodes, semantic relationships, provenance, calculation dependencies and fingerprint |
| `engineering-connectivity.json` | Physical ports/nozzles, pipe/signal/energy segments, directed flows, line membership and instrument taps |
| `engineering-calculation-dag.json` | Standards-aware calculation dependencies, deterministic execution order and readiness states |
| `engineering-design-case-matrix.json` | Required/optional case coverage, per-metric execution status, limits and governing values |
| `engineering-discipline-package.json` | Process, mechanical, piping, instrumentation and safeguarding handoff with controlled data gaps |
| `engineering-approval-ledger.json` | Accountable approval history, effective decisions and revision-triggered revalidation state |
| `design-case-envelope.json` | Per-case results and governing pressure, temperature, flow or custom metrics |
| `equipment-register.json` | Equipment identity, design-condition status and governing design values |
| `line-register.json` | Controlled line-list inputs, evidence and completeness status |
| `instrument-register.json` | Registered simulation instruments and proposed instrument/control/safety requirements |
| `engineering-compiler-manifest.json` | Revision, graph fingerprint, artifact inventory and governance status |
| `engineering-schema-catalog.json` and `schemas/*.schema.json` | Versioned Draft 2020-12 contracts shipped with the package |
| `engineering-validation-report.json` | Structural, referential, unit-vocabulary and cross-artifact validation findings |

Every JSON artifact declares both a stable `schemaVersion` and a `schemaUri`. The compiler copies the matching JSON
Schemas into the package, checks canonical node and edge identities, verifies graph references from all registers,
detects duplicate controlled tags, checks governing-case references and units, and reconciles the manifest fingerprint
and artifact inventory. Structural or semantic errors produce `engineering-validation-report.json` and stop compilation
with `EngineeringPackageValidationException`; warnings remain visible without blocking package generation:

```java
EngineeringPackageValidationReport validation = compiled.getValidationReport();
if (validation.getWarningCount() > 0) {
  // Route the package to discipline review before controlled issue.
}

EngineeringPackageValidationReport independentlyChecked =
    EngineeringPackageValidator.validatePackage(compiled.getOutputDirectory());
```

Readers must reject unknown major schema versions instead of silently interpreting them as the current contract.
Additive evolution within a supported version is represented by optional properties; incompatible changes require a
new schema version and an explicit migration reader.

Persist a graph and supply it as the baseline for the next compilation to generate
`engineering-revision-diff.json`:

```java
EngineeringGraph baseline = EngineeringGraph.read(
    Paths.get("controlled/revision-a/engineering-model.json"));
project.setRevision("B");

EngineeringDeliverableCompiler.compile(
    project, Paths.get("build/revision-b"), baseline);
```

The diff reports added, removed and modified nodes and connections. It also propagates impact through `DEPENDS_ON` and
`GENERATED_FROM` relationships so affected calculations and documents can be identified for rerun and reapproval.

## Declare canonical physical connectivity

Use explicit `ProcessSystem.connect(...)` metadata when the engineering package must carry named ports and flow
direction independently of Java stream wiring:

```java
process.connect("20-FEED-001", "outlet", "20-VG-001", "inlet",
    ProcessConnection.ConnectionType.MATERIAL);
process.connect("20-VG-001", "levelSignal", "20-LCV-001", "command",
    ProcessConnection.ConnectionType.SIGNAL);
```

The canonical graph retains the existing equipment-level `CONNECTS_TO` relationship and adds `PORT`, `NOZZLE`,
`PIPE_SEGMENT`, `SIGNAL_CONNECTION`, `ENERGY_CONNECTION`, and `PROCESS_TAP` nodes. Directed `PROCESS_FLOW`,
`SIGNAL_FLOW`, and `ENERGY_FLOW` edges pass through the connection node; `HAS_PORT`, `PART_OF_LINE`, and `MEASURES`
edges preserve ownership and engineering context. Valves and fittings remain compatible `EQUIPMENT` nodes and carry a
`physicalCategory` property for downstream engineering tools.

Package validation rejects malformed segment cardinality, invalid port ownership, flow/connection type mismatches,
direction conflicts, and invalid line or measurement relationships. Dangling ports, unresolved document boundaries,
and controlled lines without a mapped pipe segment remain explicit review warnings. The filtered
`engineering-connectivity.json` view is fingerprint-linked to `engineering-model.json`, so CAD, DEXPI, and data-platform
consumers can read physical topology without treating it as a second source of truth.

## Build a standards-aware calculation DAG

Engineering calculations can declare prerequisite calculations and structured standards references. The compiler
validates the dependency graph, rejects unknown prerequisites and cycles, and emits a deterministic topological order:

```java
EngineeringCalculation reliefBasis = new EngineeringCalculation(
    "20-VG-001-RELIEF-BASIS", separatorNodeId, "Maximum credible relief pressure basis")
    .setStatus(EngineeringCalculation.Status.CALCULATED)
    .setResult(75.0, "bara")
    .setStandardsRequired(true)
    .addStandardReference(new EngineeringCalculation.StandardReference(
        "API 521", "2020", "4.4", "Credible overpressure scenario"));

EngineeringCalculation reliefReview = new EngineeringCalculation(
    "20-VG-001-RELIEF-REVIEW", separatorNodeId, "Relief protection review")
    .dependsOnCalculation("20-VG-001-RELIEF-BASIS")
    .setStandardsRequired(true)
    .addStandardReference(new EngineeringCalculation.StandardReference(
        "API 520 Part I", "2020", "5", "Device sizing and selection"));

project.addCalculation(reliefBasis).addCalculation(reliefReview);
```

Readiness distinguishes complete, ready, dependency-blocked, standards-blocked, explicitly blocked and failed
calculations. A completed calculation that declares standards as required but has no controlled standards basis is a
package validation error. The DAG and canonical graph use the same calculation identities and `DEPENDS_ON` edges.

## Manage design-case coverage and acceptance limits

Design cases can be grouped, prioritized, marked required or optional, and temporarily disabled without deleting their
controlled definition. Metrics can carry lower and upper acceptance limits:

```java
EngineeringDesignCase optionalStudy = new EngineeringDesignCase(
    "CASE-FUTURE", "Future compression study", EngineeringDesignCase.Type.CUSTOM,
    scenario -> configureFutureCompression(scenario))
    .setCaseGroup("FUTURE-STUDIES")
    .setRequired(false)
    .setEnabled(false)
    .setPriority(500);

EngineeringMetric pressure = EngineeringMetric.equipmentPressure("20-VG-001")
    .setAcceptanceRange(null, 70.0);
```

Cases execute by priority on isolated process copies. A metric failure no longer discards other successfully evaluated
metrics from the same case; it produces `CALCULATED_WITH_METRIC_FAILURES`. Disabled cases remain `SKIPPED`, and limit
violations are retained without being mistaken for solver failures. `engineering-design-case-matrix.json` reports the
full case-by-metric coverage, required-case gaps, metric failures and acceptance-limit violations.

## Compile discipline-ready handoffs

`engineering-discipline-package.json` organizes canonical project data for process, mechanical, piping,
instrumentation/automation and process-safety review. It includes equipment datasheets with simulated operating and
governing design values, controlled line-list rows, instrument/control requirements, and safeguarding requirements.
Each item carries its canonical graph identity, approval status, missing fields and readiness state.

The discipline summary distinguishes `REVIEW_REQUIRED`, `INCOMPLETE`, and `NOT_CONFIGURED`. These statuses make data
gaps machine-actionable without claiming that generated documents are approved or fit for construction. Existing
`equipment-register.json`, `line-register.json`, and `instrument-register.json` remain the exchange-facing registers;
the discipline package provides the coordinated review index and detailed cross-discipline context.

## Control approvals across revisions

`EngineeringApprovalRecord` binds an accountable discipline decision to a stable canonical graph node. Approved and
rejected decisions require a reviewer, controlled review reference and effective date. A later record can explicitly
supersede an earlier record, preserving the decision history instead of overwriting it.

`engineering-approval-ledger.json` reports every record and the latest effective state for each subject and discipline.
When compilation includes a baseline graph, an approval whose subject is affected by the revision diff becomes
`REVALIDATION_REQUIRED`. This is a controlled warning: the package remains structurally valid, but the prior approval
cannot be treated as current until an accountable reviewer records a new decision. Approval nodes and `APPROVES` /
`SUPERSEDES` relationships are also included in the canonical engineering graph.

For a multi-area `ProcessModel`, build and export one DEXPI engineering project per area:

```java
List<EngineeringProject> areaProjects = NorsokOffshoreEngineeringBuilder
    .fromProcessModel("Integrated facility", processModel, true);

for (EngineeringProject areaProject : areaProjects) {
  DexpiEngineeringExporter.export(areaProject,
      Paths.get("build/engineering-package", areaProject.getProcessSystem().getName()));
}
```

This preserves the area-document boundary used by DEXPI while retaining shared inter-area stream tags for model-level
reconciliation.

## Executable notebooks

- [Full ProcessSystem engineering package](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_full_processsystem.ipynb)
  demonstrates equipment and piping design, compressor maps, trip envelopes, explicit PSV sizing, transient
  blowdown/flare calculations, materials screening, DEXPI materialization, cause/effect and all sidecars.
- [Multi-area ProcessModel engineering packages](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_processmodel.ipynb)
  demonstrates one governed project/package per process area, shared-stream topology and area-level comparison of
  requirements, calculated design coverage and unresolved inputs.

Both notebooks use workspace Java classes, execute end to end, save their generated package under
`build/notebook-output/`, and retain the `REVIEW_REQUIRED` / not-fit-for-construction governance boundary.

The package contains:

```text
engineering-package/
├── plant.dexpi.xml
├── plant-proteus.xml
├── plant-pydexpi.xml
├── cause-and-effect.json
├── engineering-calculations.json
├── engineering-manifest.json
├── interoperability-report.json
└── datasets/
    └── <compressor-tag>-compressor-map.json
```

`plant.dexpi.xml` uses the native object/property/reference serialization introduced by DEXPI 2.0. Every export is
validated against the official `DEXPI_XML_Schema.xsd` bundled from the DEXPI Specification 2.0 release and then checked
for imported Core/Plant model declarations, unique identities and tags, resolved references, equipment/nozzle integrity,
and piping-node reference types. It carries process equipment, instrumentation functions, safeguard final elements,
off-page connectors, nozzles, piping nodes and process connections.

`plant-proteus.xml` is the backward-compatible graphical P&amp;ID handoff. It uses the Proteus 4.1.1 structure and
contains the generated graphical instrumentation, safeguard proposals and NeqSim engineering attributes described
below. It is deliberately not labelled as native DEXPI 2.0. Keeping both artifacts avoids disguising a Proteus body
with a DEXPI 2.0 header while native graphical DEXPI 2.0 coverage is expanded.

`plant-pydexpi.xml` contains the same governed graphical model with the namespace omitted for compatibility with the
pyDEXPI Proteus importer. Run
`python devtools/validate_dexpi_interoperability.py engineering-package --require-pydexpi --output
engineering-package/interoperability-report.json` to qualify that import. The
generated `interoperability-report.json` keeps native schema/semantic status separate from pyDEXPI and commercial-CAE
qualification. Commercial import and round-trip evidence must follow
`docs/integration/dexpi-commercial-cae-evidence-template.json`; a missing vendor test remains
`QUALIFICATION_REQUIRED` rather than being inferred from schema validity.

The DEXPI model contains explicit, graphically positioned process-instrumentation functions, signal-generating
functions, instrumentation loops, information-flow relationships, control valves, shutdown valves, check valves,
pressure-safety valves, and blowdown valves. Every generated object is associated with its protected equipment and
source requirement. The manifest retains standards, rationale, provenance, review state, and validation findings.
The Proteus file remains the most complete graphical view. Native DEXPI 2.0 now carries the corresponding semantic
instrumentation and safeguard topology; native drawing representations are expanded incrementally and must be checked
in the target CAE tool before controlled issue.

`engineering-calculations.json` runs the existing NeqSim engineering engines and keeps their results in one governed
handoff. It includes equipment mechanical design, simulation operating points, feasible trip-setting envelopes when
declared design limits exist, overpressure/PSV calculations, readiness-gated dynamic blowdown and flare calculations,
and NORSOK materials/corrosion screening. Each section records whether it was calculated, proposed, blocked by missing
input, or requires review. The DEXPI equipment objects reference this file and carry simulated operating conditions and
declared design pressure, temperature, material, corrosion allowance, relief set pressure, and valve failure action
where those values have been supplied.

Version 2 of the calculation handoff also contains a controlled line list and ASME B31.3/NORSOK P-002 screening,
API 521 relief-scenario coverage, low-demand SIF PFD/voting verification, timed shutdown-sequence checks, and an
engineering-readiness matrix. Readiness is reported as completed/required items, missing-input count, completeness
percentage, severity, responsible discipline, and approval state. A 100% completeness score means that the configured
calculation/evidence set is present; it never means that the design is approved or fit for construction.

Readiness counts only successful calculation statuses. A blocked or failed blowdown/flare handoff remains
`NOT_CALCULATED_NOT_READY`, and a relief cause is counted as covered only when its associated study evaluates
successfully. Merely attaching an input object or scenario does not increase completeness.

The materialized compressor template includes antisurge flow/differential-pressure measurement, an antisurge
controller and recycle valve, suction and discharge shutdown isolation, reverse-flow prevention, blowdown, suction
low-pressure, discharge high-pressure/high-temperature, and machinery-protection functions. Separator templates
include pressure and level control, independent high-high/low-low level and pressure functions, relief, and blowdown
proposals. Only equipment already present in the process model is created as process equipment; auxiliary protection
objects are generated as governed P&amp;ID proposals.

Generated antisurge recycle valves are connected from compressor discharge to suction in the graphical topology.
PSV and BDV proposals are connected to dedicated protected-equipment nozzles and placed in a distinct relief/blowdown
network. Suction/discharge ESDVs and non-return valves are connected on the protected-equipment side. Connections that
still require an upstream line, flare-header or disposal-system tie-in carry an explicit `UnresolvedBoundary=YES` and
remain incomplete engineering.

Declare known document boundaries explicitly instead of encoding them only in free text:

```java
project.addBoundary(new EngineeringBoundary("20-FLARE-001", "20-VG-001",
    EngineeringBoundary.Type.FLARE_HEADER));
project.addBoundary(new EngineeringBoundary("20-CD-001", "20-VG-001",
    EngineeringBoundary.Type.CLOSED_DRAIN)
    .resolve("P&ID-20-DR-001", "LINE-LIST-20-REV-B"));
```

Supported types cover process inlet/outlet, flare, vent, closed drain, utility inlet/outlet and recycle tie-ins. Each
is serialized as a directional DEXPI off-page connector. Unresolved boundaries remain review findings in the manifest.

`cause-and-effect.json` is a traceable proposal generated from the same requirements. It intentionally leaves final
set points unassigned. Voting remains unassigned for rule-only requirements; when a controlled
`SafetyFunctionDesign` is attached, its subsystem MooN architectures are written into DEXPI governance attributes and
the cause/effect proposal. Proposed effects still require resolution through HAZOP/LOPA, the shutdown philosophy, and
the safety requirements specification.

## Add sizing studies

Equipment mechanical design and process-derived materials screening run automatically during export. A conservative
blocked-outlet PSV screen is also run when an equipment item has declared design pressure and relief set pressure, a
simulated inlet flow, and a usable fluid:

```java
separator.setDesignConditions(new DesignConditions()
    .setDesignPressure(70.0)
    .setReliefSetPressure(68.0)
    .setMaxDesignTemperature(80.0)
    .setMinDesignTemperature(-46.0));
```

Add explicit API 521 scenarios when the project has established credible causes and rates:

```java
ProtectedItem protectedSeparator = new ProtectedItem("20-VG-001", 70.0)
    .setReliefSetPressureBara(68.0)
    .setBackPressureBara(2.0);

ReliefScenario blockedOutlet = new BlockedOutletRelief()
    .setInflowRateKgPerS(maximumCredibleInflowKgPerS)
    .setReliefPressureBara(68.0)
    .setFluid(relievingFluid)
    .calculate();

project.addOverpressureStudy(
    new OverpressureProtectionStudy(protectedSeparator).addScenario(blockedOutlet));
```

Use `addBlowdownFlareStudy(...)` with a reviewed `DynamicBlowdownFlareStudyDataSource` to run transient vessel
depressurization, concurrent-source aggregation, BDV/orifice checks, PSV sizing, flare load/capacity, radiation and
header Mach checks. The runner will return readiness blockers instead of calculating when required inventory, valve,
fire, header, flare or evidence inputs are absent. Use `setMaterialsReviewInput(...)` to overlay project material
register, aqueous chemistry, inspection, coating and design-life data on the process-derived service conditions.

## Controlled line list and piping design

Hydraulic length, inner diameter, roughness and elevation remain properties of explicit `PipeLineInterface` equipment.
Overlay the controlled mechanical line-list row before export:

```java
project.addLineDesignInput(new LineDesignInput("20-PL-001-A", "20-PL-001")
    .setNominalPipeSize("8")
    .setSchedule("80")
    .setMaterialGrade("X65")
    .setPipingClass("HC-600")
    .setOuterDiameter(219.1, "mm")
    .setNominalWallThickness(12.7, "mm")
    .setCorrosionAllowance(3.0, "mm")
    .setDesignPressureBara(120.0)
    .setDesignTemperatureC(150.0)
    .setEquivalentFittingsLengthM(18.0)
    .setProposedSupportSpacingM(5.0)
    .addEvidenceReference("CONTROLLED-LINE-LIST-REV-A"));
```

The exporter reports simulated pressure drop and velocity, NORSOK P-002 hydraulic checks, minimum wall thickness,
MAOP, stress/velocity screening and support-spacing results from the existing topside piping calculator. Detailed
flexibility, nozzle-load, support, fabrication, flange and stress analysis remain piping-discipline deliverables.

## Relief-scenario coverage

An `OverpressureProtectionStudy` calculates the scenarios it contains. A separate `ReliefScenarioBasis` records which
causes the hazard review determined to be credible, so the exporter can report coverage rather than treating one sized
case as a complete API 521 assessment:

```java
project.addReliefScenarioBasis(new ReliefScenarioBasis("20-VG-001")
    .require(ReliefCause.BLOCKED_OUTLET)
    .require(ReliefCause.CONTROL_VALVE_FAILURE)
    .require(ReliefCause.FIRE)
    .require(ReliefCause.THERMAL_EXPANSION)
    .setHazardReviewReference("HAZOP-20-001")
    .addEvidenceReference("RELIEF-SCENARIO-REGISTER-REV-B"));
```

Use the existing `BlockedOutletRelief`, `ControlValveFailureRelief`, `FireCaseRelief`, `TubeRuptureRelief`,
`CheckValveLeakRelief` and custom `ReliefScenario` APIs to populate the study. Missing credible causes remain explicit.

## SIL/PFD and voting verification

Only add a `SafetyFunctionDesign` after the SIL target and architecture have a controlled LOPA/SRS basis. The
low-demand screening combines sensor, logic-solver and final-element PFD contributions, MooN voting, diagnostic
coverage, repair time, proof-test interval and beta-factor common-cause contribution:

```java
SafetyFunctionDesign sif = new SafetyFunctionDesign(
    "SIF-20-001", "20-KA-001-DISCHARGE-P-HH", 2)
    .setLopaReference("LOPA-20-001")
    .setSrsReference("SRS-20-001")
    .setSafeState("Compressor stopped and isolated")
    .addSubsystem(new SafetyFunctionDesign.Subsystem("Pressure transmitters",
        SafetyFunctionDesign.SubsystemType.SENSOR, 2, 3,
        1.0e-6, 0.60, 8760.0, 8.0, 0.05))
    .addSubsystem(new SafetyFunctionDesign.Subsystem("Safety logic solver",
        SafetyFunctionDesign.SubsystemType.LOGIC_SOLVER, 1, 1,
        1.0e-7, 0.90, 8760.0, 8.0, 0.0))
    .addSubsystem(new SafetyFunctionDesign.Subsystem("Trip and isolation",
        SafetyFunctionDesign.SubsystemType.FINAL_ELEMENT, 1, 1,
        2.0e-6, 0.50, 8760.0, 8.0, 0.0));
project.addSafetyFunctionDesign(sif);
```

The calculation is a transparent verification screen, not a SIL determination. Certified failure data, systematic
capability, independence, proof-test coverage, bypass management and the complete IEC 61511 lifecycle remain required.
Project-defined voting is written into the DEXPI governance attributes and cause/effect sidecar.

## Shutdown sequencing and readiness

Use `ShutdownSequence` to record the cause, protected equipment, safe state, actions, fail positions, delays, execution
times, response-time budget, HAZOP/SRS references and reset/restart definition. The exporter checks sequence completeness
and timing margin. Use the existing `EmergencyShutdownTestRunner` for dynamic process-response validation.

```java
project.addShutdownSequence(new ShutdownSequence("ESD-20-001", "High-high discharge pressure")
    .setProtectedEquipmentTag("20-KA-001")
    .setSafeState("Compressor stopped and isolated")
    .setHazopReference("HAZOP-20-001")
    .setSrsReference("SRS-20-001")
    .setResponseTimeBudgetSeconds(12.0)
    .setResetAndRestartDefined(true)
    .addAction(new ShutdownSequence.Action(
        "20-KA-001", "Trip driver", "STOPPED", 0.5, 1.0))
    .addAction(new ShutdownSequence.Action(
        "ESDV-20-001", "Close isolation", "CLOSED", 1.0, 6.0)));
```

## Safety governance

Automatically generated requirements are deterministic proposals. They are exported with
`RULE_INFERRED`, `REVIEW_REQUIRED`, and—where applicable—`SIL_UNASSIGNED`.

This is intentional. IEC 61511 requires project-specific hazard and risk assessment and a safety
requirements specification. Equipment type or normal operating conditions alone are not a valid basis
for assigning SIL, final trip set points, voting architecture, or required risk reduction.

After HAZOP/LOPA, record the externally determined target and assessment reference:

```java
requirement.setSilTarget("SIL_2", "LOPA-20-001 rev B");
requirement.approve("Responsible automation engineer; SRS-20-001 rev C");
```

Approval records should identify the accountable discipline and controlled project document. The
generated model supports review; it does not replace project assurance or regulatory verification.

## Engineering maturity and current scope

The profile generates requirements and selected materialized functions for separators, compressors, heaters,
coolers, pumps, and control valves. The compressor vertical slice covers antisurge control, suction/discharge
protection, machinery protection, isolation, reverse-flow prevention, settle-out and blowdown assessment, and full
performance-map sidecars.

This output is suitable as a machine-readable concept/pre-FEED starting point, not as an issued-for-construction
P&amp;ID or an approved safety design. NeqSim can calculate equipment and piping design results supported by its
mechanical-design models, API 520/521 relief sizes supported by credible scenarios, dynamic blowdown/flare results
supported by reviewed inventories and network data, and screening-level material recommendations. Project engineering
must still complete and approve the scenario basis, detailed piping/nozzle/stress design, fire-and-gas mapping,
package/vendor interfaces, alarm rationalization, SIL determination and verification, final voting and trip settings,
shutdown actions, tag allocation and maintainability reviews before controlled issue. Native DEXPI XML schema and
native drawing-representation completion are also required before controlled issue. Native schema and supported-profile semantic
validation are automatic. pyDEXPI import is exercised by the interoperability tool. Round-trip qualification in a
named commercial CAE product remains controlled external evidence and is never reported as passed without its test
record. The committed golden regression pair is under `src/test/resources/dexpi/2.0/golden/`.

Company or project rules can implement `EngineeringRule` and be registered with `addRule(...)`. Keep
operator-specific alarm philosophy, trip thresholds, voting, proprietary design requirements, and
internal document references in a private rule package; the public NORSOK profile remains
plant-agnostic.
