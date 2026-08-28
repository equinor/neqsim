---
title: DEXPI and P&ID current-master audit
description: Current-source inventory, superseded-work reconciliation, test baseline, and campaign traceability for NeqSim DEXPI and P&ID workflows.
---

# DEXPI and P&ID current-master audit

This audit freezes the Phase 0 baseline for the professional DEXPI/P&ID campaign at
NeqSim commit `38ff94fc` (12 August 2026). The paired acceptance notebook was inspected
on NeqSim-Colab commit `085a4120`. The audit records source that exists on those revisions;
it does not qualify a commercial CAE tool, approve a P&ID, or claim ISO 10628 conformance.

The implementation has four complementary output paths. They are not interchangeable:

1. legacy DOT/Graphviz simulator diagrams;
2. the future native professional SVG/PDF PFD document set;
3. native DEXPI 2.0 Process exchange for BFD/PFD semantics; and
4. Proteus/native DEXPI Plant and governed P&ID proposal workflows.

The [process-diagram exporter inventory](../process/processmodel/process_diagram_export_inventory.md)
defines the shared topology and compatibility boundary. This page extends that inventory across
the detailed DEXPI reader, writer, validator, renderer, engineering-package, P&ID synthesis,
test, fixture, example, and qualification surfaces required by issue #2899.

## Current implementation inventory

### Exchange models, readers, and writers

| Capability | Current source | Verified scope | Explicit boundary |
| --- | --- | --- | --- |
| Proteus-compatible export | `processmodel.dexpi.DexpiXmlWriter`, compatibility facade `processmodel.DexpiXmlWriter` | `ProcessSystem`, `ProcessModel`, pyDEXPI namespace-omitted output, layout and compatibility sheets | Not native DEXPI 2.0; sheets are not a controlled drawing set |
| Proteus-compatible import | `DexpiXmlReader`, `DexpiTopologyResolver`, `DexpiEquipmentFactory`, `DexpiSimulationBuilder` | Equipment, nozzles, piping segments, instruments, mappings, and a runnable process scaffold | Imported topology still requires fluid, cases, specifications, dynamics, and accountable review |
| Native DEXPI 2.0 Plant | `Dexpi20XmlWriter`, `Dexpi20EngineeringMaterializer` | Core/Plant items, piping, nozzles, instrumentation, safeguards, boundaries, and representations | Plant exchange is not Process/PFD exchange or a DEXPI EV certificate |
| Native DEXPI 2.0 Process | `Dexpi20ProcessModelWriter`, `Dexpi20ProcessTopologyAssessment`, `Dexpi20ProcessModelPackageWriter`, `Dexpi20ProcessModelPackageAssessment`, `Dexpi20ProcessModelPackageReader`, `Dexpi20ProcessModelPackageRevisionImpact`, `Dexpi20ProcessModelPackageDocumentImpact` | Process steps, explicit material ports, streams, selected physical quantities, canonical topology assessment, deterministic assessed per-area packages, immutable exact-content intake snapshots, package-to-package area/connection revision evidence, and fail-closed projection onto controlled drawing/sheet/register review scope | Intake and revision APIs expose defensive per-area XML plus assessed material/energy/information connection evidence; they do not reconstruct a `ProcessModel`, promote manifest-only relationships to native whole-plant DEXPI, decide MOC, prove study completion, or approve a drawing |
| Common model helpers | `DexpiMetadata`, `DexpiStream`, `DexpiProcessUnit`, `DexpiInstrumentInfo`, `DexpiStreamUtils`, `DexpiServiceClassifier`, `NorsokLineNumber` | Metadata, supported object classification, line/tag helpers, and compatibility DTOs | These helpers do not constitute a shared immutable drawing/document model |
| Round-trip profile | `DexpiRoundTripProfile`, `EngineeringDexpiRoundTripQualifier` | Internal structural identity/reference comparison and explicit qualification status | Internal round trip is weaker than named-product import/export evidence |

The duplicate classes directly under `neqsim.process.processmodel` are compatibility facades.
New implementation work belongs in the `processmodel.dexpi` or `engineering` packages unless a
reviewed public-API migration says otherwise.

### Validation, conformance, layout, and rendering

