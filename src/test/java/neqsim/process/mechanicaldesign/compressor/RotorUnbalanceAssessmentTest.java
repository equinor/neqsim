package neqsim.process.mechanicaldesign.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RotorUnbalanceAssessment}.
 *
 * @author ESOL
 * @version 1.0
 */
public class RotorUnbalanceAssessmentTest {

  /** API 617 / 671 allowable unbalance, with and without the coupling floor. */
  @Test
  public void testApiAllowableUnbalance() {
    assertEquals(3.2632, RotorUnbalanceAssessment.apiAllowableUnbalance(5.55, 10800.0, false), 1.0e-3);
    assertEquals(7.2008, RotorUnbalanceAssessment.apiAllowableUnbalance(5.55, 10800.0, true), 1.0e-3);
    assertEquals(63.5, RotorUnbalanceAssessment.apiAllowableUnbalance(100.0, 10000.0, true), 1.0e-9);
  }

  /** ISO 21940-11 grade G2.5 for a 100 kg rotor at 3000 r/min. */
  @Test
  public void testIsoPermissibleUnbalance() {
    assertEquals(795.77, RotorUnbalanceAssessment.isoPermissibleUnbalance(2.5, 100.0, 3000.0), 0.01);
  }

  /** Force and vibration limits against hand calculation. */
  @Test
  public void testForceAndVibrationLimits() {
    double rpmForOmega1000 = 1000.0 * 60.0 / (2.0 * Math.PI);
    assertEquals(500.0, RotorUnbalanceAssessment.centrifugalForce(500.0, rpmForOmega1000), 1.0e-6);
    assertEquals(25.4, RotorUnbalanceAssessment.apiShaftVibrationLimit(12000.0), 1.0e-9);
    assertEquals(25.4, RotorUnbalanceAssessment.apiShaftVibrationLimit(3000.0), 1.0e-9);
    assertEquals(12.7, RotorUnbalanceAssessment.apiShaftVibrationLimit(48000.0), 1.0e-9);
    double[] zones = RotorUnbalanceAssessment.isoZoneBoundaries(10000.0);
    assertEquals(48.0, zones[0], 1.0e-9);
    assertEquals(90.0, zones[1], 1.0e-9);
    assertEquals(132.0, zones[2], 1.0e-9);
    assertEquals(22.5, RotorUnbalanceAssessment.significantChangeThreshold(10000.0), 1.0e-9);
  }

  /** Vector change and influence coefficient. */
  @Test
  public void testVectorChangeAndInfluence() {
    assertEquals(5.0, RotorUnbalanceAssessment.vectorChange(3.0, 0.0, 4.0, 90.0), 1.0e-9);
    assertEquals(0.0, RotorUnbalanceAssessment.vectorChange(10.0, 30.0, 10.0, 30.0), 1.0e-9);
    assertEquals(0.04, RotorUnbalanceAssessment.influenceCoefficient(20.0, 500.0), 1.0e-12);
  }

  /** Lost coupling bolt fragment on a 11.1 kg coupling. */
  @Test
  public void testEvaluateLostBolt() {
    RotorUnbalanceAssessment.Result r = RotorUnbalanceAssessment.evaluate(10.0, 57.0, 5.55, 10800.0);
    assertEquals(570.0, r.getUnbalanceGmm(), 1.0e-9);
    assertEquals(79.16, r.getUnbalanceRatio(), 0.01);
    assertTrue(r.exceedsAllowable());
    assertTrue(r.getCentrifugalForceN() > 700.0);
    assertTrue(r.toJson().contains("unbalanceRatio"));
    assertFalse(RotorUnbalanceAssessment.evaluate(0.001, 1.0, 5.55, 10800.0).exceedsAllowable());
  }

  /** Invalid input is rejected. */
  @Test
  public void testInvalidInput() {
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        RotorUnbalanceAssessment.angularSpeed(0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        RotorUnbalanceAssessment.unbalanceFromMass(-1.0, 10.0);
      }
    });
  }
}
