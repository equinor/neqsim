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
   and applicable standard editions. Include ISO 15519-1:2010 general diagram
   rules and ISO 15519-2:2015 measurement/control in the project review basis
   when applicable; verify controlled text rather than treating the renderer
   declaration as compliance. Use ISO 10628 drawing conventions,
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
   Keep the explicit canonical P&ID as the sole native instrumentation authority:
   this professional path disables generic requirement-to-PCE/safeguard/nozzle
   synthesis before applying that model. All requirements stay in the manifest
   and review registers, with the selected policy/retained IDs in projection
   evidence. Engineering-only export keeps generic typed proposals by default;
   `ENGINEERING_ASSESSMENT` never creates a process instrument or sensing nozzle.
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
8. For ISO-basis professional drawings, use the native professional profile;
   do not substitute an independently hand-laid-out PDF/SVG and claim the
   requested NeqSim capability has passed. Inspect
   `pid-drawing-content-report.json`, `pid-physical-projection.json`, and the
   exact native `.pid-projection.json`. Close unresolved physical connections,
   utility sensing, explicit auxiliary series ordering, pipe-size/class and
   symbol-review gaps before claiming the applicable completeness/conformance.
   Distinguish standards-basis implementation from independently reviewed ISO
   conformance. Preserve manual layout intent and its reported profile.
   Inspect every sheet at final paper size and detail scale. Apply the existing
   PFD/P&ID layout-review checks. Fix the generator for overlap, clipping,
   ambiguous junctions, missing arrows, unreadable labels, incorrect symbols or
   broken off-page references. Preserve manual placement/routing evidence.
9. Validate the actual 2.0.1 reference-model imports, types, properties,
   multiplicities and references in addition to XML schema validity. Keep
   projection loss and named receiving-tool qualification visible. Legacy
   DEXPI 2.0.0 and Proteus compatibility outputs retain their own profile labels.

## External symbol libraries

