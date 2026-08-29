---
title: Engineering diagram delivery workflow
description: Fail-closed publication of assessed DEXPI, controlled semantic JSON, native SVG/PDF, and deterministic review evidence from ProcessSystem or ProcessModel.
---

# Engineering diagram delivery workflow

`EngineeringDiagramDelivery` is an opt-in Java facade for publishing the related engineering-diagram
projections of one canonical NeqSim model as a single assessed delivery. It supports both a
`ProcessSystem` and a multi-area `ProcessModel` without changing the existing DOT/Graphviz, Classic,
DEXPI, Proteus, document-model, or native-renderer APIs.

Every published directory contains:

| Artifact | Purpose |
| --- | --- |
| `document-set.json` | Immutable controlled semantic document, drawing, sheet, off-page, revision, units, provenance, and loss evidence |
| `svg/*.svg` | One deterministic native vector sheet per stable sheet ID |
| `drawing-set.pdf` | One deterministic native multi-page PDF drawing set |
| `dexpi-process.xml` | Assessed native DEXPI 2.0 Process exchange for a `ProcessSystem` |
| `dexpi-process-model.zip` | Deterministic NeqSim container of assessed per-area native DEXPI Process files for a `ProcessModel` |
| `delivery-manifest.json` | Content hashes, canonical/document/visual fingerprints, bounded DEXPI assessment, renderer diagnostics, and approval boundaries |

Exactly one of the two DEXPI artifacts is present. A multi-area package is deliberately identified
as `nativeWholePlantDexpiExchange=false`; cross-area material and unsupported energy/information
relationships remain explicit manifest evidence with structured loss diagnostics.

The DEXPI artifact always uses the 2.0 Process PFD/BFD information model. If the controlled document
uses the `PID` profile, the manifest records
`DELIVERY_PID_VIEW_NOT_DEXPI_PLANT_EXCHANGE`: the native view remains a proposal and the Process
exchange does not replace the existing DEXPI Plant/Proteus P&ID workflow. Multi-area delivery also
records `DELIVERY_DEXPI_AREA_PACKAGE_NOT_NATIVE_WHOLE_PLANT`.

## Java example

```java
EngineeringDiagramDelivery.Request request =
    EngineeringDiagramDelivery.Request.builder(
            "PLANT-01",
            "A",
            "PFD-01-001",
            "Gas processing facility",
            EngineeringDiagramDocumentSet.ContentProfile.PFD)
        .sheetFormat(NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE)
        .routingMode(NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL)
        .build();

EngineeringDiagramDelivery.Report delivery =
    EngineeringDiagramDelivery.deliver(
        processModel, Paths.get("build/engineering-delivery"), request);
if (!delivery.isComplete()) {
  throw new IllegalStateException(delivery.toJson());
}
```

After a successful process run, add `.operatingCaseId("NORMAL-01")` to retain the canonical,
unit-explicit operating-case snapshot in the controlled document and assessed DEXPI output. Optional
designation, layout, and symbol-convention registers can be supplied through the request builder;
their existing evidence and review rules remain authoritative.

The destination directory must not exist. The facade writes to a sibling staging directory, runs
the controlled-document, native-rendering, and bounded DEXPI gates, and publishes the complete
directory only when every gate passes. It never replaces an existing delivery. Artifact paths in
the manifest are relative, and every published content artifact has its byte length, media type,
and SHA-256 fingerprint. Environment-specific absolute paths are excluded, so equivalent fresh
models produce equivalent manifest content in different directories.

## Independent intake assessment

Use `EngineeringDiagramDeliveryAssessment` before relying on a stored, copied, or transferred
delivery:

```java
EngineeringDiagramDeliveryAssessment.Report assessment =
    EngineeringDiagramDeliveryAssessment.assess(
        Paths.get("build/received-engineering-delivery"));
if (!assessment.isComplete()) {
  throw new IllegalStateException(assessment.toJson());
}
```

The assessor does not modify or extract content. It independently recomputes the manifest
fingerprint and every declared artifact's byte length and SHA-256, validates media types and the
mandatory review boundaries, and requires the exact declared file set. Portable relative paths
must remain inside the delivery root; symbolic links, duplicate paths, unlisted files, missing
projections, and a DEXPI artifact inconsistent with `PROCESS_SYSTEM` or `PROCESS_MODEL` are
reported as structured errors. Intake is bounded to 4,096 artifacts, 256 MiB per artifact, and
512 MiB in total.

A successful report is deterministic and serializable restart evidence. It proves exact package
integrity within the published contract; it does not reconstruct or execute a `ProcessSystem` or
`ProcessModel`, repeat DEXPI semantic assessment, approve a drawing, or establish standards or
commercial-CAE conformance.

## Compare two verified deliveries

Use `EngineeringDiagramDeliveryComparison` after independent intake assessment to distinguish an
unchanged transferred copy from a controlled revision:

```java
EngineeringDiagramDeliveryComparison.Report comparison =
    EngineeringDiagramDeliveryComparison.compare(
        Paths.get("build/baseline-delivery"),
        Paths.get("build/revised-delivery"));
if (!comparison.isComplete()) {
  throw new IllegalStateException(comparison.toJson());
}
```

Both inputs must pass `EngineeringDiagramDeliveryAssessment`, use the same plant identity, and
share the same `ProcessSystem` or `ProcessModel` source scope. The deterministic report classifies
every declared artifact as `ADDED`, `REMOVED`, `MODIFIED`, or `UNCHANGED`; identifies changed
controlled JSON, native SVG, PDF, DEXPI Process XML, or multi-area DEXPI package projections; and
returns the corresponding review scopes. Environment-specific directory paths are excluded from
the comparison fingerprint.

Changed content with an unchanged controlled revision fails closed as
`REVISION_REUSE` with `DELIVERY_REVISION_REUSED_WITH_CHANGED_CONTENT`. A normal revision change
remains `REVIEW_REQUIRED`. This comparison is artifact and manifest evidence only: it does not
repeat DEXPI semantic or external-validator assessment, reconstruct a process model, decide
management of change, approve a drawing, or establish fitness for construction or standards
conformance.

## Engineering and qualification boundary

The generated document status, issue purpose, source revision, calculated values, and diagnostics
come from the controlled semantic model. Delivery publication does not promote their engineering
authority. The manifest always records:

- `approvalStatus=REVIEW_REQUIRED`;
- `fitnessForConstruction=false`; and
- `iso10628ConformanceClaimed=false`.

A simulation-driven BFD or PFD is an engineering proposal. A P&ID remains a proposal until
accountable design data and discipline review exist. Project symbol conventions can improve a
native view, but standards mapping requires licensed source traceability and accountable review.
The delivery gate is therefore a reproducibility and bounded-assessment gate, not DEXPI EV
certification, commercial-CAE interoperability evidence, drawing approval, or standards
conformance.
