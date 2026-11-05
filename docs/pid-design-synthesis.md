---
title: "Governed P&ID Design Synthesis"
description: "Prepare a professional P&ID review package with DEXPI 2.0.1, traceable instrumentation and safeguards, and explicit bindings to steady-state and dynamic NeqSim tests."
keywords: "professional P&ID, P&ID synthesis, DEXPI 2.0.1, simulation bindings, dynamic tests, HAZOP preparation, safeguarding, control instrumentation, NORSOK"
---


For the relationship between P&ID proposal synthesis, native DEXPI 2.0 Plant exchange, Proteus/pyDEXPI compatibility,
validation, and named-tool qualification, see the [DEXPI Engineering Guide](engineering/dexpi-guide).

NeqSim can generate a reviewable P&ID proposal from the same engineering project that owns the
process simulation, requirements, relief studies and DEXPI export. The generator deliberately does
not claim that an automatically produced drawing is approved or fit for construction.

## Professional P&ID and simulation handoff

Start with the runnable `ProcessSystem` and its controlled `EngineeringProject`.
Prepare the P&ID through `PidDesignSynthesizer` and the applicable rule catalog.
Create `PidSimulationBindings.from(project, pid)`, bind the actual equipment,
measurement and controller objects, and check the current project/P&ID
identities before passing the model and bindings to
`ProfessionalPidPackageExporter`. The
professional path selects native DEXPI 2.0.1 explicitly and retains the design
model, engineering registers, drawing and projection findings, and simulation
binding evidence. Existing `PidEngineeringPackageExporter.export(...)` remains
the compatibility package path.

The five-argument professional export accepts the project, P&ID model,
`PidSimulationBindings`, controlled `Dexpi20PlantExportMetadata`, and a new
output directory. Its longer overload also accepts the drawing number,
evidence-bearing symbol conventions and persistent layout register. Converge
the source process before export and inspect these outputs:

| Output | Purpose |
| --- | --- |
| `plant.dexpi.xml`, `dexpi-2.0.1-conformance.json` | Exact native 2.0.1 exchange and its assessment |
| `drawings/pid-drawing-set.pdf`, `drawings/pid-sheet-NNN.svg` | Native A1 review drawings and per-sheet vector views; explicit legacy layouts retain their selected representation |
| `drawings/pfd-overview.pdf`, `drawings/pfd-overview.svg` | Native A1 process overview for one to four primary units, with actual streams, valves and normal operating data |
| `engineering-graph.json`, `pid-document-set.json` | Controlled semantic and document identities |
| `pid-engineering-registers.json` | Detailed proposals, connections and missing engineering input |
| `simulation-model.neqsim`, `pid-simulation-bindings.json` | Saved executable process and explicit P&ID tag handoff |
| `dynamic-capability-report.json` | Base-model dynamic capability/activation audit |
| `professional-pid-readiness.json` | Drawing, exchange, projection and review findings |

The saved process can be loaded with `ProcessSystem.loadFromNeqsim(...)`;
check for a failed load and preserve the matching model, project and binding
definition when resuming studies. The saved process does not implement an
unbound proposed control or safeguard.

Select the exchange edition in the project basis using
`NorsokOffshoreEngineeringBuilder.from(name, process, "2.0.1")`.
`ProfessionalPidPackageExporter` rejects a conflicting or missing DEXPI edition
before writing files. Existing two-argument builder calls retain their legacy
2.0 basis and compatibility behavior. On reopening, recreate the same project ID,
revision and edition, restore `PidDesignModel.fromJson(...)`, and restore
`PidSimulationBindings.fromJson(project, pid, bindingJson)` before simulation.

`generationStatus=INCOMPLETE_NATIVE_PROPOSAL_PROJECTION` means proposal tags
are missing from the native exchange. Close those mappings before treating
the native file as a full P&ID handover. Tag presence alone does not establish
correct physical connections: `nativeProposalConnectivityQualification`
remains `REQUIRED` and `fullPidSemanticEquivalence` remains `NOT_QUALIFIED`.
Inspect those findings and each drawing rather than treating file generation
as completion.

Create `PidSimulationBindings` from the project and P&ID model. Explicitly bind
the P&ID valve, instrument and controller tags to the corresponding live NeqSim
objects. The bindings resolve the same identities on isolated process copies;
they do not replace those objects with separate drawing-only simulation models.
Use `PidSimulationSession` to initialize and run steady state and to execute
scoped `DynamicSafetyScenario` tests after strict configured-copy preflight.
Persist returned scoped session results as controlled evidence; the session
does not automatically record them on the project. Project-configured studies
can be executed through `EngineeringSimulationRunner`; the exporter includes
`engineering-simulation-results.json` when that project result exists.
Regenerate the package in a new directory with the linked evidence. Rebuild
stale bindings before export if the source topology, P&ID or revision changed.

