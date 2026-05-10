package neqsim.process.safety.processsafetysystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for NORSOK S-001 Clause 10.4.7 secondary pressure protection screening.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class S001SecondaryPressureProtectionCriteriaTest {

  /**
   * Verifies default annual frequency targets by pressure band.
   */
  @Test
  void defaultTargetFrequencyFollowsPressureBands() {
    assertEquals(1.0e-2,
        S001SecondaryPressureProtectionCriteria.getDefaultTargetFrequencyPerYear(90.0, 100.0,
            150.0),
        1.0e-12);
    assertEquals(1.0e-3,
        S001SecondaryPressureProtectionCriteria.getDefaultTargetFrequencyPerYear(120.0, 100.0,
            150.0),
        1.0e-12);
    assertEquals(1.0e-4,
        S001SecondaryPressureProtectionCriteria.getDefaultTargetFrequencyPerYear(175.0, 100.0,
            150.0),
        1.0e-12);
  }

  /**
   * Verifies a complete acceptable case.
   */
  @Test
  void completeCriteriaPasses() {
    S001SecondaryPressureProtectionResult result = new S001SecondaryPressureProtectionCriteria()
        .setMaximumEventPressureBara(120.0).setDesignPressureBara(100.0)
        .setTestPressureBara(150.0).setDemandFrequencyPerYear(1.0e-4)
        .setReliefLeakageAssessed(Boolean.TRUE).setReliefLeakageToSafeLocation(Boolean.TRUE)
        .setProofTestIntervalMonths(6.0).evaluate();

    assertTrue(result.isAcceptable());
    assertTrue(result.toJson().contains("10.4.7"));
  }

  /**
   * Verifies an event pressure above test pressure fails screening.
   */
  @Test
  void eventPressureAboveTestPressureFails() {
    S001SecondaryPressureProtectionResult result = new S001SecondaryPressureProtectionCriteria()
        .setMaximumEventPressureBara(175.0).setDesignPressureBara(100.0)
        .setTestPressureBara(150.0).setDemandFrequencyPerYear(1.0e-5)
        .setReliefLeakageAssessed(Boolean.TRUE).setReliefLeakageToSafeLocation(Boolean.TRUE)
        .setProofTestIntervalMonths(6.0).evaluate();

    assertFalse(result.isAcceptable());
    assertFalse(result.isPressureWithinTestPressure());
  }
}