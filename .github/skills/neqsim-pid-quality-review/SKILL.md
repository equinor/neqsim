---
name: neqsim-pid-quality-review
version: "1.0.0"
description: "Quality-control regenerated NeqSim P&ID packages against source topology, physical versus signal semantics, instrument sensing, utilities, off-page links, symbols, design inputs and controlled ISO/IEC/ISA standards evidence. Requires deterministic checks plus content-hash-bound independent visual and standards review before PASS_FOR_ENGINEERING_REVIEW."
last_verified: "2026-11-03"
---

# P&ID package quality review

Use this workflow after P&ID generation or regeneration and before describing a
package as having passed P&ID review. It applies to native DEXPI, engineering
model/registers and the actual SVG/PDF sheets as a coordinated set. It does not
establish construction fitness or receiving-tool qualification.

The [pid-quality-review agent](../../agents/pid-quality-review.agent.md) owns
the integrated review. Use
[P&ID process operations](../neqsim-pid-process-operations/SKILL.md) for generation
and source bindings, [PFD layout review](../neqsim-pfd-layout-review/SKILL.md) for
geometry, and [standards-review](../../agents/standards-review.agent.md) for
controlled requirements and discipline calculations. Their scope-specific PASS
values cannot replace this gate.

## Required inputs

- The source process, engineering project, P&ID design and bindings, their IDs
  and revisions, NeqSim commit, selected interpreter and generation command.
  A claim of fresh master requires the actual fetched master commit and export
  provenance; a directory name or version label is insufficient.
- The complete generated package and manifest, including model/projection,
  native XML, SVG/PDF sheets, registers, content/readiness/validation reports and
  unresolved actions. Read actual files rather than trusting summary counts.
- Governed process/utility boundaries, control and instrumentation basis, line
  and device design inputs and source references. Keep operating values,
  calculated values, proposed values and approved design values distinguishable.
- The controlled standards register and project drawing/instrument conventions.
  Record exact part, edition/year, amendments/corrigenda and applicability; do
  not use `latest`, a bare standard number or a generic exporter profile as the
  basis. See [standards checklist](references/standards-checklist.md).
- Independent visual and controlled standards review records for the exact
  package content, or explicit open actions identifying who must provide them.

Find controlled references through the task's document-retrieval workflow before
calling them unavailable. Retain private/licensed material only in permitted
task locations; public repository files contain paraphrases and references.

## Deterministic gate and evidence contract

Use the selected interpreter, never bare `python` or a new environment. From the
repository root run:

```text
<selected-python> devtools/pid_quality_review.py PACKAGE --output DIR
```

Place DIR under the task's review outputs and outside PACKAGE so review products
do not alter the reviewed package. Read the tool's `--help`, findings and emitted
evidence schema before assembling review records; its schema is authoritative.
Do not invent unsupported JSON fields or treat a boolean assertion as evidence.
After actual independent reviews exist, run:

```text
<selected-python> devtools/pid_quality_review.py PACKAGE --output DIR --visual-review VISUAL.json --standards-review STANDARDS.json
```

The checker owns automatic findings and hash calculation. Keep its reports and
review receipts immutable for that revision. A schema-valid review record is a
receipt, not verification that the review happened. The integrated reviewer must
inspect the referenced evidence and declared independence.

The gate emits `pid-quality-review.json` and `pid-quality-review.md`; its JSON
schema version is `neqsim_pid_quality_review.v1`. Use these evidence contracts
and check the running tool for any later revision:

| Record | Required content |
| --- | --- |
| Visual | `schemaVersion=neqsim_pid_visual_review.v1`, `packageManifestSha256`, `reviewer`, `reviewedAt`, and `artifacts` with `path`, `sha256`, `status` and observed `checks` (`name`, `status`, `evidence`) |
| Standards | `schemaVersion=neqsim_pid_standards_review.v1`, the same `packageManifestSha256`, `reviewer`, `reviewedAt`, `standards` entries (`standard`, `status`, `reference`, `evidence`) and `scope` covering `content`, `symbols`, `pce`, `drafting` |

`packageManifestSha256` is SHA-256 of the raw `package-manifest.json` bytes.
The visual record must cover the drawing-set PDF and every P&ID SVG with their
actual file hashes. Required visual checks are `symbol_meaning`,
`physical_connections`, `sensing_locations`, `utility_segregation`,
`continuations` and `readability`. Identify whether each reviewer is a person
or AI. Standards entries must use the exact emitted basis strings, including
editions, and reference controlled sources actually available to the reviewer.
Use the supported evidence text/receipts for provenance; do not add invented
approval fields or fill records with generic passing text.

Required review provenance, expressed in the tool's supported schema:

- Current manifest fingerprint and reviewed artifact SHA-256 hashes,
  exact sheet/revision list, reviewer identity/role, date, scope and findings.
- Visual coverage of every final sheet, final paper size and readable detail
  crops; actual observations about attachments, symbols, routes and labels.