When PCA or another symbol library is requested, follow the
[external-symbol acquisition contract](../../../../docs/pid-design-synthesis.md#external-symbol-libraries-including-pca).
Distinguish the library URL, exact canonical glyph IRI and environment-specific
retrieval URL. PCA documents authenticated Reader/Creator retrieval through
`GET /esl/engineeringSymbols` as JSON, JSON-LD or Turtle; use the deployed Swagger
schema for exact payloads and direction values. Do not probe credentials or
invent per-symbol IRIs.

Record whether a real payload and its resource-specific rights were verified.
Preserve original bytes/hash, version/content status, attribution and review
evidence, geometry units/axes, rotation centre and connection-point identities,
coordinates and directions. The existing convention register records generic
shape/provenance inputs; a `sourceReference` does not import SVG or ports.
Require an explicit geometry/port transform, actual material/signal attachment
checks, SVG/PDF parity and applicable project/standards review. If prerequisites
are missing, deliver the sourced acquisition/import contract, keep the supported
authored-symbol fallback explicit, and retain unresolved review HOLDs. Do not
claim PCA glyph use or qualification from a library link or public visibility.

## Automatic proposals from actual native safety functions

For an executable candidate, use `PidSafetyFunctionImporter.fromNativeFunction(...)`
to extend a copied canonical `PidDesignModel` with actual healthy native sources,
voting and final elements. Reuse exact existing bindings and rebuild the binding
register after each import/change. Keep trip/voting/timing choices as explicit
study-policy inputs; native import does not perform HAZOP/LOPA or assign SIL.

Use actual measurement-backed `SafetyFunctionChannel` factories and the local
`manualTripFromPushButton(...)` factory where applicable. Held-sample factories
derive the source tag and reject a source captured from another executing copy.
Manual binary input is distinct from a pressure held sample and must not carry
hidden linked automatic actions. Legacy arbitrary extractors cannot establish
verified native measurement identity. Native qualified channel demands latch
until reset, with fixed configured voting; separate objects do not establish
installed hardware independence or simultaneous raw-signal coincidence.

The scenario factory must consume the governed canonical values. Validate its
actual configured copy through `PidSafetyFunctionValidator.validateConfigured(...)`,
retain the returned configuration fingerprint/evidence reference in the scenario,
and repeat validation with `assertSameConfiguration(...)` before execution.
Check exact source/target references, voting/count, unit/trip/delay, stroke and
actual fail-closed position. Only the bounded native trip/parallel-trip action
policy is supported for this isolation contract; changing a command to opening,
its order or delay cannot retain the same qualified configuration. Keep physical
source identity separate from configured numerical helper/execution-order data.

Use event-exact timing for held-sample cases and native strict execution. For
project-configured studies, `EngineeringSimulationRunner.runStrict(...)` records
the actual newly executed results on the project. Attach evidence through
`PidSafetyFunctionValidator.attachResult(...)` with declared required full-window
`MAINTAIN_BOUNDS` criteria. Default first-reach criteria do not establish an
always-within-bounds result. Report unexecuted/incomplete, observed FAIL, no-demand,
vote-without-actuation and incomplete versus completed demand response separately.
Expected-fault assertions must preserve native process FAIL. Keep component and
cumulative-energy verdicts separate; response success cannot clear numerical HOLD.

The tested `AdvancedSafetyPidExample` uses the actual two-inventory
`AdvancedV101SafetyStudy`, imports native ESD/HIPPS/manual functions, reads their
canonical values back into factories, executes the final project strictly and
regenerates native DEXPI 2.0.1, four P&ID sheets and a PFD. Its three HIPPS channels
meet one shared voting function. The actual manual button has no fake process
sensing location. Reciprocal detail sheets show actual existing stream/valve-port
monitoring contexts, and an inter-unit valve appears once on its declared inlet
sheet. Shared final-element targets receive no inferred independent-barrier credit.

Retain exact native-ID crosswalks plus canonical settings/receipts/result hashes
beside the package. XML-only parameter/PLC transfer, installed independence,
relief adequacy, SIL and controlled standards compliance remain unqualified.
The authored vector families remain in use; no PCA payload/glyph import is implied.

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

After regeneration, use `@pid-quality-review` and
[`neqsim-pid-quality-review`](../../neqsim-pid-quality-review/SKILL.md).
Run `neqsim pid-review PACKAGE --output REVIEW_DIR` outside the reviewed
package. Preserve its automatic, visual, standards and qualification findings;
missing controlled references or design inputs remain HOLD even when geometry
and native DEXPI checks pass. Review receipts must match the current artifact
hashes. Never edit drawings, readiness reports or evidence to obtain a pass.

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


## Evidence from dynamic feedback

- Check that a bound actual inlet valve retains its inlet attachment, both
  real streams and its visible PFD/P&ID representation. Check separate outlet
  transmitter routes against vessel level taps.
- Keep numerical pressure-coupling helpers in an explicit versioned scenario
  configuration. Do not export them as physical hardware; retain both source
  and configured fingerprints.
- Reapply every canonical candidate parameter to actual native objects, read
  back the applied values, rebind and rerun after an edit. Preserve result
  checksums and rejected candidates. Declare unsupported combinations.
- Distinguish first-reach-by-deadline criteria from whole-window constraints;
  the latter need an accumulated predicate gated at the scenario horizon.
- Audit external mass/component/energy balances at exact closure and reopen;
  a strict capability audit is not quantitative validation. Keep failed
  numerical qualification separate from passing response criteria.
- Verify parameter sidecars and native IDs together. Do not claim XML-only
  controller transfer, CAEX interchange, approved SIS design or deployment.

## Passive pressure-relief branch

For an actual PSV, follow `docs/process/safety/psv-pid-verification.md`.
`PidPressureReliefImporter` preserves the current canonical graph and adds/reuses only the
actual native passive valve. Require an actual splitter/branch/discharge route and registered
capacity-basis evidence; do not add a PLC trip or fabricated pressure instrument to represent
self-action. Apply canonical values on a pristine isolated copy and use
`PidPressureReliefValidator` to reject stale bindings, ignored settings and undeclared faults
before strict execution. Distinguish no demand, no lift, no positive flow, relief, reseat,
incomplete and observed FAIL. Capacity certification and disposal/flare adequacy remain HOLD.

For native DEXPI 2.0.1, the official `SetPressureHigh` field is gauge pressure. Map canonical
bara only with an explicit positive `simulationAtmosphericReferenceBara`, retain the
conversion in the native-ID crosswalk, and never treat `SetPressureLow` as reseat pressure.
Without a reference, retain the absolute sidecar and mark the gauge field unassigned/HOLD.
Review the actual tee, spring-relief symbol, both real branch line IDs and discharge battery
limit in every native P&ID/PFD and graph inventory; no additional control signal belongs on
this passive valve.

## Compressor automation packages

When the source model contains a compressor, scrubber, cooling and anti-surge
recycle, follow the [agentic compressor workflow](../../neqsim-compressor-antisurge-recycle/references/agentic-compressor-workflow.md)
for the coordinated model, rotating, control, safety and independent review
handoffs. Require the actual physical return and measurement/action graph,
converged steady results, governed configuration readback and fresh fault/refinement
runs before attaching dynamic evidence. Preserve the distinction between proposed
instrument installation, direct model-derived controller inputs and executed
measurement paths. Coordinate I/O, loops, cause/effect, permissive/reset/bypass
records and test coverage with the same source revision and native P&ID package.
