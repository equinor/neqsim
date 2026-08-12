---
title: Process diagram exporter and API inventory
description: Current-master inventory and compatibility contract for NeqSim DOT, Graphviz, PFD, DEXPI 2.0 Process, and Proteus P&ID paths.
---

# Process diagram exporter and API inventory

This inventory records the process-diagram and engineering-exchange paths present on NeqSim
`master` at commit `1dee5b51` (11 August 2026). It is the Phase 0 baseline for issue #1332. It
does not change an exporter, qualify a drawing, or claim ISO 10628 conformance. The paired
[DEXPI and P&ID current-master audit](../../integration/dexpi-pid-current-master-audit.md)
extends this baseline across the detailed #2899 reader, writer, validator, engineering, P&ID,
test, fixture, example, and qualification surfaces.

The intended architecture is one canonical semantic plant model projected into complementary
outputs:

- compatibility DOT and Graphviz views for simulator topology;
- a future native, deterministic SVG/PDF PFD document set;
- DEXPI 2.0 Process exchange for BFD/PFD semantics; and
- the existing Proteus/DEXPI P&ID proposal and interchange workflow.

Large facilities remain one semantic `ProcessModel` containing multiple `ProcessSystem` areas.
Future sheets are controlled views of that plant, not independent graphs.

## Public entry-point inventory

The classes and methods below are public and are used by repository documentation, examples, or
tests. Treat them as compatibility-sensitive even where NeqSim has not published a separate formal
API-stability classification.

