---
title: Engineering diagram document and sheet model
description: Immutable controlled-document views, stable sheet identities, reciprocal off-page references, revision metadata, and proposal boundaries for NeqSim diagrams.
---

# Engineering diagram document and sheet model

NeqSim can project one canonical process topology into an immutable controlled-document proposal.
The document model is separate from process execution scheduling and from every renderer or exchange
format. It is the shared semantic layer for native SVG/PDF PFD drawing sets and the existing
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
  approval state, and provenance;
- opt-in reviewed equipment tags and stream numbers with explicit source, reviewer, review record,
  timestamp, and project revision evidence; and
- deterministic revision impact identifying added, removed, and modified semantic objects and every
  affected drawing and sheet; and
- opt-in manual sheet definitions, stable object-to-sheet assignments, pinned coordinates, and
  protected connection routes with explicit review evidence; and
- distinct conservative BFD, PFD, and P&ID drawing views over the same complete canonical semantic
  object snapshot, with structured diagnostics for every profile omission; and
- byte-deterministic JSON for equivalent fresh process models.

The automatic partition remains deliberately conservative. It does not choose sheet sizes, grids,
symbols, legends, or title-block geometry. Manual layout intent is supplied separately through an
`EngineeringDiagramLayoutRegister`; it is never hidden in the process execution graph.

## Content profiles

`ContentProfile` controls only drawing and sheet membership. `getSemanticObjects()` always retains
the complete canonical model with the same stable identities, properties, units, provenance, and
connections, regardless of the selected profile.

| Profile | Drawing-view content | Deliberate omissions |
| --- | --- | --- |
| `BFD` | Areas, equipment blocks, boundaries, material ports/nozzles, and material connections | Controlled line detail, energy connections, instrumentation, and signal connections |
| `PFD` | Areas, equipment, boundaries, controlled lines, material and energy ports/connections | Instrumentation and signal ports/connections |
| `PID` | Every visual semantic object currently represented by the canonical model | None at the content-policy layer; source-adapter losses remain diagnostic |

Parallel material connections remain distinct in all three profiles. Multi-area off-page connector
pairs are created only for connections visible in the selected profile, so a BFD does not acquire
energy or signal cross-sheet references and a PFD does not acquire signal references. Manual sheet,
pinned-position, or protected-route evidence targeting an omitted object is retained in its source
register but reported as `DIAGRAM_CONTENT_PROFILE_LAYOUT_OMITTED` rather than silently changing the
profile.

Every document set records `DIAGRAM_CONTENT_PROFILE_PROPOSAL_ONLY`, and each omitted visual object
records `DIAGRAM_CONTENT_PROFILE_OBJECT_OMITTED`. These are loss/projection diagnostics, not errors;
the omitted objects remain available in the canonical semantic snapshot and to other drawing or
exchange projections.

The BFD policy treats current unit operations as conservative functional blocks; it does not infer
or aggregate licensed-standard process blocks. The PFD and P&ID policies likewise do not qualify
symbols, content, layout, measurement/control conventions, or drawing practice. None of the three
profiles claims ISO 10628, ISO 14617, ISA, or project-standard conformance or engineering approval.

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
bara, mass flow in kg/s, and mass-specific enthalpy in J/kg. Each value remains `CALCULATED` and
`REVIEW_REQUIRED`, identifies its case and source semantic object, and includes simulation-result
provenance. The topology-only
overloads remain unchanged and do not read live operating values.

### Governed stream-table companion

`EngineeringDiagramStreamTable` creates an immutable, deterministic stream-table companion from one
operating-case document snapshot. It reads only canonical `LINE` and `CALCULATION` semantic objects;
it does not read live process objects or add fields to `EngineeringDiagramDocumentSet.toJson()`.
Every row retains the canonical stream ID, external key, source label, and process area. Temperature,
absolute pressure, mass flow, and mass-specific enthalpy retain their explicit unit, quantity basis,
engineering state, approval state, source calculation ID, and simulation-result provenance.

A reviewed `STREAM_NUMBER` designation is preferred as the display identifier and its source
reference is retained. Without reviewed evidence, the canonical source label remains visible.
Missing cases or quantities, malformed values, duplicate values, and calculation references to
unknown streams are reported as structured diagnostics rather than silently invented or discarded.

