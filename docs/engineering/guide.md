---
title: "NeqSim Engineering Guide"
description: "A practical guide for progressing from a validated NeqSim process model through design cases, closed-loop engineering, discipline verification, and controlled handover."
keywords: "engineering guide, design workflow, governing envelope, equipment sizing, safety verification, engineering package"
---

# NeqSim Engineering Guide

This guide describes the recommended engineering path for a `ProcessSystem` or multi-area `ProcessModel`. It focuses
on what must be defined, executed, checked, and retained at each gate. For API detail, follow the linked specialist
documentation rather than treating this page as a replacement for discipline methods.

## Choose the workflow

| Starting point | Recommended route |
| --- | --- |
| Learning or method evaluation | Run the [focused engineering notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/process_to_engineering_simulator.ipynb) |
| One tagged process system | Use the [Process-to-Engineering Simulator](../integration/process-to-engineering-simulator) |
| Several process areas or shared systems | Use the [Process Model to Engineering Workflow](../integration/process-to-engineering-workflow) |
| Full offshore facility example | Run the [Complete Offshore Engineering Study](../integration/complete-offshore-process-engineering-study) |
| Open-ended engineering study with research and reporting | Use [Solve an Engineering Task](../tutorials/solve-engineering-task) |

## Gate 1: Establish a trustworthy process model

Begin with a tagged, converged model whose thermodynamic basis, feed definition, operating conditions, equipment
topology, recycle behavior, and unit conventions are explicit.

Minimum evidence:

- stable equipment and stream identities;
- mass and energy closure at the required tolerance;
- converged normal operation with no silently ignored unit failure;
- documented fluid characterization, equation of state, mixing rule, and property package;
- comparison against plant, laboratory, published, or controlled reference results; and
- a record of assumptions and known model limitations.

Do not begin sizing from a model that merely completes a run. Numerical convergence, physical closure, and benchmark
agreement are separate checks. See [Numerical Health and Engineering Closure](../integration/numerical-health-and-engineering-closure).

## Gate 2: Define controlled design cases

Create executable cases for the conditions that can be represented by steady process simulation, normally including
normal operation, maximum production, minimum turndown, relevant feed compositions, and hot/cold or high/low pressure
conditions. Each case should retain:

- a stable case identifier and type;
- the controlled source for every changed input;
- units and applicability limits;
- priority or governing intent; and
- acceptance criteria for convergence and constraints.

See [Design Cases and Governing Envelopes](design-cases-and-envelopes) for case construction, metric direction,
acceptance limits, status handling, and coverage review.

Startup, shutdown, compressor trip, settle-out, fire, blocked outlet, and depressurization are not made credible by
renaming a steady-state case. Represent them with the appropriate dynamic or safety-scenario model and link the
approved hazard-review basis.

Gate check: every design variable and calculated load can be traced to the case that governs it.

## Gate 3: Run isolated cases and create governing envelopes

Run cases on independent copies. This prevents one case or design update from contaminating another and preserves the
original process model as the controlled physics source.

Review:

- convergence and unit-level run status for every case;
- maximum and minimum pressure, temperature, flow, duty, power, liquid inventory, and composition as relevant;
- case-to-case discontinuities or unexpected phase changes;
- the governing case for each equipment item, line, valve, instrument, and safety load; and
- numerical warnings, extrapolation, and missing input evidence.

The [Engineering Simulator Foundations](../integration/engineering-simulator-foundations) describes the case runner,
readiness, provenance, and uncertainty contracts.

## Gate 4: Close the process/design loop

Use explicit discipline modules or an explicit auto-configuration policy to:

1. calculate candidate sizes and ratings from the governing envelope;
2. select discrete dimensions, schedules, drivers, ranges, or standard sizes;
3. apply the selected physical variables to an isolated design copy;
4. rerun hydraulics and process cases;
5. re-evaluate constraints; and
6. continue until both design variables and process results are stable.

Typical loop variables include separator diameter and length, pipe inside diameter, valve Cv, driver rating,
exchanger area, instrument range, pressure rating, and preliminary relief or blowdown targets.

Gate check:

- all cases converged after the final design update;
- physical and process-value changes satisfy declared tolerances;
- every declared constraint passes;
- no hidden screening default was used;
- all selected values retain method, units, governing case, and input evidence; and
- the source process model remains unchanged.

See [Process-to-Engineering Simulator](../integration/process-to-engineering-simulator) for configuration patterns,
extension points, and result contracts.

## Gate 5: Complete discipline verification

The common design loop coordinates shared inputs and dimensions; each discipline must still review its governing
methods, assumptions, and interfaces.