| Path | Primary entry points | Current topology source | Output and status | Compatibility requirement |
|---|---|---|---|---|
| `ProcessSystem` convenience PFD | `toDOT()`, `toDOT(DiagramDetailLevel)`, `createDiagramExporter()`, `toSVG()`, `exportDiagramSVG(Path)`, `exportDiagramPNG(Path)` | `ProcessGraphBuilder` through `ProcessDiagramExporter` | Deterministic simulator-style DOT; SVG/PNG use external Graphviz | Preserve signatures, defaults, DOT availability without Graphviz, and legacy serialization until equivalence evidence supports a reviewed migration |
| Configurable simulator PFD | `ProcessDiagramExporter(ProcessSystem)`, configuration setters, `toDOT()`, `exportDOT`, `exportSVG`, `exportPNG`, `exportPDF` | `ProcessGraphBuilder.buildGraph(ProcessSystem)` | DOT plus Graphviz-rendered SVG/PNG/PDF; not a native professional document renderer | Preserve existing visual options and output behavior; a native renderer must be opt-in until qualified |
| Legacy `ProcessSystem` Graphviz | `ProcessSystem.exportToGraphviz(...)`, `ProcessSystemGraphvizExporter.export(...)` | Equipment stream introspection and export options | Legacy Graphviz file with optional stream values and property table | Retain as a compatibility output; do not silently redirect it through a new model or renderer |
| Legacy minimal PFD DOT | `ProcessFlowDiagramExporter(ProcessSystem).toDot()` | Shared stream identity plus explicit `ProcessConnection` declarations | Minimal equipment-node/stream-edge DOT | Preserve the constructor and `toDot()` result contract while public callers remain supported |
| Multi-area Graphviz | `ProcessModel.toDOT()`, `createGraphvizExporter()`, `exportToGraphviz(...)`, `exportAreaDOT(...)`, and `ProcessModelGraphvizExporter` | Ordered areas and shared stream identity | One clustered plant DOT and optional per-area DOT files | Preserve current combined and per-area APIs. Per-area files are compatibility views, not the future controlled multi-sheet document model |
| Canonical topology adapter | `ProcessDiagramGraphAdapter.fromProcessSystem(...)`, `fromProcessModel(...)` | `ProcessSystem`, explicit connections, and ordered `ProcessModel` areas; opt-in successful-run operating case | Defensive deterministic `EngineeringGraph`, fingerprint, structured diagnostics, and optional unit-explicit stream operating values | Reuse as the shared semantic foundation; the assessed DEXPI Process path consumes one topology snapshot, while legacy renderers and compatibility writers retain their established paths |
| Native DEXPI 2.0 Process | `Dexpi20ProcessModelWriter.write(...)`, `writeAndAssess(...)`, `writeAndAssessTopology(...)` | Direct `ProcessSystem` traversal for compatibility APIs; canonical material projection for assessed overloads; opt-in named-case calculation nodes for operating values | Native DEXPI 2.0 Process XML with steps, material ports, streams, quantities, conformance report, and optional structured topology/value-source evidence | Preserve the native Process profile and existing sequential serialization; canonical values omit and diagnose gaps without stream fallback |
| Native DEXPI 2.0 Plant | `Dexpi20XmlWriter.write(...)`, `writeAndAssess(...)` | Direct `ProcessSystem` engineering/plant mapping | Native DEXPI 2.0 Plant XML and conformance report | Keep separate from Process/PFD exchange and from Proteus compatibility output |
| Proteus-compatible DEXPI | `DexpiXmlWriter.write(...)`, `writeForPyDexpi(...)`, `write(ProcessModel, ...)`, `writeSheets(...)` | Direct process/engineering mapping plus `DexpiLayoutEngine` | Proteus-compatible P&ID XML, pyDEXPI variant, layouts, and per-area sheets | Preserve import/export compatibility. Combined export flattens areas and logs/skips distinct equipment with duplicate names; per-area sheets expose boundary feeds but do not yet form a controlled, paired-reference document set |
| Proteus import and simulation reconstruction | `DexpiXmlReader`, `DexpiSimulationBuilder`, `DexpiTopologyResolver`, `DexpiEquipmentFactory` | Imported nozzles, piping segments, equipment, instruments, and mappings | DEXPI/Proteus XML to a runnable `ProcessSystem` with explicit loss and validation boundaries | Keep as the detailed P&ID workflow; do not infer that a simulation-only PFD contains complete piping, valve, nozzle, instrument, or safeguard design |
| DEXPI/diagram convenience bridge | `DexpiDiagramBridge` | Existing reader, writer, and `ProcessDiagramExporter` | Import, simulator DOT generation, and Proteus re-export convenience operations | Preserve as a compatibility facade; it is not the canonical shared exporter |
| Governed engineering P&ID proposal | `PidDesignSynthesizer`, `PidDexpiMaterializer`, `PidEngineeringPackageExporter`, `DexpiEngineeringExporter` | `EngineeringProject`, `PidDesignModel`, rule catalogs, governed registers, and engineering evidence | Proposed P&ID/DEXPI package, registers, validation, and provenance artifacts | Retain explicit proposal/review states. Do not promote inferred content to approved engineering data |

## Model inventory and ownership

| Model | Intended ownership | Present limitation relevant to #1332 |
|---|---|---|
| `ProcessGraph` | Execution topology and dependency scheduling | Execution-oriented; not a drawing/document model |
| `EngineeringGraph` | Exchange-neutral engineering objects and revision comparison | Mutable and broader than a qualified process-diagram document contract |
| `ProcessDiagramGraphAdapter.Result` | Deterministic projection of `ProcessSystem` or multi-area `ProcessModel` into `EngineeringGraph` | Stable topology and an opt-in current operating case with temperature, absolute pressure, mass flow, units, and provenance are present; tags/stream numbers, document/sheet IDs, controlled views, operating envelopes, and persistent layout remain absent |
| `ProcessDiagramExporter` configuration | Simulator-style content and Graphviz layout policy | Layout is renderer-specific and has no document-set, revision-block, approval, or controlled off-page-reference model |
| DEXPI 2.0 Process model | Tool-neutral BFD/PFD exchange | The opt-in assessed path consumes canonical `ProcessSystem` material topology and named-case operating values for registered stream subjects; values for implicit equipment outlet streams are explicitly omitted and diagnosed, compatibility APIs remain direct, and no multi-area `ProcessModel` overload exists |
| Proteus/DEXPI P&ID model | Detailed P&ID proposal, import/export, and graphical interchange | Separate profile with richer piping/instrument semantics; must remain an engineering proposal until accountable data are present |