```java
EngineeringDiagramDocumentSet operatingDocuments =
    ProcessDiagramDocumentSetAdapter.fromProcessModel(
        processModel,
        "PLANT-01",
        "B",
        "PFD-01-001",
        "Gas processing facility",
        EngineeringDiagramDocumentSet.ContentProfile.PFD,
        "NORMAL-01",
        new EngineeringDiagramDesignationRegister());
EngineeringDiagramStreamTable streamTable =
    EngineeringDiagramStreamTable.fromDocumentSet(operatingDocuments, "NORMAL-01");

for (EngineeringDiagramStreamTable.Row row : streamTable.getRows()) {
  EngineeringDiagramStreamTable.Value pressure =
      row.getValues().get(EngineeringDiagramStreamTable.Quantity.PRESSURE);
  if (pressure != null) {
    logger.info("{} pressure: {} {}", row.getDisplayIdentifier(), pressure.getResultValue(),
        pressure.getResultUnit());
  }
}
```

### Explicit-boundary mass and stream-enthalpy balances

`EngineeringDiagramBalanceTable` aggregates only explicit boundary assignments against a governed
stream table. Each assignment records a stable balance ID, canonical stream semantic ID, inlet or
outlet direction, source reference, and `PROPOSED` or `REVIEWED` evidence state. The aggregator never
guesses direction from drawing topology. Unknown or duplicate assignments, missing values, wrong
units or bases, negative boundary flow, non-finite results, and source-table losses remain visible as
structured diagnostics.

```java
List<EngineeringDiagramBalanceTable.Boundary> boundaries =
    Arrays.asList(
        new EngineeringDiagramBalanceTable.Boundary(
            "BAL-AREA-01",
            feedStreamId,
            EngineeringDiagramBalanceTable.Direction.INLET,
            "project-balance-register:BAL-AREA-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED),
        new EngineeringDiagramBalanceTable.Boundary(
            "BAL-AREA-01",
            productStreamId,
            EngineeringDiagramBalanceTable.Direction.OUTLET,
            "project-balance-register:BAL-AREA-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED));
EngineeringDiagramBalanceTable balanceTable =
    EngineeringDiagramBalanceTable.fromStreamTable(streamTable, boundaries);
```

For each balance, mass residual is inlet mass flow minus outlet mass flow in kg/s. Relative mass
residual divides that result by the larger absolute inlet or outlet total and is zero when both totals
are zero. Stream enthalpy flow is `massFlow [kg/s] * specificEnthalpy [J/kg]` in W; its residual is the
inlet total minus the outlet total, and its relative residual uses the same larger-total denominator.
It intentionally excludes equipment heat duties and shaft work, so it is not a complete energy
balance.

### Explicit component-resolved mass balances

`EngineeringDiagramComponentBalanceTable` is an additive companion to an existing explicit-boundary
balance table. It binds user-supplied component mass-flow records to the same stable balance and
stream identities. Every record retains the source component identity and name, explicit `kg/s`
unit, `COMPONENT_MASS` basis, source reference, provenance, and `PROPOSED` or `REVIEWED` evidence
state. A component requires one explicit zero or non-zero value on every declared boundary stream;
missing, duplicate, unknown, non-finite, negative, wrongly based, or wrongly unitized values remain
visible as structured diagnostics.

```java
List<EngineeringDiagramComponentBalanceTable.ComponentFlow> componentFlows =
    Arrays.asList(
        new EngineeringDiagramComponentBalanceTable.ComponentFlow(
            "BAL-AREA-01",
            feedStreamId,
            "methane",
            "methane",
            8.5,
            "kg/s",
            "COMPONENT_MASS",
            "project-component-register:BAL-AREA-01",
            "simulation-case:NORMAL-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED),
        new EngineeringDiagramComponentBalanceTable.ComponentFlow(
            "BAL-AREA-01",
            productStreamId,
            "methane",
            "methane",
            8.5,
            "kg/s",
            "COMPONENT_MASS",
            "project-component-register:BAL-AREA-01",
            "simulation-case:NORMAL-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED));
EngineeringDiagramComponentBalanceTable componentBalances =
    EngineeringDiagramComponentBalanceTable.fromBalanceTable(balanceTable, componentFlows);
```

For each stable balance/component pair, the projection reports inlet and outlet component mass flow,
their residual, the larger-total relative residual, declared and supplied boundary counts, and an
explicit completeness flag. It does not derive composition from a live process object, infer omitted
zeroes, apply tolerances, reconcile values, or close equipment heat/work terms. The sum of supplied
component inlet/outlet totals is also compared with the source total-mass balance; a difference is a
warning with no inferred project tolerance. This separation keeps component data provenance explicit
and leaves existing stream-table, balance-table, controlled
document, Classic DOT/Graphviz, native SVG/PDF, DEXPI 2.0 Process, and Proteus/P&ID outputs unchanged.

