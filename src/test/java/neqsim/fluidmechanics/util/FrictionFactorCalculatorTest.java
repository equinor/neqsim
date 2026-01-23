package neqsim.fluidmechanics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FrictionFactorCalculator}.
 */
public class FrictionFactorCalculatorTest {
  private static final double TOLERANCE = 1e-6;

  @Test
  void testLaminarFlowFrictionFactor() {
    // In laminar flow, f = 64/Re
    double reynoldsNumber = 1000;
    double relativeRoughness = 0.001;

    double frictionFactor =
        FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness);

    assertEquals(64.0 / reynoldsNumber, frictionFactor, TOLERANCE);
    assertEquals("laminar", FrictionFactorCalculator.getFlowRegime(reynoldsNumber));
  }

  @Test
  void testTurbulentFlowFrictionFactor() {
    // At high Reynolds numbers, Haaland equation should apply
    double reynoldsNumber = 100000;
    double relativeRoughness = 0.0001;

    double frictionFactor =
        FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness);

    // Known approximate value for these conditions
    assertTrue(frictionFactor > 0.01 && frictionFactor < 0.03);
    assertEquals("turbulent", FrictionFactorCalculator.getFlowRegime(reynoldsNumber));
  }

  @Test
  void testTransitionZone() {
    double reynoldsNumber = 3000;
    double relativeRoughness = 0.001;

    double frictionFactor =
        FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness);

    // Should be between laminar and turbulent values
    double fLaminar = 64.0 / 2300.0;
    double fTurbulent = FrictionFactorCalculator.calcHaalandFrictionFactor(4000, relativeRoughness);

    assertTrue(frictionFactor >= Math.min(fLaminar, fTurbulent));
    assertTrue(frictionFactor <= Math.max(fLaminar, fTurbulent));
    assertEquals("transition", FrictionFactorCalculator.getFlowRegime(reynoldsNumber));
  }

  @Test
  void testZeroReynoldsNumber() {
    double frictionFactor = FrictionFactorCalculator.calcDarcyFrictionFactor(0, 0.001);

    assertEquals(0.0, frictionFactor, TOLERANCE);
    assertEquals("no-flow", FrictionFactorCalculator.getFlowRegime(0));
  }

  @Test
  void testVerySmallReynoldsNumber() {
    double frictionFactor = FrictionFactorCalculator.calcDarcyFrictionFactor(1e-12, 0.001);

    assertEquals(0.0, frictionFactor, TOLERANCE);
  }

  @Test
  void testSmoothPipe() {
    // For smooth pipes (roughness = 0), friction should still be calculated
    double reynoldsNumber = 50000;
    double relativeRoughness = 0.0;

    double frictionFactor =
        FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness);

    // Smooth pipe should have lower friction than rough pipe
    double frictionRough = FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, 0.01);
    assertTrue(frictionFactor < frictionRough);
  }

  @Test
  void testFanningVsDarcy() {
    double reynoldsNumber = 10000;
    double relativeRoughness = 0.001;

    double darcy =
        FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness);
    double fanning =
        FrictionFactorCalculator.calcFanningFrictionFactor(reynoldsNumber, relativeRoughness);

    assertEquals(darcy / 4.0, fanning, TOLERANCE);
  }

  @Test
  void testPressureDropCalculation() {
    double frictionFactor = 0.02;
    double diameter = 0.1; // m
    double density = 1000; // kg/m³
    double velocity = 2.0; // m/s

    double dpPerLength = FrictionFactorCalculator.calcPressureDropPerLength(frictionFactor,
        diameter, density, velocity);

    // Expected: 0.02 * 1000 * 4 / (2 * 0.1) = 400 Pa/m
    assertEquals(400.0, dpPerLength, TOLERANCE);
  }

  @Test
  void testNegativeReynoldsNumber() {
    // Should handle negative Reynolds numbers (reverse flow)
    double frictionFactor = FrictionFactorCalculator.calcDarcyFrictionFactor(-5000, 0.001);

    assertTrue(frictionFactor > 0);
  }

  @Test
  void testContinuityAtLaminarTransition() {
    // Friction factor should be continuous at Re = 2300
    double relativeRoughness = 0.001;
    double fBelow = FrictionFactorCalculator.calcDarcyFrictionFactor(2299.9, relativeRoughness);
    double fAbove = FrictionFactorCalculator.calcDarcyFrictionFactor(2300.1, relativeRoughness);

    assertTrue(Math.abs(fBelow - fAbove) < 0.001);
  }

  @Test
  void testContinuityAtTurbulentTransition() {
    // Friction factor should be continuous at Re = 4000
    double relativeRoughness = 0.001;
    double fBelow = FrictionFactorCalculator.calcDarcyFrictionFactor(3999.9, relativeRoughness);
    double fAbove = FrictionFactorCalculator.calcDarcyFrictionFactor(4000.1, relativeRoughness);

    assertTrue(Math.abs(fBelow - fAbove) < 0.001);
  }

  @Test
  void testHighReynoldsNumber() {
    // Should handle very high Reynolds numbers without overflow
    double reynoldsNumber = 1e8;
    double relativeRoughness = 0.0001;

    double frictionFactor =
        FrictionFactorCalculator.calcDarcyFrictionFactor(reynoldsNumber, relativeRoughness);

    assertTrue(Double.isFinite(frictionFactor));
    assertTrue(frictionFactor > 0);
  }
}
