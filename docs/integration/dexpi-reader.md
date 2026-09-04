---
title: "DEXPI P&ID Import, Export and Visualization"
description: "Complete DEXPI integration for NeqSim — import P&ID XML, export professional drawings, and preserve supported SIS and HIPPS semantics."
keywords: "DEXPI, P&ID, piping and instrumentation diagram, XML import, XML export, ISO 10628, process flow diagram, PFD, SIS, HIPPS, safety function, visualization"
---

> New to the available exchange profiles? Start with the
> [DEXPI Engineering Guide](../engineering/dexpi-guide.md) to choose between Proteus compatibility, pyDEXPI rendering,
> native DEXPI 2.0 Plant/P&ID, native Process/PFD/BFD, and governed engineering-package workflows.

NeqSim provides a complete [DEXPI](https://dexpi.org/) integration that supports:

- **Import** — parse DEXPI XML P&ID documents into runnable `ProcessSystem` models
- **Export** — serialize any NeqSim process into DEXPI XML with professional P&ID layout
- **Round-trip** — import, simulate, and re-export with simulation results embedded
- **Visualization** — auto-layout with ISO 10628:2012 shapes, instruments, signal lines, drawing borders, stream tables, and symbol legends

## Architecture overview

| Class | Purpose |
|-------|---------|
| `DexpiXmlReader` | Parses DEXPI XML into lightweight `DexpiProcessUnit` / `DexpiStream` objects |
| `DexpiXmlWriter` | Exports `ProcessSystem` to DEXPI XML with full P&ID layout |
| `DexpiLayoutEngine` | Computes auto-layout positions; renders graphical elements (shapes, lines, instruments, borders) |
| `DexpiShapeCatalog` | Generates `ShapeCatalogue` with 22 ISO 10628:2012 equipment shapes |
| `DexpiLayoutConfig` | Configurable layout parameters (spacing, fonts, colors, feature toggles) |
| `DexpiMetadata` | Shared constants for `GenericAttribute` names used by both reader and writer |
| `DexpiSimulationBuilder` | High-level builder: DEXPI XML to runnable `ProcessSystem` |
| `DexpiTopologyResolver` | Nozzle/connection graph, topological sort, cycle detection |
| `DexpiEquipmentFactory` | Converts DEXPI placeholders to real NeqSim equipment with sizing |
| `DexpiMappingLoader` | Thread-safe cached loader for equipment mapping `.properties` files |
| `DexpiStreamUtils` | Shared outlet-stream resolution for separators, splitters, two-port equipment |
| `DexpiRoundTripProfile` | Validates minimal metadata for reliable round-trip fidelity |
| `DexpiStream` | Lightweight piping segment with DEXPI class, line number, and fluid code |
| `DexpiProcessUnit` | Imported equipment with original DEXPI class and mapped `EquipmentEnum` |
| `DexpiInstrumentInfo` | Instrument metadata (tag, type, function letter) |
| `DexpiConnectionInfo` | Immutable source-order material-connection evidence and endpoint resolution |

---

## Importing DEXPI XML

The `DexpiXmlReader` converts DEXPI XML P&ID exports into `ProcessSystem` models. It recognises
major equipment (pumps, heat exchangers, tanks, control valves, reactors, compressors, inline
analysers) and imports piping segments as runnable `DexpiStream` units tagged with the source line
number.

### P&ID and SCD handoffs from STID or PDF sources

DEXPI XML is the preferred direct import path because it preserves equipment, nozzles, piping
segments, instruments, and signal-line structure in a machine-readable form. When the source is STID
metadata, a scanned P&ID, a PDF drawing, or a System Control Diagram (SCD), first normalize the
evidence outside the DEXPI reader instead of treating the drawing as a runnable model.

Use this separation of responsibility:

| Source evidence | Normalize to | NeqSim target |
|-----------------|--------------|---------------|
| STID tag and document metadata | equipment, document, instrument, relief, and control-loop candidates | `StidHazopDataSource`, `OperationalTagMap`, task evidence packs |
| P&ID drawing content | process nodes, material edges, valves, nozzles, drains, vents, and line numbers | `ProcessSystem`, `ProcessConnection`, `DexpiSimulationBuilder` input, or manual flowsheet setup |
| SCD / cause-and-effect content | initiators, logic, final elements, trips, permissives, and setpoints | measurement devices, controllers, `ProcessConnection.ConnectionType.SIGNAL`, operational scenarios |
| Historian or plant-data tag map | public logical tags bound to private historian tags and automation addresses | `OperationalTagMap` and `ProcessAutomation` |

The current NeqSim scope is therefore not CAD recognition. It is deterministic use of normalized
P&ID/SCD evidence once a document reader, OCR step, DEXPI converter, or STID connector has extracted
the relevant structure. This keeps engineering evidence traceable: unreviewed metadata can seed a
model scaffold, while controlled drawing content is required before line routing, interlocks,
setpoints, or safeguard credit are used in a calculation.

For SCD-like workflows, model the control content as live simulation objects rather than as a drawing
file: transmitters, controller devices, signal connections, alarm/trip schedules, and operational
scenarios. This matches the existing dynamic-simulation and operations APIs and allows the same
logical tag map to support field-data comparison, HAZOP preparation, and what-if valve or trip
studies.

```java
Path xmlFile = Paths.get("/path/to/dexpi.xml");
SystemSrkEos exampleFluid = new SystemSrkEos(298.15, 50.0);
exampleFluid.addComponent("methane", 0.9);
exampleFluid.addComponent("ethane", 0.1);
exampleFluid.setMixingRule(2);
exampleFluid.init(0);

Stream template = new Stream("feed", exampleFluid);
template.setFlowRate(1.0, "MSm3/day");
template.setPressure(50.0, "bara");
template.setTemperature(30.0, "C");

ProcessSystem process = DexpiXmlReader.read(xmlFile.toFile(), template);

DexpiProcessUnit feedPump = (DexpiProcessUnit) process.getUnit("P4711");
if (feedPump.getMappedEquipment() == EquipmentEnum.Pump) {
  // handle pump metadata
}
```

### Structured supported-subset diagnostics

Use `readWithDiagnostics(...)` when an import must retain machine-readable evidence for source
objects that the reader cannot reconstruct. The returned process is built by the same Java parser
as `read(...)`; the additional report lists skipped objects in deterministic source-document order.

```java
DexpiXmlReader.ImportResult result =
    DexpiXmlReader.readWithDiagnostics(xmlFile.toFile(), template);
ProcessSystem process = result.getProcessSystem();
List<DexpiInstrumentInfo> instruments = result.getInstruments();
List<DexpiConnectionInfo> connections = result.getConnections();

for (DexpiXmlReader.ImportDiagnostic diagnostic : result.getDiagnostics()) {
  System.out.printf("%s %s %s%n",
      diagnostic.getSeverity(), diagnostic.getCode(), diagnostic.getElementId());
}

String evidenceJson = result.toJson();
```

The JSON uses the stable report schema `neqsim_dexpi_proteus_import.v1` and records the exact
source ID, component class, XML element name, severity, and diagnostic code for every unsupported
or unclassified equipment or piping-component object. Warnings describe honest supported-subset
loss; malformed XML and parser failures still raise `DexpiXmlReaderException`. Existing `read(...)`
and `load(...)` calls keep their previous behavior and logging.

Piping-network diagnostics also distinguish source-backed values from reconstruction aids. Calling
`readWithDiagnostics(...)` without a template records `DEXPI_IMPORT_DEFAULT_TEMPLATE_USED`, because
the compatibility reader supplies a synthetic methane/ethane fluid and operating state. For every
segment, the report identifies operating values retained from the template, invalid source numbers,
and source numbers whose units had to use the documented default. Missing identity, component class,
line number, service/fluid code, nominal-size representation, piping class, and insulation are
reported separately. In particular, the reader never converts hydraulic bore into DN, NPS, or
schedule; absent nominal size remains an explicit source-data gap.

The same import now returns the source instrumentation inventory parsed from that one XML document.
These `DexpiInstrumentInfo` objects are metadata records, not live controllers or transmitters.
Instrumentation diagnostics report missing identity and function metadata, explicitly missing or
unresolved sensing attachments, unresolved loop membership, incomplete measuring-line and signal
source/target references, missing signal medium, and incomplete final-element or actuation-location
evidence. Valid source references resolve against the complete non-catalogue document identity set,
including equipment nozzles and actuating functions. The reader never promotes measurement-only or
incomplete source content into closed-loop control intent.

The same result also preserves every source `Connection` in document order, including parallel
connections between the same endpoints. Each immutable `DexpiConnectionInfo` retains the owning
piping-network segment, `FromID` and `ToID` direction, the resolved endpoint element names, and
resolution status. Missing source IDs receive deterministic evidence-only identities; missing,
duplicate, self-referential, or unresolved source references remain explicit diagnostics. This
inventory does not infer connectivity or rewire the returned `ProcessSystem`.

For resolved nozzle endpoints, the same record exposes only explicit source ownership: the nearest
ancestor `Equipment` or `PipingComponent` identity and XML element name. Direct equipment or
piping-component endpoints own themselves. Orphaned nozzles and owner elements without source IDs
remain deterministic warnings; the reader does not derive ownership from coordinates, tags,
stream order, or simulation state.

`ImportResult.getConnectionEndpoints()` provides a distinct endpoint inventory in first-reference
order. Each immutable `DexpiConnectionEndpointInfo` records the resolved element and explicit owner
evidence plus the incoming and outgoing connection-evidence IDs in source order. Counts retain every
occurrence, including parallel connections. Blank endpoint references remain findings and are omitted
from this keyed inventory; unresolved non-empty IDs remain visible.

`getIncidenceRole()` classifies only this directed source evidence: zero incoming and one outgoing
is `SOURCE`; one incoming and zero outgoing is `SINK`; one of each is `PASS_THROUGH`; one
incoming and multiple outgoing is `SPLIT`; multiple incoming and one outgoing is `MERGE`; and
all remaining non-empty patterns are `COMPLEX`. `isPotentialMultiConnectionNode()` reports
whether either directed side has multiple occurrences. These values help reviewers locate topology
that needs engineering interpretation. They do not prove hydraulic continuity, identify a physical
branch or fitting, create live process connectivity, or establish process intent.

`ImportResult.getConnectionComponents()` groups non-empty endpoint identities by weak connectivity
through explicit material-connection references. Components are ordered by their first endpoint;
their endpoint IDs retain first-reference order and their connection-evidence IDs retain source
order, including parallel occurrences. Each immutable `DexpiConnectionComponentInfo` also lists
source, sink, potential multi-connection, and unresolved endpoints from the endpoint evidence.
A connection with one non-empty endpoint remains visible as a singleton component. A connection
with two blank endpoint references remains diagnostic evidence and is not assigned invented nodes.
This grouping is a document-review aid, not proof that the grouped references form a hydraulically
continuous line or executable process network.

`ImportResult.getConnectionCycles()` exposes cyclic strongly connected groups in the directed graph
of explicit non-empty material-connection endpoint references. A group is reported when it has more
than one endpoint, or when a singleton endpoint has an explicit self-reference. Cycle groups are
ordered by their first endpoint; endpoint IDs retain first-reference order and internal connection
IDs retain source order, including parallel occurrences. `getEndpoints()` exposes the corresponding
immutable `DexpiConnectionEndpointInfo` records in first-reference order, including incidence role,
resolution state, resolved XML element name, and Equipment/PipingComponent ownership. This lets Java
and JPype reviewers inspect complete cycle-local endpoint evidence without joining against the global
endpoint inventory. `getConnections()` exposes the corresponding immutable
`DexpiConnectionInfo` records in the same order, including source connection and owning
`PipingNetworkSegment` identities, endpoint resolution, resolved element names, and explicit
Equipment/PipingComponent ownership. Each immutable `DexpiConnectionCycleInfo` links to its owning
weak connection component, keeps unresolved endpoint IDs visible, and separately lists source-ordered
connection occurrences entering and leaving the cyclic group. Boundary lists preserve parallel
references and exclude connections whose source or target identity is blank.
`getBoundaryConnections()` additionally preserves the overall source order
across incoming and outgoing occurrences. Each immutable `DexpiConnectionCycleBoundaryInfo` records
the connection evidence ID, original source connection ID, owning `PipingNetworkSegment` ID,
direction relative to the cyclic group, explicit internal and external endpoint identities, whether
each endpoint resolves, and the resolved endpoint element and Equipment/PipingComponent owner
identities already present in the endpoint inventory. Reader-produced records also expose the
complete immutable source objects through `getConnection()`, `getInternalEndpoint()`, and
`getExternalEndpoint()`. Java and JPype callers can therefore inspect connection direction,
endpoint resolution and ownership, full source-ordered incidence lists, and incidence roles locally;
`hasCompleteEvidence()` distinguishes that projection from legacy values built with the older
constructors. Legacy object getters return `null` while their existing scalar evidence remains
unchanged. Missing source, segment, endpoint, or owner evidence remains an empty field. This avoids
joining separate inventories or inferring boundary orientation, provenance, or ownership while
retaining parallel and unresolved source evidence. It does not identify a hydraulic recycle,
enumerate elementary paths, assert convergence behavior, repair or rewire topology, or establish
process intent.

`toJson()` includes `instrumentCount`, `connectionCount`, `connectionEndpointCount`,
`connectionComponentCount`, `connectionCycleCount`, and the ordered connection, endpoint,
component, and directed-cycle inventories, including incidence roles, review subsets, complete
cycle-local endpoint and internal connection occurrences, and explicit cycle-boundary occurrences with
nested connection and endpoint evidence, alongside the process-unit
count and findings. Python callers through
JPype use the same
`ImportResult.getInstruments()`, `ImportResult.getConnections()`,
`ImportResult.getConnectionEndpoints()`, `ImportResult.getConnectionComponents()`, and
`ImportResult.getConnectionCycles()` getters; there is no separate Python reconstruction model.

`INFO` entries carry provenance and do not make `hasLosses()` true by themselves. `WARNING` and
`ERROR` entries do. The diagnostic sequence and JSON are deterministic for the same XML and template.
The report schema is experimental: consumers should key on stable diagnostic codes rather than list
positions as additional supported-subset checks are added.

This evidence applies to NeqSim's Proteus-compatible DEXPI Plant/P&ID 4.1.1 supported subset. It is
not proof of full semantic or graphical round-trip equivalence, native DEXPI 2.0 support, DEXPI
certification, standards conformance, or engineering approval.

Each imported equipment item is represented as a lightweight `DexpiProcessUnit` that records the
original DEXPI class, mapped `EquipmentEnum` category, and contextual information (line numbers,
fluid codes). Piping segments become `DexpiStream` objects that clone pressure, temperature, and
flow settings from the template stream.

### Metadata conventions

Both the reader and writer share `DexpiMetadata` constants that describe the recommended generic
attributes for DEXPI exchanges. Equipment exports include tag names, line numbers, and fluid codes.
Piping segments carry segment numbers, nominal-diameter representations, piping-class and
insulation codes, and operating pressure/temperature/flow triples with explicit unit annotations.
The reader preserves these values on `DexpiStream`; it does not derive them from hydraulic inside
diameter. Query `DexpiMetadata.recommendedStreamAttributes()` and
`DexpiMetadata.recommendedEquipmentAttributes()` for the minimal metadata sets guaranteed by NeqSim.

---

## Exporting to DEXPI XML

The `DexpiXmlWriter` serializes any `ProcessSystem` into a DEXPI XML document with professional
P&ID visualization. The writer produces valid DEXPI XML with proper namespace declarations.

### Basic export

```java
// Build and run a process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.run();

// Export with auto-layout
DexpiXmlWriter.write(process, new File("output.xml"));
```

### NeqSim-native SVG rendering

`DexpiXmlSvgRenderer` renders the geometry in a Proteus-compatible DEXPI Plant/P&ID exchange
directly to SVG. It resolves each equipment, valve, nozzle, off-page connector, and instrument
instance against the document's `ShapeCatalogue`, and preserves process, signal, utility, label,
stream-table, drawing-border, and title-block primitives. The SVG is therefore a view of the DEXPI
document rather than a separately reconstructed process diagram.

```java
File dexpi = new File("plant.xml");
File svg = new File("plant.svg");

DexpiXmlWriter.write(process, dexpi);
DexpiXmlSvgRenderer.render(dexpi, svg);
```

For automated drawing-quality gates, assess the same Proteus-compatible exchange before publishing
the SVG:

```java
DexpiVisualQualityAssessment.Report visualReport =
    DexpiVisualQualityAssessment.assess(dexpi);
Files.write(
    new File("plant.visual-quality.json").toPath(),
    visualReport.toJson().getBytes(StandardCharsets.UTF_8)
);
if (visualReport.hasErrors()) {
  throw new IllegalStateException(visualReport.toJson());
}
```

The report records the exact `PlantInformation/@SchemaVersion`, drawing extent, source and SVG
primitive counts, catalogue-instance coverage, positioned-coordinate bounds, text-height risks,
duplicate identities, and the deterministic SVG SHA-256. Errors identify geometry that cannot be
rendered faithfully; warnings identify visible review risks. The assessment is a software quality
gate, not an ISO-conformance, DEXPI-certification, engineering-approval, or
fitness-for-construction decision.

The renderer is self-contained Java and does not require Graphviz or pyDEXPI. Generated title
blocks are marked `PROPOSAL`, and their initial revision is `Engineering Proposal`; a controlled
owner status and accountable review are required before a drawing can be issued for design or
construction. Use an independent DEXPI consumer as an interoperability check when qualifying an
exchange for a project handoff.

An intentionally unconfigured `new Stream("spare")` may remain registered in the `ProcessSystem`.
Its `run(UUID)` call completes as an inactive topology placeholder without inventing a fluid state,
and the DEXPI writers and renderer ignore unsupported empty geometry while preserving the connected
process. Equipment that consumes the placeholder still requires a real thermodynamic inlet before it
can run.

### Layout and visualization features

The export path applies NeqSim's built-in auto-layout and visualization defaults: equipment tag
labels, nozzles, routed connection lines, service labels, flow arrows, stream tables, battery-limit
connectors, symbol legends, and equipment bar labels when simulation data is available. `DexpiLayoutConfig`
collects the tunable layout fields used by the DEXPI layout workstream; check the writer API before
using custom layout configuration from application code, because the stable public export calls are the
`write(...)`, `writeForPyDexpi(...)`, and `writeSheets(...)` overloads shown below.

### Exporting a multi-area ProcessModel

A whole `ProcessModel` (several process areas) can be exported in a single call. All areas are
flattened into one drawing; equipment is added by object identity so a stream shared between two
areas is registered once, and genuine name collisions are skipped with a logged warning.

```java
ProcessModel plant = new ProcessModel();
plant.add("Inlet", inletArea);
plant.add("Compression", compressionArea);

// One call writes a single combined P&ID for the whole plant
DexpiXmlWriter.write(plant, new File("plant.xml"));

// Or split into one DEXPI sheet per area
List<File> sheets = DexpiXmlWriter.writeSheets(plant, new File("sheets"));
```

### pyDEXPI-friendly export (namespace omitted)

[pyDEXPI](https://github.com/process-intelligence-research/pyDEXPI) and other Proteus readers that
perform *unqualified* tag look-ups cannot resolve elements when the default
`xmlns="http://sandbox.dexpi.org/xml"` namespace is present. Use `writeForPyDexpi` (or the
`DexpiDiagramBridge.exportForPyDexpi` convenience method) to write content-identical XML with the
default namespace omitted while preserving the `OriginatingSystem*` `PlantInformation` metadata that
Proteus loaders expect, so the file loads directly without a namespace-stripping pre-pass.

```java
// Java — writer
DexpiXmlWriter.writeForPyDexpi(process, new File("plant.pydexpi.xml"));

// Java — diagram bridge convenience
DexpiDiagramBridge.exportForPyDexpi(process, Paths.get("plant.pydexpi.xml"));
```

The `examples/notebooks/professional_process_flow_diagrams.ipynb` notebook wraps this in a reusable
`render_dexpi_pid(process, name)` helper that exports the DEXPI file and renders genuine ISA-5.1
symbols via `pydexpi.loaders.svg_loader.DrawDiagram`, degrading gracefully when pyDEXPI is absent.

The same notebook also shows the **most compact recipe** — a P&ID figure in four working lines
(export → load → draw → display):

```python
from pydexpi.loaders.svg_loader import DrawDiagram
from IPython.display import SVG, display

DexpiDiagramBridge.exportForPyDexpi(process, JPaths.get("compact.dexpi.xml"))
model = ProteusSerializer().load(out_dir, "compact.dexpi.xml")
DrawDiagram(model.diagram, padding=5.0, pretty=True).save_svg("compact", str(out_dir))
display(SVG(filename=str(out_dir / "compact.svg")))
```

For a standalone, importable end-to-end pipeline (NeqSim build → DEXPI export → pyDEXPI render),
see [`examples/neqsim/render_neqsim_dexpi_with_pydexpi.py`](https://github.com/equinor/neqsim/blob/master/examples/neqsim/render_neqsim_dexpi_with_pydexpi.py).
Its `build_process`, `export_dexpi`, and `render` functions can be imported directly into a notebook
or run as a script via `python render_neqsim_dexpi_with_pydexpi.py`. The example uses
`writeForPyDexpi`, so it no longer needs a separate namespace-stripping compatibility pass.

### Round-trip (import, simulate, re-export)

```java
Stream templateStream = new Stream("feed", fluid);
templateStream.setFlowRate(1.0, "MSm3/day");
templateStream.setPressure(50.0, "bara");
templateStream.setTemperature(30.0, "C");

DexpiXmlWriter.roundTrip(
    new File("input_pid.xml"),
    new File("output_with_results.xml"),
    templateStream);
```

### XML namespace compliance

The writer produces namespace-aware DEXPI XML:

- Root `PlantModel` element includes `xmlns` (DEXPI namespace), `xmlns:xsi`, and `xsi:schemaLocation`
- Document factory is configured with `setNamespaceAware(true)`
- Equipment elements carry `ComponentClassURI` attributes mapped to RDL (Reference Data Library) URIs

For consumers that require *unqualified* tag look-ups (such as pyDEXPI), use
`writeForPyDexpi` / `DexpiDiagramBridge.exportForPyDexpi`, which emit the same content with the
default `xmlns` omitted (see *pyDEXPI-friendly export* above).

### Proteus compatibility and native DEXPI 2.0

`DexpiXmlWriter.write(...)`, `writeForPyDexpi(...)`, and `writeSheets(...)` retain the established
Proteus 4.1.1 path for compatible tools. Do not obtain native DEXPI 2.0 by changing only a Proteus
header: DEXPI 2.0 uses a different object/property/reference serialization with a `Model` root.

Use the native writers for the official DEXPI 2.0 information models:

```java
// Plant model: P&ID equipment, piping, instrumentation, safeguards, and diagrams
Dexpi20ConformanceAssessment.Report plantReport =
    Dexpi20XmlWriter.writeAndAssess(process, new File("plant.dexpi.xml"));

// Process model: PFD/BFD process steps, material ports, streams, and state quantities
Dexpi20ConformanceAssessment.Report processReport =
    Dexpi20ProcessModelWriter.writeAndAssess(process, new File("process.dexpi.xml"));
```

Both writers emit the official DEXPI XML structure, import the versioned `Core`, `Plant`, or
`Process` model at `https://data.dexpi.org/models/2.0.0/`, validate against the bundled official
V2.0.0 XSD, and run reference and supported-profile semantic checks. See
[DEXPI 2.0 native exchange and conformance](dexpi-20-conformance.md) for exact scope, mappings, and
qualification requirements.

### SIS and HIPPS semantics in Proteus-compatible exports

The `DexpiXmlWriter` path preserves configured safeguarding semantics in its Proteus-compatible plant-model body:

| Source model or tag | Exported semantics |
|---|---|
| `PSHH`, `PAHH`, `LSHH`, `TSHH`, or `FSL` instrument tag | `GenericAttributes Set="SystemAssignment"` containing `ControlSystem=SIS`; an ordinary process transmitter remains DCS |
| Existing shutdown identifiers such as `XV`, `SD`, `ZS`, `SV`, `ESD`, or `HIPPS` | SIS assignment according to the writer's tag classifier |
| `HIPPSValve` | `GateValve` final element with closed safe state |
| Pressure transmitter registered with `HIPPSValve.addPressureTransmitter(...)` | HIPPS sensor role and membership in the same safety function |

For a HIPPS final element and its registered sensors, the writer emits a `SafetyInstrumentedFunction` generic-attribute
set containing `SafetyFunctionType`, `SafetyFunctionTag`, `FunctionalRole`, `SensorTag`, `SensorTags`,
`FinalElementTag`, `SafetyIntegrityLevel`, `VotingArchitecture`, `SafeState`, `ProofTestInterval` in hours,
`ClosureTime` in seconds, and `ControlSystem=SIS`. Diagram SIL markers use the configured HIPPS SIL when the
instrument has a layout position.

Association is object-based: add the same transmitter objects both to the `HIPPSValve` and the `ProcessSystem`.
A similar tag that is not registered with the valve may still be classified as SIS by its tag, but it does not receive
HIPPS membership metadata.

These mappings are implemented by `DexpiXmlWriter.write(...)`, `writeForPyDexpi(...)`, and the related
Proteus-compatible export variants. They are not currently implemented by `Dexpi20XmlWriter`. Native DEXPI 2.0
exports therefore require a separate semantic coverage review; do not infer HIPPS preservation from a successful
schema/profile report. Exported SIL and voting data record configured model values only. They do not verify PFDavg,
hardware-fault tolerance, independence, proof-test effectiveness, an IEC 61511 safety-requirements specification, or
project approval.

See [HIPPS implementation](../safety/hipps_implementation.md) and
[SIS logic implementation](../safety/sis_logic_implementation.md) for the simulation-side models.

### Line data and NORSOK line numbers

Each piping connection carries operating line data and a NORSOK Z-003 line-identification label:

- A `FluidCode` generic attribute (service code: `PG` process gas, `PL` process liquid, `FL` flare,
  `DR` drain, `FG` fuel gas, `UT` utility) derived by `DexpiServiceClassifier`
- Operating pressure, temperature and flow generic attributes when available on the stream
- A line designation composed by `NorsokLineNumber`, with nominal size, fluid code, sequence,
  piping class, and insulation code when those values are available
- A visible `SIZE?` field and `LineSizeStatus=MISSING_SOURCE_DATA` when neither source nominal size
  nor a model inside diameter is available

Source-backed line data can be supplied by a `DexpiStream`:

```java
DexpiStream line = new DexpiStream("process-line", fluid, "PipingNetworkSegment", "1001", "PG");
line.setNominalDiameterRepresentation("DN 150");
line.setPipingClassCode("A1B");
line.setInsulationType("H25");

// Optional endpoint values make a real transition explicit.
line.setFlowInNominalDiameterRepresentation("DN 150");
line.setFlowOutNominalDiameterRepresentation("DN 100");
line.setFlowInPipingClassCode("A1B");
line.setFlowOutPipingClassCode("B2C");
```

If a NeqSim pipe model supplies only hydraulic diameter, the drawing identifies it explicitly as
`ID ... mm`; it is never relabelled as DN or NPS. A source-backed endpoint-size change produces a
`PipeReducer` with distinct flow-in and flow-out sizes and connection points. Explicit piping-class
or insulation changes produce a `PropertyBreak` marker. The exporter does not guess nominal size,
schedule, piping class, or insulation.

The writer serializes numeric generic-attribute values with eight significant digits. This
scale-aware canonical precision suppresses insignificant solver noise so repeated exports remain
stable without rounding small, non-zero engineering values to zero.

Battery-limit feeds and products that are not wired to another unit on the sheet are marked with
off-page connector symbols carrying `FEED` / `PRODUCT` cross references, and instrument tags are
checked against ISA-5.1 with a `TagConformanceWarning` attribute added for non-conforming tags.


### Equipment mapping (native NeqSim to DEXPI)

The writer reverse-maps Java classes to DEXPI `ComponentClass` strings:

| NeqSim class | DEXPI ComponentClass | RDL URI suffix |
|---|---|---|
| `ThreePhaseSeparator` | `ThreePhaseSeparator` | `RDS327962` |
| `Separator` | `Separator` | `RDS327962` |
| `Compressor` | `CentrifugalCompressor` | `RDS414622` |
| `Pump` | `CentrifugalPump` | `RDS415550` |
| `Cooler` | `AirCoolingSystem` | `RDS327938` |
| `HeatExchanger` | `ShellAndTubeHeatExchanger` | `RDS327918` |
| `Heater` | `FiredHeater` | `RDS327914` |
| `HIPPSValve` | `GateValve` | `RDS415208` |
| `ThrottlingValve` | `GlobeValve` (tag prefix may map to gate/ball/check/butterfly) | `RDS415212` |
| `Expander` | `Expander` | `RDS414776` |
| `Mixer` | `Mixer` | `RDS4149564` |
| `Splitter` | `Splitter` | `RDS4112354` |
| `DistillationColumn` | `DistillationColumn` | `RDS327902` |

Valve types are further distinguished by tag name prefix: `XV-` (gate valve), `BV-` (ball valve),
`NRV-` / `CV-` (check valve), `BFV-` (butterfly valve), or globe valve (default).

### Nozzles and connections

Every exported equipment element receives `<Nozzle>` children with phase-aware positioning:

| Equipment type | Outlet nozzles |
|---|---|
| `Separator` | 2 (gas top, liquid bottom) |
| `ThreePhaseSeparator` | 3 (gas top, oil middle, water bottom) |
| All other equipment | 1 |

`<Connection>` elements link outlet nozzles to inlet nozzles using **stream identity matching**.
Pass-through `Stream` wrappers are recognised by tracing the delegated fluid identity.

### Simulation results export

After `process.run()`, the writer exports converged simulation results (operating pressure,
temperature, and flow rate) as `GenericAttribute` entries on each equipment element.

### Mechanical design data export

When equipment has a `MechanicalDesign` associated, the writer exports:

- **Design pressure** (bara)
- **Design temperature** (°C, converted from Kelvin)
- **Wall thickness** (mm)
- **Material grade**
- **Outer diameter** (mm)
- **Weight** (kg)

These appear as rows in the equipment data bar and as `GenericAttribute` entries in the XML.

### Valve-specific data

For `ThrottlingValve` equipment, the writer exports the valve flow coefficient (Cv) as a
`GenericAttribute` when a non-zero value is available.

### Piping network export

`DexpiStream` segments are grouped by line number (or fluid code) into
`<PipingNetworkSystem>` / `<PipingNetworkSegment>` elements. Each segment carries piping class
and line size metadata when available. Networks are labelled with a `NeqSimGroupingKey` generic
attribute for downstream visualization tools.

---

## P&ID Layout and Visualization

The `DexpiLayoutEngine` computes auto-layout positions and renders a full set of graphical
elements that produce professional-quality P&ID drawings.

### ISO 10628:2012 shape catalogue

The `DexpiShapeCatalog` generates a `<ShapeCatalogue>` with 22 standardized shapes drawn using
DEXPI graphical primitives (Circle, PolyLine, TrimmedCurve). Each shape records its ISO 10628
registration number via a `ComponentName` attribute.

| Shape | ISO Reference | Description |
|-------|--------------|-------------|
| Vertical separator | ISO 10628:2012-2091-A | Vessel body with dished heads |
| Three-phase separator | ISO 10628:2012-2091-A | Vessel with weir partition |
| Centrifugal compressor | ISO 10628:2012-2331-A | Trapezoid with circle driver |
| Centrifugal pump | ISO 10628:2012-2301-A | Circle with discharge triangle |
| Air-cooled heat exchanger | ISO 10628:2012-2141-A | Rectangular with fan symbol |
| Fired heater | ISO 10628:2012-2191-A | Rectangular enclosure with flame |
| Shell-and-tube heat exchanger | ISO 10628:2012-2131-A | Rectangular with tube passes |
| Globe valve | ISO 10628:2012-X8058-A | Double-triangle (bowtie) |
| Gate valve | ISO 10628:2012-X8062-A | Bowtie with vertical bar |
| Ball valve | ISO 10628:2012-X8038-A | Bowtie with filled circle |
| Check valve | ISO 10628:2012-X8072-A | Triangle with vertical stop |
| Butterfly valve | ISO 10628:2012-X8042-A | Bowtie with vertical line |
| Expander/turbine | ISO 10628:2012-2331-A | Reversed trapezoid |
| Mixer | — | Converging tee |
| Splitter | — | Diverging tee |
| Nozzle | — | Small stub |
| Generic equipment | — | Dashed rectangle (fallback) |
| Distillation column | ISO 10628:2012-2092-A | Tall vessel with 5 internal tray lines |
| Relief valve | ISO 10628:2012-X8088-A | Triangle with spring/bonnet line and arrowhead |
| Solenoid valve | — | Diamond outline with coil symbol |
| Utility supply | — | Circle with incoming arrow |

### Auto-layout features

The layout engine automatically arranges equipment left-to-right following process flow topology.
Per ISO 10628 drafting practice, a separator's **gas/overhead branch routes to the upper part** of
the sheet and the **liquid/bottoms branch to the lower part**, with the inlet train and any
recombined streams kept on the centre line. This keeps the gas train (compressors, coolers,
scrubbers) visually above the liquid train (pumps, oil/water treatment) and stops bottoms lines
from crossing gas equipment. The engine produces the following visual elements:

**Drawing frame and border:**
- Auto-fit sheet size (A4 to A0) based on process extent
- ISO 5457 drawing border with margin lines
- Column/row grid markers (A-H columns, 1-6 rows) for location referencing
- Title block with drawing name, scale bar, and revision areas

**Equipment visualization:**
- Equipment data bars below each unit showing tag name, operating conditions (T, P, flow), and mechanical design data
- Automatic shape selection based on equipment type
- Equipment rotation support (rotation-aware position reference vectors)

**Process lines:**
- Process flow lines connecting equipment nozzles
- Flow direction arrows on each line segment
- Stream labels at midpoints
- Phase-aware nozzle positioning (gas exits top, liquid exits bottom, water exits lowest)

**Instrumentation (per ISA 5.1):**
- Instrument circles with function letter labels (PT, TT, FT, LT, AT)
- Proper ISA 5.1 function letter decomposition (first letter = measured variable, subsequent = function)
- Field transmitter bubbles connected by measuring lines to an explicit process-segment or nozzle sensing location
- Level-transmitter measuring lines terminate at a dedicated tank or separator sensing tap; oil
  level and water-interface functions use distinct taps and never reuse a process inlet or liquid outlet
- Central/control-room controller bubbles distinguished from field instruments by their symbol and DEXPI location metadata
- PID controller parameters displayed (Kp, Ti, Td) when controllers are present
- Typed signal lines from transmitters to controllers and from controllers to actual final control elements
- SIL-rated instrument visualization with concentric double-border circles for SIL 2 and above
- Fail-position markers on control valves: **FC** (red), **FO** (green), **FL** (amber)
- Solenoid valve symbols with diamond shape and wiring to controllers

When a process contains no explicit measurement devices, the writer may add measurement-only
engineering proposals. These are marked `[PROP]` on the sheet and carry
`Origin=SYNTHESIZED_PROPOSAL`, `ApprovalStatus=UNREVIEWED`, and `Scope=MEASUREMENT_ONLY` metadata.
Compatibility metadata also records `InstrumentationSource=SYNTHESIZED_PROPOSAL` and
`EngineeringStatus=PROPOSED`. Explicit transmitters expose `MeasurementAttachmentTargetID`; closed
loops expose both `FinalControlElementID` and `FinalControlElementTag`.
The writer does not synthesize controllers, manipulated variables, or final control elements. A
closed loop is drawn only when the model contains an explicit controller attached to a connected
valve or manipulated equipment item; otherwise the controller is retained as an incomplete-loop
warning without a command line to empty drawing space.

This representation follows the separation of sensing, signal-conveying, control, and actuation
functions used by DEXPI and the identification/location conventions used by ISA-5.1. It does not
imply that the transmitter circle must geometrically overlap the process line: the measuring line
and its `is located in` association identify the physical sensing point.

The implementation is informed by [ISO 10628-1 diagram rules](https://www.iso.org/standard/51840.html),
[ISO 10628-2 graphical symbols](https://www.iso.org/standard/51841.html),
[IEC 62424 PCE representation](https://webstore.iec.ch/en/publication/25442), the
[ISA-5 series](https://www.isa.org/standards-and-publications/isa-standards/isa-5-standard), and the
[DEXPI 1.4 model](https://dexpi.org/static/pid_specification_1.4/). These references guide the
projection; generated sheets still require project-specific engineering and drafting review.

**Safety elements:**
- PST (Partial Stroke Test) annotation boxes near safety valves
- Relief valve shapes with ISO 10628 spring/bonnet symbol

**Piping detail:**
- Heat trace indication marks (zigzag pattern with ET/ST type labels)
- Insulation marks on process lines
- Piping class and line size attribute export
- Source-backed reducer symbols for line-size transitions
- Property-break symbols for explicit piping-class or insulation changes

**Annotations:**
- Equipment weight annotations (dry and operating weight)
- Sample point markers (filled dot with stem and tag label)
- Gauge glass symbols (narrow rectangle with connection stubs and "LG" label)
- Utility connection point markers with utility code labels

**Additional drawing elements:**
- Stream table with equipment conditions (T, P, flow, phase, density, MW, Cp)
- Symbol legend identifying all shapes used in the drawing
- Revision history table
- Battery limit boundary (dashed rectangle)
- Multi-page drawing support with page grid computation and continuation arrows

### Layout configuration

`DexpiLayoutConfig` provides a builder-pattern API for customizing every aspect of the layout:

**Spacing and positioning:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `xSpacing` | 100.0 | Horizontal spacing between equipment (mm) |
| `yBranchOffset` | 60.0 | Vertical offset for liquid branches (mm) |
| `xStart` | 80.0 | X-coordinate of first equipment (mm) |
| `yBase` | 150.0 | Y-coordinate of main process line (mm) |
| `defaultScale` | 1.0 | Overall drawing scale factor |
| `instrumentOffsetY` | 45.0 | Vertical offset for instruments above process line (mm) |
| `instrumentXSpacing` | 15.0 | Horizontal spacing between instruments (mm) |
| `borderMargin` | 14.0 | Drawing border margin (mm) |
| `batteryLimitPadding` | 30.0 | Battery limit boundary padding (mm) |

**Typography and line styles:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `fontName` | `"Calibri"` | Font family for all text |
| `tagFontHeight` | 4.5 | Font height for tag labels (mm) |
| `processLineWeight` | 0.5 | Stroke weight for process lines |
| `signalLineWeight` | 0.2 | Stroke weight for signal/instrument lines |
| `lineColorR/G/B` | 0.5/0.5/0 | RGB color for process lines |

**Feature toggles:**

| Toggle | Default | Controls |
|--------|---------|----------|
| `showStreamTable` | `true` | Stream data table at bottom of drawing |
| `showSymbolLegend` | `true` | Symbol identification legend |
| `showRevisionHistory` | `true` | Revision history table |
| `showBatteryLimit` | `true` | Battery limit boundary |
| `showFlowArrows` | `true` | Flow direction arrows |
| `showStreamLabels` | `true` | Stream name labels on lines |
| `showEquipmentBars` | `true` | Equipment data bars |
| `showInsulationMarks` | `true` | Insulation tick marks on pipes |
| `showFailPositionMarkers` | `true` | FC/FO/FL markers on control valves |
| `showSilMarkers` | `true` | SIL-level double-border circles |
| `showOrientationMarkers` | `true` | Equipment orientation indicators |

---

## Building runnable simulations from DEXPI

The raw connection inventory returned by `DexpiXmlReader.readWithDiagnostics(...)` is loss
evidence. It deliberately preserves parallel edges and malformed references. The builder path below
has a different purpose: it resolves a supported subset into runnable equipment-level topology.

The `DexpiSimulationBuilder` provides a high-level API that goes beyond basic import: it resolves
the P&ID topology (nozzle/connection graph), instantiates real NeqSim equipment (separators,
compressors, valves, heat exchangers, etc.) with sizing attributes from DEXPI GenericAttributes,
and wires them into a runnable `ProcessSystem`.

```java
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

ProcessSystem process = new DexpiSimulationBuilder(new File("plant.xml"))
    .setFluidTemplate(fluid)
    .setFeedPressure(50.0, "bara")
    .setFeedTemperature(30.0, "C")
    .setFeedFlowRate(1.0, "MSm3/day")
    .setAutoInstrument(true)
    .build();

process.run();
```

The builder performs these steps internally:

1. **Topology resolution** — `DexpiTopologyResolver` parses `<Equipment>`, `<Nozzle>` and
   `<Connection>` elements into a directed graph, collapses inline piping components (valves,
   reducers) into equipment-level edges, and produces a topological ordering via Kahn's algorithm.
   The resolver also detects cycles and logs warnings when cyclic dependencies are found;
   `ResolvedTopology.hasCycle()` can be queried programmatically.
2. **Equipment mapping** — `DexpiMappingLoader` reads `dexpi_equipment_mapping.properties` and
   `dexpi_piping_component_mapping.properties` from the classpath to translate DEXPI ComponentClass
   strings (e.g. `CentrifugalCompressor`) into `EquipmentEnum` values.
3. **Equipment instantiation** — `DexpiEquipmentFactory` creates real NeqSim equipment from the
   mapped enum, applying sizing attributes such as `InsideDiameter`, `TangentToTangentLength`,
   `DesignPressure`, `ValveCv` and `Orientation`. Distillation columns are instantiated with
   `NumberOfTrays` and `FeedTray` attributes when present. Column subtypes are detected from the
   DEXPI class name: absorbers (class containing "absorb") are created without condenser or
   reboiler, and strippers (class containing "strip") without a condenser.
4. **Stream wiring** — The builder walks the topology in upstream-to-downstream order, connecting
   outlet streams of upstream equipment to inlets of downstream equipment.
5. **Auto-instrumentation** — When enabled, `DynamicProcessHelper.instrumentAndControl()` adds
   transmitters and PID controllers to separators, compressors and heat exchangers. DEXPI
   instrument tags are matched to auto-generated transmitters by category prefix (e.g. `PT-` for
   pressure transmitters) and the auto-generated names are **renamed** to the actual DEXPI tag
   names. Controller tags are derived by replacing the function letter (e.g. `PT-100` to `PC-100`).
6. **Namespace-aware parsing** — The builder supports an optional `setNamespaceAware(true)` flag
   for DEXPI documents that use XML namespaces. When enabled, the underlying DOM parser and
   equipment factory use namespace-aware element resolution.

### Sizing attributes

The following DEXPI GenericAttributes are automatically extracted and applied to equipment:

| Attribute | Applied to | Effect |
|-----------|-----------|--------|
| `InsideDiameter` | Separators | Sets `setInternalDiameter()` |
| `TangentToTangentLength` | Separators | Sets `setSeparatorLength()` |
| `Orientation` | Separators | Sets vertical orientation flag |
| `DesignPressure` | Compressors, Valves | Sets outlet pressure |
| `DesignTemperature` | Heat exchangers | Sets outlet temperature |
| `ValveCv` | Valves | Sets flow coefficient via `setCv()` |
| `NumberOfTrays` | Distillation columns | Sets number of trays |
| `FeedTray` | Distillation columns | Sets feed tray location |

---

## Round-trip profile and validation

The `DexpiRoundTripProfile` utility validates that a process contains the minimal metadata required
for reliable imports and exports: runnable `DexpiStream` segments (with line/fluid references and
operating conditions), tagged equipment, and at least one piece of equipment alongside the piping
network. Regression tests enforce this profile on the reference training case and the re-imported
export artefacts to guarantee round-trip fidelity.

---

## Security considerations

Both the reader and writer configure their XML factories with hardened defaults:

- Secure-processing is enabled
- External entity resolution is disabled
- `ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` properties are cleared

These guardrails prevent XXE injection attacks and should be preserved if the parsing/serialisation
logic is extended.

---

## Generating PFD diagrams from DEXPI

The `DexpiDiagramBridge` class provides seamless integration between DEXPI imports and NeqSim's
PFD diagram generation system:

```java
// One-step: import DEXPI and create diagram exporter
ProcessDiagramExporter exporter = DexpiDiagramBridge.importAndCreateExporter(
    Paths.get("plant.xml"));
exporter.exportDOT(Paths.get("diagram.dot"));
exporter.exportSVG(Paths.get("diagram.svg"));  // Requires Graphviz

// Full round-trip: import, simulate, diagram, export
ProcessSystem system = DexpiDiagramBridge.roundTrip(
    Paths.get("input.xml"),     // Input DEXPI
    Paths.get("diagram.dot"),   // Output DOT diagram
    Paths.get("output.xml"));   // Re-exported DEXPI with simulation results
```

The bridge automatically configures the diagram exporter to display DEXPI metadata (tag names, line
numbers, fluid codes) alongside equipment labels.

---

## Tested examples

### Import test

`DexpiXmlReaderTest` imports the official
[C01V04-VER.EX01.xml](https://gitlab.com/dexpi/TrainingTestCases/-/blob/master/dexpi%201.3/example%20pids/C01%20DEXPI%20Reference%20P&ID/C01V04-VER.EX01.xml)
training case from the DEXPI Training Test Cases repository. It verifies that expected equipment
(heat exchangers, pumps, tanks, valves, piping segments) are discovered, streams remain active after
`process.run()`, and exported metadata (pressure, temperature, flow, units) survives a round-trip
reload.

### Export / visualization tests

`DexpiExportForViewerTest` exercises five export scenarios:

1. **Gas processing** — separator, compressor, cooler, valve with flow lines and equipment bars
2. **Two-stage compression** — multi-stage compressor train with intercooling
3. **Official DEXPI example** — reproduces the official DEXPI reference P&ID structure
4. **Instruments test** — transmitters, PID controllers, signal lines, SIL markers, fail-position markers
5. **Professional P&ID** — full-featured drawing with border, title block, stream table, symbol legend, revision history, battery limit

All five tests validate that the exported XML is well-formed and contains the expected DEXPI
elements (Equipment, PipingComponent, PipingNetworkSystem, Drawing, ShapeCatalogue, etc.).
