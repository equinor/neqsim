package neqsim.process.safety.dispersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.safety.dispersion.HazardousAreaCalculator.ReleaseGrade;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HazardousAreaCalculator}.
 *
 * @author ESOL
 * @version 1.0
 */
public class HazardousAreaCalculatorTest {

  /**
   * A methane secondary-grade jet release should yield a Zone 2 classification and a positive hazardous distance.
   */
  @Test
  public void testMethaneSecondaryReleaseZone2() {
    HazardousAreaCalculator calc = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604)
        .setReleaseGrade(ReleaseGrade.SECONDARY).setSafetyFactor(0.5);

    assertTrue(calc.effectiveDiameterM() > 0.0);
    assertTrue(calc.lflMassFraction() > 0.0 && calc.lflMassFraction() < 0.1);
    assertTrue(calc.hazardousDistanceM() > 0.0);
    assertEquals("Zone 2", calc.zoneClassification());
  }

  /**
   * A larger release should produce a larger hazardous distance; a stricter safety factor should increase it too.
   */
  @Test
  public void testHazardousDistanceScaling() {
    HazardousAreaCalculator small = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604);
    HazardousAreaCalculator large = new HazardousAreaCalculator(1.0, 6.0, 340.0, 0.044, 0.01604);
    assertTrue(large.hazardousDistanceM() > small.hazardousDistanceM());

    HazardousAreaCalculator lenient = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604).setSafetyFactor(0.5);
    HazardousAreaCalculator strict = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604).setSafetyFactor(0.25);
    assertTrue(strict.hazardousDistanceM() > lenient.hazardousDistanceM());
  }

  /**
   * Continuous and primary grades should map to Zone 0 and Zone 1.
   */
  @Test
  public void testZoneMapping() {
    HazardousAreaCalculator continuous = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604)
        .setReleaseGrade(ReleaseGrade.CONTINUOUS);
    HazardousAreaCalculator primary = new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604)
        .setReleaseGrade(ReleaseGrade.PRIMARY);
    assertEquals("Zone 0", continuous.zoneClassification());
    assertEquals("Zone 1", primary.zoneClassification());
  }
}