### Explicit heat-transfer and shaft-work closure

`EngineeringDiagramEnergyBalanceTable` is an additive companion to an existing explicit-boundary
balance table. It closes the source stream-enthalpy terms with explicitly declared heat-transfer and
shaft-work ports. Every port retains a stable balance identity, canonical equipment or control-volume
identity, distinct stable port identity, energy kind, direction relative to the control volume, source
reference, and evidence state. Separate port identities preserve parallel energy connections without
collapsing them.

Energy-flow values are non-negative `W` values on an `ENERGY_RATE` basis. Direction comes only from
the port declaration, never from a sign convention, drawing topology, or live equipment duty. Every
declared port requires one explicit zero or non-zero flow with source, provenance, and evidence state.

```java
List<EngineeringDiagramEnergyBalanceTable.EnergyPort> energyPorts =
    Arrays.asList(
        new EngineeringDiagramEnergyBalanceTable.EnergyPort(
            "BAL-AREA-01",
            heaterSemanticObjectId,
            "energy-port:heater-duty",
            EngineeringDiagramEnergyBalanceTable.EnergyKind.HEAT_TRANSFER,
            EngineeringDiagramEnergyBalanceTable.EnergyDirection.INTO_CONTROL_VOLUME,
            "project-energy-register:BAL-AREA-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED),
        new EngineeringDiagramEnergyBalanceTable.EnergyPort(
            "BAL-AREA-01",
            compressorSemanticObjectId,
            "energy-port:compressor-shaft",
            EngineeringDiagramEnergyBalanceTable.EnergyKind.SHAFT_WORK,
            EngineeringDiagramEnergyBalanceTable.EnergyDirection.INTO_CONTROL_VOLUME,
            "project-energy-register:BAL-AREA-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED));
List<EngineeringDiagramEnergyBalanceTable.EnergyFlow> energyFlows =
    Arrays.asList(
        new EngineeringDiagramEnergyBalanceTable.EnergyFlow(
            "BAL-AREA-01",
            "energy-port:heater-duty",
            250000.0,
            "W",
            "ENERGY_RATE",
            "simulation-result:NORMAL-01:heater-duty",
            "simulation-case:NORMAL-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED),
        new EngineeringDiagramEnergyBalanceTable.EnergyFlow(
            "BAL-AREA-01",
            "energy-port:compressor-shaft",
            125000.0,
            "W",
            "ENERGY_RATE",
            "simulation-result:NORMAL-01:compressor-power",
            "simulation-case:NORMAL-01",
            EngineeringDiagramBalanceTable.EvidenceState.PROPOSED));
EngineeringDiagramEnergyBalanceTable energyBalances =
    EngineeringDiagramEnergyBalanceTable.fromBalanceTable(
        balanceTable, energyPorts, energyFlows);
```

For each balance, total energy input is inlet stream enthalpy flow plus heat transfer and shaft work
declared into the control volume. Total energy output is outlet stream enthalpy flow plus heat
transfer and shaft work declared out of the control volume. The energy residual is total input minus
total output in W; its relative residual divides by the larger absolute total and is zero when both
totals are zero. A result is complete only when the source stream-enthalpy balance is complete, the
balance has at least one explicit energy port, and every port has one usable flow value.

Unknown or duplicate balances, ports, and flows; missing values; non-finite or negative rates; and
wrong units or bases remain visible as structured diagnostics. A zero energy rate must be recorded
explicitly. The companion does not read live equipment duties, infer absent heat/work terms, apply a
project tolerance, reconcile data, or approve a balance. Existing balance/component APIs,
controlled-document JSON, Classic DOT/Graphviz, native SVG/PDF, DEXPI 2.0 Process, and Proteus/P&ID
outputs remain unchanged.

### Explicit tolerance assessment

`EngineeringDiagramBalanceAssessment` evaluates existing residuals against explicit, sourced
criteria without changing any stream value. Each criterion identifies one stable balance and
declares absolute and relative limits for mass residual (kg/s and dimensionless) and stream-enthalpy
residual (W and dimensionless). A quantity is `WITHIN_TOLERANCE` only when both residual magnitudes
satisfy their declared limits. Missing, duplicate, unknown, incomplete, or exceeded criteria remain
visible through deterministic status values and structured diagnostics.

