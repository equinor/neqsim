package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_GPA2145 - Physical constants for hydrocarbons.
 *
 * @author ESOL
 */
class Standard_GPA2145Test extends neqsim.NeqSimTest {
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
    testSystem.addComponent("propane", 0.03);
    testSystem.addComponent("nitrogen", 0.015);
    testSystem.addComponent("CO2", 0.005);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test molar mass calculation.
   */
  @Test
  void testMolarMass() {
    Standard_GPA2145 standard = new Standard_GPA2145(testSystem);
    standard.calculate();
    double molarMass = standard.getValue("molarMass");
    // Methane dominant gas: should be around 17-20 g/mol
    assertTrue(molarMass > 16.0 && molarMass < 22.0,
        "Molar mass should be 16-22 g/mol but was " + molarMass);
  }

  /**
   * Test pure component molar mass.
   */
  @Test
  void testGrossHeatingValue() {
    Standard_GPA2145 standard = new Standard_GPA2145(testSystem);
    standard.calculate();
    double ghv = standard.getValue("GHV");
    // GHV for methane-rich gas should be ~1000 BTU/scf
    assertTrue(ghv > 900.0 && ghv < 1200.0, "GHV should be 900-1200 BTU/ft3 but was " + ghv);
  }

  /**
   * Test mixture heating value.
   */
  @Test
  void testRelativeDensity() {
    Standard_GPA2145 standard = new Standard_GPA2145(testSystem);
    standard.calculate();
    double rd = standard.getValue("relativeDensity");
    assertTrue(rd > 0.5 && rd < 0.8, "Relative density should be 0.5-0.8 but was " + rd);
  }

  /**
   * Test units.
   */
  @Test
  void testUnits() {
    Standard_GPA2145 standard = new Standard_GPA2145(testSystem);
    assertEquals("g/mol", standard.getUnit("molarMass"));
    assertEquals("BTU/ft3", standard.getUnit("GHV"));
  }
}
