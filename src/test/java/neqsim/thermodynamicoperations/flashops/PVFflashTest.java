package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test PVFflash (Pressure-Vapor Fraction flash).
 */
class PVFflashTest {

  @Test
  void testPVFflashMidVaporFraction() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.PVFflash(0.5);

    double beta = fluid.getBeta();
    assertEquals(0.5, beta, 0.01, "Vapor fraction should be close to 0.5");
    assertTrue(fluid.getTemperature() > 100.0, "Temperature should be above 100 K");
    assertTrue(fluid.getTemperature() < 400.0, "Temperature should be below 400 K");
  }

  @Test
  void testPVFflashBubblePoint() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 20.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-pentane", 0.5);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.PVFflash(0.0);

    // At bubble point, vapor fraction should be ~0
    assertTrue(fluid.getTemperature() > 50.0, "Bubble point temperature must be positive");
  }

  @Test
  void testPVFflashDewPoint() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 20.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-pentane", 0.5);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.PVFflash(1.0);

    // At dew point, vapor fraction should be ~1
    assertTrue(fluid.getTemperature() > 50.0, "Dew point temperature must be positive");
  }

  @Test
  void testPVFflashConsistency() {
    // Flash at VF=0.5, note the temperature, then do a TP flash at that T
    // and verify beta ~ 0.5
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 15.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.PVFflash(0.3);
    double tempAfterPVF = fluid.getTemperature();

    // Now do a fresh TP flash at the found temperature
    SystemInterface fluid2 = new SystemSrkEos(tempAfterPVF, 15.0);
    fluid2.addComponent("methane", 0.8);
    fluid2.addComponent("ethane", 0.15);
    fluid2.addComponent("propane", 0.05);
    fluid2.setMixingRule("classic");

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();
    fluid2.init(2);

    assertEquals(0.3, fluid2.getBeta(), 0.02,
        "TP flash at PVF temperature should give same vapor fraction");
  }
}
