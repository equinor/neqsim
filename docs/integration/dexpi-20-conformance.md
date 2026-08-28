---
title: DEXPI 2.0 native exchange and conformance
description: Official native DEXPI XML Plant and Process model export, deterministic validation, and auditable conformance evidence.
---

# DEXPI 2.0 native exchange and conformance

> Start with the [DEXPI Engineering Guide](../engineering/dexpi-guide) when deciding whether the recipient needs the
> native Plant/P&ID model, native Process/PFD/BFD model, or a Proteus-compatible exchange.

DEXPI 2.0.0 was released on 10 October 2025. It combines the Plant/P&ID and Process/PFD/BFD
information models and introduces DEXPI XML as their common serialization. Native DEXPI XML has a
`Model` root containing `Import`, `Object`, `Components`, `Data`, and `References` elements; it is
not Proteus XML with a different namespace.

NeqSim provides two fail-closed native exporters:

| API | Official model imports | Exchange purpose |
|---|---|---|
| `Dexpi20XmlWriter` | Core 2.0.0 + Plant 2.0.0 | P&ID plant items, piping, instruments, safeguards, boundaries, and diagram representations |
| `Dexpi20ProcessModelWriter` | Core 2.0.0 + Process 2.0.0 | PFD/BFD process steps, material ports, streams, and physical state quantities |

The official DEXPI XML V2.0.0 XSD is bundled under CC BY 4.0 for deterministic offline validation.
`Dexpi20ConformanceAssessment` verifies its reviewed SHA-256 fingerprint before using the result as
conformance evidence.

## Process-model export

```java
File exchange = new File("gas-processing-pfd.dexpi.xml");
Dexpi20ConformanceAssessment.Report report =
    Dexpi20ProcessModelWriter.writeAndAssess(process, exchange);
if (!report.isSchemaAndProfileConformant()) {
  throw new IllegalStateException(report.getErrors().toString());
}
Files.write(Paths.get("gas-processing-pfd.conformance.json"),
    report.toJson().getBytes(StandardCharsets.UTF_8));
```

For a migration or regression gate, also compare the supported material topology with the shared
canonical diagram graph:

```java
Dexpi20ProcessTopologyAssessment.Report topology =
    Dexpi20ProcessModelWriter.writeAndAssessTopology(
        process, exchange, "PLANT-001", "A");
if (!topology.isSchemaProfileAndSupportedTopologyValid()) {
  throw new IllegalStateException(topology.getDiagnostics().toString());
}
Files.write(Paths.get("gas-processing-pfd.topology.json"),
    topology.toJson().getBytes(StandardCharsets.UTF_8));
```

The topology report records `exportTopologySource=CANONICAL_ENGINEERING_GRAPH`, the canonical graph
fingerprint and stable connection IDs, calculated/review-required source provenance, the canonical and exported directed
material-connection manifests, every exported stream and its distinct source/target port IDs, and structured diagnostics. Connection comparison
is multiplicity-sensitive, so two parallel streams between the same steps must remain two streams.
Synthetic product sinks are retained in the exported-connection inventory but excluded from the
in-model topology comparison.

`writeAndAssessTopology(...)` builds one canonical snapshot, uses its supported material-connection
projection to drive the native Process exchange, and assesses the same snapshot. Regression coverage
requires the assessed simple and parallel-branch output to preserve the existing sequential DEXPI
serialization. The compatibility APIs `write(...)` and `writeAndAssess(...)` still use their direct
`ProcessSystem` traversal and remain unchanged.

After a successful run, an opt-in overload can source physical quantities from the same canonical
snapshot instead of rereading streams during serialization:

```java
process.run();
Dexpi20ProcessTopologyAssessment.Report operatingCase =
    Dexpi20ProcessModelWriter.writeAndAssessTopology(
        process, exchange, "PLANT-001", "A", "NORMAL-001");
```

This overload records
`exportOperatingValueSource=CANONICAL_ENGINEERING_GRAPH_CALCULATION_NODES`. It accepts only
finite, case-matched canonical calculation nodes with the reviewed K, bara-absolute, and kg/s
bases, then converts them deterministically to degree Celsius, bar absolute, and kilogram/hour for
DEXPI Process. A missing or incompatible node omits that individual quantity and emits
`DEXPI_PROCESS_OPERATING_VALUE_MISSING`; it never falls back to a live stream read. The established
four-argument overload remains topology-only and retains its existing XML and report shape.

## Multi-area ProcessModel package

`Dexpi20ProcessModelPackageWriter.writeAndAssess(...)` provides a bounded multi-area delivery
container without inventing a DEXPI whole-plant hierarchy:

```java
File processPackage = new File("facility-process-model.zip");
Dexpi20ProcessModelPackageWriter.Report packageReport =
    Dexpi20ProcessModelPackageWriter.writeAndAssess(
        processModel, processPackage, "PLANT-001", "A");
if (!packageReport.isComplete()) {
  throw new IllegalStateException(packageReport.toJson());
}
```

