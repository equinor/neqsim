---
title: "Engineering Deliverables and Handover"
description: "Guide to compiling, validating, reviewing, revising, and handing over coordinated NeqSim engineering packages, including DEXPI and CFIHOS."
keywords: "engineering deliverables, engineering package, DEXPI, CFIHOS, registers, datasheets, handover, approval, revision"
---

# Engineering Deliverables and Handover

NeqSim can compile process and engineering results into a coordinated, machine-readable package. The purpose is to
make values, identities, cases, methods, dependencies, findings, and review state consistent across deliverables. A
generated package is review-ready evidence, not automatic approval or fitness for construction.

## Choose the correct output

| Required output | Entry point |
| --- | --- |
| Quick Proteus-compatible P&ID or pyDEXPI visualization | `DexpiXmlWriter` or `DexpiDiagramBridge` |
| Native DEXPI 2.0 Plant or Process exchange | `Dexpi20XmlWriter` or `Dexpi20ProcessModelWriter` |
| DEXPI plus coordinated cases, registers, calculations, and validation | `EngineeringDeliverableCompiler` |
| Coordinated multi-area packages and root manifest | `ProcessModelEngineeringSimulator.Result.compile(...)` |
| Review-required generated P&ID proposal | `PidEngineeringPackageExporter` |
| Project information handover staging | `Cfihos20HandoverExporter` applied to the canonical engineering graph |
| Integrity-protected portable model revision | `NeqSimModelPackage` and `ModelPackageValidator` |
| Fail-closed qualification and readiness backlog | `EngineeringProductionReadinessAssessment` and `EngineeringQualificationPlan` |

For DEXPI profile selection and qualification, start with the [DEXPI Engineering Guide](dexpi-guide).

## Package layers

### Identity and provenance

The canonical `EngineeringGraph` provides stable identities and relationships for projects, equipment, lines,
instruments, calculations, requirements, evidence, documents, and approvals. It also provides the dependency basis for
revision diff and impact analysis.

Principal artifacts include:

- `engineering-model.json`;
- `engineering-connectivity.json`;
- `engineering-calculation-dag.json`; and
- `engineering-revision-diff.json` or revision-impact reports when a baseline is supplied.

The compiler also writes `neqsim-model-package.json`. `NeqSimModelPackage` records asset/model identity, revision,
baseline revision, lifecycle state, qualification state, dependencies, software versions, artifact inventory, and
SHA-256 hashes. `ModelPackageValidator` detects missing or altered files. Integrity is not engineering approval.

### Cases and calculations

The package retains controlled case definitions, per-case results, governing values, calculation methods, units,
constraints, warnings, and readiness:

- `engineering-design-case-matrix.json`;
- `design-case-envelope.json`;
- `engineering-calculations.json`;
- `engineering-numerical-health.json`; and
- `engineering-discipline-orchestration.json`, `engineering-production-readiness.json`,
  `engineering-qualification-plan.json`, and other discipline calculation artifacts.

See [Design Cases and Governing Envelopes](design-cases-and-envelopes) for case construction and interpretation.

### Registers and datasheets

Coordinated outputs may include:

- equipment, line, valve, instrument, I/O, alarm, trip, and relief registers;
- equipment and PSV datasheets;
- process design basis and utility summary;
- materials-selection and preliminary mechanical reports;
- shutdown narratives and flare/blowdown reports; and
- unresolved engineering actions and evidence registers.

Every calculated field should retain its unit, governing case, method or source calculation, and readiness state.
The package also includes an `engineering-approval-ledger.json` and external-evidence register. These preserve supplied
decisions and evidence state; they do not manufacture approval.

### DEXPI exchange

The package can contain native DEXPI 2.0 Plant and Process XML plus Proteus and pyDEXPI representations, validation
reports, internal structural round-trip evidence, and deterministic diagram layout. These representations share graph
identity but serve different receiving tools and information-model purposes.

### CFIHOS staging

`Cfihos20HandoverExporter` maps canonical graph identities and properties through a project-controlled CFIHOS 2.0 Core
or Extended RDL mapping. It generates deterministic staging tables, a gap register, file digests, and a fail-closed
assessment.

The staging CSVs are not official bulk-loader templates. The Principal still owns contractual completeness,
transformation to required exchange templates, named target-system validation, and final information acceptance.

## Recommended issue workflow

1. **Freeze inputs.** Record project ID, revision, process-model fingerprint, case basis, standards, methods, and
   controlled evidence.
2. **Run engineering.** Require converged cases, a closed design loop, satisfied constraints, and explicit findings.
3. **Compile once.** Generate all coordinated artifacts from the same designed process copy and canonical graph.
4. **Validate the package.** Check schemas, identities, references, units, fingerprints, manifests, and cross-artifact
   consistency.
5. **Complete external qualification.** Attach vendor, independent benchmark, named-CAE, safety-lifecycle, and
   authority evidence required by the issue purpose.
6. **Review and approve.** Record accountable discipline decisions against stable graph subjects; do not overwrite
   previous decisions.