| Capability | Current source/evidence | Status |
| --- | --- | --- |
| Official native schema validation | `Dexpi20XmlValidator`, bundled `dexpi/2.0/DEXPI_XML_Schema.xsd` and resource README | Automated for the bundled DEXPI 2.0 schema snapshot |
| Supported semantic profile | `Dexpi20SemanticValidator`, `Dexpi20ModelInspector`, `Dexpi20ConformanceAssessment` | Automated for NeqSim's declared supported subset; not full DEXPI coverage |
| Proteus layout | `DexpiLayoutEngine`, `DexpiLayoutConfig`, `DexpiShapeCatalog` | Deterministic compatibility layout with routing, symbols, title fields, and off-page graphics |
| Simulator/P&ID bridge | `diagram.DexpiDiagramBridge` and Python rendering helpers | Convenience import, export, and external rendering path; not canonical ownership |
| Public-tool qualification helper | `devtools/validate_dexpi_interoperability.py`, pinned DEXPIViewer baseline, and `render_neqsim_dexpi_with_pydexpi.py` | Exact-checkout DEXPIViewer findings and pyDEXPI import are machine-readable and separate from clean/commercial qualification |
| Commercial CAE evidence | `dexpi-commercial-cae-evidence-template.json`, `DexpiToolQualificationRunner`, `DexpiToolQualificationEvidence` | Template and evidence contract only; named product/version observation and accountable difference review are required |
| Native professional SVG/PDF | No shared native renderer or controlled document-set model | Confirmed gap; Graphviz and external/tool-specific paths remain available |

### Governed engineering and P&ID proposal model

| Layer | Current source | Evidence/state carried |
| --- | --- | --- |
| Canonical engineering package | `EngineeringProject`, `EngineeringGraph`, `EngineeringDeliverableCompiler`, `EngineeringCoordinatedPackage` | Stable identities, calculation dependencies, cases, registers, manifests, validation findings, revisions, and approval boundaries |
| DEXPI engineering materialization | `DexpiEngineeringExporter`, `DexpiEngineeringMaterializer`, `Dexpi20EngineeringMaterializer`, `DexpiEngineeringValidator` | Plant/Process exchange projections, coordinated manifest, and structured validation |
| P&ID proposal model | `PidDesignModel`, `PidDesignBasis`, `PidElement`, `PidDesignContext`, `PidProposalStatus` | Proposed elements, basis, explicit status, and project context |
| Rule-based synthesis | `PidDesignSynthesizer`, `PidRuleCatalog`, `NorsokPidRuleCatalog`, rules under `engineering.pid.rules` | Process topology, separator/compressor/pump/thermal controls and safeguarding proposals |
| Completeness and study preparation | `PidCompletenessValidator`, `PidCompletenessReport`, `PidHazopStudyRunner` | Fix-oriented proposal findings and HAZOP preparation evidence, never a completed accountable study |
| Package handoff | `PidDexpiMaterializer`, `PidEngineeringPackageExporter`, `EngineeringRegisterExporter`, `EngineeringPackageManifest` | Coordinated XML, registers, evidence, and review-required package state |

Simulation values, design data, project-entered values, verification, review, and approval remain
different states. Generated HAZOP nodes, cause/effect rows, SIL checks, safeguards, and drawings are
proposals or software-qualified evidence until accountable engineering work supplies the missing
data and approvals.

## Executable evidence inventory

### Java regression groups

Run the current DEXPI/P&ID baseline with the repository Maven wrapper. The focused group is:

```text
Dexpi20ProcessModelWriterTest
Dexpi20ProcessModelPackageWriterTest
Dexpi20ProcessModelPackageAssessmentTest
Dexpi20ProcessModelPackageReaderTest
Dexpi20ProcessModelPackageRevisionImpactTest
Dexpi20SemanticValidatorTest
DexpiXmlWriterTest
DexpiXmlReaderTest
DexpiTopologyResolverTest
DexpiRenderingImprovementsTest
DexpiDesignConditionsExportTest
DexpiInstrumentTest
PidDesignModelTest
PidCompletenessAndDexpiTest
ControlInstrumentationPidSynthesisTest
SafeguardingPidSynthesisTest
PidHazopStudyRunnerTest
EngineeringCompilerFoundationTest
ProcessToEngineeringSimulatorTest
EngineeringProductionQualificationWorkflowTest
QualifiedEngineeringReferenceFacilityTest
```