| Discipline | Principal checks | Documentation |
| --- | --- | --- |
| Process | heat and material balance, equipment loads, operability, utility demand | [Process Design Guide](../process/process_design_guide) |
| Mechanical | design pressure/temperature, material class, thickness, weight, nozzle and equipment constraints | [Mechanical Design](../process/mechanical_design) |
| Piping | diameter/schedule, velocity, pressure drop, rating, relief inlet loss, vibration and stress screens | [Topside Piping Design](../process/topside_piping_design) |
| Rotating | operating envelope, driver margin, surge/stonewall or NPSH, temperatures, recycle and transients | [Compressor Design Feasibility](../process/compressor_design_feasibility) |
| Valves and instruments | Cv/opening, choked or flashing service, range, uncertainty, response, failure action | [Instrument Design](../process/instrument-design) |
| Electrical | motor/VFD, cable, transformer, switchgear, load list, hazardous area | [Electrical Design](../process/electrical-design) |
| Materials | fluid exposure, corrosion/degradation mechanisms, material class and operating envelope | [Mechanical Design Standards](../process/mechanical_design_standards) |

Record unresolved assumptions as actions. Do not convert a screening correlation into an approved discipline method by
omitting the warning.

## Gate 6: Verify control, safety, and abnormal operation

Connect the designed process to the control and safeguarding model. Depending on scope, verify:

- control-loop response and actuator travel;
- compressor trip, anti-surge recycle, settle-out, and restart constraints;
- ESD isolation and safe-state timing;
- PSV capacity, inlet loss, backpressure, and two-phase method selection;
- blowdown duration, minimum metal/fluid temperature, hydrate risk, and flare concurrency;
- HIPPS or SIS voting, bypass/degraded modes, PFD/PFH, and proof-test assumptions; and
- consequence, escalation, and facility-response assumptions where required.

Credible scenarios, simultaneous-event groups, fire zones, safeguards, SIL targets, and acceptance decisions must come
from the controlled safety lifecycle. NeqSim can calculate and retain evidence; it cannot approve that evidence.

Start with [Safety Documentation](../safety/) and the
[safety integration section](../integration/process-to-engineering-simulator#safety-and-scenario-integration).

## Gate 7: Compile and review the engineering package

Compile deliverables from the designed process copy and canonical engineering graph. A coordinated package can
include:

- process design basis and case matrix;
- governing envelopes and calculation evidence;
- equipment, line, valve, instrument, I/O, alarm, trip, and relief registers;
- equipment and PSV datasheets;
- utility, materials, flare, and blowdown summaries;
- DEXPI PFD/P&ID exchange files and validation reports;
- unresolved-action, evidence, revision-impact, and approval registers; and
- package manifests and content hashes.

Review package consistency before issue: tags, units, revision, governing cases, calculated values, graph identities,
and unresolved actions must agree across artifacts. See [DEXPI Engineering Generation](../integration/dexpi-engineering-generation)
and [Engineering Deliverables and Handover](deliverables-and-handover). Use the
[DEXPI Engineering Guide](dexpi-guide) to select and qualify the correct exchange profile.

## Gate 8: Manage revisions and stale evidence

When the process model, design basis, method, or evidence changes:

1. issue a controlled change event;
2. compare the new model/package with the approved baseline;
3. traverse dependencies to identify affected calculations and deliverables;
4. invalidate stale validation and approval states;
5. rerun only the justified scope while retaining unchanged evidence; and
6. issue a revision-impact report with accountable actions.

Use [Model Change Events](../process/model-change-events) and
[Model Impact Analysis](../process/model-impact-analysis) for the lifecycle workflow.

## Review-ready versus construction-ready

| Status | Meaning |
| --- | --- |
| Calculated | A method executed and produced a traceable result |
| Internally validated | Numerical, benchmark, and consistency checks passed within the declared scope |
| Review-ready | Inputs, methods, findings, warnings, and actions are organized for accountable engineering review |
| Approved | The authorized discipline or safety-lifecycle role accepted the controlled evidence |
| Fit for construction | All required detailed design, vendor, independent, regulatory, and construction-authority gates are closed |

NeqSim engineering outputs should normally be treated as calculated, internally validated, or review-ready. The
package must fail closed rather than infer approval or fitness for construction from successful simulation.

## Final checklist

- [ ] Process model is tagged, converged, closed, and benchmarked.
- [ ] Design and abnormal cases are explicit, controlled, and correctly modeled.
- [ ] Governing cases are retained per calculated quantity.
- [ ] The process/design loop converged on an isolated copy.
- [ ] Discipline methods, assumptions, units, and constraints are traceable.
- [ ] Control and safety scenarios have controlled credibility and acceptance bases.
- [ ] Deliverables agree on tags, values, revision, and evidence state.
- [ ] Missing vendor, independent, HAZOP/LOPA/SRS, authority, or approval evidence remains visible.
- [ ] Model changes invalidate and regenerate the correct dependent evidence.

Return to the [Engineering documentation hub](./).
