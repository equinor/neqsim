package neqsim.fluidmechanics.flownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for InterfacialAreaCalculator.
 */
class InterfacialAreaCalculatorTest {
  // Reference properties for air-water at atmospheric conditions
  private static final double RHO_G = 1.2; // kg/m³
  private static final double RHO_L = 1000.0; // kg/m³
  private static final double SIGMA = 0.072; // N/m
  private static final double DIAMETER = 0.1; // m

  @Test
  void testStratifiedAreaPhysicalRange() {
    // Stratified flow interfacial area
    double a = InterfacialAreaCalculator.calculateStratifiedArea(DIAMETER, 0.3);

    // Should be positive and reasonable (order of 10-100 for 0.1m pipe)
    assertTrue(a > 0, "Interfacial area should be positive");
    assertTrue(a < 100, "Interfacial area should be reasonable for 0.1m pipe");
  }

  @Test
  void testStratifiedAreaZeroHoldup() {
    double a = InterfacialAreaCalculator.calculateStratifiedArea(DIAMETER, 0.0);
    assertEquals(0.0, a, "Zero holdup should give zero area");
  }

  @Test
  void testStratifiedAreaFullHoldup() {
    double a = InterfacialAreaCalculator.calculateStratifiedArea(DIAMETER, 1.0);
    assertEquals(0.0, a, "Full holdup should give zero area");
  }

  @Test
  void testAnnularAreaIncreasesWithHoldup() {
    double a1 = InterfacialAreaCalculator.calculateAnnularArea(DIAMETER, 0.1);
    double a2 = InterfacialAreaCalculator.calculateAnnularArea(DIAMETER, 0.3);

    // Thicker film should have higher surface area due to smaller core
    assertTrue(a2 > a1, "Annular area should increase with holdup");
  }

  @Test
  void testBubbleAreaIncreasesWithVoidFraction() {
    double a1 = InterfacialAreaCalculator.calculateBubbleArea(DIAMETER, 0.95, RHO_G, RHO_L, SIGMA);
    double a2 = InterfacialAreaCalculator.calculateBubbleArea(DIAMETER, 0.80, RHO_G, RHO_L, SIGMA);

    // More gas (lower holdup) means more bubbles and higher area
    assertTrue(a2 > a1, "Bubble area should increase with void fraction");
  }

  @Test
  void testDropletAreaIncreasesWithLiquidFraction() {
    double usg = 20.0; // m/s
    double a1 = InterfacialAreaCalculator.calculateDropletArea(DIAMETER, 0.02, RHO_G, usg, SIGMA);
    double a2 = InterfacialAreaCalculator.calculateDropletArea(DIAMETER, 0.05, RHO_G, usg, SIGMA);

    // More liquid means more droplets
    assertTrue(a2 > a1, "Droplet area should increase with liquid holdup");
  }

  @Test
  void testSlugAreaPositive() {
    double usg = 2.0;
    double usl = 0.5;
    double a =
        InterfacialAreaCalculator.calculateSlugArea(DIAMETER, 0.5, RHO_G, RHO_L, usg, usl, SIGMA);

    assertTrue(a > 0, "Slug area should be positive");
  }

  @Test
  void testCalculateInterfacialAreaForAllPatterns() {
    double usg = 5.0;
    double usl = 0.5;
    double holdup = 0.3;

    for (FlowPattern pattern : FlowPattern.values()) {
      double a = InterfacialAreaCalculator.calculateInterfacialArea(pattern, DIAMETER, holdup,
          RHO_G, RHO_L, usg, usl, SIGMA);
      assertTrue(a >= 0, "Interfacial area should be non-negative for " + pattern);
    }
  }

  @Test
  void testSauterDiameterPositive() {
    double epsilon = 100.0; // m²/s³ - high turbulence
    double d32 = InterfacialAreaCalculator.calculateSauterDiameter(RHO_G, RHO_L, SIGMA, epsilon);

    assertTrue(d32 > 0, "Sauter diameter should be positive");
    assertTrue(d32 < 0.1, "Sauter diameter should be small for high turbulence");
  }

  @Test
  void testSmallerPipeGivesHigherArea() {
    // Smaller diameter should give higher interfacial area per unit volume
    double a_small = InterfacialAreaCalculator.calculateStratifiedArea(0.05, 0.3);
    double a_large = InterfacialAreaCalculator.calculateStratifiedArea(0.15, 0.3);

    assertTrue(a_small > a_large, "Smaller pipe should have higher area per volume");
  }
}