The first group covers native Plant/Process schema and semantics, deterministic canonical
material/value export, Proteus import/export, mappings, instrumentation, layout, off-page symbols,
and compatibility. The second group covers proposal synthesis, controls/safeguards, completeness,
study preparation, coordinated packages, and qualification evidence.

The repository also contains deterministic native fixtures under
`src/test/resources/dexpi/2.0/golden`, including the branching Plant fixture, manifest, semantic
summary, schema provenance, and a reviewed DEXPIViewer baseline pinned to commit
`18a17b1e38ba15a1a6ba49dd8265ddcff7c766ad`. That external baseline has eight errors and three warnings. The native
Plant writer now has additive controlled metadata and export-options overloads. The options path can explicitly
connect detected feed and product boundaries through directional `FlowInPipeOffPageConnector` and
`FlowOutPipeOffPageConnector` objects with owned piping nodes and complete segment item/node references. The
compatibility and metadata-only overloads plus the committed golden fixture remain byte-unchanged. The reviewed
baseline therefore remains an 8-error/3-warning drift gate until a controlled fixture is regenerated and the pinned
verifier is rerun; no clean result is inferred. Genuine graphical representation groups remain separate open work,
and reciprocal controlled-sheet connector pairs remain owned by the document-set layer. The coordinated
[engineering-diagram reference cases](dexpi-reference-cases.md) add executable, synthetic public
simple, branched, and multi-area process fixtures. They check canonical topology, material balance,
native Process/Plant exchange where supported, Proteus compatibility, legacy DOT, and governed P&ID
proposal boundaries from fresh models.

### Examples and notebooks

| Artifact | Current purpose | Qualification gap |
| --- | --- | --- |
| `professional_process_flow_diagrams.ipynb` | Simulator Graphviz views and Proteus/pyDEXPI rendering | Not a native professional PFD or standards qualification |
| `dexpi_pid_visualization.ipynb` | DEXPI visualization and internal round-trip evidence | External renderer/tool qualification remains environment-specific |
| `dexpi_engineering_full_processsystem.ipynb` | Governed `ProcessSystem` engineering/DEXPI package | Does not qualify a multi-sheet accountable P&ID |
| `dexpi_engineering_processmodel.ipynb` | Multi-area engineering package | Deterministic per-area native Process packaging is available; notebook adoption and native whole-plant/document semantics remain gaps |
| `complete_pid_design_synthesis.ipynb` | Governed P&ID proposal synthesis and completeness | Proposal state; not discipline approval |
| `process_to_engineering_simulator.ipynb` | End-to-end calculated engineering package | Not the NeqSim-Colab acceptance notebook |
| NeqSim-Colab `dexpi_safety_study_workflow.ipynb` | User-facing Proteus/native profile selection, SIS/HIPPS evidence, HAZOP/cause-effect preparation, completeness and traceability | Current saved run uses released NeqSim 3.17.0; it does not yet exercise current-master canonical operating values, realistic multi-area/cross-sheet content, revision delta, or a native professional drawing set |

Merged NeqSim-Colab PR #119 clean-executed the acceptance notebook against NeqSim source
`12b0e2fa`: all 15 executable cells and 20 focused checks passed, no saved error output remained,
native Plant/Process XML and topology evidence were deterministic, and all three figures plus the
MathJax equation were visually inspected. The source-equivalent hosted Java 8/21 matrix explicitly
passed the complete DEXPI/P&amp;ID test baseline. Later unrelated changes do not replace that recorded
Phase 0 evidence; this PR's exact-head regression and full CI cover the new reference cases.

## Closed-unmerged #2443 reconciliation

PR #2443 closed without merge at head `562c3590`. Its ideas must not be resurrected as a branch.
Current source shows that its useful capabilities were subsequently implemented or retained through
other merged work:

