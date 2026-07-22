package neqsim.process.equipment.flare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RelevantWindCalculator}.
 */
public class RelevantWindCalculatorTest {
  /**
   * The worst sector should be scaled above the reference speed and identified by direction.
   */
  @Test
  void testWorstSectorAndElevationScaling() {
    RelevantWindCalculator calc = new RelevantWindCalculator();
    calc.setProfile(10.0, 100.0, 0.14);
    calc.addSector("N", 8.0, 0.2);
    calc.addSector("NE", 12.0, 0.3);
    calc.addSector("E", 5.0, 0.5);
    calc.calc();

    // Power-law scaling factor (100/10)^0.14 > 1, so worst sector speed exceeds its reference
    // value.
    double scale = Math.pow(100.0 / 10.0, 0.14);
    assertEquals(12.0 * scale, calc.getWorstSectorSpeed(), 1e-6, "Worst sector should be the 12 m/s NE sector scaled");
    assertEquals("NE", calc.getWorstSectorDirection(), "Worst sector direction should be NE");
    assertTrue(calc.getWorstSectorSpeed() > 12.0, "Elevation scaling should raise the speed above the reference");
    assertNotNull(calc.toJson());
  }

  /**
   * The relevant wind speed should be positive and not exceed the worst sector speed.
   */
  @Test
  void testRelevantWindSpeedBounded() {
    RelevantWindCalculator calc = new RelevantWindCalculator();
    calc.setProfile(10.0, 80.0, 0.12);
    calc.setDesignExceedanceFraction(0.9);
    calc.addSector("N", 6.0, 0.25);
    calc.addSector("S", 9.0, 0.25);
    calc.addSector("E", 11.0, 0.25);
    calc.addSector("W", 14.0, 0.25);
    calc.calc();

    assertTrue(calc.getRelevantWindSpeed() > 0.0, "Relevant wind speed should be positive");
    assertTrue(calc.getRelevantWindSpeed() <= calc.getWorstSectorSpeed() + 1e-9,
        "Relevant wind speed should not exceed the worst sector speed");
    assertFalse(calc.getWorstSectorDirection().isEmpty(), "Worst sector direction should be set");
    assertEquals(4, calc.getSectors().size(), "All sectors should be retained");
  }

  /**
   * A larger shear exponent should produce a larger speed at the flare elevation.
   */
  @Test
  void testLargerShearHigherSpeed() {
    RelevantWindCalculator low = new RelevantWindCalculator();
    low.setProfile(10.0, 100.0, 0.10);
    low.addSector("N", 10.0, 1.0);
    low.calc();

    RelevantWindCalculator high = new RelevantWindCalculator();
    high.setProfile(10.0, 100.0, 0.25);
    high.addSector("N", 10.0, 1.0);
    high.calc();

    assertTrue(high.getWorstSectorSpeed() > low.getWorstSectorSpeed(),
        "Larger shear exponent should give a higher elevation wind speed");
  }
}
