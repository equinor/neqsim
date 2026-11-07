package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Checks the public strict project execution path and retained native failure provenance. */
class EngineeringSimulationRunnerStrictTest {
  /** Failed configured-copy preflight is retained without running a time-zero initiating event. */
  @Test
  void strictProjectRunRecordsFailedPreflightWithoutAdoptingForeignResults() {
    EngineeringProject project = project();
    final AtomicInteger events = new AtomicInteger();
    project.addDynamicSafetyScenario(DynamicSafetyScenario.builder("STRICT-PROJECT", "Unsupported adaptive mode")
        .durationSeconds(1.0).timeStepSeconds(0.1).triggerTimeSeconds(0.0)
        .configureCase("UNSUPPORTED-ADAPTIVE-v1", process -> process.setAdaptiveTimestepEnabled(true))
        .initiatingEvent("EVENT-v1", process -> events.incrementAndGet())
        .addLogic("UNEXECUTED-LOGIC-v1", process -> new ESDLogic("Unexecuted logic")).addEquipmentTag("FEED")
        .addCriterion(DynamicScenarioCriterion
            .builder("positive", "Positive feed pressure", "bara", "PRESSURE-v1",
                process -> process.getUnit("FEED").getPressure())
            .acceptanceRange(Double.valueOf(0.0), null).deadlineSeconds(1.0).build())
        .build());
    EngineeringSimulationResult result = EngineeringSimulationRunner.runStrict(project);
    assertSame(result, project.getLatestEngineeringSimulationResult());
    assertEquals(1, result.getDynamicScenarioResults().size());
    DynamicSafetyScenarioResult scenario = result.getDynamicScenarioResults().get(0);
    assertFalse(scenario.isPassed());
    assertEquals("STRICT_PREFLIGHT_FAILED", scenario.getTimingPolicy());
    assertEquals(0.0, scenario.getCompletedDurationSeconds(), 0.0);
    assertEquals(0, events.get());
    assertTrue(result.hasFailedDynamicScenario());
    assertFalse(project.getEngineeringProcessSystem().isAdaptiveTimestepEnabled());
  }

  /** @return small converged native project with no external result injection */
  private static EngineeringProject project() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    return NorsokOffshoreEngineeringBuilder.from("Strict native project", process).projectId("STRICT-PROJECT")
        .registerProposedInstruments(false).build().setRevision("A");
  }
}