| #2443 proposal | Current-master evidence | Reconciliation |
| --- | --- | --- |
| Native DEXPI 2.0 XML plus XSD/profile validation | `Dexpi20XmlWriter`, `Dexpi20XmlValidator`, `Dexpi20SemanticValidator`, bundled schema | Present and superseded by current native writer/conformance APIs |
| Preserve Proteus and add pyDEXPI-friendly output | `DexpiXmlWriter.writeForPyDexpi`, `DexpiDiagramBridge`, rendering helper | Present; remains a separate compatibility profile |
| Materialize instrumentation, safeguards, representations, and directional off-page connectors | Plant materializers, model inspector, semantic validator, Proteus writer/layout and regression tests | Present for declared subsets; controlled paired cross-sheet document semantics remain a gap |
| Controlled engineering boundaries | `EngineeringBoundary`, `EngineeringProject`, graph builder and package compiler | Present for process, utility, recycle, flare/vent/drain handoff scopes |
| Count relief/piping/blowdown readiness only from finite evidence | engineering calculation/validation and production-readiness paths | Present as fail-closed engineering evidence; not safeguard approval |
| Deterministic branching golden fixtures and negative semantic tests | native golden package and semantic-validator tests | Present; product-specific graphical round trip is not implied |
| Executable pyDEXPI and commercial-CAE evidence contracts | interoperability helper, named-tool evidence template and qualification runner | Present as tooling/contracts; no commercial product is qualified by repository evidence |
| Notebook, guide, agent, and skill guidance | current engineering notebooks, integration guides, agents, and P&ID skill | Present; qualification must continue when affected APIs or acceptance evidence change |

No current roadmap item requires reopening #2443. Remaining work is recorded as explicit gaps in
the current roadmaps and in the traceability matrix below.

## Campaign criterion traceability

Status meanings: **present** is current source with executable evidence; **partial** has a bounded
supported subset and explicit loss; **gap** has no qualifying implementation; **external** requires
licensed standards, a named external tool, or accountable engineering review.

