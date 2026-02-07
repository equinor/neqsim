package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_ISO14687 - Hydrogen fuel quality.
 *
 * @author ESOL
 */
class Standard_ISO14687Test extends neqsim.NeqSimTest {

  /**
   * Test Grade A (PEM fuel cell) hydrogen.
   */
  @Test
  void testGradeAPureHydrogen() {
    SystemInterface h2System = new SystemSrkEos(273.15 + 15.0, 1.01325);
    h2System.addComponent("hydrogen", 0.999999);
    h2System.addComponent("nitrogen", 0.000001);
    h2System.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(h2System);
    ops.TPflash();

    Standard_ISO14687 standard = new Standard_ISO14687(h2System);
    standard.setGrade("A");
    standard.calculate();

    double purity = standard.getValue("purity");
    assertTrue(purity > 99.97, "High purity H2 should be > 99.97% but was " + purity);
    assertTrue(standard.isOnSpec(), "Very pure H2 should pass Grade A");
  }

  /**
   * Test that impure hydrogen fails Grade A.
   */
  @Test
  void testImpureHydrogenFailsGradeA() {
    SystemInterface h2System = new SystemSrkEos(273.15 + 15.0, 1.01325);
    h2System.addComponent("hydrogen", 0.98);
    h2System.addComponent("nitrogen", 0.01);
    h2System.addComponent("oxygen", 0.005);
    h2System.addComponent("CO2", 0.005);
    h2System.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(h2System);
    ops.TPflash();

    Standard_ISO14687 standard = new Standard_ISO14687(h2System);
    standard.setGrade("A");
    standard.calculate();

    double purity = standard.getValue("purity");
    assertTrue(purity < 99.97, "Impure H2 purity should be < 99.97%");
    assertFalse(standard.isOnSpec(), "Impure H2 should fail Grade A");
  }

  /**
   * Test Grade D (industrial) is more lenient.
   */
  @Test
  void testGradeDIndustrial() {
    SystemInterface h2System = new SystemSrkEos(273.15 + 15.0, 1.01325);
    h2System.addComponent("hydrogen", 0.995);
    h2System.addComponent("nitrogen", 0.005);
    h2System.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(h2System);
    ops.TPflash();

    Standard_ISO14687 standard = new Standard_ISO14687(h2System);
    standard.setGrade("D");
    standard.calculate();

    double purity = standard.getValue("purity");
    assertTrue(purity > 99.0, "Industrial H2 should have reasonable purity");
  }

  /**
   * Test units.
   */
  @Test
  void testUnits() {
    SystemInterface h2System = new SystemSrkEos(273.15 + 15.0, 1.01325);
    h2System.addComponent("hydrogen", 0.999);
    h2System.addComponent("nitrogen", 0.001);
    h2System.setMixingRule("classic");

    Standard_ISO14687 standard = new Standard_ISO14687(h2System);
    assertTrue("mol%".equals(standard.getUnit("purity")) || "%".equals(standard.getUnit("purity")));
  }
}
