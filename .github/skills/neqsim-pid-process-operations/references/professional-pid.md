# Professional P&ID, DEXPI 2.0.1 and simulation handoff

Use this workflow for a request such as: "Prepare a professional P&ID from this
NeqSim model, including instrumentation and safeguards, and run steady-state
and dynamic tests based on the final drawing."

## Scope and source model

1. Inspect the supplied model and current public APIs. Run the base process and
   record convergence, fluids, thermodynamic method, cases, units and revision.
   Resolve actual stream connections, branches and recycles; do not derive
   topology from equipment insertion order or draw numerical recycle helpers as
   physical equipment.
2. Reuse the governed `EngineeringProject` and its design cases, line inputs,
   relief bases, safety functions, shutdown sequences and evidence records.
   Preserve calculated operating conditions separately from design limits and
   project-entered values. Record missing input as HOLD with owner and rationale.
   For a new 2.0.1 project use the three-argument
   `NorsokOffshoreEngineeringBuilder.from(name, process, "2.0.1")` overload.
   The professional exporter requires this edition in the standards register;
   legacy two-argument builder calls retain their existing 2.0 basis.
3. Establish battery limits, sheet format, project tag conventions, jurisdiction
   and applicable standard editions. Use ISO 10628 drawing conventions,
   ANSI/ISA-5.1 instrumentation and applicable IEC, relief and NORSOK requirements
   according to the project basis. Record licensed-text review gaps; never
   invent clauses, trip settings, SIL, voting, valve fail actions or approvals.

## Synthesize, bind and generate the engineering package

4. Synthesize the P&ID with `PidDesignSynthesizer` and the relevant
   `NorsokPidRuleCatalog`. Include instrumentation and control-loop proposals,
   isolation/check/shutdown/relief devices, drain/vent/blowdown interfaces and
   nozzles where supported by the project basis. Check each physical connection,
   sensing tapping and signal path. Require the actual runtime device or an
   explicit unresolved proposal; a tag is not a complete device model.
   Keep separator level sensing on the vessel and its control valve on the
   liquid/oil outlet. For a three-phase separator, retain the separate
   oil/water-interface loop and water-outlet control valve.
5. Create `PidSimulationBindings.from(project, pid)` before exporting. Use
   explicit `bindEquipment`, `bindMeasurement` and `bindController`
   associations for the actual objects represented by the P&ID tags. Bind a
   control valve to that valve, not to its upstream vessel. Do not infer a
   binding from a similar name or from the parent equipment. Require unique,
   type-correct targets, check current project/P&ID identities with
   `bindings.assertCurrent(project, pid)`, and retain unresolved proposed
   objects in the binding report. Review the base dynamic capability audit
   separately from the configured scenario's later strict preflight.
6. Use `ProfessionalPidPackageExporter` for the coordinated review package.
   Supply the existing design model, bindings and controlled
   `Dexpi20PlantExportMetadata`; inspect the exact overload in source. The
   professional path explicitly selects native DEXPI 2.0.1. For native-only
   export use `DexpiEngineeringExporter.exportDexpi201(...)`; for a bare process
   export use `Dexpi201XmlWriter.writeAndAssess(...)`.
7. Read the emitted manifest and readiness/coverage reports. Require the actual
   assessed XML, engineering model/registers, vector PDF/SVG drawings, binding
   evidence and unresolved-design report. Do not describe a register-only or
   tag-only projection as a fully connected and rendered P&ID.
   Inspect `professional-pid-readiness.json` and treat
   `INCOMPLETE_NATIVE_PROPOSAL_PROJECTION` as an unresolved native handover.
   Treat `INCOMPLETE_DOCUMENT_MODEL` and `INCOMPLETE_DRAWING` as failed technical
   drawing gates; retained diagnostic files are not a completed drawing set.
   `nativeProposalConnectivityQualification=REQUIRED` and
   `fullPidSemanticEquivalence=NOT_QUALIFIED` retain the separate connection
   and full-exchange gates even when every proposal tag is present.
8. Inspect every sheet at final paper size and detail scale. Apply the existing
   PFD/P&ID layout-review checks. Fix the generator for overlap, clipping,
   ambiguous junctions, missing arrows, unreadable labels, incorrect symbols or
   broken off-page references. Preserve manual placement/routing evidence.
