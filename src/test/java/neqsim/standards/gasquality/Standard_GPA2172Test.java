package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_GPA2172 - Calculation of GHV, RD, Z, and GPM.
 *
 * @author ESOL
 */
class Standard_GPA2172Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
    testSystem.addComponent("methane", 0.85);
    testSystem.addComponent("ethane", 0.06);
    testSystem.addComponent("propane", 0.04);
    testSystem.addComponent("i-butane", 0.01);
    testSystem.addComponent("n-butane", 0.015);
    testSystem.addComponent("nitrogen", 0.015);
    testSystem.addComponent("CO2", 0.01);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test GHV calculation.
   */
  @Test
  void testCalculateGHV() {
    Standard_GPA2172 standard = new Standard_GPA2172(testSystem);
    standard.calculate();
    double ghv = standard.getValue("idealGrossHeatingValue");
    assertTrue(ghv > 900.0 && ghv < 1200.0, "GHV should be 900-1200 BTU/scf but was " + ghv);
  }

  /**
   * Test relative density.
   */
  @Test
  void testRelativeDensity() {
    Standard_GPA2172 standard = new Standard_GPA2172(testSystem);
    standard.calculate();
    double rd = standard.getValue("relativeDensity");
    // Natural gas RD is typically 0.55-0.80
    assertTrue(rd > 0.55 && rd < 0.80, "RD should be 0.55-0.80 but was " + rd);
  }

  /**
   * Test GPM calculation.
   */
  @Test
  void testGPM() {
    Standard_GPA2172 standard = new Standard_GPA2172(testSystem);
    standard.calculate();
    double gpmC2 = standard.getValue("GPMC2Plus");
    double gpmC3 = standard.getValue("GPMC3Plus");
    assertTrue(gpmC2 > 0, "GPM C2+ should be positive");
    assertTrue(gpmC3 > 0, "GPM C3+ should be positive");
    assertTrue(gpmC2 >= gpmC3, "GPM C2+ should be >= GPM C3+");
  }

  /**
   * Test compressibility summation factor.
   */
  @Test
  void testCompressibility() {
    Standard_GPA2172 standard = new Standard_GPA2172(testSystem);
    standard.calculate();
    double z = standard.getValue("Z");
    assertTrue(z > 0.99 && z < 1.01, "Z at standard conditions should be near 1.0 but was " + z);
  }
}
