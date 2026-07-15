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
├── cause-and-effect.json
├── engineering-calculations.json
├── engineering-manifest.json
└── datasets/
    └── <compressor-tag>-compressor-map.json
```

The DEXPI model contains explicit, graphically positioned process-instrumentation functions, signal-generating
functions, instrumentation loops, information-flow relationships, control valves, shutdown valves, check valves,
pressure-safety valves, and blowdown valves. Every generated object is associated with its protected equipment and
source requirement. The manifest retains standards, rationale, provenance, review state, and validation findings.

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

The materialized compressor template includes antisurge flow/differential-pressure measurement, an antisurge
controller and recycle valve, suction and discharge shutdown isolation, reverse-flow prevention, blowdown, suction
low-pressure, discharge high-pressure/high-temperature, and machinery-protection functions. Separator templates
include pressure and level control, independent high-high/low-low level and pressure functions, relief, and blowdown
proposals. Only equipment already present in the process model is created as process equipment; auxiliary protection
objects are generated as governed P&amp;ID proposals.

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
shutdown actions, tag allocation, maintainability reviews, and native DEXPI 2.0 schema/tool validation before
controlled issue.

Company or project rules can implement `EngineeringRule` and be registered with `addRule(...)`. Keep
operator-specific alarm philosophy, trip thresholds, voting, proprietary design requirements, and
internal document references in a private rule package; the public NORSOK profile remains
plant-agnostic.
