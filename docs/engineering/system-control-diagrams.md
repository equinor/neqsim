---
title: "System Control Diagrams with shared P&ID and DEXPI identity"
description: "Generate revision-bound SCD function sheets, explicit template and terminal assignments, native SVG/PDF and DEXPI cross-references from an existing NeqSim automation model."
keywords: "SCD, System Control Diagram, NORSOK I-005, IEC PAS 63131, automation, instrument design, P&ID, DEXPI"
---

# System Control Diagrams

`ScdDesignModel` and `ScdPackageExporter` add System Control Diagram (SCD)
function sheets to the existing native P&ID/DEXPI package. The SCD uses the same
`PidDesignModel` IDs, tags, directed functional links, equipment/line context and
`PidSimulationBindings`. It does not create a second automation graph.

## Standards basis

Select and declare an exact SCD edition in the project's `EngineeringDesignBasis`,
as for the ISO basis of the existing PFD/P&ID workflow. The selected reference is
printed on every SCD sheet and retained in the model and cross-reference register.

| Deliverable | Basis and scope |
| --- | --- |
| PFD/BFD | Existing ISO 10628-1/-2 drawing workflow |
| P&ID | Existing ISO 10628, IEC 62424 and ANSI/ISA-5.1 basis |
| SCD | Explicitly selected NORSOK I-005:2013 or legacy IEC PAS 63131:2017 |
| Exchange | Existing native DEXPI 2.0.1 Plant model, with a linked NeqSim SCD sidecar |

The [IEC publisher record](https://webstore.iec.ch/en/publication/60599) records
IEC PAS 63131:2017 as **withdrawn on 27 September 2024**. Its replacement series
is under development; see the [BSI project record](https://standardsdevelopment.bsigroup.com/projects/2024-03213).
These draft editions are not accepted as published drawing profiles. The NORSOK
edition is also explicitly labelled a legacy project-selected basis; no active
publication-status claim is made for it.

No configured document root was available during initial implementation, and the
complete controlled standards text was not available for normative verification.
Template selection, drawing conventions, terminal meaning and complete function
behaviour require review against the project's controlled text and function library.
The package records **standards conformance not assessed**. Its geometry checks
and DEXPI schema/profile checks are separate evidence.

## Explicit automation inputs

Add these attributes to the shared P&ID elements **before** capturing bindings.
Editing them afterwards intentionally invalidates the old SCD and binding snapshot.

| Attribute | Purpose |
| --- | --- |
| `scdTemplate` | Explicit MA, MB, CA, OA, SBV, SBE or PROJECT selection; missing input remains HOLD |
| `scdTemplateReference` | Project function-library or convention reference for the assignment |
| `system` | Caller-declared PCS, PSD, ESD, SIS or other system; never inferred from tag names |
| `scdInputTerminal`, `scdOutputTerminal` | Explicit terminal designations; absent values remain HOLD |
| `scdInputTerminals`, `scdOutputTerminals` | Optional peer-tag-to-terminal maps overriding common terminals |
| `scdSignalType` | Caller-declared ANALOGUE or BINARY output type |
| `scdSignalTypes` | Optional target-tag-to-type map, for mixed analogue value and binary alarm outputs |

MA/MB assignments are accepted on measurement elements, CA on controllers, OA on
modulating valve output representations, SBV on shutdown/blowdown valve
representations and SBE on electrical signal representations. `PROJECT` explicitly
identifies other project-specific logic. These are bounded representation checks;
assigning a code does not implement its normative function behaviour.

Alarms, trips, voting, permissives, latching/reset, bypass, fail action, priorities,
setpoints and time settings remain source/project data. Existing P&ID attributes
and provenance are retained in the SCD JSON. Their complete graphical template
behaviour is not synthesized. Missing engineering inputs are never filled from
operating conditions. A block without an actual binding remains a design proposal.

## Export

Given a solved `project`, its annotated `pid`, current `bindings`, and a new
`outputDirectory`, the following API sequence is exercised by
`ScdPackageExporterTest.documentedExportSharesNativeIdsAndExecutableBindings`:

```java
EngineeringStandard scdBasis = new EngineeringStandard("NORSOK I-005", "2013",
    "System control diagram", "Project-selected legacy SCD drawing basis; controlled text review required");
project.getDesignBasis().addStandard(scdBasis);
ScdDesignModel scd = new ScdDesignModel(project, pid, bindings, "SCD-20-001", scdBasis);
Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
    .builder("2026-11-08T00:00:00Z", "NeqSim SCD example", "NeqSim", "workspace")
    .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "SCD-REFERENCE")
    .build();
Path output = ScdPackageExporter.export(project, pid, bindings, scd, metadata, outputDirectory);
```

Imports are from `neqsim.process.engineering`, `neqsim.process.engineering.scd`,
`neqsim.process.processmodel.dexpi` and `java.nio.file`. The complete public
synthetic pressure-control fixture and explicit project annotations are in
[the executable test](../../src/test/java/neqsim/process/engineering/scd/ScdPackageExporterTest.java).

The resulting package contains the existing P&ID/PFD drawings, native DEXPI,
saved process and source registers, plus:

- `scd-design-model.json`: detached, revision-bound blocks, signals and binding evidence;
- `scd-dexpi-cross-reference.json`: source P&ID IDs/tags and actual native DEXPI IDs;
- `drawings/scd-drawing-set.pdf`: native A3 vector SCD function sheets;
- `drawings/scd-sheet-NNN.svg` and `.layout.json`: individual sheets and their exact review geometry.

Named S-number continuations identify both ends of every functional link. The
renderer paginates high fan-in/fan-out and feedback without crossed wires.
It retains equipment/line context and explicit system assignments. It does not
yet provide a combined process-background SCD, vendor CAD template library,
IEC 61131-3 code generation or SCD import/round-trip editing.

The renderer owns a detached constructor-time snapshot. The package exporter
checks current project/P&ID/binding identity before rendering and again before
publication; regenerate the SCD after source edits.

Use the existing PFD/P&ID layout checker on each layout sidecar, then inspect the
SVG/PDF visually. Every text item is retained in the sidecar. Standardized
symbols and complete template semantics need separate discipline review.

## Simulation and protection

Continue to use [native compressor automation](../process/safety/compressor-automation-workflow.md)
and the existing P&ID native control/safety APIs for configuration and dynamic tests.
The SCD retains the same runtime names; it does not execute or certify the diagram.
PCS/SIS labels do not establish independence, SIL achievement or hardware separation.
DEXPI represents the supported plant/instrumentation objects; SCD-specific template
data stays in the identified sidecar rather than extending the DEXPI schema.

Related: [DEXPI engineering](dexpi-guide.md),
[P&ID quality review](pid-quality-review.md) and
[engineering deliverables](deliverables-and-handover.md).
