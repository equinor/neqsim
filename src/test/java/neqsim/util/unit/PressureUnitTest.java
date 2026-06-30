package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;

class PressureUnitTest extends neqsim.NeqSimTest {
  /**
   * testSetPressure
   */
  @Test
  public void testSetPressure() {
    neqsim.thermo.system.SystemPrEos fluid = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setPressure(0.0, "barg");

    // ThermodynamicOperations testOps = new ThermodynamicOperations(fluid);
    // testOps.TPflash();
    // fluid.initProperties();

    assertEquals(ThermodynamicConstantsInterface.referencePressure, fluid.getPressure("bara"), 1e-4);
    assertEquals(0.0, fluid.getPressure("barg"), 1e-4);
    assertEquals(1.01325, fluid.getPressure("bara"), 1e-4);
    assertEquals(101325.0, fluid.getPressure("Pa"), 1e-4);
    assertEquals(101.3250, fluid.getPressure("kPa"), 1e-4);
    assertEquals(1.0, fluid.getPressure("atm"), 1e-4);
    assertEquals(14.6959488, fluid.getPressure("psi"), 1e-4);
    assertEquals(14.6959488, fluid.getPressure("psia"), 1e-4);
    assertEquals(0.0, fluid.getPressure("psig"), 1e-4);

    fluid.setPressure(11.0, "bara");
    // testOps.TPflash();

    assertEquals(11.0, fluid.getPressure(), 1e-4);
    assertEquals(11.0 - 1.01325, fluid.getPressure("barg"), 1e-4);
    assertEquals(11.0, fluid.getPressure("bara"), 1e-4);
    assertEquals(11.0e5, fluid.getPressure("Pa"), 1e-4);
    assertEquals(11e2, fluid.getPressure("kPa"), 1e-4);
    assertEquals(10.856155933, fluid.getPressure("atm"), 1e-4);
    assertEquals(159.54151180, fluid.getPressure("psi"), 1e-4);
    assertEquals(159.54151180, fluid.getPressure("psia"), 1e-4);
    // psig = (11.0 bara - reference) converted to psi
    PressureUnit checker = new PressureUnit(11.0, "bara");
    assertEquals(checker.getValue("psig"), fluid.getPressure("psig"), 1e-4);
  }

  @Test
  public void testBargPsiaConversion() {
    PressureUnit unit = new PressureUnit(5.0, "barg");
    double psia = unit.getValue("psia");
    double expectedPsia = new PressureUnit(5.0 + ThermodynamicConstantsInterface.referencePressure, "bara")
        .getValue("psia");
    assertEquals(expectedPsia, psia, 1e-6);
    assertEquals(5.0, PressureUnit.convert(psia, "psia", "barg"), 1e-6);
  }

  @Test
  public void testPsigBaraConversion() {
    // Convert 100 psig to bara
    double bara = new PressureUnit(100.0, "psig").getValue("bara");
    double expectedBara = (100.0 + ThermodynamicConstantsInterface.referencePressure / 0.0689475729317831)
        * 0.0689475729317831;
    assertEquals(expectedBara, bara, 1e-6);
    assertEquals(100.0, PressureUnit.convert(bara, "bara", "psig"), 1e-6);
  }

  @Test
  public void testAtmPsiConversion() {
    double psi = new PressureUnit(1.0, "atm").getValue("psi");
    double expectedPsi = ThermodynamicConstantsInterface.referencePressure / 0.0689475729317831;
    assertEquals(expectedPsi, psi, 1e-6);
    assertEquals(1.0, PressureUnit.convert(psi, "psi", "atm"), 1e-6);
  }

  @Test
  public void testSIValue() {
    assertEquals(101325.0, new PressureUnit(0.0, "barg").getSIvalue(), 1e-6);
    assertEquals(1100000.0, new PressureUnit(11.0, "bara").getSIvalue(), 1e-6);
  }
}
