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

The package contains:

```text
engineering-package/
├── plant.dexpi.xml
├── cause-and-effect.json
├── engineering-manifest.json
└── datasets/
    └── <compressor-tag>-compressor-map.json
```

The DEXPI model contains explicit, graphically positioned process-instrumentation functions, signal-generating
functions, instrumentation loops, information-flow relationships, control valves, shutdown valves, check valves,
pressure-safety valves, and blowdown valves. Every generated object is associated with its protected equipment and
source requirement. The manifest retains standards, rationale, provenance, review state, and validation findings.

The materialized compressor template includes antisurge flow/differential-pressure measurement, an antisurge
controller and recycle valve, suction and discharge shutdown isolation, reverse-flow prevention, blowdown, suction
low-pressure, discharge high-pressure/high-temperature, and machinery-protection functions. Separator templates
include pressure and level control, independent high-high/low-low level and pressure functions, relief, and blowdown
proposals. Only equipment already present in the process model is created as process equipment; auxiliary protection
objects are generated as governed P&amp;ID proposals.

`cause-and-effect.json` is a traceable proposal generated from the same requirements. It intentionally leaves final
set points and voting architectures unassigned and identifies proposed effects that must be resolved through
HAZOP/LOPA, the shutdown philosophy, and the safety requirements specification.

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
P&amp;ID or an approved safety design. Project engineering must complete line/nozzle and piping specifications,
relief-scenario sizing, flare and blowdown hydraulics, fire-and-gas mapping, package/vendor interfaces, alarm
rationalization, SIL determination and verification, tag allocation, maintainability reviews, and native DEXPI 2.0
schema/tool validation before controlled issue.

Company or project rules can implement `EngineeringRule` and be registered with `addRule(...)`. Keep
operator-specific alarm philosophy, trip thresholds, voting, proprietary design requirements, and
internal document references in a private rule package; the public NORSOK profile remains
plant-agnostic.