- Standards register with exact editions, controlled source document IDs,
  revisions/hashes, evidence locations, requirement/clause references,
  applicability decisions, dispositions and reviewer authority.
- Independence statement and an identifiable review record or workflow receipt.
  A hash proves content binding, not reviewer independence or competence.

Missing, stale, incomplete, unsupported or self-issued independent-review
evidence means HOLD. A changed source, model, register, native XML or drawing
requires a new fingerprint and affected-review assessment; never copy a former
passing receipt onto regenerated content.

## Review sequence

1. **Establish identity.** Compare source/project/P&ID/binding revisions and
   native IDs with the manifest and all sheets. Identify the actual generation
   baseline. Read unresolved actions, professional readiness, native validation,
   projection and drawing-content reports. Record absent inputs as HOLD with
   owner, evidence needed and affected scope.
2. **Check physical connectivity independently.** Reconstruct directed material
   routes from actual source ports/streams, not equipment insertion order, tag
   presence or rendered proximity. Compare each branch, junction, recycle,
   bypass, vessel phase outlet, relief/blowdown/drain/vent path and boundary with
   projected lines, native connections and registers. Numerical recycle helpers
   are not physical devices. Coincident lines need explicit junction semantics.
3. **Separate connection domains.** Material flow, utility flow, sensing/tapping,
   electrical/pneumatic/data signals and mechanical associations must remain
   distinguishable by declared type, endpoints and drawing legend. Signals do
   not create a process stream; a rendered line alone does not prove a nozzle.
   Check utility-side ports and returns separately from process-side ports,
   including heat exchangers; reject unintended utility/process mixing.
4. **Verify instruments and valves in context.** Trace each sensing attachment
   to the actual measured vessel/line/nozzle and runtime binding. A controller's
   parent equipment is not automatically its sensor location. Check pressure and
   temperature tap position, flow measurement segment, vessel level attachment,
   three-phase interface measurement, controller-to-final-element signal and
   the manipulated valve's actual material route. Check check-valve direction,
   actuator/fail-action source, relief orientation and destination. Unspecified
   fail positions, SIL, setpoints or voting remain unresolved; do not infer them.
5. **Reconcile sheets and design data.** Match off-page connectors in both
   directions by controlled identity, service, flow direction and destination
   sheet/zone. A real battery limit requires a governed external destination;
   it is not an unmatched internal connector waiver. Reconcile equipment,
   nozzle, line, instrument and valve IDs with registers. Check required line
   size/class, material and design P/T evidence and device type/rating/data
   against the declared project scope. Missing governed input is HOLD; a
   contradictory connection or design value is FAIL. Do not fabricate sizes or
   copy normal operating pressure into design pressure to close a gap.
6. **Inspect final drawings.** Run the existing layout gate, then inspect each
   final sheet at its intended paper size and dense regions at detail scale.
   Check symbol selection and orientation against the controlled convention,
   actual sensing/utility attachments, line identities, flow arrows, distinct
   signal styles, off-page references, labels, title/revision/status and legend.
   A low-resolution preview cannot establish print legibility. Record what was
   viewed and bind the visual review to those exact files.
7. **Review the standards basis.** Use the referenced checklist to map applicable
   requirements to controlled evidence and observed artifacts. Read the actual
   applicable requirement rather than inferring it from an ISO/IEC/ISA name.
   Conflicts between project conventions or editions require a governed
   resolution. Record exclusions with a reason and accountable acceptance;
   never silently omit a family to obtain PASS.
8. **Rerun and report.** Return defects to the source/generator owner, preserve
   previous exports, regenerate into a new directory and rerun affected gates.
   Never patch the output drawing, XML, hash, readiness report or review record
   to obtain acceptance. Report automatic checks, visual review, standards
   evidence and remaining actions separately, with links to the evidence.

## Verdict and limits

| Status | Required disposition |
| --- | --- |
| FAIL | A verified defect or failed automatic/review check. Return a concrete source/generator correction with object and artifact references. |
| HOLD | No known defect is sufficient to fail, but an input, controlled reference, review, applicability decision or current evidence is absent or unverified. State the missing evidence and owner. |
| PASS_FOR_ENGINEERING_REVIEW | All automatic checks passed, every final sheet has current content-bound independent visual evidence, controlled standards review is complete for the declared scope, and no blocking finding or evidence gap remains. |

FAIL takes precedence when defects and evidence gaps coexist. Never promote
HOLD to PASS from generated claims, successful export, a layout score, tag counts,
generic standards labels, synthetic reviews or self-certification. Do not relax
the checker or overwrite evidence. State the checked scope and review limits.

Keep named receiving-CAE tool/version import, connectivity/symbol preservation,
projection loss and round-trip qualification separate. A pass here does not
change `NOT_QUALIFIED` exchange status, grant engineering/safety approval or
authorize construction/operation. Always retain `fitnessForConstruction=false`.