9. Validate the actual 2.0.1 reference-model imports, types, properties,
   multiplicities and references in addition to XML schema validity. Keep
   projection loss and named receiving-tool qualification visible. Legacy
   DEXPI 2.0.0 and Proteus compatibility outputs retain their own profile labels.

## Run steady-state and dynamic tests
10. Resolve bindings against each isolated process copy. Use
    `PidSimulationSession` for the executed handoff; keep the original process
    unchanged. Run steady state first and retain convergence and balance
    evidence. Reassess the binding after a controlled revision or topology
    change instead of silently reusing stale references.
    Retain `simulation-model.neqsim`, `pid-simulation-bindings.json` and the
    matching P&ID/project definition. Load the process with
    `ProcessSystem.loadFromNeqsim(...)`, check for failure, and reconstruct the
    same project ID/revision with proposed-instrument registration disabled.
    Restore the P&ID with `PidDesignModel.fromJson(...)`, then restore bindings
    with `PidSimulationBindings.fromJson(project, pid, bindingJson)` before
    constructing the new session. Recomputed fingerprints must match; a saved
    file or binding report alone does not establish current-model equivalence.
11. Define a `DynamicSafetyScenario` with explicit configuration, event, logic,
    measured criteria, timing and exercised P&ID scope. Use the session's strict
    dynamic path, which checks the configured isolated model before transient
    execution. Report unsupported or inactive dynamic states and missing runtime
    bindings; never count repeated algebraic calculations as physical dynamics.
    Declare the actual bound simulation names in the scenario's equipment-tag
    evidence; the binding register links those names back to the P&ID aliases.
12. Retain the scenario definition, exercised P&ID tags, initialized operating
    case, dynamic capability report, pass/fail criteria, errors and results.
    Apply timestep sensitivity to rapid events. Use dedicated hydraulic surge
    or blowdown/flare models where the existing process scope is insufficient.
    Scoped scenario success does not establish full-P&ID simulation readiness.
    Persist the returned scoped session results as controlled evidence; the
    session does not automatically record them as a project simulation result.
    For project-configured case/scenario execution, use
    `EngineeringSimulationRunner` and retain its project result. Regenerate the
    package in a new output directory with the linked evidence and final test
    disposition. If the P&ID, process topology or revision changed, rebuild and
    validate the bindings before regenerating.

For a multi-area `ProcessModel`, preserve area-scoped identities and prepare
the governed area projects through the explicit-edition
`fromProcessModel(name, plant, registerProposedInstruments, "2.0.1")` builder
overload. Retain controlled area IDs and revisions when reopening. Do not infer a global
pressure-flow or transient solve from area-by-area execution.

## Acceptance and reporting

Report each item as PASS, FAIL, NOT TESTED or NOT APPLICABLE:

| Item | Required evidence |
| --- | --- |
| Source coverage | Stable source-to-drawing identities and preserved physical connections |
| P&ID engineering | Devices, nozzles, lines, control/safety functions, destinations and explicit HOLD items |
| DEXPI 2.0.1 | Exact file assessment, official-model semantics and projection-loss findings |
| Drawings | Vector outputs, sheet index, labels/symbols, physical/signal attachments and visual review |
| Steady state | Isolated initialized process, convergence and stated balance tolerances |
| Dynamic scenario | Correct bindings, strict capability preflight, events/logic and measured criteria |
| Revision | Current process/P&ID/binding identity and affected-test disposition |
| Qualification | Named-tool observations, standards review and accountable approvals recorded separately |

Link the generated files and summarize counts, tested scenarios, unresolved
bindings, unprojected native content and engineering decisions. Keep the package
FOR ENGINEERING REVIEW. Neither a generated safeguard nor a passing test assigns
SIL, completes HAZOP/LOPA/SRS, grants safety credit or authorizes field operation.

## Source entry points

- `src/main/java/neqsim/process/engineering/pid/`
- `src/main/java/neqsim/process/processmodel/dexpi/Dexpi201XmlWriter.java`
- `src/main/java/neqsim/process/engineering/dexpi/DexpiEngineeringExporter.java`
- `src/main/java/neqsim/process/safety/scenario/DynamicSafetyScenarioRunner.java`
- `docs/pid-design-synthesis.md`
- `docs/engineering/dexpi-guide.md`