The deterministic ZIP contains one independently schema/profile/topology-assessed native DEXPI
Process XML file per named `ProcessModel` area and a NeqSim `manifest.json`. The manifest pins the
canonical plant fingerprint, stable area and connection IDs, source revision, file SHA-256 values,
and structured loss diagnostics. An optional operating-case overload uses the same case-matched
canonical calculation-node rules as the single-area assessed exporter.

Cross-area material connections are preserved as `MANIFEST_ONLY_CROSS_AREA`. Energy and
information connections are preserved as `MANIFEST_ONLY_NOT_MAPPED_TO_NATIVE_PROCESS`. They are
therefore explicit plant-wide semantic evidence, but are not claimed to be native DEXPI Process
relationships. Controlled documents/sheets and graphics remain separate projections. The package
reports `nativeWholePlantDexpiExchange=false`, `approvalStatus=REVIEW_REQUIRED`, and
`fitnessForConstruction=false`; it is not a new DEXPI profile, standards-conformance claim, or
drawing approval.

When DEXPI must be published together with its controlled document and native drawing views, use
the [engineering diagram delivery workflow](engineering-diagram-delivery.md). The facade composes
the assessed writer above with controlled JSON, native SVG/PDF, deterministic artifact hashes, and
the same review-required boundaries; it does not merge graphical or document semantics into the
DEXPI information model.

For package intake or restart, independently reassess rather than trusting the embedded manifest:

```java
Dexpi20ProcessModelPackageAssessment.Report intakeReport =
    Dexpi20ProcessModelPackageAssessment.assess(processPackage);
if (!intakeReport.isValid()) {
  throw new IllegalStateException(intakeReport.toJson());
}
```

The offline assessment enforces bounded archive entry names and sizes, the versioned manifest
contract, exact manifest/area/package SHA-256 evidence, unique stable area and connection identities,
and the declared manifest-only status plus loss diagnostic for every cross-area material, energy,
and information connection. It independently reruns the bundled DEXPI 2.0 schema, official Process
import, and NeqSim supported-profile assessment for each XML. A restarted package cannot promote
`REVIEW_REQUIRED`, `fitnessForConstruction=false`, or
`nativeWholePlantDexpiExchange=false`; altered approval fields fail closed.

After assessment, use the bounded reader when a caller needs exact per-area XML and connection
evidence without extracting archive paths or trusting the embedded manifest:

```java
Dexpi20ProcessModelPackageReader.Snapshot snapshot =
    Dexpi20ProcessModelPackageReader.read(processPackage);
for (Dexpi20ProcessModelPackageReader.AreaDocument area : snapshot.getAreaDocuments()) {
  byte[] exactAssessedXml = area.getXmlBytes();
  // Pass the defensive copy to a controlled DEXPI Process consumer.
}
```

`read(...)` reassesses first, captures the archive only within the bounded package size, and
requires the captured bytes to match the assessment SHA-256 before exposing content. The serializable
snapshot retains plant/revision/case provenance, package and manifest fingerprints, defensive exact
area XML, and immutable material/energy/signal connection evidence. It deliberately does not extract
files, reconstruct or execute a `ProcessModel`, reinterpret manifest-only connections as native
DEXPI Process relationships, or change the engineering approval state.

Compare two valid snapshots when a controlled revision needs machine-readable intake evidence:

```java
Dexpi20ProcessModelPackageRevisionImpact impact =
    Dexpi20ProcessModelPackageRevisionImpact.compare(baselineSnapshot, revisedSnapshot);
if (!impact.isConnectionTopologyEquivalent()) {
  Files.write(Paths.get("facility-process-model.revision-impact.json"),
      impact.toJson().getBytes(StandardCharsets.UTF_8));
}
```

The revision-impact report classifies every stable area and material/energy/signal connection as
`ADDED`, `REMOVED`, `MODIFIED`, or `UNCHANGED`. Exact package, manifest, canonical, and
per-area XML SHA-256 evidence remains separate from stable area-identity and assessed connection
equivalence. This prevents a controlled revision or case change from being mislabeled as a
topology change while still retaining byte-level document evidence. Comparison requires the same
plant identity and always returns `REVIEW_REQUIRED`,
`engineeringReviewRequired=true`, `fitnessForConstruction=false`, and
`nativeWholePlantDexpiExchange=false`; it is revision evidence, not an MOC decision or approval.

Project assessed package changes onto independently regenerated controlled document revisions when
the handover must identify every affected drawing and sheet without reconstructing a simulation:

```java
Dexpi20ProcessModelPackageDocumentImpact documentImpact =
    Dexpi20ProcessModelPackageDocumentImpact.project(
        impact, baselineDocumentSet, revisedDocumentSet);
if (!documentImpact.isProjectionComplete()) {
  throw new IllegalStateException(documentImpact.toJson());
}
Files.write(Paths.get("facility-process-model.document-impact.json"),
    documentImpact.toJson().getBytes(StandardCharsets.UTF_8));
```

The cross-artifact report maps changed stable area and material/energy/signal connection identities
through both controlled document revisions, including reciprocal off-page connector views. It also
detects semantic-object, sheet-content, reviewed-designation, and persistent-layout changes and
lists coordinated review scopes for controlled documents, drawings, sheets, designation/layout
registers, operating-case tables, information registers, and engineering studies. An area or
connection identity absent from both document revisions produces `INCOMPLETE`, never a silent
success. Revision-only package evidence does not invent sheet impact.

This report remains `REVIEW_REQUIRED` and not fit for construction. Its review-scope list is a
deterministic coordination aid, not proof that a table, study, MOC process, external DEXPI validator,
or accountable drawing review has been completed.

The single-area assessed path reports energy and signal connections, multi-area `ProcessModel`
hierarchy, controlled document/sheet semantics, and drawing graphics as unsupported scopes. These
warnings do not hide a supported material-topology error; missing, unexpected, unresolved-port, and
reused-port findings are errors.

Each process connection has a dedicated source and target `MaterialPort`. The ports and
`Process.Stream` carry reciprocal references, stable identifiers, nominal directions, and explicit
mass-flow, absolute-pressure, and temperature quantities when finite simulation values are
available. The exporter uses kilogram/hour, bar absolute, and degree Celsius references from the
official Core physical-quantity model.

Reviewed NeqSim-to-DEXPI Process mappings are:

| NeqSim equipment | DEXPI 2.0 Process type |
|---|---|
| feed or boundary `Stream` | `Source` |
| unconsumed product outlet | `Sink` |
| compressor | `Compressing` |
| pump | `Pumping` |
| separator | `SeparatingByGravity` |
| distillation column | `Distilling` |
| cooler | `Cooling` |
| heat exchanger | `ExchangingThermalEnergy` |
| heater | `HeatingInFurnace` |
| tank | `StoringFluids` |
| control valve | `RegulatingFlow` |
| mixer | `MixingSimple` |
| splitter | `SplittingMaterial` |
| expander | `TransportingFluids` |
| pipeline or pipe segment | `TransportingFluids` |

An unmapped equipment class aborts export with its Java type and tag. The exporter never substitutes
an unreviewed generic DEXPI type merely to make a file validate.

## Conformance layers

`Dexpi20ConformanceAssessment` records separate decisions for:

1. the bundled official V2.0.0 schema fingerprint;
2. DEXPI XML schema validation;
3. exact versioned Core plus Plant or Process imports;
4. unique identities and resolvable references;
5. NeqSim-supported Plant or Process semantic-profile rules; and
6. the SHA-256 digest and object/reference counts of the assessed file.

Only a report with all five technical gates passing returns
`isSchemaAndProfileConformant() == true`. A Plant file assessed as a Process profile, a version-mixed
model import, dangling reference, missing required port/stream relationship, or unknown generated
profile type fails explicitly.

The report deliberately states `NOT_A_DEXPI_EV_CERTIFICATE` and
`namedCaeRoundTripStatus=QUALIFICATION_REQUIRED`. Schema and supported-profile conformance do not
replace import/export/reimport testing in the exact CAE product and version used by a project.
Record that separate evidence through `DexpiToolQualificationRunner` and
`DexpiToolQualificationEvidence`.

## Release and review controls

- Pin exchange evidence to DEXPI 2.0.0 model URIs and the file digest; never silently reinterpret a
  later DEXPI model release as 2.0.0.
- Store the conformance JSON, source model revision, generated XML, named-tool round-trip, and
  accountable semantic-difference review together.
- Compare the committed native golden fixture and semantic inventory when modifying type mappings,
  topology, units, or serialization order.
- Retain the canonical topology fingerprint, structured topology report, operating-case identifier
  and value-source evidence, and source model revision beside the XML and conformance report when
  using the opt-in operating-case overload of `writeAndAssessTopology(...)`.
- For a multi-area package, retain the ZIP and manifest SHA-256 values and review every manifest-only
  cross-area, energy, and information diagnostic; do not treat package completeness as native
  whole-plant DEXPI coverage.
- Treat graphics, project standard-library restrictions, vendor extensions, and CAE certification as
  separate qualification scopes.

Official references: [DEXPI Specification 2.0.0](https://dexpi.gitlab.io/-/Specification/-/jobs/11676485644/artifacts/src/.build/html/html/index.html)
and the [DEXPI specification source](https://gitlab.com/dexpi/Specification), licensed CC BY 4.0.
