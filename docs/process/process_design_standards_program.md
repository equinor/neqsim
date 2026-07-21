---
title: Process-design standards implementation program
description: Priorities, evidence gates, and engineering boundaries for extending standards support in NeqSim.
---

NeqSim should implement more standards support, but catalog breadth is not the governing measure of
success. The useful target is traceable calculation and review coverage for a named edition, stated
applicability envelope, and independent evidence. This program separates work that can be maintained
in an open-source simulator from conformity activities that remain with the project and accountable
engineers.

The current source-of-truth matrix is in
[Mechanical Design Standards in NeqSim](mechanical_design_standards.md). No entry currently meets the
`VALIDATED` or `QUALIFIED` maturity gate.

## Work completed in the foundation

- Publisher-sourced lifecycle status and current-edition metadata are separate from factory support.
- Strict current, checked historical, transitional factory, and requirement-pack selections have
  distinct fail-closed contracts.
- Cross-equipment standards can map to immutable calculation and review capabilities without
  pretending that one equipment class implements a whole publication.
- Recognized pipeline and unsupported pressure-vessel standards no longer silently use unrelated
  generic wall-thickness fallbacks.
- Closed design loops preserve the complete case-run policy and cannot converge on an incomplete
  envelope by default.
- The generated support matrix states the calculation path, maturity, current-kernel status, and
  engineering boundary for every catalog entry.

## Implementation priorities

| Priority | Scope | Current NeqSim basis | Next releasable increment |
| --- | --- | --- | --- |
| P0 | Design-basis integrity | Typed selections, lifecycle catalog, case envelopes, readiness and provenance contracts | Keep catalog verification dates current; add migration tests for every behavior change |
| P1 | Pressure relief and depressuring | API 521/API 526 kernels; API 520 Part 1 capability mapping; relief and flare calculations | Implement one coherent current-edition relief workflow covering scenario register, sizing, inlet/outlet checks, reaction loads, disposal-system coupling, and explicit exclusions |
| P1 | Process criteria and line sizing | NORSOK P-002 line-sizing and point checks; design-review workflow | Build a licensed project requirement profile and trace each implemented check to a stable requirement identifier and independent numerical example |
| P1 | Pressure equipment, piping, and pipelines | Generic vessel screens; pipeline mechanical screen; DNV-ST-F101 capability mapping | Add exact-edition ASME VIII Div. 1, ASME B31.3, and DNV-ST-F101 pressure-containment kernels with load-case inputs and fail-closed applicability |
| P1 | Control valves | IEC 60534 sizing implementations and design-loop selection | Verify applicable series parts and editions, unify result/provenance models, and add independent liquid, gas, flashing, cavitation, and noise datasets |
| P2 | Heat exchangers and air coolers | API 660 shell-and-tube screen; generic thermal/mechanical calculators | Implement current API 660 datasheet-level screening first; keep API 661 catalog-only until an air-cooler-specific model and evidence exist |
| P2 | Rotating equipment | API 610/API 617 historical adapters; generic compressor factors | Replace legacy edition labels with current-edition kernels, separate purchaser/vendor requirements from calculable screens, and add vendor-independent benchmarks |
| P2 | Tanks and low-pressure storage | API 650 tank screen; API 620/API 625 catalog entries | Qualify API 650 pressure/wind/seismic shell screening; leave API 620/API 625 fail-closed until storage-system-specific implementations exist |
| P2 | Materials and corrosion | NORSOK M-001/M-506 capabilities; material-property lookup | Add project material data sheets, sour-service/environment inputs, uncertainty, and traceable material-selection decisions without treating lookup tables as acceptance |
| P2 | Process and functional safety | NORSOK S-001, ISO 10418, and IEC 61511 review/reliability capabilities | Add requirement-coverage reports, controlled assumptions, independence/approval roles, and lifecycle evidence links; retain external approval |
| P3 | Remaining catalog entries | Identity and applicability metadata | Promote only when a named owner, licensed source basis, test dataset, and maintenance plan exist |

## Acceptance gates for a calculation kernel

A kernel may move from `CATALOGUED` to `SCREENING` only when all of the following are present:

1. Exact publisher, document identifier, edition, amendments, and verification date.
2. A public input/output contract with units, validity range, assumptions, and excluded requirements.
3. Explicit equipment and service applicability with unsupported conditions blocked before calculation.
4. Deterministic calculations that do not mutate process or calculator inputs.
5. Structured results containing status, findings, method version, edition, and input provenance.
6. Unit, boundary, failure-mode, unit-system equivalence, and regression tests.
7. Documentation that uses “screening” and names the checks that remain external.

Promotion to `VALIDATED` additionally requires independently produced benchmark data over the stated
range, tolerance justification, reviewer identity, and controlled evidence stored outside the
implementation. Promotion to `QUALIFIED` requires a released method version, change control,
traceability, reproducible evidence, and approval for a named use. A passing regression test alone
does not satisfy either gate.

## Requirement-pack coverage contract

Requirement packs intentionally do not reproduce publisher clauses. A project that needs a
conformity assessment should maintain a licensed coverage register with at least:

| Field | Purpose |
| --- | --- |
| Requirement ID | Stable project-controlled reference to the licensed source |
| Applicability | Applicable, not applicable with rationale, or unresolved |
| Evidence type | Calculation, simulation, document, inspection, test, vendor data, or review |
| NeqSim capability | Exact method and version when NeqSim supplies evidence |
| Input provenance | Case, data source, units, timestamp, and configuration fingerprint |
| Result and limitation | Structured outcome plus excluded checks and uncertainty |
| Responsible role | Preparer, checker, approver, and required independence |
| Status | Open, evidenced, accepted, deviated, or rejected |

NeqSim can calculate and assemble evidence, but it should not embed licensed normative text, decide
unresolved applicability, approve deviations, or issue certification.

## Change-control rules

- Do not update an enum edition without updating the lifecycle source, exact kernel support, support
  matrix, migration notes, and tests independently.
- Do not silently retarget a kernel to a new publisher edition. Add a new method version and retain
  reproducibility for the old basis.
- Treat tolerance changes as engineering changes: document whether they represent numerical
  conditioning, model uncertainty, or an independently justified acceptance range.
- Preserve partial case reports on failure and block convergence when the required envelope is
  incomplete or unaccepted.
- Prefer a narrow validated kernel over a broad class name that implies unsupported conformity.

## Delivery sequence

Each standards increment should be a reviewable change containing the catalog update, implementation,
tests, benchmark evidence manifest, generated support-matrix update, migration note, and one runnable
example. CI should regenerate and compare the matrix, execute targeted kernel tests, and then run the
complete NeqSim suite. Independent engineering review remains a release prerequisite for maturity
promotion.
