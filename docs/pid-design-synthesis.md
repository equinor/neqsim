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
| `drawings/pid-drawing-set.pdf`, `drawings/pid-sheet-NNN.svg` | A3 review drawings and per-sheet vector views |
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

- "Prepare a professional A3 P&ID with DEXPI 2.0.1 from this NeqSim model."
- "Add instrumentation and safeguarding proposals with their design assumptions."
- "Run the base steady-state case, then test the bound outlet valve and shutdown response."

Require an assessed native exchange, reviewable vector drawings, coherent
registers, explicit missing design inputs, and the scoped simulation results.
Keep visual acceptance, receiving-tool qualification, standards review and
accountable engineering approval as separate checks. Generated files alone do
not establish that every proposed component is physically connected, drawn,
simulated or approved.

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
`PidHazopStudyRunner` then prepares one traceable HAZOP node per process item, generates parameter/guide-word
deviations, links matching proposed safeguards, reports unresolved connections, and leaves every workshop decision
`OPEN`.

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