```java
EngineeringDiagramBalanceAssessment.Criteria criteria =
    new EngineeringDiagramBalanceAssessment.Criteria(
        "BAL-AREA-01",
        0.01,
        0.001,
        1000.0,
        0.01,
        "project-balance-criteria:BAL-AREA-01",
        EngineeringDiagramBalanceTable.EvidenceState.PROPOSED);
EngineeringDiagramBalanceAssessment assessment =
    EngineeringDiagramBalanceAssessment.fromBalanceTable(
        balanceTable, Collections.singletonList(criteria));
```

The assessment itself is a tolerance check, not statistical data reconciliation. It does not adjust
measured or calculated values, supply component balances, close heat/work terms, infer project
tolerances, or approve a balance.

### Tolerance-gated mass-balance reconciliation

`EngineeringDiagramMassBalanceReconciliation` is an additive evidence projection over the stream
table, balance table, and assessment. It invokes NeqSim's existing linear weighted-least-squares
engine only for a complete mass balance classified `OUTSIDE_TOLERANCE`. A balance already within its
declared limits is retained unchanged and needs no uncertainty declarations.

Every participating boundary stream requires a positive finite standard uncertainty in kg/s on a
`MASS` basis, together with a source reference, provenance, and evidence state. Source fingerprints
and document/graph/case identity must match. Unknown, duplicate, invalid, or missing uncertainty
records; incomplete or underspecified boundaries; solver failures; negative candidates; and
candidates outside the explicit criteria produce structured diagnostics.

```java
List<EngineeringDiagramMassBalanceReconciliation.Uncertainty> uncertainties =
    Arrays.asList(
        new EngineeringDiagramMassBalanceReconciliation.Uncertainty(
            "BAL-AREA-01", inletSemanticId, 0.05, "kg/s", "MASS",
            "instrument-register:FT-101", "calibration certificate CAL-101",
            EngineeringDiagramBalanceTable.EvidenceState.REVIEWED),
        new EngineeringDiagramMassBalanceReconciliation.Uncertainty(
            "BAL-AREA-01", outletSemanticId, 0.08, "kg/s", "MASS",
            "instrument-register:FT-102", "calibration certificate CAL-102",
            EngineeringDiagramBalanceTable.EvidenceState.REVIEWED));
EngineeringDiagramMassBalanceReconciliation reconciliation =
    EngineeringDiagramMassBalanceReconciliation.fromSources(
        streamTable, balanceTable, assessment, uncertainties);
```

The result contains immutable source-to-candidate adjustments, balance-level residual and
statistical evidence, deterministic JSON/fingerprints, and fail-visible diagnostics. It never writes
reconciled values back to a `ProcessSystem`, stream table, balance table, diagram, or exchange file.
It does not eliminate gross errors, model covariance, reconcile component or energy balances, or
turn reviewed evidence into engineering approval. Those are distinct engineering layers.

Building either companion does not change Classic DOT/Graphviz, native SVG/PDF, DEXPI 2.0 Process
exchange, or the Proteus/DEXPI P&ID workflow. `REVIEWED` boundary or criterion evidence does not
approve a PFD, P&ID, simulation result, balance, tolerance, or design data.

Canonical source names and carried connection names are retained as source designations. They are
not silently promoted to project-approved equipment tags or line numbers. Project-entered tags and
stream numbers require their own provenance and review state before they can supersede those source
designations.

Use an `EngineeringDiagramDesignationRegister` when a project has reviewed designation evidence.
The register targets stable semantic-object IDs and supports `EQUIPMENT_TAG` for equipment plus
`STREAM_NUMBER` for line or pipe-segment objects. An invalid target or designation/object-kind
combination produces a structured error and is not attached to the semantic object. The canonical
source label remains available alongside the reviewed designation.

```java
EngineeringDiagramDesignationRegister register =
    new EngineeringDiagramDesignationRegister()
        .withDesignation(
            new EngineeringDiagramDesignationRegister.Designation(
                equipmentId,
                EngineeringDiagramDesignationRegister.Kind.EQUIPMENT_TAG,
                "V-101",
                "project-register:equipment-tags",
                EngineeringDiagramDesignationRegister.ReviewState.REVIEWED,
                "Process discipline",
                "review:TAG-42",
                "2026-08-13T07:00:00Z",
                "B"));

EngineeringDiagramDocumentSet reviewedDocuments =
    ProcessDiagramDocumentSetAdapter.fromProcessModel(
        processModel,
        "PLANT-01",
        "B",
        "PFD-01-001",
        "Gas processing facility",
        EngineeringDiagramDocumentSet.ContentProfile.PFD,
        register);
```

