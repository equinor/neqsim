package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

/**
 * Independent ideal-gas conservative-variable controls for the optional Rusanov phase flux.
 *
 * @author NeqSim
 * @version 1.0
 */
class RusanovConservativeFluxTest {
  private final AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();

  /** Pressure diffusion must act on internal plus kinetic energy, not enthalpy inventory. */
  @Test
  void pressureJumpUsesTotalEnergyInventory() {
    PhaseState left = ideal(10.0, 0.0, 1e6, 0.5);
    PhaseState right = ideal(10.0, 0.0, 2e6, 0.5);
    PhaseFlux flux = calculator.calcRusanovFlux(left, right, 1.0);
    double speed = right.soundSpeed;
    double expected = -0.5 * speed * (0.5 * 2e6 / 0.4 - 0.5 * 1e6 / 0.4);
    assertEquals(expected, flux.energyFlux, Math.abs(expected) * 3e-15);
  }

  /** Compare all flux components against Euler variables constructed independently from gamma and pressure. */
  @Test
  void idealGasMatrixAndPressureMetadata() {
    for (double area : new double[] {0.01, 1.0148}) {
      for (double rhoLeft : new double[] {1.0, 800.0}) {
        for (double rhoRight : new double[] {1.0, 800.0}) {
          for (double pressureLeft : new double[] {1e5, 15e6}) {
            for (double pressureRight : new double[] {1e5, 15e6}) {
              for (double uLeft : new double[] {-5.0, 3.0}) {
                for (double uRight : new double[] {-2.0, 7.0}) {
                  for (double alpha : new double[] {1e-30, 0.25, 1.0}) {
                    PhaseState left = ideal(rhoLeft, uLeft, pressureLeft, alpha);
                    PhaseState right = ideal(rhoRight, uRight, pressureRight, 1.0 - 0.5 * alpha);
                    checkFlux(left, right, area);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /** Exactly absent properties are unused; a positive trace follows the same conservative formula. */
  @Test
  void exactAbsenceAndPositiveTraces() {
    PhaseState absent = new PhaseState(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0);
    PhaseFlux zero = calculator.calcRusanovFlux(absent, absent, 1.0);
    assertEquals(0.0, zero.massFlux, 0.0);
    assertEquals(0.0, zero.momentumFlux, 0.0);
    assertEquals(0.0, zero.energyFlux, 0.0);
    assertEquals(0.0, zero.interfaceHoldup, 0.0);
    assertEquals(0.0, zero.interfacePressure, 0.0);
    assertEquals(0.0, zero.holdupFlux, 0.0);
    PhaseState wet = ideal(800, 0, 1e6, 0.6);
    PhaseFlux inflow = calculator.calcRusanovFlux(absent, wet, 1.0);
    PhaseFlux outflow = calculator.calcRusanovFlux(wet, absent, 1.0);
    assertTrue(inflow.massFlux < 0.0);
    assertEquals(-inflow.massFlux, outflow.massFlux, 0.0);
    assertEquals(-inflow.energyFlux, outflow.energyFlux, 0.0);
    assertEquals(inflow.momentumFlux, outflow.momentumFlux, 0.0);
    for (double alpha : new double[] {1e-30, 1e-16, 1e-8}) {
      checkFlux(ideal(800, 0, 1e6, alpha), wet, 1.0);
    }
  }

  /** An identical state returns the physical Euler flux, including kinetic energy in total enthalpy. */
  @Test
  void equalStateConsistencyAndReferenceShift() {
    PhaseState state = ideal(10, -4, 1e6, 0.3);
    PhaseFlux equal = calculator.calcRusanovFlux(state, state, 2.0);
    assertEquals(2 * 0.3 * 10 * -4, equal.massFlux, 0.0);
    assertEquals(equal.massFlux * -4 + 2 * 0.3 * 1e6, equal.momentumFlux, 1e-9);
    assertEquals(equal.massFlux * state.enthalpy, equal.energyFlux, 1e-7);
    PhaseState right = ideal(20, 2, 2e6, 0.8);
    PhaseFlux before = calculator.calcRusanovFlux(state, right, 1.0);
    state.enthalpy -= 1e6;
    right.enthalpy -= 1e6;
    PhaseFlux after = calculator.calcRusanovFlux(state, right, 1.0);
    assertEquals(-1e6 * before.massFlux, after.energyFlux - before.energyFlux, Math.abs(before.massFlux) * 1e-8);
  }

  /** Positive explicit mass updates need the declared Courant bound; no unconditional stability is claimed. */
  @Test
  void massPositivityHasACourantCondition() {
    PhaseState dry = ideal(10, 0, 1e6, 0.0);
    PhaseState wet = ideal(10, 0, 1e6, 1.0);
    double faceFlux = calculator.calcRusanovFlux(wet, dry, 1.0).massFlux;
    for (double courant : new double[] {0.25, 0.5, 0.9}) {
      double dt = courant / wet.soundSpeed;
      assertTrue(10.0 - 2 * dt * faceFlux >= 0.0);
    }
    assertTrue(10.0 - 2 * 1.1 / wet.soundSpeed * faceFlux < 0.0);
  }

  /** Reject invalid occupied states and overflowing outputs rather than silently emitting nonfinite evidence. */
  @Test
  void invalidInputsAndOverflowReject() {
    PhaseState valid = ideal(10, 0, 1e6, 0.5);
    for (double area : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> calculator.calcRusanovFlux(valid, valid, area));
    }
    for (double alpha : new double[] {-1, 1.1, Double.NaN}) {
      PhaseState invalid = ideal(10, 0, 1e6, alpha);
      assertThrows(IllegalArgumentException.class, () -> calculator.calcRusanovFlux(invalid, valid, 1.0));
    }
    PhaseState invalid = ideal(10, 0, 1e6, 0.5);
    invalid.enthalpy = Double.NaN;
    assertThrows(IllegalArgumentException.class, () -> calculator.calcRusanovFlux(valid, invalid, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> calculator.calcRusanovFlux(valid, ideal(10, 1, 2e6, 0.8), Double.MAX_VALUE));
  }

  /**
   * Construct a gamma=1.4 ideal-gas phase, with total specific enthalpy.
   *
   * @param density density in kg/m3
   * @param velocity phase velocity in m/s
   * @param pressure absolute pressure in Pa
   * @param alpha phase area fraction
   * @return independently specified ideal-gas state
   */
  private static PhaseState ideal(double density, double velocity, double pressure, double alpha) {
    return new PhaseState(density, velocity, pressure, Math.sqrt(1.4 * pressure / density),
        3.5 * pressure / density + 0.5 * velocity * velocity, alpha);
  }

  /**
   * Compare native flux with the Euler conservation law, not the implementation's enthalpy conversion.
   *
   * @param left left phase state
   * @param right right phase state
   * @param area pipe area in m2
   */
  private void checkFlux(PhaseState left, PhaseState right, double area) {
    double speed = Math.max(Math.abs(left.velocity) + left.soundSpeed, Math.abs(right.velocity) + right.soundSpeed);
    double[] ul = {left.holdup * left.density, left.holdup * left.density * left.velocity,
        left.holdup * (left.pressure / 0.4 + 0.5 * left.density * left.velocity * left.velocity)};
    double[] ur = {right.holdup * right.density, right.holdup * right.density * right.velocity,
        right.holdup * (right.pressure / 0.4 + 0.5 * right.density * right.velocity * right.velocity)};
    double pl = left.holdup * left.pressure;
    double pr = right.holdup * right.pressure;
    double[] fl = {ul[1], ul[1] * left.velocity + pl, left.velocity * (ul[2] + pl)};
    double[] fr = {ur[1], ur[1] * right.velocity + pr, right.velocity * (ur[2] + pr)};
    PhaseFlux value = calculator.calcRusanovFlux(left, right, area);
    double[] actual = {value.massFlux, value.momentumFlux, value.energyFlux};
    for (int i = 0; i < 3; i++) {
      double expected = area * (0.5 * (fl[i] + fr[i]) - 0.5 * speed * (ur[i] - ul[i]));
      double scale = area * (Math.abs(fl[i]) + Math.abs(fr[i]) + speed * (Math.abs(ul[i]) + Math.abs(ur[i])));
      assertEquals(expected, actual[i], 3e-14 * scale);
    }
    assertEquals(0.5 * (left.holdup + right.holdup), value.interfaceHoldup, 0.0);
    assertEquals(area * 0.5 * (pl + pr), area * value.interfaceHoldup * value.interfacePressure,
        area * (Math.abs(pl) + Math.abs(pr)) * 3e-15);
    double expectedHoldup = 0.5 * (left.holdup * left.velocity + right.holdup * right.velocity)
        - 0.5 * speed * (right.holdup - left.holdup);
    assertEquals(expectedHoldup, value.holdupFlux, Math.abs(expectedHoldup) * 3e-15);
  }
}
