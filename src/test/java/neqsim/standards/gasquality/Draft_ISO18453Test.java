package neqsim.standards.gasquality;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.standards.StandardInterface;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;

public class Draft_ISO18453Test extends neqsim.NeqSimTest {
  @Test
  void testCalculate() {
    SystemInterface testSystem = new SystemGERGwaterEos(273.15 - 5.0, 20.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("water", 0.0000051);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(8);

    testSystem.init(0);

    StandardInterface standard = new Draft_ISO18453(testSystem);
    standard.setSalesContract("Base");
    standard.calculate();

    Assertions.assertEquals(-21.775841183117222, standard.getValue("dewPointTemperature"),
        1e-8);
    Assertions.assertEquals("C", standard.getUnit("dewPointTemperature"));

    testSystem.setStandard("Draft_ISO18453");
    testSystem.getStandard().setSalesContract("Base");
    testSystem.getStandard().calculate();

    Assertions.assertEquals(-21.775841183117222,
        testSystem.getStandard().getValue("dewPointTemperature"), 1e-8);
    Assertions.assertEquals("C", testSystem.getStandard().getUnit("dewPointTemperature"));

    Assertions.assertEquals(70.0, testSystem.getStandard().getValue("pressure"));
    Assertions.assertEquals("bar", testSystem.getStandard().getUnit("pressureUnit"));

    Assertions.assertTrue(testSystem.getStandard().isOnSpec());
  }
}