Retain source revision, binding scope, missing runtime objects, scenario
criteria, capability findings and results. A change to the P&ID/model or its
controlled revision requires reassessing the bindings and affected tests.
Drawing topology alone does not specify fluid composition, thermodynamics,
inventories, geometry, actuator response, controller tuning or shutdown logic.
Unimplemented proposed objects remain visible gaps and cannot be credited as
executed safeguards.

For multi-area `ProcessModel` work, retain area identities and prepare governed
area projects through the existing multi-area builder. A single-area simulation
binding does not establish a globally coupled multi-area dynamic solver.

Use `@engineering-deliverables` and `neqsim-pid-process-operations` for requests
such as:

- "Prepare a professional P&ID with DEXPI 2.0.1 from this NeqSim model."
- "Add instrumentation and safeguarding proposals with their design assumptions."
- "Run the base steady-state case, then test the bound outlet valve and shutdown response."

Require an assessed native exchange, reviewable vector drawings, coherent
registers, explicit missing design inputs, and the scoped simulation results.
Keep visual acceptance, receiving-tool qualification, standards review and
accountable engineering approval as separate checks. Generated files alone do
not establish that every proposed component is physically connected, drawn,
simulated or approved.

## Actual native safety functions, automatic proposals and executed feedback

When an executable candidate already exists, `PidSafetyFunctionImporter.fromNativeFunction(...)`
proposes its actual measurement channels, voting function, final elements and
native settings in a copied `PidDesignModel`. It reuses exact existing bindings,
preserves canonical IDs and leaves the input model unchanged. Rebuild
`PidSimulationBindings` after each import or governed proposal change. The
importer does not derive a hazard assessment, choose a trip threshold or assign
SIL: those remain explicit engineering-policy inputs.

`SafetyFunctionChannel.highTripFromMeasurement(...)` and its low-trip counterpart
derive the channel tag from the actual measurement and read its enabled held
physical-step sample. Capturing a source from another process copy fails.
`manualTripFromPushButton(...)` reads the actual local binary button and rejects
hidden linked automatic actions. Legacy arbitrary extractors remain available
but do not establish verified measurement identity. The native channel latches
a qualified demand until reset; fixed configured voting is not a simultaneous
raw-signal coincidence test. Distinct simulated channel objects do not prove
independent installed taps, power, logic or common-cause resistance.

The bounded configured-copy workflow is:

1. Import the actual healthy native candidate, then bind its instruments,
   controllers and physical valves through their exact canonical identities.
2. Read governed proposal attributes into the scenario factory. Use
   `PidSafetyFunctionValidator.validateConfigured(...)` to compare actual source
   references, units, thresholds, qualification/logic delays, voting, actuator
   stroke/fail position and ordered command policy with the canonical graph.
   The supported isolation policy is native fail-closed ESD valves commanded by
   exact native trip actions, optionally grouped in parallel. Opening commands,
   opaque actions, missing targets and incompatible graph/settings fail closed.
3. Retain the returned `ConfiguredFunction` fingerprint and evidence reference
   in the scenario definition. Repeat validation on the actual configured copy
   inside its factory and use `assertSameConfiguration(...)` before execution.
   Enable event-exact timing for held-sample scenarios. Preserve the physical
   source fingerprint separately from the configured topology and numerical
   execution order; numerical pressure-coupling helpers are not drawn hardware.
4. Run scoped studies through `PidSimulationSession`, or declared project
   scenarios through `EngineeringSimulationRunner.runStrict(...)`. The latter
   executes strict configured-copy preflight and retains the actual native
   results on that project; it does not adopt a caller-supplied result list.
5. Attach results with `PidSafetyFunctionValidator.attachResult(...)`, declaring
   required full-window `MAINTAIN_BOUNDS` criteria. Preserve the resulting
   canonical IDs, configuration/scenario/result hashes and actual native
   readback beside the DEXPI package. Regenerate into a new directory and run
   the deterministic and independent drawing/standards reviews.

