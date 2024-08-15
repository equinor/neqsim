package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

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
    assertEquals(-0.0040512245077, fluid.getPressure("psig"), 1e-4);

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
    assertEquals(144.841511503000, fluid.getPressure("psig"), 1e-4);

  }
}

