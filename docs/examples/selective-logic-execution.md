---
title: "Selective Logic Execution with ProcessScenarioRunner"
description: "Executable Java example for registering process logic and running one named subset in a bounded NeqSim scenario."
---

Use `ProcessScenarioRunner` when one process model has several registered logic
sequences but a scenario should exercise only a named subset. Selection controls
which registered sequences the runner advances; it does not activate a sequence,
prove that a safeguard is adequate, or replace a functional-safety test plan.

## Engineering question

Can one high-pressure screening scenario execute `HIPPS Protection` while leaving
the separately registered `ESD Level 1` logic idle, and can the software result be
checked without confusing it with safety-system qualification?

The example below uses one gas feed because the contract under test is logic
selection. Pressure is in bar absolute (`bara`), flow is in `kg/hr`, temperature is
in degrees Celsius, and both scenario duration and time step are in seconds.

## Executable selective-logic example

Save the program as `SelectiveLogicScenarioExample.java`, compile it with the
NeqSim and Log4j2 dependencies on the class path, and enable Java assertions when
running it (`java -ea ... SelectiveLogicScenarioExample`).

```java
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.util.scenario.ProcessScenarioRunner;
import neqsim.process.util.scenario.ScenarioExecutionSummary;
import neqsim.process.util.scenario.ScenarioExecutionSummary.LogicResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class SelectiveLogicScenarioExample {
  private static final Logger logger =
      LogManager.getLogger(SelectiveLogicScenarioExample.class);

  private SelectiveLogicScenarioExample() {}

  public static void main(String[] args) {
    SystemInterface gas = new SystemSrkEos(298.15, 55.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(55.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();

    ESDLogic hippsLogic = new ESDLogic("HIPPS Protection");
    ESDLogic esdLogic = new ESDLogic("ESD Level 1");

    ProcessScenarioRunner runner = new ProcessScenarioRunner(process);
    runner.addLogic(hippsLogic);
    runner.addLogic(esdLogic);

    ProcessSafetyScenario highPressure =
        ProcessSafetyScenario.builder("High-pressure screen")
            .customManipulator(
                "Feed",
                equipment -> {
                  if (!(equipment instanceof Stream)) {
                    throw new IllegalStateException("Feed must be a Stream");
                  }
                  ((Stream) equipment).setPressure(60.0, "bara");
                })
            .build();

    assert runner.getLogicSequences().size() == 2;
    assert runner.activateLogic("HIPPS Protection");

    ScenarioExecutionSummary summary =
        runner.runScenarioWithLogic(
            "HIPPS-only software screen",
            highPressure,
            1.0,
            1.0,
            Collections.singletonList("HIPPS Protection"));

    Map<String, LogicResult> results = summary.getLogicResults();
    double finalPressureBara = feed.getPressure("bara");
    double finalFlowKgPerHour = feed.getFlowRate("kg/hr");

    assert summary.isSuccessful();
    assert summary.getErrors().isEmpty();
    assert results.size() == 1;
    assert results.containsKey("HIPPS Protection");
    assert !results.containsKey("ESD Level 1");
    assert hippsLogic.getState() == LogicState.COMPLETED;
    assert esdLogic.getState() == LogicState.IDLE;
    assert Double.isFinite(finalPressureBara);
    assert Math.abs(finalPressureBara - 60.0) < 1.0e-10;
    assert Double.isFinite(finalFlowKgPerHour) && finalFlowKgPerHour > 0.0;

    logger.info(
        "Scenario {} selected {} logic result at {} bara and {} kg/hr",
        summary.getScenarioName(),
        results.size(),
        finalPressureBara,
        finalFlowKgPerHour);
  }
}
```

The two `ESDLogic` instances intentionally contain no equipment actions. That keeps
the example focused on registration, activation, and named-subset execution: the
selected sequence advances from `RUNNING` to `COMPLETED`, while the unselected
sequence remains `IDLE`. Add validated actions only after the selection behavior is
understood.

## Selection and state semantics

1. `addLogic(...)` registers a sequence; it does not activate it.
2. `activateLogic(name)` changes the matching sequence to an active state.
3. `runScenarioWithLogic(..., names)` advances only registered sequences whose names
   appear in `names`.
4. A `null` or empty name list means all registered logic, not no logic.
5. `reset()` resets all registered logic, renews the simulation identifier, and reruns
   the process steady state. Use it before another scenario only when that shared-state
   behavior is intended.
6. `clearAllLogic()` removes registrations; it is not equivalent to resetting their
   internal states.

Treat names as configuration identifiers. Duplicate or misspelled names can make a
screen exercise a different subset than intended, so check the returned logic-result
map and fail closed if an expected name is absent.

## Engineering boundary

This example proves software selection and execution behavior for one bounded model.
It does not prove HIPPS or ESD demand coverage, voting architecture, SIL/PFD,
independence, common-cause treatment, response time, valve capacity, final-element
travel, pressure protection, relief adequacy, proof testing, alarm management,
operator response, or compliance with a governing standard. A real study needs a
validated dynamic process model, traceable trip set points and delays, physical final
elements, failure modes, acceptance criteria, sensitivity cases, and accountable
process- and functional-safety review.

## Related documentation

- [Process logic framework](../simulation/process_logic_framework.md)
- [ESD testing workflow](../safety/esd_testing_workflow.md)
- [Scenario generation](../process/safety/scenario-generation.md)
- [Troubleshooting](../troubleshooting/index.md)
