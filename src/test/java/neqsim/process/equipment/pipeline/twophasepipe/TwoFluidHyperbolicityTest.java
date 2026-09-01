package neqsim.process.equipment.pipeline.twophasepipe;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Well-posedness of the two-fluid momentum system.
 *
 * <p>
 * Writing the equal-pressure two-fluid model in quasi-linear form and eliminating the pressure with the volume
 * constraint leaves a two-by-two system for the void wave whose characteristic roots are
 * </p>
 *
 * <pre>
 * lambda = (rho_l*alpha_g*u_g + rho_g*alpha_l*u_l) / D
 *          +- sqrt(delta_p_i*D - rho_g*rho_l*alpha_g*alpha_l*du^2) / D,   D = rho_l*alpha_g + rho_g*alpha_l
 * </pre>
 *
 * <p>
 * The roots are real, and the initial-value problem well posed, only when the discriminant is non-negative. Without an
 * interfacial pressure difference the discriminant is {@code -rho_g*rho_l*alpha_g*alpha_l*du^2}, which is negative for
 * any slip whenever both phases are present: the classical model is ill-posed, short wavelengths grow without bound,
 * and the growth rate is set by the mesh rather than by the physics. These tests pin that statement, and pin that the
 * Bestion closure in {@link TwoFluidConservationEquations} supplies exactly the interfacial pressure difference the
 * criterion requires.
 * </p>
 */
public class TwoFluidHyperbolicityTest {

  private static final double GAS_DENSITY = 50.0;
  private static final double LIQUID_DENSITY = 700.0;

  private static TwoFluidSection section(double liquidHoldup, double slip) {
    TwoFluidSection sec = new TwoFluidSection(0.0, 1.0, 0.2, 0.0);
    sec.setGasHoldup(1.0 - liquidHoldup);
    sec.setLiquidHoldup(liquidHoldup);
    sec.setGasDensity(GAS_DENSITY);
    sec.setLiquidDensity(LIQUID_DENSITY);
    sec.setGasVelocity(slip);
    sec.setLiquidVelocity(0.0);
    return sec;
  }

  /**
   * Discriminant of the void-wave characteristic polynomial, computed independently of the model code.
   *
   * @param liquidHoldup liquid volume fraction
   * @param slip gas minus liquid velocity in m/s
   * @param interfacialPressure interfacial pressure difference in Pa
   * @return discriminant; negative means complex characteristics
   */
  private static double discriminant(double liquidHoldup, double slip, double interfacialPressure) {
    double alphaL = liquidHoldup;
    double alphaG = 1.0 - liquidHoldup;
    double d = LIQUID_DENSITY * alphaG + GAS_DENSITY * alphaL;
    return interfacialPressure * d - GAS_DENSITY * LIQUID_DENSITY * alphaG * alphaL * slip * slip;
  }

  /** Without the interfacial pressure term the system is ill-posed wherever two phases coexist with slip. */
  @Test
  void testClassicalTwoFluidSystemIsIllPosedAtEveryLiquidFraction() {
    double[] liquidHoldups = { 0.05, 0.1, 0.2, 0.3, 0.5, 0.7, 0.9 };
    for (double alphaL : liquidHoldups) {
      double value = discriminant(alphaL, 2.0, 0.0);
      Assertions.assertTrue(value < 0.0, "the classical system must have complex characteristics at liquid holdup "
          + alphaL + ", but the " + "discriminant was " + value);
    }
  }

  /** The Bestion closure must deliver the critical interfacial pressure scaled by its coefficient. */
  @Test
  void testBestionClosureSuppliesTheCriticalInterfacialPressure() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableInterfacialPressure(true);
    double coefficient = equations.getInterfacialPressureCoefficient();

    double[] liquidHoldups = { 0.1, 0.3, 0.5, 0.7, 0.9 };
    double[] slips = { 0.5, 2.0, 5.0 };
    for (double alphaL : liquidHoldups) {
      for (double slip : slips) {
        TwoFluidSection sec = section(alphaL, slip);
        double alphaG = 1.0 - alphaL;
        double d = LIQUID_DENSITY * alphaG + GAS_DENSITY * alphaL;
        double critical = GAS_DENSITY * LIQUID_DENSITY * alphaG * alphaL * slip * slip / d;
        double supplied = equations.calcInterfacialPressureDifference(sec);
        Assertions.assertEquals(coefficient * critical, supplied, 1.0e-6 * Math.max(1.0, coefficient * critical),
            "interfacial pressure at liquid holdup " + alphaL + " and slip " + slip);
      }
    }
  }

  /** With the closure active the characteristics must be real, and strictly so for a coefficient above one. */
  @Test
  void testInterfacialPressureClosureRestoresRealCharacteristics() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableInterfacialPressure(true);
    double coefficient = equations.getInterfacialPressureCoefficient();
    Assertions.assertTrue(coefficient > 1.0,
        "a coefficient of one leaves the system marginally hyperbolic with a double root, so the default must "
            + "exceed one, but it was " + coefficient);

    double[] liquidHoldups = { 0.1, 0.3, 0.5, 0.7, 0.9 };
    for (double alphaL : liquidHoldups) {
      for (double slip : new double[] { 0.5, 2.0, 5.0 }) {
        double supplied = equations.calcInterfacialPressureDifference(section(alphaL, slip));
        double value = discriminant(alphaL, slip, supplied);
        Assertions.assertTrue(value > 0.0, "characteristics must be real at liquid holdup " + alphaL + " and slip "
            + slip + ", but the discriminant was " + value);
      }
    }
  }

  /**
   * The reported void-wave speed must not fall below the true characteristic speed.
   *
   * <p>
   * The speed relative to the mixture is {@code sqrt(discriminant)/D}, which for the Bestion closure reduces to
   * {@code du*sqrt(rho_g*rho_l*alpha_g*alpha_l*(delta - 1))/D}. The value used for the time-step limit is
   * {@code sqrt(delta_p_i/D)}, larger by {@code sqrt(delta/(delta - 1))}. Being an upper bound keeps the step stable;
   * the ratio is recorded here because it is the factor by which the time step is smaller than it needs to be.
   * </p>
   */
  @Test
  void testReportedVoidWaveSpeedBoundsTheTrueCharacteristicSpeed() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableInterfacialPressure(true);
    double coefficient = equations.getInterfacialPressureCoefficient();
    double expectedRatio = Math.sqrt(coefficient / (coefficient - 1.0));

    for (double alphaL : new double[] { 0.2, 0.5, 0.8 }) {
      for (double slip : new double[] { 1.0, 3.0 }) {
        TwoFluidSection sec = section(alphaL, slip);
        double alphaG = 1.0 - alphaL;
        double d = LIQUID_DENSITY * alphaG + GAS_DENSITY * alphaL;
        double trueSpeed = Math.sqrt(discriminant(alphaL, slip, equations.calcInterfacialPressureDifference(sec))) / d;
        double reportedSpeed = equations.calcVoidWaveSpeed(sec);

        Assertions.assertTrue(reportedSpeed >= trueSpeed,
            "the time-step limit must not underestimate the void wave at liquid holdup " + alphaL);
        Assertions.assertEquals(expectedRatio, reportedSpeed / trueSpeed, 1.0e-6 * expectedRatio,
            "void-wave speed ratio at liquid holdup " + alphaL + " and slip " + slip);
      }
    }
  }
}
