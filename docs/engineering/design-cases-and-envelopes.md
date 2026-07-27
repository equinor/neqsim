---
title: "Engineering Design Cases and Governing Envelopes"
description: "Guide to defining controlled NeqSim engineering cases, metrics, acceptance limits, isolated execution, and governing design envelopes."
keywords: "engineering design cases, governing envelope, design basis, maximum production, turndown, engineering metrics"
---

# Engineering Design Cases and Governing Envelopes

An engineering result is meaningful only when its governing condition is known. NeqSim design cases bind controlled
input changes to isolated process simulations, evaluate declared metrics, and retain the case that governs each value.

## Select the correct case model

| Case family | Typical examples | Recommended representation |
| --- | --- | --- |
| Normal operation | expected feed, ambient, utilities, and setpoints | Steady `EngineeringDesignCase` |
| Capacity | maximum production, rich/lean composition, high inlet pressure | Steady `EngineeringDesignCase` |
| Turndown | minimum stable rate, low pressure, minimum utility demand | Steady `EngineeringDesignCase` plus control/operability checks |
| Environmental or utility envelope | hot/cold ambient, cooling-medium limits, reduced utility capacity | Steady case when equilibrium is sufficient; dynamic case when response matters |
| Startup and shutdown | inventory establishment, sequencing, heating/cooling, recycle transitions | Dynamic scenario with initial conditions and sequence evidence |
| Equipment trip and settle-out | compressor trip, pump trip, blocked train, pressure equalization | Dynamic scenario; use a steady settle-out calculation only for its valid endpoint |
| Fire, blowdown, or blocked outlet | relief load, depressurization, minimum temperature, flare concurrency | Controlled safety scenario and dynamic/relief calculation |

The `EngineeringDesignCase.Type` taxonomy includes steady and accidental names so cases can be registered consistently.
The name does not make the calculation method credible: a case called `FIRE` that only changes a steady feed rate is
not a fire analysis.

## Minimum controlled case definition

Every case should retain:

- stable `id`, name, type, and case group;
- whether it is required and enabled;
- case-specific input values with units;
- an evidence reference for each controlled input;
- approval or review status;
- priority and intended engineering use; and
- convergence and acceptance requirements.

Example:

```java
EngineeringDesignCase maximum = new EngineeringDesignCase(
    "CASE-MAX",
    "Maximum production",
    EngineeringDesignCase.Type.MAXIMUM_PRODUCTION,
    scenario -> ((Stream) scenario.getUnit("20-FEED-001"))
        .setFlowRate(1.15e6, "kg/hr"))
    .setCaseGroup("PRODUCTION")
    .setPriority(20)
    .setApprovalStatus("REVIEW_REQUIRED")
    .addInput(new EngineeringDesignCase.Input(
        "feed rate", 1.15e6, "kg/hr", "PROCESS-DESIGN-BASIS-REV-A"));

project.addDesignCase(maximum);
```

Use the configurator only for the declared case change. The runner applies it to an isolated `ProcessSystem.copy()`;
it should not access or mutate the controlled source process through another reference.

## Define metrics before running cases

An `EngineeringMetric` defines:

- a stable metric and subject identity;
- display name and unit;
- an extractor evaluated on each case copy;
- governing direction: `MAXIMUM`, `MINIMUM`, or `MAXIMUM_ABSOLUTE`; and
- optional lower and upper acceptance limits.

Built-in factories cover common process and rotating-equipment quantities, including:

- equipment pressure, temperature, inlet mass/volume flow, density, and pressure drop;
- compressor power, head, speed, efficiency, discharge temperature, and chart extrapolation;
- surge, stonewall, and anti-surge control-line margins;
- required recycle fraction and recycle-cooler duty; and
- pump power/NPSH and heater duty metrics where applicable.

```java
project.addEngineeringMetric(EngineeringMetric.equipmentPressure("20-VG-001"));
project.addEngineeringMetric(EngineeringMetric.equipmentTemperature("20-VG-001"));
project.addEngineeringMetric(EngineeringMetric.equipmentInletMassFlow("20-VG-001"));
project.addEngineeringMetric(EngineeringMetric.compressorPower("20-KA-001"));
project.addEngineeringMetric(EngineeringMetric.compressorSurgeMargin("20-KA-001"));
```

Choose the direction from the engineering question. Maximum pressure can govern a pressure rating, minimum density can
govern a gas-velocity check, and minimum surge margin can govern compressor operability. A single generic maximum does
not describe all design limits.

