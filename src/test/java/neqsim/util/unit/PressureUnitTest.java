package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    assertEquals(5.0, new PressureUnit(psia, "psia").getValue("barg"), 1e-6);
  }

  @Test
  public void testPsigBaraConversion() {
    PressureUnit converter = new PressureUnit(0.0, "bara");
    PressureUnit psig = new PressureUnit(100.0, "psig");
    double bara = psig.getValue("bara");
    double expectedBara = 100.0 * converter.getConversionFactor("psi")
        + ThermodynamicConstantsInterface.referencePressure;
    assertEquals(expectedBara, bara, 1e-6);
    assertEquals(100.0, new PressureUnit(bara, "bara").getValue("psig"), 1e-6);
  }

  @Test
  public void testAtmPsiConversion() {
    PressureUnit converter = new PressureUnit(0.0, "bara");
    PressureUnit psi = new PressureUnit(1.0, "atm");
    double expectedPsi = ThermodynamicConstantsInterface.referencePressure / converter.getConversionFactor("psi");
    assertEquals(expectedPsi, psi.getValue("psi"), 1e-6);
  }

  @Test
  public void testSIValue() {
    assertEquals(101325.0, new PressureUnit(0.0, "barg").getSIvalue(), 1e-6);
    assertEquals(1100000.0, new PressureUnit(11.0, "bara").getSIvalue(), 1e-6);
  }

  @Test
  public void testMpaAndBarConversions() {
    assertEquals(1.0e6, new PressureUnit(1.0, "MPa").getSIvalue(), 1e-3);
    assertEquals(1.0e5, new PressureUnit(1.0, "bar").getSIvalue(), 1e-6);
    assertEquals(1.0, new PressureUnit(10.0, "bara").getValue("MPa"), 1e-9);
    assertEquals(10.0, new PressureUnit(1.0, "MPa").getValue("bara"), 1e-9);
    assertEquals(1.0, PressureUnit.convert(1.0, "bar", "bara"), 1e-9);
  }

  @Test
  public void testUnsupportedUnitThrows() {
    assertThrows(RuntimeException.class, () -> new PressureUnit(1.0, "torr").getSIvalue());
    assertThrows(RuntimeException.class, () -> new PressureUnit(1.0, "bara").getValue("torr"));
  }
}
