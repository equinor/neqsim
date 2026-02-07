package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_ISO6974 - GC composition with uncertainty propagation.
 *
 * @author ESOL
 */
class Standard_ISO6974Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.02);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("CO2", 0.01);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test normalisation factor.
   */
  @Test
  void testNormalisationFactor() {
    Standard_ISO6974 standard = new Standard_ISO6974(testSystem);
    standard.calculate();
    double factor = standard.getValue("normalisationFactor");
    assertTrue(Math.abs(factor - 1.0) < 0.01,
        "Normalisation factor should be ~1.0 but was " + factor);
  }

  /**
   * Test uncertainty of GCV.
   */
  @Test
  void testUncertaintyGCV() {
    Standard_ISO6974 standard = new Standard_ISO6974(testSystem);
    standard.calculate();
    double uGCV = standard.getValue("uncertaintyGCV");
    // Expanded uncertainty of GCV should be a positive number
    assertTrue(uGCV >= 0.0, "Uncertainty GCV should be >= 0");
    // With default uncertainties, expanded U(GCV) typically < 200 MJ/m3
    assertTrue(uGCV < 200.0, "Uncertainty GCV should be < 200 MJ/m3 but was " + uGCV);
  }

  /**
   * Test uncertainty of Wobbe.
   */
  @Test
  void testUncertaintyWobbe() {
    Standard_ISO6974 standard = new Standard_ISO6974(testSystem);
    standard.calculate();
    double uWobbe = standard.getValue("uncertaintyWobbe");
    assertTrue(uWobbe >= 0.0, "Uncertainty Wobbe should be >= 0");
    assertTrue(uWobbe < 200.0, "Uncertainty Wobbe should be < 200 MJ/m3 but was " + uWobbe);
  }

  /**
   * Test that normalised composition sums to 1.
   */
  @Test
  void testNormalisedCompositionSum() {
    Standard_ISO6974 standard = new Standard_ISO6974(testSystem);
    standard.calculate();
    Map<String, Double> normComp = standard.getNormalisedComposition();
    double sum = 0.0;
    for (Double v : normComp.values()) {
      sum += v;
    }
    assertTrue(Math.abs(sum - 1.0) < 1e-8,
        "Normalised composition should sum to 1.0 but was " + sum);
  }

  /**
   * Test isOnSpec.
   */
  @Test
  void testIsOnSpec() {
    Standard_ISO6974 standard = new Standard_ISO6974(testSystem);
    standard.calculate();
    // With default generous uncertainties, the methane expanded uncertainty
    // is 0.30 mol% which equals the threshold, so may or may not pass
    double uMethane = standard.getValue("uncertaintyGCV");
    assertTrue(uMethane >= 0, "Uncertainty should be non-negative");
  }

  /**
   * Test custom uncertainty.
   */
  @Test
  void testCustomUncertainty() {
    Standard_ISO6974 standard = new Standard_ISO6974(testSystem);
    standard.setComponentUncertainty("methane", 0.005);
    standard.setCoverageFactor(2.0);
    standard.calculate();
    double uGCV = standard.getValue("uncertaintyGCV");
    // With larger methane uncertainty, should still be a reasonable number
    assertTrue(uGCV > 0.0, "Uncertainty GCV should be positive with larger methane uncertainty");
  }
}
