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

## Native ISO-basis drawing and connectivity checks

The professional exporter now uses `ProfessionalPidDiagramRenderer` and
`ProfessionalPidSymbolLibrary` for default native A1 landscape scenes. It
preserves the actual separator orientation, uses distinct control, isolation,
check and relief-valve families, adds pneumatic actuator decoration only for
an explicitly supplied `actuatorType=PNEUMATIC_DIAPHRAGM_PROPOSAL`,
separates field measurement from shared control/safety functions, and retains
named signal continuations. Existing explicitly controlled manual layouts
retain their previous representation and are labelled separately in readiness;
they are never silently replaced by the automatic professional layout.

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