`MAINTAIN_BOUNDS` checks every sampled value over its declared post-event window
and requires both window endpoints. Default `REACH_BY_DEADLINE` instead measures
first attainment; an initially satisfied sample cannot establish an invariant.
Neither mode proves continuous-time behavior between samples. Feedback retains
`NOT_EXECUTED`, `INCOMPLETE`, `OBSERVED_FAIL`, `MONITORED_NO_DEMAND`,
`DEMAND_WITHOUT_ACTUATION`, `ACTUATED_RESPONSE_INCOMPLETE` and
`DEMAND_RESPONSE_OBSERVED` separately. Deadline credit covers only explicitly
declared criteria. Expected-fault assertions never relabel an observed process
FAIL as PASS. Keep component and cumulative-energy assessments separate; a
response PASS cannot clear a numerical energy HOLD/FAIL.

The public `AdvancedSafetyPidExample` and `AdvancedV101SafetyStudy` test fixtures
exercise this path on two actual separator inventories, an upstream ESD valve,
a separate interstage finite-stroke HIPPS candidate, three actual pressure
channels, three BPCS loops and a local manual trip. The native export includes
four P&ID sheets and a PFD for that example. Shared manual/automatic final-element
targets receive no inferred independent-barrier credit. Manual trip does not
imply a turbine or compressor ETS model.

The same function/instrument/actuator IDs map to native DEXPI 2.0.1 objects and
signal links. Governed executable settings, evidence and implementation remain a
coordinated package: XML alone does not configure a PLC, reconstruct scenario
code or qualify a named receiving-tool round trip. Controlled standards review,
relief/safety lifecycle evidence and accountable approval remain HOLD.

## Native ISO-basis drawing and connectivity checks

After generation, apply the separate
[P&ID quality and standards review](engineering/pid-quality-review.md).
`neqsim pid-review` rechecks actual artifacts and integrates content-bound visual
and controlled standards receipts. Native/geometry success alone cannot produce
a completed standards review. The generating owner fixes defects and regenerates;
the independent reviewer retains findings and engineering HOLDs.

For a professional package, the explicit canonical `PidDesignModel` is the sole
native instrumentation proposal authority. The exporter disables generic
requirement-to-PCE/safeguard/nozzle synthesis before applying that model; all
requirements remain in the engineering manifest and review registers. The
engineering-projection sidecar records the policy and retained requirement IDs.
Engineering-only native export keeps its default generic typed proposals, but
an `ENGINEERING_ASSESSMENT` never invents a process instrument or sensing nozzle.
Callers applying their own canonical overlay can select the explicit
`exportDexpi201(..., includeRequirementProposals)` overload accordingly.

Auxiliary material lines use the same line identity in the physical projection,
native XML, drawing routes and engineering registers. Project line-design inputs
retain their values/evidence as REVIEW_REQUIRED, including actual product
boundaries; series auxiliary devices share one line row. Absent auxiliary data
remain HOLD. Vessel sensing strokes meet the actual dished hull, rather than its
bounding rectangle. Equipment/stream-owned tee anchors are on the equipment side
of synthesized inline devices. Inline projection precedes auxiliary tees so
proposal iteration order cannot move a recycle across isolation or check valves.
Use an explicit supported source-port owner for other locations; do not infer an
arbitrary point along a stream that contains several devices.

The professional exporter now uses `ProfessionalPidDiagramRenderer` and
`ProfessionalPidSymbolLibrary` for default native A1 landscape scenes. It
preserves the actual separator orientation, uses distinct control, isolation,
check and relief-valve families, adds pneumatic actuator decoration only for
an explicitly supplied `actuatorType=PNEUMATIC_DIAPHRAGM_PROPOSAL`,
separates field measurement from shared control/safety functions, and retains
named signal continuations. Existing explicitly controlled manual layouts
retain their previous representation and are labelled separately in readiness;
they are never silently replaced by the automatic professional layout.

Locally represented two- or three-input functions have distinct signal anchors
and a single shared voting symbol. An actual bound manual push button is a
field function without a fabricated process sensing tap in either the drawing
or native DEXPI. Actual valve-port monitors and eligible unconnected overflow
monitors use reciprocal instrumentation-detail sheets with the existing stream,
port and primary-sheet identities. Detail views repeat a labelled existing
segment; they do not create additional valves or piping. A bound inter-unit
valve is shown on its declared receiving-equipment sheet rather than as two
physical valves on adjacent sheets. Unsupported locations still remain HOLD.

