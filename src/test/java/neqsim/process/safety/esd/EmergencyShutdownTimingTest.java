package neqsim.process.safety.esd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Qualifies the physical-step clock and measured final-element response for offline shutdown tests.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class EmergencyShutdownTimingTest {
  /** The process, logic and evidence clocks must agree across a fractional trigger and final step. */
  @Test
  void alignsLogicAndProcessStepsAtTriggerAndEnd() {
    List<Double> logicSteps = new ArrayList<Double>();
    ESDLogic logic = new ESDLogic("ESD-001") {
      private static final long serialVersionUID = 1L;

      @Override
      public void execute(double dt) {
        logicSteps.add(dt);
      }
    };
    List<Double> physicalSteps = new ArrayList<Double>();
    List<UUID> ids = new ArrayList<UUID>();
    ProcessSystem process = new ProcessSystem() {
      private static final long serialVersionUID = 1L;

      @Override
      public void runTransient(double dt, UUID id) {
        physicalSteps.add(dt);
        ids.add(id);
      }
    };
    EmergencyShutdownTestPlan plan = EmergencyShutdownTestPlan.builder("Fractional scan").initializeSteadyState(false)
        .timeStep(1.0).duration(2.75).triggerTime(1.25).build();
    EmergencyShutdownTestResult result = EmergencyShutdownTestRunner.run(process, plan, logic);
    assertEquals(Arrays.asList(1.0, .25, 1.0, .5), physicalSteps);
    assertEquals(Arrays.asList(1.0, .5), logicSteps);
    assertEquals(4, new HashSet<UUID>(ids).size());
    assertEquals(2.75, result.getTimeSeries().get(4).getTimeSeconds(), 0.0);
  }

  /** A rejected step ends the test without a fabricated end-of-step observation. */
  @Test
  void failedStepNeverProducesEvidenceAtFutureTime() {
    ProcessSystem process = new ProcessSystem() {
      private static final long serialVersionUID = 1L;

      @Override
      public void runTransient(double dt, UUID id) {
        throw new IllegalStateException("Rejected trial");
      }
    };
    EmergencyShutdownTestPlan plan = EmergencyShutdownTestPlan.builder("Rejected scan").initializeSteadyState(false)
        .timeStep(1.0).duration(3.0).build();
    EmergencyShutdownTestResult result = EmergencyShutdownTestRunner.run(process, plan);
    assertEquals(EmergencyShutdownTestResult.Verdict.FAIL, result.getVerdict());
    assertEquals(1, result.getTimeSeries().size());
    assertEquals(0.0, result.getTimeSeries().get(0).getTimeSeconds(), 0.0);
  }

  /** Late, briefly reopened, missing and invalid feedback cannot pass a response-time requirement. */
  @Test
  void measuresPersistentFeedbackFromTheTrigger() {
    EmergencyShutdownTestCriterion criterion = EmergencyShutdownTestCriterion.finalAtMost("closed", "ZT", 5, "%")
        .withResponseDeadline(1).withClause("case").withSeverity("HIGH").withText("Close", "Check feedback");
    assertTrue(check(criterion, 80., 100., 5., 0.).isPassed());
    assertFalse(check(criterion, 0., 100., 50., 0.).isPassed());
    assertFalse(check(criterion, 0., 0., 10., 0.).isPassed());
    assertFalse(check(criterion, 0., null, 0., 0.).isPassed());
    assertFalse(check(criterion, 0., 100., Double.NaN, 0.).isPassed());
    assertFalse(check(criterion, 0., 0.).isPassed());
    EmergencyShutdownTestCriterion open = EmergencyShutdownTestCriterion.finalAtLeast("open", "ZT", 95, "%")
        .withResponseDeadline(1);
    assertTrue(check(open, 0., 0., 95., 100.).isPassed());
    assertEquals("s", check(open, 0., 0., 95., 100.).toMap().get("unit"));
    assertEquals(1.0, check(open, 0., 0., 95., 100.).toMap().get("observedValue"));
    assertThrows(IllegalArgumentException.class, () -> open.withResponseDeadline(-1));
    assertThrows(IllegalStateException.class,
        () -> EmergencyShutdownTestCriterion.noSimulationErrors("ok").withResponseDeadline(1));
    assertThrows(IllegalArgumentException.class,
        () -> EmergencyShutdownTestPlan.builder("invalid").triggerTime(Double.NaN).build());
  }

  /**
   * Replays one-second feedback observations with a trigger at one second.
   *
   * @param criterion acceptance criterion
   * @param values sampled feedback; null represents an unavailable tag
   * @return acceptance result
   */
  private EmergencyShutdownTestCriterion.Result check(EmergencyShutdownTestCriterion criterion, Double... values) {
    EmergencyShutdownTestPlan plan = EmergencyShutdownTestPlan.builder("Feedback").triggerTime(1).build();
    EmergencyShutdownTestResult result = new EmergencyShutdownTestResult(plan);
    for (int i = 0; i < values.length; i++) {
      result.addSample(
          new EmergencyShutdownTestResult.SignalSample(i,
              values[i] == null ? Collections.<String, Double>emptyMap() : Collections.singletonMap("ZT", values[i])),
          null);
    }
    return criterion.evaluateRun(plan, result);
  }
}