## Execute isolated cases

`EngineeringCaseRunner` executes enabled cases on independent process copies. `EngineeringCaseRunOptions` controls
parallelism, whether convergence is required, and incomplete-result propagation. The default
`RETURN_PARTIAL` policy retains all available evidence. `THROW_WITH_PARTIAL_RESULT` raises an
`EngineeringCaseExecutionException`; its `getPartialReport()` preserves the same auditable case and
metric findings. The report retains definition and result fingerprints so a case-set change can be
distinguished from a result change.

Case status remains explicit:

| Status | Meaning |
| --- | --- |
| `CALCULATED` | Process converged and all declared metrics were evaluated |
| `CALCULATED_WITH_METRIC_FAILURES` | Process ran but at least one metric could not be evaluated |
| `CALCULATED_NOT_CONVERGED` | A result exists but convergence requirements were not satisfied |
| `FAILED` | Case configuration or simulation failed |
| `SKIPPED` | Case was disabled or intentionally excluded |

Successful metrics from a partial case may still identify a candidate governing value, but the
envelope remains explicitly incomplete. Failed/non-converged cases and metrics with no governing
value remain visible and must be resolved before a complete report can be required.

## Interpret the governing envelope

For every successfully evaluated metric, `EngineeringDesignEnvelope` selects the value according to its governing
direction and retains:

- metric identity, subject, name, and unit;
- governing case ID and case name;
- governing value;
- acceptance-limit status and margin; and
- case summary counts and violations.

The envelope is a selection result, not an approval. Review it for physical discontinuities, phase changes,
extrapolation, unexpected governing cases, and missing coverage.

`isComplete()` means every configured metric has a governing value and no case is failed or partial.
It does not mean limits pass. `isAccepted()` is deliberately stricter: the envelope must be complete,
every governing metric must have at least one configured acceptance limit, and no evaluated case may
violate a limit. Therefore an otherwise successful envelope with unconfigured limits remains
review-required rather than reporting a false acceptance.

## Connect cases to the closed design loop

The process-to-engineering simulator repeatedly:

1. runs the controlled cases;
2. builds the governing envelope;
3. proposes sizes or ratings from that envelope;
4. applies selected values to an isolated design copy;
5. reruns the cases and constraints; and
6. stops only when physical variables and process results are stable.

If a selected pipe diameter, separator geometry, valve Cv, or exchanger area changes the process hydraulics, the old
envelope is stale. The rerun is what closes the engineering loop.

Within each iteration, declared module dependencies are evaluated as topological levels. A downstream
module sees upstream selections from that same iteration. Conflicting proposals for one state key are
rejected unless every proposer declares the same governing maximum or minimum rule; the loop never
uses module list order as an implicit engineering-selection rule.

## Case-coverage review

Use a case matrix to review equipment and metric coverage:

| Review question | Required evidence |
| --- | --- |
| Are all required cases present? | Case IDs, types, required/enabled state, and controlled basis |
| Did every required case converge? | Per-case process and unit run status |
| Did every metric evaluate? | Per-case metric status and units |
| Is the envelope complete? | No failed/partial case and no missing governing metric ID |
| Is the governing direction correct? | Engineering rationale for maximum/minimum/absolute selection |
| Are limits explicit and accepted? | Lower/upper limits, units, source, margin, and `isAccepted()` state |
| Are accidental cases modeled with adequate physics? | Dynamic/safety method, initial conditions, sequence, and credibility reference |
| Is the envelope still current? | Definition fingerprint, result fingerprint, process revision, and design-loop iteration |

## Common mistakes

- Treating normal operation as the only design case.
- Encoding several physical changes in a case without recording each input and source.
- Calling a steady-state perturbation startup, fire, trip, or blowdown.
- Selecting all governing quantities by maximum value.
- Ignoring failed cases when publishing a clean envelope.
- Applying selected dimensions to the controlled source model rather than an isolated design copy.
- Reusing an envelope after a geometry, method, composition, or design-basis revision.

## Related documentation

- [Engineering Guide](guide)
- [Engineering Simulator Foundations](../integration/engineering-simulator-foundations)
- [Process-to-Engineering Simulator](../integration/process-to-engineering-simulator)
- [Numerical Health and Engineering Closure](../integration/numerical-health-and-engineering-closure)
- [Complete Offshore Engineering Study](../integration/complete-offshore-process-engineering-study)