The drawing basis is ISO 10628-1:2014 for diagram content/drafting,
ISO 10628-2:2012 and ISO 14617 for process symbols, ISO 5457/7200 for
paper/document fields, and IEC 62424 with ANSI/ISA-5.1 for PCE notation. The
new authored vector catalogue is **not an independently qualified ISO symbol
catalogue**. The exporter records standards review, design-input and discipline
HOLDs instead of claiming certification or assigning an approver.

Inspect these additional artifacts:

| Artifact | What it proves |
| --- | --- |
| `pid-physical-projection.json` | Shared, source-backed equipment/line/nozzle attachments and directed functional links used by both drawing and native exchange |
| `pid-drawing-content-report.json` | Actual rendered sheet index, symbol/content coverage, line-design and valve-data gaps, controlled issue date and explicit ISO-review status |
| `drawings/pid-sheet-NNN.layout.json` | Source geometry for the mandatory deterministic layout check, separate from standards/content review |
| `plant.dexpi.xml.pid-projection.json` | Native IDs, connected proposal projection, real signal/actuator endpoints, unresolved elements and assessment of the final exact XML bytes |

A valid DEXPI file does not establish a complete P&ID. A geometrically valid
sheet does not establish ISO content/symbol conformance. Require all source
and proposal identities, physical/signal endpoints, sizing/class information
and controlled review evidence before claiming the relevant qualification.
Source-area document identities remain in `pid-document-set.json`; the derived
representation sheet index is in `pid-drawing-content-report.json` and shares
the same source-graph fingerprint. Neither sheet pagination nor an extra
representation changes the physical simulation model.

Supply controlled `LineDesignInput` rows keyed by the actual stream/line tag.
The drawings show their nominal size and piping class. Missing data remain
`DN:HOLD` and `CLASS:HOLD`; operating pressure is never substituted for design
pressure. A missing fail action remains `FA:HOLD` even if the proposal catalog
suggests a failure direction. Drive selection, valve sizing, proof testing,
relief sizing, trip values and safety approval remain engineering inputs.

Auxiliary routes require explicit source and destination identities. For an
existing process line, supply both `physicalSourceEquipmentTag` and
`physicalSourceStreamTag`, with corresponding target fields for a recycle.
For a new design nozzle, supply `physicalSourceEquipmentTag`,
`physicalSourceNozzleTag`, `physicalSourcePortRole` (`UTILITY` or `PROCESS`) and
`designEvidenceReference`; target-nozzle fields are symmetric. A new utility
nozzle is a distinct owned plant object and is never connected to the process
gas line by inference. `destinationTag` identifies a controlled external
interface. A relief destination can name its generated off-page proposal tag.

A series circuit such as a utility SDV and TCV requires one shared
`physicalRouteId`, identical endpoint/evidence inputs, and unique explicit
nonnegative `physicalRouteOrder` integers. It generates one ordered physical
path. Missing, duplicate or conflicting order remains HOLD; the exporter does
not create a parallel bypass or invent a series order. Cooler/exchanger
utility-loss flow measurement similarly requires its utility sensing point;
it does not measure the hot process inlet. A simple fired-heater process
low-flow proposal still uses its actual process inlet.

Process-nozzle synthesis excludes streams, valve piping nodes, tee helpers
and numerical helpers. Their existing physical source ports remain in the
engineering graph. Generic thermal duty models use the generic 2.0.1
HeatExchanger type; air-cooling or fired construction is selected only for an
explicit source equipment type. Compatibility 2.0.0 mappings remain unchanged.

Native Process/PFD 2.0.1 export also supplies required compression/heat-exchange
method values from the supported generic semantics. It no longer needs a
notebook-side XML repair. An unspecified compression method is not silently
labelled centrifugal from an isentropic-efficiency calculation.

The source-only native PFD overview uses the same authored equipment symbols,
shows actual valves and stream pressure, temperature and mass flow, and omits
proposed instrumentation. It currently supports small forward-flow trains with
one to four primary units. Inspect `pfdOverview` in the readiness report and
`drawings/pfd-overview.layout.json`; unsupported sizes are reported explicitly,
and geometry errors are retained as incomplete drawings. This bounded overview
does not establish arbitrary full-plant routing support.

Passive valves and splitters remain HAZOP nodes using their actual source
stream boundaries. They do not receive invented vessel-nozzle proposals or
automatically credited safeguards.

## External symbol libraries, including PCA

The professional renderer currently uses NeqSim-authored vector families. The
`EngineeringDiagramConventionRegister` records generic node-kind shapes and
provenance; its `sourceReference` does not import SVG geometry, connection points
or a PCA symbol. The professional scene calls `ProfessionalPidSymbolLibrary`
directly. Keep that authored-symbol provenance unless an actual external asset
has been acquired and its mapping verified.

