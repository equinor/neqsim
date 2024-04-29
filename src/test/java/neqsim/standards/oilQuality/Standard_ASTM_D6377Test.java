package neqsim.standards.oilQuality;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class Standard_ASTM_D6377Test {
  @Test
  void testCalculate() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.0006538);
    testSystem.addComponent("ethane", 0.006538);
    testSystem.addComponent("propane", 0.006538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(0.7298246193, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(1.8710732396722, standard.getValue("TVP", "bara"), 1e-3);
  }
}
