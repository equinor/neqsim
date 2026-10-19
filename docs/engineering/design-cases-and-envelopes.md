---
title: "Engineering Design Cases and Governing Envelopes"
description: "Guide to defining controlled NeqSim engineering cases, metrics, acceptance limits, isolated execution, and governing design envelopes."
keywords: "engineering design cases, governing envelope, design basis, maximum production, turndown, engineering metrics"
---

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

## Complete executable example

This Java 8 example creates a controlled methane feed, evaluates normal and maximum absolute-pressure cases, and
retains the governing value without mutating the source process. Temperature is in K, pressure is absolute bara, and
mass flow is in kg/h.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringCaseRunner;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public final class DesignCaseEnvelopeExample {
  private static final Logger logger = LogManager.getLogger(DesignCaseEnvelopeExample.class);

  private DesignCaseEnvelopeExample() {}

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem sourceProcess = new ProcessSystem();
    sourceProcess.add(feed);
    sourceProcess.run();
    double sourcePressureBara = feed.getPressure("bara");

    EngineeringDesignCase normal = pressureCase(
        "CASE-NORMAL", "Normal production", EngineeringDesignCase.Type.NORMAL, 50.0, 10);
    EngineeringDesignCase maximum = pressureCase(
        "CASE-MAX", "Maximum production", EngineeringDesignCase.Type.MAXIMUM_PRODUCTION, 80.0, 20);

    EngineeringMetric pressure = EngineeringMetric.equipmentPressure("20-FEED-001")
        .setAcceptanceRange(null, Double.valueOf(90.0));
    EngineeringCaseSet caseSet = new EngineeringCaseSet("feed-pressure-envelope")
        .addCase(normal)
        .addCase(maximum)
        .addMetric(pressure);

    EngineeringCaseRunReport report = EngineeringCaseRunner.run(
        sourceProcess, caseSet, EngineeringCaseRunOptions.sequential());
    EngineeringDesignEnvelope.GoverningValue governing = report.getEnvelope()
        .getGoverningValues()
        .get("20-FEED-001.pressure");

    assert report.isComplete();
    assert report.isAccepted();
    assert governing != null;
    assert "CASE-MAX".equals(governing.getDesignCaseId());
    assert Math.abs(governing.getValue() - 80.0) < 1.0e-10;
    assert Math.abs(feed.getPressure("bara") - sourcePressureBara) < 1.0e-10;

    logger.info("governingCase={} governingPressure={} bara accepted={}",
        governing.getDesignCaseId(), governing.getValue(), report.isAccepted());
  }

  private static EngineeringDesignCase pressureCase(String id, String name,
      EngineeringDesignCase.Type type, final double pressureBara, int priority) {
    return new EngineeringDesignCase(id, name, type,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            ((Stream) process.getUnit("20-FEED-001")).setPressure(pressureBara, "bara");
          }
        })
        .setCaseGroup("PRODUCTION")
        .setPriority(priority)
        .setApprovalStatus("REVIEW_REQUIRED")
        .addInput(new EngineeringDesignCase.Input(
            "feed pressure", pressureBara, "bara", "PROCESS-DESIGN-BASIS-REV-A"));
  }
}
```

The documentation regression compiles this exact fence with `-source 8 -target 8` and executes it with assertions
enabled. The example uses pressure only to make isolation and governing-case selection explicit; a real design basis
normally includes flow, composition, ambient, utilities, equipment limits, and credible accidental scenarios.
The exact example and the policy boundaries below are covered by
[EngineeringCaseRunnerTest](../../src/test/java/neqsim/process/engineering/designcase/EngineeringCaseRunnerTest.java).

## Minimum controlled case definition

Every case should retain:

- stable `id`, name, type, and case group;
- whether it is required and enabled;
- case-specific input values with units;
- an evidence reference for each controlled input;
- approval or review status;
- priority and intended engineering use; and
- convergence and acceptance requirements.

Use the configurator only for the declared case change. The runner applies it to an isolated `ProcessSystem.copy()`;
it should not access or mutate the controlled source process through another reference.

The runner records `required` and approval metadata, but does not enforce them as execution or acceptance
gates. Priority controls case ordering, as described below. The example's `REVIEW_REQUIRED` cases can produce
`isAccepted() == true`: this is metric-limit
acceptance, not engineering approval. Review required-case coverage and approval separately before using an envelope
as a controlled design basis.

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

Choose the direction from the engineering question. Maximum pressure can govern a pressure rating, minimum density can
govern a gas-velocity check, and minimum surge margin can govern compressor operability. A single generic maximum does
not describe all design limits.

## Execute isolated cases

`EngineeringCaseRunner` executes enabled cases on independent process copies. `EngineeringCaseRunOptions` controls
parallelism, whether convergence is required, and incomplete-result propagation. The default `RETURN_PARTIAL` policy
retains all available evidence. `THROW_WITH_PARTIAL_RESULT` raises an `EngineeringCaseExecutionException`; its
`getPartialReport()` preserves the same auditable case and metric findings. The report retains definition and result
fingerprints so a case-set change can be distinguished from a result change.

Convergence is required by default. With `requireConvergence(false)`, the runner deliberately admits evaluated values
even when `ProcessSystem.solved()` is false. In that mode, `DesignCaseResult.isConverged()` records satisfaction of the
configured policy, not independent evidence of physical convergence; a fully evaluated case can be `CALCULATED` and
its envelope can be accepted. Do not use this bypass to qualify an unsolved process for engineering design.

Case status remains explicit:

| Status | Meaning |
| --- | --- |
| `CALCULATED` | The configured convergence policy is satisfied and all declared metrics were evaluated |
| `CALCULATED_WITH_METRIC_FAILURES` | The convergence policy is satisfied, but only some metrics were evaluated |
| `CALCULATED_NOT_CONVERGED` | At least one metric value exists, but required process convergence was not reported |
| `FAILED` | Case execution failed, or all metric evaluations failed |
| `SKIPPED` | Case was disabled; its configurator and metrics were not executed |

Successful metrics from a partial case may still identify a candidate governing value, but the envelope remains
explicitly incomplete. Failed/non-converged cases and metrics with no governing value remain visible and must be
resolved before a complete report can be required.

The failure policy responds to an incomplete report, not to every omission or engineering-review concern.
`THROW_WITH_PARTIAL_RESULT` does not reject a complete but limit-violating report; check `isAccepted()` separately.

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

Equal governing candidates retain the first case in `EngineeringCaseSet.getCases()` order: ascending numeric
priority, then lexicographic case ID, not insertion order or approval status. This also applies to equal absolute
magnitudes under `MAXIMUM_ABSOLUTE`. Sequential and parallel runs preserve the same selection order. Priority only
breaks equal-value ties; it does not override a more extreme metric value.

`isComplete()` means every configured metric has a governing value and no case is failed or partial. It does not mean
limits pass. `isAccepted()` is deliberately stricter: the envelope must be complete, every governing metric must have
at least one configured acceptance limit, and no evaluated case may violate a limit. Therefore an otherwise successful
envelope with unconfigured limits remains review-required rather than reporting a false acceptance.

A disabled required case is `SKIPPED`, not failed or partial. If another enabled case supplies every metric, that
omission does not prevent completeness or acceptance. If all cases are disabled, configured metrics have no governing
values and the report is incomplete. Conversely, an enabled case marked `required=false` still contributes failures
and limit violations: optional metadata does not remove it from the calculation. Check the required/enabled case
matrix separately; `isComplete()` alone is not a case-coverage certificate.

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

Within each iteration, declared module dependencies are evaluated as topological levels. A downstream module sees
upstream selections from that same iteration. Conflicting proposals for one state key are rejected unless every
proposer declares the same governing maximum or minimum rule; the loop never uses module list order as an implicit
engineering-selection rule.

## Case-coverage review

Use a case matrix to review equipment and metric coverage:

| Review question | Required evidence |
| --- | --- |
| Are all required cases present? | Case IDs, types, required/enabled state, and controlled basis |
| Did every required case converge? | Per-case process and unit run status, with convergence checking enabled |
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
- Treating skipped required cases, a convergence bypass, or unreviewed approval metadata as design approval.
- Applying selected dimensions to the controlled source model rather than an isolated design copy.
- Reusing an envelope after a geometry, method, composition, or design-basis revision.

## Related documentation

- [Engineering Guide](guide.md)
- [Engineering Simulator Foundations](../integration/engineering-simulator-foundations.md)
- [Process-to-Engineering Simulator](../integration/process-to-engineering-simulator.md)
- [Numerical Health and Engineering Closure](../integration/numerical-health-and-engineering-closure.md)
- [Complete Offshore Engineering Study](../integration/complete-offshore-process-engineering-study.md)