The requested [PCA symbol-library Members view](https://posccaesar.org/ontology/digengsym/rdl/core?tab=Members)
identifies a library view, not a verified individual glyph. PCA documents
Reader/Creator retrieval through `GET /esl/engineeringSymbols` as JSON, JSON-LD or
Turtle. The API base depends on the deployment; use its returned access links and
deployed Swagger schema rather than guessing a host, per-symbol IRI, payload
fields or integer direction meanings. Preserve a resource's canonical IRI
separately from the environment-specific URL used to retrieve it. See the
[official API reference](https://docs.posccaesar.org/platform-and-services/pca-consume/api-reference/)
and [retrieval guidance](https://docs.posccaesar.org/platform-and-services/pca-consume/get-content-through-the-api/).

Before a future adapter is implemented, require this candidate acquisition
record. It is an application-side evidence contract, not a claimed PCA payload
schema:

| Evidence | Required content |
| --- | --- |
| Identity and status | Exact resource/library IRIs, version and content status, replacement/deprecation information, acquisition date and access environment |
| Reproducible source | Original representation, media type, SHA-256 and deployed schema version; server version/date metadata where supplied |
| Rights and provenance | Resource-specific rights and attribution, applicable permission/agreement and review reference; no rights inferred from public visibility |
| Geometry | Original shape/path data, dimensions/viewBox, coordinate units/axes, rotation centre and fill rule |
| Connections | Original point IDs, coordinates and raw direction values, verified direction schema, and explicit mappings to canonical material/signal ports |
| Project mapping | Source equipment class, symbol family, orientation/actuator/location semantics, supported transforms, mapping revision and review evidence |

Apply one documented coordinate transform to the geometry, rotation centre and
connection points, including the required unit and direction conversions. Keep
the physical process and P&ID as the authority for material/signal connections;
do not infer them from a picture. Validate actual hull/sensing attachments and
both SVG and PDF output, then perform the applicable project and controlled
symbol/standards review. Retain immutable source bytes for reproducible offline
generation. See PCA's [geometry guidance](https://docs.posccaesar.org/platform-and-services/pca-evolve/digital-symbol-editor-best-practices/).

No PCA glyph payload or resource-specific glyph licence has been verified here.
Check the [PCA terms](https://posccaesar.org/terms/) and applicable resource rights
before embedding or redistributing assets; this guide makes no licence decision.
Until those prerequisites are met, retain the supported authored families and
state that PCA glyphs have not been imported. Unknown families still fail closed.
A library reference, reviewed project convention, PCA content status and ISO
qualification are separate evidence; none establishes a safe control/safety
design or a qualified receiving-tool round trip.

## Compatibility proposal example

```java
PidDesignModel pid = PidDesignSynthesizer.synthesize(project,
    new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"),
    NorsokPidRuleCatalog.completeProposals());

PidCompletenessReport completeness = PidCompletenessValidator.validate(pid);
PidHazopStudyReport hazopPreparation = PidHazopStudyRunner.run(project, pid);
PidEngineeringPackageExporter.ExportResult exported =
    PidEngineeringPackageExporter.export(project, pid, outputDirectory);
```

## Implemented proposal scope

`NorsokPidRuleCatalog.controlAndInstrumentation()` proposes conventional ISA-style measurement and control loops for
separators, compressors, pumps, heaters, coolers, and exchangers. Set points, ranges, failure actions, installation
details, and control narratives remain project inputs.

`NorsokPidRuleCatalog.completeProposals()` adds process topology, equipment safeguarding, pressure relief, shutdown
and isolation valves, non-return valves, blowdown, drains, and vents where the governed project basis supports them.
The implemented rule families are independently reviewable and cover:

- process-topology connections;
- separator control and safeguarding;
- compressor control, anti-surge interfaces, and safeguarding;
- pump control and safeguarding;
- heater/cooler/exchanger control and safeguarding; and
- pressure-relief proposals linked to overpressure studies.

Separator level sensing belongs to the vessel and the total-level control valve
belongs to the liquid/oil outlet. Three-phase separator proposals include a
separate oil/water-interface transmitter/controller and a water-outlet control
valve. These phase-specific paths still require the project instrument design,
executable device bindings and reviewed control configuration.

Every proposal retains its source rule, engineering rationale, standards, requirement IDs, equipment identity, and
graph connections.

## Completeness and HAZOP preparation

`PidCompletenessValidator` checks tag uniqueness, references, connections, and minimum functional categories.
`PidHazopStudyRunner` then prepares one traceable HAZOP node per physical process item, generates parameter/guide-word
deviations, links matching proposed safeguards, reports unresolved connections, and leaves every workshop decision
`OPEN`.

Streams provide node boundaries. Numerical `Recycle`, `Calculator`, and `Adjuster` helpers are excluded from
physical HAZOP nodes and do not require safeguard proposals. Real equipment still requires P&ID coverage;
a model containing only numerical helpers is not ready for a HAZOP workshop.

This is preparation for an IEC 61882 multidisciplinary workshop. It does not establish credible causes, consequences,
IPL independence, residual risk, action closure, or approval. Reviewed outcomes can continue through the
[HAZOP and LOPA to Draft SRS Handoff](process/safety/hazop-lopa-srs-handoff).

## Package contents

The package contains the native DEXPI document, Proteus and PyDEXPI exchange documents,
`pid-design-model.json`, `pid-completeness-report.json`, and `pid-hazop-study.json`. Missing proposal tags are
materialized into the compatibility documents: supported valve categories become piping components, while other
proposals use instrumentation-function representations. These projections do not prove physical connection or
complete native-model coverage. The sidecars preserve details that an exchange profile cannot represent losslessly.

The exporter refreshes the engineering package manifest after P&ID elements are materialized into the DEXPI and
pyDEXPI documents.

The [dual-profile reference delivery](integration/comparesimulations-pfd-pid-acceptance) can also opt into
source-linked P&ID proposal registers and completeness sidecars. That facade renders proposal overlays alongside
the canonical PFD/P&ID drawings. Its exchange projection and full semantic equivalence remain separate checks;
an overlay does not turn a proposed item into a connected native object or a live simulation device.

## Status boundary

`structurallyComplete` means that references and minimum functional categories passed automated
checks. `readyForApproval` remains false until discipline and HAZOP/LOPA review findings are closed.
`fitnessForConstruction` is always false for generated proposals.

The executable notebook and its generated package are retained as GitHub Actions artifacts for
independent review.
The CI contract also runs Spotless and the focused P&ID test suite before rendering.

See the
[`complete_pid_design_synthesis.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/complete_pid_design_synthesis.ipynb)
notebook for the executable workflow.

## Dynamic feedback and ISO 15519 review

The professional drawing profile declares ISO 15519-1:2010 (general process
diagram rules) and ISO 15519-2:2015 (measurement and control) in its content
report and identifies ISO 15519 in the sheet heading. These are review-basis
references. `standardsAssessment` explicitly retains controlled-requirements
review for both parts; `isoConformanceStatus` and construction fitness remain
unchanged until the required engineering review is complete. A DEXPI 2.0.1
profile assessment does not establish ISO drawing conformance.

Bind an existing inlet shutdown valve to the actual valve and retain its
owner-relative inlet attachment and stream identity. The professional PFD
also shows that directly connected upstream valve. Additional actual outlet
transmitters receive distinct sensing routes so they do not cross the vessel
level-tap route. Check both process and sensing paths after binding changes.

A dynamic study can read candidate settings from `PidDesignModel`, construct
a versioned scenario configuration, run strict `PidSimulationSession` tests,
and attach result identities and checksums to a revised design. Numerical
pressure-equality helpers belong to that declared scenario configuration;
they are not physical P&ID equipment. Preserve base and configured source
fingerprints, actual native bindings, rejected candidates and negative tests.

Retain settings, units, assumptions and tested effects in the canonical design
and simulation-result sidecars with an explicit native-ID crosswalk. Native
XML connectivity alone is not proof that controller parameters or safety
logic round-trip into another CAE or control system. Native dynamic criteria
that test first entry into a range by a deadline do not prove a whole-window
envelope; use a horizon-gated accumulated predicate for that requirement.
Proposed relief, blowdown and additional trips receive no test credit unless
their actual executable effects were exercised.

A separator low-low-level proposal declares a liquid-outlet isolation/control
route and leaves its final element as `PROJECT_INPUT_REQUIRED` until an
applicable project target is supplied. It must not silently connect to the
inlet shutdown valve, whose retained high-high-level action addresses a
different hazard. A conventional liquid level control valve is not
automatically an independent safety final element. Generic cause-and-effect
requirement templates also need an explicit alias-to-canonical-tag crosswalk
before they can be treated as executable device identities.