### Persistent manual layout intent

Use an `EngineeringDiagramLayoutRegister` to retain reviewed layout choices across regeneration.
The register can add a stable manual sheet, assign an equipment or endpoint object to that sheet,
pin an object in millimetres, and protect a connection route with ordered waypoints. Every entry
records its source, evidence state, recorder, timestamp, and revision. The document adapter validates
all stable IDs and produces structured errors instead of silently applying stale references.

Connection sheet membership is derived from its endpoint locations. Moving equipment also moves its
owned ports and nozzles unless an endpoint has an explicit assignment. When the endpoints land on
different sheets, the adapter retains one authoritative semantic connection and creates exactly two
reciprocal off-page connectors. Protected route geometry is attached to each applicable sheet view;
it does not create another process connection.

```java
EngineeringDiagramLayoutRegister layout =
    new EngineeringDiagramLayoutRegister()
        .withSheet(
            new EngineeringDiagramLayoutRegister.SheetDefinition(
                "separator-detail",
                "002",
                "Separator detail",
                "project-layout-register",
                EngineeringDiagramLayoutRegister.EvidenceState.REVIEWED,
                "Process discipline",
                "2026-08-13T12:00:00Z",
                "B"))
        .withAssignment(
            new EngineeringDiagramLayoutRegister.SheetAssignment(
                separatorId,
                "separator-detail",
                "project-layout-register",
                EngineeringDiagramLayoutRegister.EvidenceState.REVIEWED,
                "Process discipline",
                "2026-08-13T12:00:00Z",
                "B"))
        .withPinnedPosition(
            new EngineeringDiagramLayoutRegister.PinnedPosition(
                separatorId,
                "separator-detail",
                180.0,
                95.0,
                EngineeringDiagramLayoutRegister.CoordinateUnit.MILLIMETRE,
                "project-layout-register",
                EngineeringDiagramLayoutRegister.EvidenceState.REVIEWED,
                "Process discipline",
                "2026-08-13T12:00:00Z",
                "B"));

EngineeringDiagramDocumentSet manuallyArranged =
    ProcessDiagramDocumentSetAdapter.fromProcessModel(
        processModel,
        "PLANT-01",
        "B",
        "PFD-01-001",
        "Gas processing facility",
        EngineeringDiagramDocumentSet.ContentProfile.PFD,
        register,
        layout);
```

Coordinates and waypoints are renderer-neutral proposal data. The register does not assert that a
route is physically constructible, that a layout complies with a drawing standard, or that an
accountable engineer has approved the result.

### Deterministic native SVG/PDF rendering

`NativeEngineeringDiagramRenderer` projects the controlled document set directly to native SVG and
PDF without Graphviz. Both formats consume the same deterministic page plan. The renderer preserves
stable sheet and semantic-object IDs, positions pinned in drawing-paper millimetres, protected route
waypoints, reciprocal off-page references, drawing/sheet titles, revision, status, issue purpose,
and source-graph fingerprint.

```java
NativeEngineeringDiagramRenderer.Result rendered =
    new NativeEngineeringDiagramRenderer(
            manuallyArranged,
            NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE)
        .render();

Map<String, String> svgBySheetId = rendered.getSvgBySheetId();
byte[] multiPagePdf = rendered.getPdf();
Map<String, String> visualFingerprints = rendered.getVisualFingerprintsBySheetId();
for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : rendered.getDiagnostics()) {
  logger.warn("{}: {}", diagnostic.getCode(), diagnostic.getMessage());
}
```

`A3_LANDSCAPE` and `A1_LANDSCAPE` provide controlled paper geometry in millimetres. They identify
the paper dimensions only; they do not assert standards conformance. Unpinned objects receive a
stable grid position, while out-of-bounds pinned coordinates, protected routes, and automatic
layout overflow remain fail-visible through structured diagnostics. Manual geometry is retained
unchanged instead of being silently repaired.

