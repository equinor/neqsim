---
title: "NRC-2899: Agentic compressor automation and native P&ID feedback"
description: "Proposed native control, fixed-step compressor study, configuration feedback and engineering qualification contracts."
---

# NRC-2899: Agentic compressor automation and native P&ID feedback

- Status: PROPOSED
- Owners: @EvenSol; independent process, controls, safety and exchange reviewers to be recorded
- Created: 2026-11-07
- Target release: UNPLANNED; compatibility classification pending owner review
- Tracking: [#2899](https://github.com/equinor/neqsim/issues/2899), [#2911](https://github.com/equinor/neqsim/issues/2911)
- Baseline: `d8c9354ab490564c3c2220b8f735c645cd575c79`

This proposal is **not accepted**. Keep it in a proposal-only draft pull request,
separate from the tested implementation candidate. Neither draft provides merge,
release, engineering or construction approval. Follow the
[NRC process](../development/rfc-process.md) before changing a public contract.

## Context and problem

Native compressor maps, control devices, dynamic vessels, safety functions and
professional DEXPI export exist, but an agent needs a reproducible bridge between
the actual process graph, canonical control settings, isolated fault tests and
drawings. A plausible diagram or a successfully issued command cannot establish
actual actuator response, hydraulic authority or full protection.

Current-master reproductions also found anti-surge commands bypassing actuator
travel, shutdown motion being overridden by normal control, and check-valve UUID
paths bypassing the declared cracking threshold. These are tracked in
[#4899](https://github.com/equinor/neqsim/issues/4899),
[#4900](https://github.com/equinor/neqsim/issues/4900) and
[#4901](https://github.com/equinor/neqsim/issues/4901).

## Decision

The following decision is proposed:

1. Add a reusable, explicitly scoped compressor study and public orchestration
   entrypoint. Use native finite suction/discharge vessels, map compressor,
   check valve, cooler, export and cold recycle hydraulics. Keep one fixed-step
   execution owner; do not add a competing general solver or scheduler.
2. Import actual sampled instruments, control links and supported shutdown
   actions into the canonical P&ID. Apply supported canonical policy on an
   isolated pristine native copy, read it back, then declare fault overrides.
   Reject stale receipts, unbound targets and unsupported policy edits.
3. Retain held FT-to-calculated-margin-to-ASC causality and actual valve motion.
   Retain distinct BPCS/SIS native sensor objects without claiming installed
   independence. Expose embedded cooler utility availability and native driver
   commands without inventing physical utility valves or electrical equipment.
4. Export native DEXPI 2.0.1, source-bound drawings, registers, configuration,
   exact results and a runnable native graph. Keep response, conservation,
   map/phase applicability, sampling and project qualification as separate gates.
5. Extend existing process, rotating-equipment, control, safety and independent
   P&ID review agents. Discovery exposes Java/JPype inspection; it does not add
   an undocumented curated MCP dynamic-execution option.

## Public contracts affected

- New `CompressorAutomationStudy`, `CompressorAutomationWorkflow`,
  `PidNativeControlSystem`, `CompressorSurgeMarginMeasurement` and
  `TripCompressorAction` APIs and their versioned configuration/evidence maps.
- Workflow CLI: `<design.json|-> <new-output-dir> [canonical-pid.json]`;
  explicit physical units, strict typed JSON, fresh output per execution.
- Anti-surge controller commands become target requests. The actual valve owns
  finite motion, shutdown priority and physical-step identity.
- Check-valve UUID and transient paths enforce the same declared ideal gate;
  detached capacity queries do not mutate connected process state.
- Cooler utility availability and complete dynamic-policy readback are additive.
  Legacy serialized coolers retain full availability.
- Canonical actual compressor shutdown actions and explicitly supported
  fail-open compressor roles extend the existing safety importer/readback;
  unrelated closed-only role contracts remain guarded.

## Engineering and safety boundary

The reference design, chart, capacities and thresholds are synthetic assumptions.
The compressor is quasi-steady with a declared programmed speed profile; rotor
inertia, mechanical surge, reverse flow and zero-throughput churning are not
qualified. Single-phase capacity requires actual phase evidence. A calculated
margin using native model references is not an independently installed field
measurement system.

Native response PASS cannot clear energy-policy exceedances, invalid map points,
negative active rundown margin, partial fault windows or missing vendor/SRS data.
The candidate retains four partial model/input-limit fault cases, completed
negative final-element tests and cooling-loss rundown boundary crossing.
Full graph rollback is rejected by the native transaction-coverage gate;
reproducible reruns use fresh native deep copies, not a failed mutable graph.

ISO 15519/10628/14617, ISA 5.1 and IEC 62424/61511 references require controlled
editions and accountable applicability review. No new SIL/IPL, complete HIPPS,
ETS, connected compressor relief/disposal, OEM, receiving-tool or construction
qualification is granted. PCA reference-data knowledge does not establish acquired
symbol geometry, graphic licensing or symbol conformance.

## Compatibility and migration

Preserve existing signatures and serialized identifiers. Leave ordinary valve
physical-step idempotency disabled by default for legacy loops that reuse UUIDs.
Opt-in studies supply one UUID per physical interval; zero-duration algebraic
evaluation must not consume physical actuator movement.

Callers that previously advanced only `AntiSurgeController.runTransient` must
also execute the actual valve/process interval. Reading target position as if it
were actual position is invalid. Retain regression evidence for this behavioral
correction and obtain owner classification before release; do not silently claim
that dependent integrations are unchanged. The implementation guide records the
handoff and supported execution contract.

Undefined native diagnostics are exported as strict JSON null with explicit path
and kind; native text/fingerprints and normalized-file hashes are distinct. Do
not interpret an earlier result or schema major as the new evidence contract.

## Validation and qualification evidence

The separately tested candidate has 222 focused tests passing across 28 classes,
full main/test compilation and Java 8 checks on all 32 changed Java sources.
Independent reviews cover original defect reproductions, held map calculations,
positive-flow native Cv, consumed work, mass/component/energy ledgers, source
identity, native archive reload and execution, DEXPI schema/profile, strict layout
and visual inspection of every sheet.

Fourteen default fault profiles and fixed-map time-step checks preserve native
PASS, FAIL and partial results. Changing only canonical ASC gain 400 to 20 reaches
actual readback, changes valve motion and fails surge-margin acceptance; stale
receipts are rejected. This demonstrates policy causality, not plant qualification.

Require independent human review, exact-head CI and resolution of the
compatibility decision before merge. Passing automated checks cannot substitute
for accountable engineering approval.

## Alternatives considered

- Diagram-only proposals do not prove actual process or actuator behavior.
- A second standalone control solver would duplicate existing native semantics.
- Reusing an initialized mutable scenario as the next source risks inherited
  inventory, fault and controller state.
- Suppressing failed criteria, replacing undefined values with zero, relaxing
  limits or extrapolating an unqualified map would hide the acceptance boundary.

## Rollback strategy

Keep the proposal and implementation separate. Before release, revert the single
implementation commit if owners reject its compatibility or execution contract.
Do not reinterpret new configuration/evidence files using older readers. Preserve
historical failed test and study evidence; a code revert does not qualify a plant.

## Decision record

- 2026-11-07: PROPOSED. No owner acceptance, independent human approval, release
  decision or industrial qualification has been recorded.
