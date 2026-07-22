package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrificeWellTester}.
 */
public class OrificeWellTesterTest {
  /**
   * The base-case factors should match the GPSA critical-flow prover definitions.
   */
  @Test
  void testFactorsAndRate() {
    OrificeWellTester tester = new OrificeWellTester();
    tester.setOrificeCoefficient(2.0);
    tester.setFlowingConditions(100.0, 520.0);
    tester.setGasProperties(0.65, 1.0);
    tester.calcRate();

    assertEquals(1.0, tester.getTemperatureFactor(), 1e-9, "Ftf should be 1 at 520 R");
    assertEquals(Math.sqrt(1.0 / 0.65), tester.getGravityFactor(), 1e-9, "Fg should be sqrt(1/G)");
    assertEquals(1.0, tester.getSupercompressibilityFactor(), 1e-9, "Fpv should be 1 at Z=1");

    double expected = 2.0 * 100.0 * 1.0 * Math.sqrt(1.0 / 0.65) * 1.0;
    assertEquals(expected, tester.getGasRate(), 1e-6, "Gas rate should match Qg = C*Pf*Ftf*Fg*Fpv");
    assertTrue(tester.getGasRate() > 0.0, "Gas rate should be positive");
    assertNotNull(tester.toJson());
  }

  /**
   * Higher static pressure should give a proportionally higher rate.
   */
  @Test
  void testRateScalesWithPressure() {
    OrificeWellTester low = new OrificeWellTester();
    low.setOrificeCoefficient(2.0);
    low.setFlowingConditions(100.0, 520.0);
    low.setGasProperties(0.65, 1.0);
    low.calcRate();

    OrificeWellTester high = new OrificeWellTester();
    high.setOrificeCoefficient(2.0);
    high.setFlowingConditions(200.0, 520.0);
    high.setGasProperties(0.65, 1.0);
    high.calcRate();

    assertEquals(2.0, high.getGasRate() / low.getGasRate(), 1e-6, "Rate should scale linearly with pressure");
  }

  /**
   * Higher flowing temperature should reduce the temperature factor and the rate.
   */
  @Test
  void testHotterGasLowerRate() {
    OrificeWellTester cool = new OrificeWellTester();
    cool.setOrificeCoefficient(2.0);
    cool.setFlowingConditions(100.0, 520.0);
    cool.setGasProperties(0.65, 1.0);
    cool.calcRate();

    OrificeWellTester hot = new OrificeWellTester();
    hot.setOrificeCoefficient(2.0);
    hot.setFlowingConditions(100.0, 600.0);
    hot.setGasProperties(0.65, 1.0);
    hot.calcRate();

    assertTrue(hot.getTemperatureFactor() < cool.getTemperatureFactor(),
        "Hotter gas should have a lower temperature factor");
    assertTrue(hot.getGasRate() < cool.getGasRate(), "Hotter gas should give a lower rate");
  }
}
