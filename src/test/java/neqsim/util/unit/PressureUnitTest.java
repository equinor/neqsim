package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class PressureUnitTest extends neqsim.NeqSimTest {
  /**
   * <p>
   * testSetPressure
   * </p>
   */
  @Test
  public void testSetPressure() {
    neqsim.thermo.system.SystemPrEos fluid = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("water", 1.0);
    fluid.setPressure(0.0, "barg");

    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid);
    testOps.TPflash();
    fluid.initProperties();

    assertEquals(ThermodynamicConstantsInterface.referencePressure, fluid.getPressure("bara"),
        1e-4);
    assertEquals(0.0, fluid.getPressure("barg"), 1e-4);
    assertEquals(1.01325, fluid.getPressure("bara"), 1e-4);
    assertEquals(101325.0, fluid.getPressure("Pa"), 1e-4);
    assertEquals(101.3250, fluid.getPressure("kPa"), 1e-4);
    assertEquals(1.0, fluid.getPressure("atm"), 1e-4);
    assertEquals(14.6959488, fluid.getPressure("psi"), 1e-4);
    assertEquals(14.6959488, fluid.getPressure("psia"), 1e-4);
    assertEquals(0.0, fluid.getPressure("psig"), 1e-4);

    fluid.setPressure(11.0, "bara");
    testOps.TPflash();

    assertEquals(11.0, fluid.getPressure(), 1e-4);
    assertEquals(11.0 - 1.01325, fluid.getPressure("barg"), 1e-4);
    assertEquals(11.0, fluid.getPressure("bara"), 1e-4);
    assertEquals(11.0e5, fluid.getPressure("Pa"), 1e-4);
    assertEquals(11e2, fluid.getPressure("kPa"), 1e-4);
    assertEquals(10.856155933, fluid.getPressure("atm"), 1e-4);
    assertEquals(159.54151180, fluid.getPressure("psi"), 1e-4);
    assertEquals(159.54151180, fluid.getPressure("psia"), 1e-4);
    assertEquals(
        (11.0 - ThermodynamicConstantsInterface.referencePressure)
            / new PressureUnit(0.0, "bara").getConversionFactor("psi"),
        fluid.getPressure("psig"), 1e-4);
  }

  @Test
  public void testBargPsiaConversion() {
    PressureUnit unit = new PressureUnit(5.0, "barg");
    double psia = unit.getValue("psia");
    double expectedPsia =
        (5.0 + ThermodynamicConstantsInterface.referencePressure) / unit.getConversionFactor("psi");
    assertEquals(expectedPsia, psia, 1e-6);
    PressureUnit converter = new PressureUnit(0.0, "bara");
    assertEquals(5.0, converter.getValue(psia, "psia", "barg"), 1e-6);
  }

  @Test
  public void testPsigBaraConversion() {
    PressureUnit converter = new PressureUnit(0.0, "bara");
    double bara = converter.getValue(100.0, "psig", "bara");
    double expectedBara =
        100.0 * converter.getConversionFactor("psi")
            + ThermodynamicConstantsInterface.referencePressure;
    assertEquals(expectedBara, bara, 1e-6);
    assertEquals(100.0, converter.getValue(bara, "bara", "psig"), 1e-6);
  }

  @Test
  public void testAtmPsiConversion() {
    PressureUnit converter = new PressureUnit(0.0, "bara");
    double psi = converter.getValue(1.0, "atm", "psi");
    double expectedPsi =
        ThermodynamicConstantsInterface.referencePressure / converter.getConversionFactor("psi");
    assertEquals(expectedPsi, psi, 1e-6);
    assertEquals(1.0, converter.getValue(psi, "psi", "atm"), 1e-6);
  }
}

