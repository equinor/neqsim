---
title: Engineering diagram document and sheet model
description: Immutable controlled-document views, stable sheet identities, reciprocal off-page references, revision metadata, and proposal boundaries for NeqSim diagrams.
---

# Engineering diagram document and sheet model

NeqSim can project one canonical process topology into an immutable controlled-document proposal.
The document model is separate from process execution scheduling and from every renderer or exchange
format. It is the shared semantic layer for future native SVG/PDF PFD drawing sets and the existing
DEXPI/Proteus P&ID workflow.

The initial model provides:

- stable document-set, drawing, sheet, semantic-object, and off-page-connector identities;
- one sheet for a `ProcessSystem` and deterministic one-sheet-per-area partitioning for a
  `ProcessModel`;
- one authoritative canonical connection projected through exactly two reciprocal off-page
  connectors when it crosses sheets;
- explicit source/peer sheet and connector references with machine-readable pair identity;
- controlled revision history, drawing status, issue purpose, source-graph fingerprint, and
  structured validation diagnostics; and
- immutable canonical semantic-object snapshots retaining source names, carried stream/connection
  designations, case-scoped calculated values, explicit units, quantity basis, engineering state,
  approval state, and provenance; and
- byte-deterministic JSON for equivalent fresh process models.

The initial automatic partition is deliberately conservative. It does not yet choose sheet sizes,
grids, coordinates, routing, symbols, legends, title-block geometry, or manual layout overrides.
Those are later document-rendering concerns and must not be hidden in the process execution graph.

## Java example

```java
EngineeringDiagramDocumentSet documents =
    ProcessDiagramDocumentSetAdapter.fromProcessModel(
        processModel,
        "PLANT-01",
        "A",
        "PFD-01-001",
        "Gas processing facility",
        EngineeringDiagramDocumentSet.ContentProfile.PFD);

if (!documents.isValid()) {
  for (EngineeringDiagramDocumentSet.Diagnostic diagnostic : documents.getDiagnostics()) {
    logger.warn("{}: {}", diagnostic.getCode(), diagnostic.getMessage());
  }
}

String controlledProposalJson = documents.toJson();
```

Use the overload with a final operating-case ID after a successful process run to retain governed
stream results in the same controlled snapshot. Temperature is stored in K, absolute pressure in
bara, and mass flow in kg/s. Each value remains `CALCULATED` and `REVIEW_REQUIRED`, identifies its
case and source semantic object, and includes simulation-result provenance. The topology-only
overloads remain unchanged and do not read live operating values.

Canonical source names and carried connection names are retained as source designations. They are
not silently promoted to project-approved equipment tags or line numbers. Project-entered tags and
stream numbers require their own provenance and review state before they can supersede those source
designations.

The adapter reuses `ProcessDiagramGraphAdapter`. It does not modify the source `ProcessSystem` or
`ProcessModel`, and it does not change legacy `toDOT()`, `ProcessDiagramExporter`, native DEXPI 2.0,
or Proteus/P&ID output.

## Engineering and approval boundary

Generated document sets start with status `WORKING` and issue purpose `ENGINEERING_PROPOSAL`.
They are not approved for design or construction. The model rejects approved/construction state
without an explicit accountable approval reference; a simulation result cannot promote its own
engineering state.

The document model does not claim ISO 10628, ISO 5457, ISO 7200, ISO 14617, ISA, DEXPI EV, or
commercial CAE conformance. Licensed standards mapping, project conventions, qualified symbols,
rendered visual review, external-product round trips, and accountable discipline approval remain
separate evidence gates.

## Validation

Run the focused regression with:

```bash
./mvnw -Dtest=ProcessDiagramDocumentSetAdapterTest test
```

The regression verifies deterministic single- and multi-area output, immutable collections,
unchanged Classic DOT output, distinct parallel cross-sheet connections, reciprocal pair
cardinality, and fail-visible broken references.
It also verifies immutable semantic snapshots, unit/case/provenance retention, rejection of
incompletely governed simulation-result values, warning-only diagnostics for pre-existing generic
calculation graphs, and unchanged topology-only behavior.