`ProcessConnection` distinguishes material, energy, and signal connections and records explicit
ports. The canonical adapter preserves distinct declarations when their type or endpoints differ.
Repeated declarations with identical type and port endpoints cannot be proven distinct because the
source contract has no independent connection ID; the adapter reports
`DIAGRAM_TOPOLOGY_DUPLICATE_CONNECTION_COLLAPSED` instead of hiding the loss.

## Output capability matrix

| Capability | DOT/Graphviz | Future native SVG/PDF PFD | DEXPI 2.0 Process | Proteus/DEXPI P&ID |
|---|---:|---:|---:|---:|
| `ProcessSystem` | Present | Missing | Present | Present |
| Multi-area `ProcessModel` | Present | Missing | Missing | Present |
| Stable plant/area/equipment/port/connection IDs through canonical adapter | Present as a separate topology baseline | Foundation only | Assessed material projection consumes the canonical snapshot and records its stable IDs; writer IDs remain compatibility-sequential | Not yet consumed as the shared graph |
| Material, energy, and signal topology | Present in canonical baseline; renderer coverage varies | Missing | Material-focused | Present where supported by the detailed profile |
| Parallel connection preservation | Golden topology evidence for distinct material/energy/signal endpoints | Missing | Multiplicity-sensitive evidence for supported simple and parallel-branch material cases | Profile-specific tests only |
| Units and provenance | Optional stream labels/tables; canonical topology plus opt-in current-case stream values in K, bara absolute, and kg/s with review-required simulation provenance | Missing document model | Opt-in assessed export consumes named-case canonical values, converts to kg/h, bar absolute, and degree Celsius, and records value-source and omission diagnostics | Simulation/design metadata and governed package artifacts |
| Controlled multi-document/multi-sheet hierarchy | Per-area compatibility files only | Missing | Missing | Partial sheet/layout features, not the shared controlled document set |
| Paired off-page connectors with direction and sheet/grid references | Missing | Missing | Missing | Graphical off-page features exist, but shared connection/document completeness is not qualified |
| Title/revision blocks and drawing status | Missing controlled model | Missing | Missing | Some rendered metadata exists; full shared revision/status governance remains unqualified |
| Native rendering without Graphviz | No | Missing | XML only | XML plus external/tool-specific rendering paths |
| Structured loss diagnostics | Canonical adapter diagnostics | Missing | Canonical/exported topology comparison plus explicit unsupported energy, signal, multi-area, document, and graphics scopes | Reader/writer/engineering validation paths |

## Test and example inventory

The following tests are the executable evidence closest to the public paths:

| Evidence | Coverage |
|---|---|
| `ProcessDiagramTopologyEquivalenceTest`, `ProcessDiagramGoldenFixtures` | Reusable golden simple and parallel-branch manifests plus parallel/recycle and multi-area directed topology across `ProcessGraph`, canonical `EngineeringGraph`, PFD DOT, and legacy/multi-area Graphviz projections |
| `ProcessDiagramGraphAdapterTest` | Stable identities, explicit ports, material/energy/signal connections, parallel endpoints, multi-area hierarchy, deterministic snapshots, case-scoped unit-explicit operating values and provenance, stale-value prevention, defensive copies, and structured diagnostics |
| `ProcessDiagramExporterTest` | DOT structure, diagram styles/options, stream annotations/tables, and Graphviz-backed exports when Graphviz is available |
| `ProcessSystemGraphvizExportTest` | Legacy complex-oil, three-phase, anti-surge, and annotated stream/property-table exports |
| `HysysStyleDiagramTest`, `OilStabilizationDiagramTest`, `GasOilWaterProcessSvgExportTest` | Professional-looking simulator examples and representative process diagrams; visual examples, not standards qualification |
| `Dexpi20ProcessModelWriterTest`, `Dexpi20SemanticValidatorTest` | Native DEXPI 2.0 Process structure, direct and canonical operating quantities, deterministic conversion, unsuccessful-run suppression, semantic rules, simple/parallel material-topology equivalence, explicit ports, and scoped loss/conformance evidence |
| `DexpiXmlWriterTest`, `DexpiXmlReaderTest`, `DexpiTopologyResolverTest` | Proteus-compatible export/import, equipment/nozzle/piping topology, round trip, instrumentation, safety metadata, and schema variants |
| `DexpiRenderingImprovementsTest` | Proteus layout, routing, crossing hops, off-page symbols, line styles, title-block fields, multi-area export, and structural XML checks |
| P&ID package tests under `process/engineering/pid` | Proposal synthesis, controls/safeguards, completeness findings, materialization, and governed engineering-package behavior |

