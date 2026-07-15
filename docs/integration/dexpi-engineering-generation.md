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
├── engineering-manifest.json
└── datasets/
    └── <compressor-tag>-compressor-map.json
```

The DEXPI equipment elements reference their applicable engineering requirement IDs and compressor-map
documents. The manifest retains standards, rationale, provenance, review state, and validation findings.

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

## Current scope

The first profile generates requirements for separators, compressors, heaters, coolers, pumps, and
control valves. The compressor vertical slice covers antisurge control, suction/discharge protection,
machinery protection, isolation, settle-out, blowdown assessment, and full performance-map sidecars.

Future profiles should add project-specific rule packs, line/nozzle specification, relief-scenario
objects, cause-and-effect matrices, fire-and-gas mapping, utilities, vendor package details, and native
DEXPI 2.0 schema validation as the official tool ecosystem matures.

Company or project rules can implement `EngineeringRule` and be registered with `addRule(...)`. Keep
operator-specific alarm philosophy, trip thresholds, voting, proprietary design requirements, and
internal document references in a private rule package; the public NORSOK profile remains
plant-agnostic.
