package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for STS0131 depressurization acceptance criteria.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class STS0131AcceptanceCriteriaTest {

  /**
   * Verifies limiting-time interpolation and criterion pass/fail aggregation.
   */
  @Test
  void evaluatesPressureMassAndFireRateAtLimitingTime() {
    DepressurizationSimulator.DepressurizationResult result =
        new DepressurizationSimulator.DepressurizationResult();
    result.initialPressureBara = 50.0;
    result.append(0.0, 50.0, 293.15, 100.0, 293.15, 0.0);
    result.append(100.0, 10.0, 280.0, 40.0, 285.0, 5.0);
    result.append(200.0, 5.0, 275.0, 20.0, 280.0, 2.0);

    STS0131AcceptanceCriteria criteria = new STS0131AcceptanceCriteria().setTimeToEscapeS(120.0)
        .setEstimatedTimeToRuptureS(100.0).setMaximumPressureAtRuptureBara(15.0)
        .setMaximumRemainingMassKg(50.0).setMaximumEscalatedFireRateKgPerS(6.0);

    STS0131AcceptanceResult acceptance = result.evaluateSTS0131(criteria);

    assertTrue(acceptance.isAcceptable());
    assertEquals(100.0, acceptance.getLimitingTimeS(), 1.0e-12);
    assertEquals(10.0, acceptance.getPressureAtLimitingTimeBara(), 1.0e-12);
    assertEquals(40.0, acceptance.getRemainingMassAtLimitingTimeKg(), 1.0e-12);
    assertTrue(acceptance.toJson().contains("STS0131"));
  }
}