Current executable examples include `professional_process_flow_diagrams.ipynb`,
`dexpi_engineering_processmodel.ipynb`, `dexpi_engineering_full_processsystem.ipynb`,
`complete_pid_design_synthesis.ipynb`, `process_to_engineering_simulator.ipynb`, and the Python
pyDEXPI rendering helpers. The saved professional-process-flow-diagram notebook has 17 executed
code cells and retained outputs. It demonstrates Graphviz simulator views and Proteus/pyDEXPI P&ID
rendering; despite its historical title and captions, it is not a native PFD renderer or standards
qualification artifact. It does not exercise the canonical adapter, DEXPI 2.0 Process exchange,
multi-area controlled sheets, or document-set validation. An executed native professional PFD
acceptance notebook satisfying the campaign criteria does not yet exist.

## Compatibility and migration contract

Future increments shall follow these rules:

1. Preserve all compatibility-sensitive entry points listed above and preserve legacy DOT and
   Graphviz defaults unless a separately reviewed migration is justified.
2. Treat the canonical adapter as the topology baseline. Do not make an exporter consume it until
   the simple, parallel/recycle, and multi-area fixtures prove semantic equivalence, including
   direction, explicit ports, connection type, and multiplicity.
3. Record every unsupported object, ambiguous connection, inferred mapping, and deliberate loss in
   structured diagnostics. A syntactically valid output is not sufficient evidence.
4. Add the native professional renderer as a complementary output. Do not replace DOT, native
   DEXPI 2.0, or Proteus/P&ID paths.
5. Model large plants once. Automatic partitioning, manual sheet assignment, pinned positions,
   routing overrides, and off-page connectors are projections owned by a controlled document set.
6. Keep simulation values, design data, project-entered data, and accountable approvals distinct,
   with explicit units, provenance, revision, and verification state.
7. Treat PFD generation as simulation-driven calculated evidence. Treat P&ID generation as a
   proposal until piping, valves, nozzles, instruments, safeguards, and accountable design data
   are present and reviewed.
8. Do not claim ISO 10628 conformance from class names, symbol labels, public summaries, schema
   validation, or visual similarity. A claim requires licensed standards mapping, qualification
   evidence, and accountable engineering review.

## Dependency-ordered next work

The canonical topology foundation supports an opt-in, successful-run operating-case snapshot, and
the DEXPI Process comparison gate drives the assessed material projection. An additional opt-in
writer overload consumes named-case calculation nodes with deterministic unit conversion and
explicit missing-value diagnostics. Compatibility APIs still traverse their established source
model and retain their sequential XML identities. The next dependency-ready increment is to widen
canonical value ownership beyond registered stream elements or, after review, extend exchange to
multi-area `ProcessModel`; hierarchy and energy/signal mappings must not be silently flattened or
lost.

The native professional document model and renderer remain later work because controlled
document/sheet identity, layout ownership, revision semantics, and licensed symbol qualification
are not yet available.
