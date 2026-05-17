package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_ISO18453 - Water dew point of natural gas.
 *
 * @author ESOL
 */
class Standard_ISO18453Test extends neqsim.NeqSimTest {

  /**
   * Test water dew point calculation.
   */
  @Test
  void testWaterDewPoint() {
    SystemInterface gas = new SystemSrkEos(273.15 + 20.0, 70.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.02);
    gas.addComponent("nitrogen", 0.02);
    gas.addComponent("water", 0.0001);
    gas.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Standard_ISO18453 standard = new Standard_ISO18453(gas);
    standard.setPressure(70.0);
    standard.calculate();

    double dewPoint = standard.getValue("dewPointTemperature");
    // Water dew point for trace water should be well below 0 C
    assertTrue(dewPoint < 20.0,
        "Water dew point should be < 20 C for trace water but was " + dewPoint);
  }

  /**
   * Test dew point unit conversion.
   */
  @Test
  void testDewPointUnits() {
    SystemInterface gas = new SystemSrkEos(273.15 + 20.0, 70.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("water", 0.001);
    gas.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Standard_ISO18453 standard = new Standard_ISO18453(gas);
    standard.setPressure(70.0);
    standard.calculate();

    double dewC = standard.getValue("dewPointTemperature", "C");
    double dewK = standard.getValue("dewPointTemperature", "K");
    assertTrue(Math.abs(dewK - (dewC + 273.15)) < 0.1, "K and C conversion should be consistent");
  }
}
