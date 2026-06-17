package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for EmulsionViscosityCalculator.
 */
public class EmulsionViscosityCalculatorTest {

  @Test
  void testEinstein_lowConcentration() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.05);
    calc.setModel("einstein");
    calc.calculate();

    double mu = calc.getEffectiveViscosity();
    // Einstein: mu_r = 1 + 2.5 * phi
    // For W/O at waterCut=0.05: mu = 5.0 * (1 + 2.5*0.05) = 5.625
    assertEquals(5.625, mu, 0.2, "Einstein model at 5% water cut");
    assertEquals("W/O", calc.getEmulsionType(), "Should be W/O emulsion");
  }

  @Test
  void testTaylor_visibleEffect() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.10);
    calc.setModel("taylor");
    calc.calculate();

    double mu = calc.getEffectiveViscosity();
    assertTrue(mu > 5.0, "Taylor model should increase viscosity");
    assertTrue(mu < 5.0 * 2.0, "Should not double at 10% water cut");
  }

  @Test
  void testBrinkman_concentration() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.20);
    calc.setModel("brinkman");
    calc.calculate();

    double mu = calc.getEffectiveViscosity();
    // Brinkman: mu = mu_c / (1-phi)^2.5
    // phi=0.2: mu = 5.0 / (0.8)^2.5 = 5.0 / 0.5724 = 8.73
    assertEquals(8.73, mu, 0.4, "Brinkman model at 20% water cut");
  }

  @Test
  void testPalRhodes_default() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.30);
    calc.calculate(); // Default model should be pal_rhodes

    double mu = calc.getEffectiveViscosity();
    assertTrue(mu > 5.0, "Pal-Rhodes should increase viscosity");
    assertNotNull(calc.getModel(), "Model should be set");
  }

  @Test
  void testPhaseInversion_detection() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(20.0);
    calc.setWaterViscosity(1.0);

    // Below inversion: W/O
    calc.setWaterCut(0.20);
    calc.calculate();
    assertEquals("W/O", calc.getEmulsionType());

    // Above inversion: O/W
    calc.setWaterCut(0.90);
    calc.calculate();
    assertEquals("O/W", calc.getEmulsionType());
  }

  @Test
  void testPhaseInversionPoint() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(10.0);
    calc.setWaterViscosity(1.0);
    calc.calculate();

    double pip = calc.getInversionWaterCut();
    assertTrue(pip > 0.2, "Inversion point should be > 0.2");
    assertTrue(pip < 0.9, "Inversion point should be < 0.9");
  }

  @Test
  void testWoelflin() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.20);
    calc.setModel("woelflin");
    calc.calculate();

    double mu = calc.getEffectiveViscosity();
    assertTrue(mu > 5.0, "Woelflin should increase viscosity for emulsions");
  }

  @Test
  void testRichardson() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.15);
    calc.setModel("richardson");
    calc.calculate();

    double mu = calc.getEffectiveViscosity();
    assertTrue(mu > 5.0, "Richardson should increase viscosity");
  }

  @Test
  void testZeroWaterCut() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(5.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.0);
    calc.calculate();

    double mu = calc.getEffectiveViscosity();
    assertEquals(5.0, mu, 0.01, "Zero water cut should give oil viscosity");
  }

  @Test
  void testViscosityCurve() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(10.0);
    calc.setWaterViscosity(1.0);
    calc.setModel("brinkman");

    double[][] curve = calc.calculateViscosityCurve(0.0, 0.6, 7);
    assertNotNull(curve);
    assertEquals(7, curve.length, "Should have 7 points");

    // Viscosity should be positive everywhere
    for (int i = 0; i < curve.length; i++) {
      assertTrue(curve[i][1] > 0, "Viscosity should be positive at all points");
    }
  }

  @Test
  void testDemulsifierCorrection() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(10.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.30);
    calc.setModel("brinkman");
    calc.calculate();
    double withoutDemulsifier = calc.getEffectiveViscosity();

    calc.setDemulsifierPresent(true);
    calc.setDemulsifierEfficiency(0.5);
    calc.calculate();
    double withDemulsifier = calc.getEffectiveViscosity();

    assertTrue(withDemulsifier < withoutDemulsifier,
        "Demulsifier should reduce emulsion viscosity");
  }

  @Test
  void testTightnessFactorEffect() {
    EmulsionViscosityCalculator calc1 = new EmulsionViscosityCalculator();
    calc1.setOilDensity(800.0);
    calc1.setWaterDensity(1000.0);
    calc1.setOilViscosity(10.0);
    calc1.setWaterViscosity(1.0);
    calc1.setWaterCut(0.25);
    calc1.setTightnessFactor(0.5);
    calc1.setModel("woelflin");
    calc1.calculate();
    double loose = calc1.getEffectiveViscosity();

    EmulsionViscosityCalculator calc2 = new EmulsionViscosityCalculator();
    calc2.setOilDensity(800.0);
    calc2.setWaterDensity(1000.0);
    calc2.setOilViscosity(10.0);
    calc2.setWaterViscosity(1.0);
    calc2.setWaterCut(0.25);
    calc2.setTightnessFactor(1.5);
    calc2.setModel("woelflin");
    calc2.calculate();
    double tight = calc2.getEffectiveViscosity();

    assertTrue(tight > loose, "Tight emulsion should have higher viscosity");
  }

  @Test
  void testToJsonNotNull() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(10.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.30);
    calc.calculate();

    String json = calc.toJson();
    assertNotNull(json);
    assertTrue(json.contains("effectiveViscosity_cP"));
    assertTrue(json.contains("emulsionType"));
    assertTrue(json.contains("inversionWaterCut"));
  }

  @Test
  void testRelativeViscosity() {
    EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
    calc.setOilDensity(800.0);
    calc.setWaterDensity(1000.0);
    calc.setOilViscosity(10.0);
    calc.setWaterViscosity(1.0);
    calc.setWaterCut(0.20);
    calc.setModel("brinkman");
    calc.calculate();

    double relVisc = calc.getRelativeViscosity();
    assertTrue(relVisc > 1.0, "Relative viscosity should be > 1");
  }
}