7. **Transform and load.** Map to Principal requirements, load into the named target system, and retain receipts,
   rejections, and reconciliation evidence.
8. **Issue the controlled revision.** Preserve package manifest and hashes with the released files.

## Validate before issue

| Gate | Minimum check |
| --- | --- |
| Structural | Every JSON artifact matches its supported schema version and URI |
| Referential | Graph and register references resolve; controlled tags are unique |
| Units | Values use the controlled unit vocabulary and retain units across artifacts |
| Case consistency | Governing case IDs resolve to successful case results |
| Calculation consistency | Results point to methods, inputs, subjects, and dependencies |
| DEXPI | Selected profiles validate; internal references and identities survive structural round trip |
| Manifest | Artifact inventory, revision, graph fingerprint, and file digests agree |
| Model package | `neqsim-model-package.json` inventories every governed artifact and all hashes verify |
| Production readiness | Failed gates and the exact qualification backlog remain visible; no absent gate is inferred as passed |
| Multi-area package | Every area package, shared dependency, executed/reused-area decision, and root manifest validates |
| Review state | Warnings, blockers, actions, approvals, and revalidation requirements remain visible |

Readers should reject an unknown major schema version instead of silently treating it as the current contract.

## Approval and readiness states

| State | What it establishes |
| --- | --- |
| Calculated | A declared method produced a result |
| Structurally valid | Schemas, identities, references, units, and manifests are coherent |
| Internally qualified | Required internal benchmarks and structural round trips passed |
| Controlled-pilot qualified | The configured facility slice passed its nine explicit topology, case, design, safety, standards, and evidence gates |
| Qualified for FEED support | Every configured production-readiness gate passed for the declared support purpose |
| Review-ready | Evidence, assumptions, findings, and actions are organized for accountable review |
| Approved | An authorized role accepted the controlled subject and evidence for a declared purpose |
| Accepted into target system | The Principal validated the transformed data in the named system |
| Fit for construction | Detailed design, vendor, independent, safety, regulatory, and construction gates are closed |

These states are not interchangeable. Successful compilation establishes neither external qualification nor approval.

## Manage revisions

When a model, case, method, design variable, or evidence item changes:

- create or derive a deterministic `ModelChangeEvent` for the new governed revision;
- compare the new canonical graph with the controlled baseline;
- use `GeneralizedImpactAnalyzer` to identify added, removed, modified, and downstream-impacted objects;
- mark dependent calculations, documents, validations, and approvals for rework;
- retain unchanged evidence only when its dependencies and applicability remain valid; and
- issue the impact report and updated manifest with the new revision.

For a multi-area `ProcessModel`, `ProcessModelEngineeringSimulator.runIncremental(...)` reruns changed and connected
areas and records which unaffected baseline areas were reused.

An approval affected by revision impact becomes `REVALIDATION_REQUIRED`; the earlier decision remains in the ledger
instead of being silently replaced.

## DEXPI and CFIHOS handover boundary

| DEXPI provides | CFIHOS handover adds |
| --- | --- |
| Process/plant information-model exchange | Project information-requirement and RDL mapping context |
| Equipment, piping, ports, streams, instruments, safeguards, and diagrams | Controlled tag/equipment classes, property/UOM identifiers, document types, and relationships |
| Schema and supported-profile conformance evidence | Mapping approval, gap register, staging manifest, and target-system transformation boundary |

Use both when the project requires machine-readable engineering exchange and controlled information handover. Neither
replaces discipline review, document control, vendor data, target-system acceptance, or construction authority.

## Consumer checklist

- [ ] Confirm project, area, revision, and package fingerprint.
- [ ] Confirm supported schema major versions before reading values.
- [ ] Check validation reports and unresolved actions before using registers or datasheets.
- [ ] Resolve every governing case and calculation reference.
- [ ] Confirm the exact DEXPI profile and receiving-tool qualification evidence.
- [ ] Confirm CFIHOS RDL delivery, mapping revision, approval, and target transformation when applicable.
- [ ] Do not infer approval from a successful calculation, export, schema validation, or file load.
- [ ] Retain the package manifest and hashes with any downstream transformation.

## Common anti-patterns

- Issuing a drawing separately from the calculations and registers that produced its values.
- Copying values between artifacts without preserving units, governing cases, or method identity.
- Treating DEXPI schema validation as named-tool or engineering approval.
- Treating neutral CFIHOS staging CSVs as the contracted target-system load format.
- Deleting warnings or unresolved actions to make the package appear complete.
- Reusing approvals after a dependency changed without impact analysis.
- Declaring `fitnessForConstruction=true` from preliminary or screening calculations.

## Related documentation

- [Engineering Guide](guide)
- [DEXPI Engineering Guide](dexpi-guide)
- [Standards-based DEXPI Engineering Generation](../integration/dexpi-engineering-generation)
- [Process Model to Engineering Workflow](../integration/process-to-engineering-workflow)
- [CFIHOS 2.0 Engineering Handover](../integration/cfihos-20-engineering-handover)
- [Model Change Events](../process/model-change-events)
- [Model Impact Analysis](../process/model-impact-analysis)