SVG output contains stable `data-sheet-id`, `data-semantic-id`, and protected-route attributes for
machine inspection. Every material, energy, and information connection receives a deterministic
primary label at the geometric half-length of its rendered route. PDF output is a deterministic vector
drawing set with one page per controlled sheet. Use `exportSvg(directory)` or `exportPdf(path)` when
files are required. Rendering does not modify the source document set, legacy DOT/Graphviz output,
DEXPI 2.0 Process exchange, or the Proteus/DEXPI P&ID workflow.

`getVisualFingerprintsBySheetId()` exposes one normalized SHA-256 fingerprint per sheet. The
fingerprint covers visible page geometry, text, and style shared by SVG and PDF while excluding XML/PDF
serialization syntax and invisible semantic identifiers. Store a reviewed fingerprint as a regression
baseline only together with the corresponding rendered artifacts and accountable visual review; a hash
match is evidence of unchanged rendering, not evidence that the drawing is correct.

The rendering result also carries deterministic drawing-quality diagnostics. It reports overlapping
object symbols, symbols clipped by the border/header/title-block boundary, primary labels estimated
to exceed their available symbol width, connection routes crossing non-endpoint objects, connection
labels overlapping objects or other connection labels, missing connection labels, and missing
semantic-object references. Existing controlled-document diagnostics are retained in the same report,
including broken reciprocal off-page pairs and stale manual layout references. Errors make
`Result.isComplete()` false; warnings retain the proposed geometry unchanged for review. These
geometric and text-width checks are conservative proposal gates, not proof of standards compliance or
accountable visual approval.

Compare two revisions of the same document-set and plant identity with `baseline.compareTo(revised)`.
The returned `EngineeringDiagramRevisionImpact` has deterministic added, removed, and modified
semantic-object IDs. It projects those changes to the sheets and drawings containing each object in
either revision. A cross-sheet stream-number change therefore affects both sheets while retaining
one semantic connection identity.

The adapter reuses `ProcessDiagramGraphAdapter`. It does not modify the source `ProcessSystem` or
`ProcessModel`, and it does not change legacy `toDOT()`, `ProcessDiagramExporter`, native DEXPI 2.0,
or Proteus/P&ID output.

## Engineering and approval boundary

Generated document sets start with status `WORKING` and issue purpose `ENGINEERING_PROPOSAL`.
They are not approved for design or construction. The model rejects approved/construction state
without an explicit accountable approval reference; a simulation result cannot promote its own
engineering state.

Designation and layout registers record review evidence only. They do not represent engineering
approval, do not authorize a P&ID or PFD for design or construction, and do not replace project
ownership of piping, valve, nozzle, instrument, safeguard, routing, or design data.

The document model and native renderer do not claim ISO 10628, ISO 5457, ISO 7200, ISO 14617, ISA,
DEXPI EV, or commercial CAE conformance. Licensed standards mapping, project conventions, qualified
symbols, rendered visual review, external-product round trips, and accountable discipline approval
remain separate evidence gates. Working proposals carry an explicit not-approved-for-design-or-
construction banner in native output.

## Validation

Run the focused regression with:

```bash
./mvnw -Dtest=ProcessDiagramDocumentSetAdapterTest test
./mvnw -Dtest=NativeEngineeringDiagramRendererTest test
./mvnw -Dtest=EngineeringDiagramStreamTableTest,EngineeringDiagramBalanceTableTest,\
EngineeringDiagramComponentBalanceTableTest,EngineeringDiagramEnergyBalanceTableTest,\
EngineeringDiagramBalanceAssessmentTest,EngineeringDiagramMassBalanceReconciliationTest test
```

The regression verifies deterministic single- and multi-area output, immutable collections,
unchanged Classic DOT output, distinct parallel cross-sheet connections, reciprocal pair
cardinality, and fail-visible broken references.
It also verifies immutable semantic snapshots, unit/case/provenance retention, rejection of
incompletely governed simulation-result values, warning-only diagnostics for pre-existing generic
calculation graphs, unchanged topology-only behavior, reviewed designation governance, fail-visible
designation mismatches, deterministic cross-sheet revision impact, persistent manual layout evidence,
unchanged semantic identities after layout-only revision changes, protected-route retention, and
fail-visible stale layout references.
The native-renderer regression verifies byte-deterministic SVG/PDF, A3 and A1 geometry, exact pinned
coordinates and protected routes, reciprocal off-page references, deterministic connection labels,
route/object and route-label obstacle diagnostics, collision, clipping, label-overflow and
broken-reference diagnostics, normalized visual fingerprints, multi-page drawing sets, fresh-model
determinism, and unchanged Classic DOT and controlled-document JSON.

