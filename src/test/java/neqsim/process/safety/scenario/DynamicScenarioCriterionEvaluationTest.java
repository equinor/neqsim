package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.scenario.DynamicScenarioCriterion.EvaluationMode;

/**
 * Discriminates sampled invariants from reaching a safe state once by a deadline.
 *
 * @author ESOL
 * @version 1.0
 */
class DynamicScenarioCriterionEvaluationTest {
  /** Preserves the documented legacy reach-by-deadline behaviour. */
  @Test
  void defaultReachModeDoesNotClaimAnInvariant() {
    DynamicScenarioCriterion criterion = builder().deadlineSeconds(10.0).build();
    DynamicSafetyScenarioResult.CriterionResult result = new DynamicSafetyScenarioResult.CriterionResult(criterion);
    result.sample(0.0, 40.0);
    result.sample(5.0, 50.0);
    result.sample(10.0, 40.0);
    assertEquals(EvaluationMode.REACH_BY_DEADLINE, criterion.getEvaluationMode());
    assertTrue(result.isPassed());
  }

  /** Retains an intermediate violation even when the process recovers before the end. */
  @Test
  void maintainModeRejectsTransientViolationAndRecovery() {
    DynamicSafetyScenarioResult.CriterionResult result = maintained();
    result.sample(0.0, 40.0);
    result.sample(5.0, 50.0);
    result.sample(10.0, 40.0);
    assertFalse(result.isPassed());
    assertEquals(Double.valueOf(5.0), result.getFirstViolationSeconds());
    Map<String, Object> evidence = result.toMap();
    assertEquals(Double.valueOf(50.0), evidence.get("monitoredMaximum"));
    assertEquals(Double.valueOf(40.0), evidence.get("monitoredMinimum"));
    assertEquals(Integer.valueOf(3), evidence.get("monitoredSampleCount"));
  }

  /** Requires observed coverage of both closed-window boundaries. */
  @Test
  void completeBoundedWindowPasses() {
    DynamicSafetyScenarioResult.CriterionResult result = maintained();
    result.sample(0.0, 40.0);
    result.sample(5.0, 43.0);
    result.sample(10.0, 42.0);
    assertTrue(result.isPassed());
    assertNull(result.getFirstViolationSeconds());
    assertEquals(Boolean.TRUE, result.toMap().get("windowStartObserved"));
    assertEquals(Boolean.TRUE, result.toMap().get("windowEndObserved"));
  }

  /** An early stop cannot pass merely because all available samples look safe. */
  @Test
  void incompleteWindowFails() {
    DynamicSafetyScenarioResult.CriterionResult result = maintained();
    result.sample(0.0, 40.0);
    result.sample(9.0, 40.0);
    assertFalse(result.isPassed());
    assertEquals(Boolean.FALSE, result.toMap().get("windowEndObserved"));
  }

  /** Does not interpolate a missing event-time boundary. */
  @Test
  void missingStartFails() {
    DynamicSafetyScenarioResult.CriterionResult result = maintained();
    result.sample(0.1, 40.0);
    result.sample(10.0, 40.0);
    assertFalse(result.isPassed());
  }

  /** Does not interpolate a missing end-time boundary from a later safe sample. */
  @Test
  void steppingPastTheEndDoesNotInventCoverage() {
    DynamicSafetyScenarioResult.CriterionResult result = maintained();
    result.sample(0.0, 40.0);
    result.sample(10.1, 40.0);
    assertFalse(result.isPassed());
  }

  /** Makes the finite declared event-relative window explicit. */
  @Test
  void outsideWindowValuesAreNotInsideWindowEvidence() {
    DynamicSafetyScenarioResult.CriterionResult result = maintained();
    result.sample(-1.0, 100.0);
    result.sample(0.0, 40.0);
    result.sample(10.0, 41.0);
    result.sample(11.0, 100.0);
    assertTrue(result.isPassed());
    assertEquals(Integer.valueOf(2), result.toMap().get("monitoredSampleCount"));
    assertEquals(Double.valueOf(41.0), result.toMap().get("monitoredMaximum"));
    assertEquals("SAMPLED_ONLY_NO_INTERPOLATION", result.toMap().get("observationCoverage"));
  }

  /** Retains inclusive lower and upper acceptance bounds. */
  @Test
  void bothInclusiveBoundsAreEnforced() {
    DynamicScenarioCriterion criterion = builder().acceptanceRange(39.0, 43.0).deadlineSeconds(10.0)
        .evaluationMode(EvaluationMode.MAINTAIN_BOUNDS).build();
    DynamicSafetyScenarioResult.CriterionResult result = new DynamicSafetyScenarioResult.CriterionResult(criterion);
    result.sample(0.0, 39.0);
    result.sample(10.0, 43.0);
    assertTrue(result.isPassed());
    result.sample(10.0, 38.999);
    assertFalse(result.isPassed());
  }

  /** Rejects undefined mode or a zero-length maintained window. */
  @Test
  void invalidMaintainedPolicyFailsBeforeExecution() {
    assertThrows(IllegalArgumentException.class, () -> builder().evaluationMode(null));
    assertThrows(IllegalStateException.class, () -> builder().evaluationMode(EvaluationMode.MAINTAIN_BOUNDS).build());
  }

  /** Keeps start/end coverage disjoint even when the entire window is shorter than a nanosecond. */
  @Test
  void tinyWindowRequiresActualTemporalCoverage() {
    DynamicScenarioCriterion criterion = builder().deadlineSeconds(5.0e-10)
        .evaluationMode(EvaluationMode.MAINTAIN_BOUNDS).build();
    DynamicSafetyScenarioResult.CriterionResult result = new DynamicSafetyScenarioResult.CriterionResult(criterion);
    result.sample(0.0, 40.0);
    assertFalse(result.isPassed());
    result.sample(0.0, 40.0);
    assertFalse(result.isPassed(), "Repeated start samples cannot invent an observed end");
    result.sample(5.0e-10, 40.0);
    assertTrue(result.isPassed());
  }

  /**
   * Creates a controlled observation definition for unit-level policy tests.
   *
   * @return pressure upper-limit builder
   */
  private static DynamicScenarioCriterion.Builder builder() {
    return DynamicScenarioCriterion.builder("PRESSURE", "Sampled pressure", "bara", "POLICY_TEST_V1", p -> 40.0)
        .acceptanceRange(null, Double.valueOf(43.0));
  }

  /**
   * Creates an invariant policy result over the closed interval from zero to ten seconds.
   *
   * @return empty result to receive controlled observations
   */
  private static DynamicSafetyScenarioResult.CriterionResult maintained() {
    return new DynamicSafetyScenarioResult.CriterionResult(
        builder().deadlineSeconds(10.0).evaluationMode(EvaluationMode.MAINTAIN_BOUNDS).build());
  }
}
