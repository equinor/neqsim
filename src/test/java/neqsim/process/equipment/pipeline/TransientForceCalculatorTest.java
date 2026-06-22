package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransientForceCalculator} (NIP-01 transient fluid forces).
 */
public class TransientForceCalculatorTest {
  private static final double DIAMETER = 0.2; // m
  private static final double AREA = 0.25 * Math.PI * DIAMETER * DIAMETER;

  /**
   * The constructor must derive the flow area from the diameter.
   */
  @Test
  void areaDerivedFromDiameter() {
    TransientForceCalculator calc = new TransientForceCalculator(DIAMETER, 90.0, 1000.0);
    assertEquals(AREA, calc.getArea(), 1.0e-9);
  }

  /**
   * The pressure thrust must equal the Joukowsky pressure rise times the flow area.
   */
  @Test
  void joukowskyPressureThrustOnSegment() {
    double rho = 1000.0;
    double waveSpeed = 1200.0;
    double deltaV = 2.0;
    double deltaP = rho * waveSpeed * deltaV; // Joukowsky surge [Pa] = 2.4e6
    TransientForceCalculator calc = new TransientForceCalculator(DIAMETER, 90.0, rho);
    double thrust = calc.pressureThrust(deltaP);
    assertEquals(deltaP * AREA, thrust, deltaP * AREA * 0.05);
  }

  /**
   * The dynamic load factor must approach 2.0 for an instantaneous (step) load and 1.0 for a load that rises slowly
   * relative to the support natural period.
   */
  @Test
  void dynamicLoadFactorBounds() {
    TransientForceCalculator calc = new TransientForceCalculator(DIAMETER, 90.0, 1000.0);
    calc.setSupportNaturalPeriod(0.02);

    calc.setRiseTime(0.0);
    assertEquals(2.0, calc.getDynamicLoadFactor(), 1.0e-9);

    calc.setRiseTime(2.0); // rise time >> period
    assertTrue(calc.getDynamicLoadFactor() < 1.05, "slow load should give a DLF near 1.0");
  }

  /**
   * The force history must capture the unsteady inertial peak and report its time.
   */
  @Test
  void forceHistoryPeakDetection() {
    TransientForceCalculator calc = new TransientForceCalculator(DIAMETER, 0.0, 1000.0);
    calc.setSegmentLength(10.0);

    double[] time = { 0.0, 0.1, 0.2, 0.3, 0.4 };
    // Equal end pressures so only the inertial (dv/dt) term acts; velocity ramps then stops.
    double[] p1 = { 0.0, 0.0, 0.0, 0.0, 0.0 };
    double[] p2 = { 0.0, 0.0, 0.0, 0.0, 0.0 };
    double[] v1 = { 2.0, 2.0, 1.0, 0.0, 0.0 };
    double[] v2 = { 2.0, 2.0, 1.0, 0.0, 0.0 };

    calc.computeForceHistory(time, p1, p2, v1, v2);
    double peak = calc.getPeakUnbalancedForce("N");
    assertTrue(Math.abs(peak) > 0.0, "deceleration must produce an inertial force");
    assertTrue(calc.getTimeOfPeakForce() >= 0.0);
    assertEquals(time.length, calc.getSegmentForceSeries().length);
  }

  /**
   * A bend force resultant must increase with the bend angle for fixed flow conditions.
   */
  @Test
  void bendForceIncreasesWithAngle() {
    TransientForceCalculator calc = new TransientForceCalculator(DIAMETER, 45.0, 1000.0);
    double f45 = calc.bendForce(1.0e6, 5.0);
    calc.setBendAngleDeg(90.0);
    double f90 = calc.bendForce(1.0e6, 5.0);
    assertTrue(f90 > f45, "90 deg bend reaction must exceed 45 deg reaction");
  }

  /**
   * Requesting the DLF before setting its inputs must fail clearly.
   */
  @Test
  void dlfRequiresInputs() {
    TransientForceCalculator calc = new TransientForceCalculator(DIAMETER, 90.0, 1000.0);
    assertThrows(IllegalStateException.class, calc::getDynamicLoadFactor);
  }
}