| #2899 criterion group | Status | Current evidence or confirmed gap |
| --- | --- | --- |
| Phase 0 exhaustive source inventory | Present in this audit | All public DEXPI/P&ID writers, readers, validators, layout/rendering helpers, engineering/P&ID models, tests, fixtures, examples, and guides are classified above |
| Phase 0 #2443 reconciliation | Present in this audit | Closed-unmerged scope mapped to current source without reviving the branch |
| Phase 0 clean Java and acceptance-notebook baseline | Present | Merged NeqSim-Colab #119 passed all 15 executable cells and 20 focused checks against source-equivalent NeqSim Java, retained deterministic Plant/Process evidence, and completed visual inspection; the matching hosted Java 8/21 matrix passed the complete DEXPI/P&amp;ID baseline |
| Phase 0 golden simple, branched, and multi-area cases | Present | Synthetic public executable cases pin material conservation, canonical topology/identity, supported native Process/Plant exchange, Proteus compatibility, legacy DOT, and governed P&ID proposal boundaries; unsupported native multi-area/document/graphics and SVG/PDF scopes remain structured limitations |
| Stable identities and relationships | Partial | Canonical plant/area/equipment/port/connection IDs and governed engineering identities exist; detailed nozzles, piping, instruments, loops, safeguards, boundaries, and drawing objects do not yet share one qualified model |
| State, units, case, and provenance separation | Partial | Canonical calculated operating values and governed design/evidence states exist; the full P&ID/document profile is not uniformly projected |
| Deterministic ProcessSystem/ProcessModel export | Partial | Canonical topology and native Process exports are deterministic in tested subsets; `ProcessModel` packaging adds assessed area XML, hashes, a plant manifest, fail-closed offline reassessment, and a serializable exact-content intake snapshot, while cross-area/energy/information semantics remain manifest-only rather than native whole-plant DEXPI |
| Export-import-export semantic equivalence and loss | Partial | Assessed package intake now preserves exact per-area XML and independently validated connection/loss status without path extraction; no runnable `ProcessModel` reconstruction is inferred, and full Plant/Process/P&ID round trip remains incomplete |
| Schema/API compatibility and migration | Partial | Compatibility facades and byte-equivalent tested paths exist; no comprehensive versioned package migration suite covers every serialized artifact |
| Revision semantic diff/impact | Partial | Controlled document-set semantic-object impact, assessed multi-area package area/connection impact, and deterministic package-to-document projection now identify changed exchange identities, affected drawings/sheets, designation/layout evidence, table/register/study review scopes, and unmatched identities. The projection remains review-required; it does not prove study completion or decide MOC |
| Professional symbols and project conventions | Partial/external | Proteus shapes, ISA/NORSOK tag helpers and proposal rules exist; a licensed ISO 10628/14617 mapping and reviewed vector catalog remain external/gap |
| Native DEXPI Core graphical projection | Assessed supported subset | The opt-in exchange-neutral adapter emits non-empty Core Diagram/RepresentationGroup structures with generic Polygon, PolyLine, and Text primitives. `Dexpi20GraphicalProjectionAssessment` verifies stable represented identities, mapped geometry/style, diagram metadata/bounds, exact-file SHA-256 provenance, and explicit losses; no Profile/SymbolUsage, clean external-validator, standards, or approval claim is made |
| Multi-sheet documents and off-page references | Implemented foundation | `EngineeringDiagramDocumentSet` owns deterministic drawing/sheet IDs, controlled references, paired off-page connectors, zones, revision/status metadata, validation, and restartable layout overrides; accountable completeness and project-specific approval remain external |
| Professional native SVG/PDF | Implemented foundation | `NativeEngineeringDiagramRenderer` produces deterministic opt-in SVG/PDF from the shared document model with fixed-port orthogonal routing, parallel lanes, title/revision fields, off-page connectors, and diagnostics while legacy Graphviz remains unchanged |
| Drawing-quality metrics and visual regression | Partial | Native rendering reports collision, clearance, bounds, routing, label, and off-page findings with deterministic regressions; a broader reviewed symbol/readability reference corpus remains incomplete |
| Detailed P&ID object coverage | Partial | Governed proposal rules cover important controls and safeguards; complete nozzle, fitting, reducer, drain/vent, bypass, blind, valve, analyzer, package, and line-design evidence is not uniform |
| Simulation-backed engineering enrichment | Partial | Coordinated cases, registers, calculations, DEXPI packages and selected canonical stream values exist; governing envelopes and all equipment/design selections are not uniformly linked through the exchange |
| DEXPI-centred study audits | Partial | Completeness, HAZOP preparation, SIS/HIPPS and qualification evidence exist; revision/MOC deltas and reusable selected-object study hooks are incomplete |
| Java/Python engineer usability | Partial | `EngineeringDiagramDelivery` provides a concise Java model-to-assessed-DEXPI/document/native-drawing API and existing package snapshots remain Java/Python-composable; Python facade adoption and a model-to-study qualification API remain gaps |
| Public DEXPI/pyDEXPI validation | Partial | Bundled schema, a pinned DEXPIViewer runner/baseline, pyDEXPI import helper, and internal fixtures exist; the reviewed external baseline is not clean and exact-version execution must be rerun per release/reference case |
| Commercial CAE qualification | External | Evidence template exists; observed named-product/version import, export, semantic diff and accountable review are mandatory |
| Executed reference workflows | Partial | `EngineeringDiagramDelivery` composes assessed DEXPI, controlled documents, native SVG/PDF, artifact hashes, and review-required diagnostics for synthetic `ProcessSystem` and multi-area `ProcessModel` references; a full realistic recycle, isolation, relief, flare, cross-sheet, revision-delta acceptance workflow remains a gap |
| Performance and completion release gate | Partial | A synthetic multi-area harness records deterministic DEXPI package export/intake, native SVG/PDF rendering, package revision, and controlled-document impact timings against conservative CI median budgets. Comparable-runner baseline history and the final independent current-master audit remain incomplete |

## Dependency-ordered next work

The immutable diagram/document model, deterministic native SVG/PDF renderer, fixed-port routing,
exchange-neutral graphical projection, generic DEXPI Core adapter, and supported-content
export-inspection assessment now form the merged dependency chain. The next graphical tranche is
blocked on a controlled choice between an exchange-neutral reviewed symbol projection and a pinned,
legally distributable DEXPI profile-symbol catalogue. Empty placeholder representation groups and
invented standards mappings are not acceptable substitutes.

Independent remaining work includes reviewed reconstruction of assessed area exchanges into a runnable model, native
whole-plant Process hierarchy and native energy/information mappings beyond manifest-only package evidence, accountable completion evidence for every flagged table/register/study and any revision-MOC decision, broader P&ID object coverage, a reviewed visual-reference corpus,
fresh exact-version external DEXPIViewer execution, named commercial-CAE qualification, and
comparable-runner performance history beyond the synthetic regression gate. These
items must retain legacy DOT/Graphviz, native DEXPI 2.0, and Proteus/P&ID compatibility and must not
be reported as standards conformance or drawing approval without accountable evidence.
