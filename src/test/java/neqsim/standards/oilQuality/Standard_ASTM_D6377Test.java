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
    testSystem.addComponent("propane", 0.06538);
    testSystem.addComponent("n-pentane", 0.1545);
    testSystem.addComponent("nC10", 0.545);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(1.10455465, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(1.666298367, standard.getValue("TVP", "bara"), 1e-3);
  }

  @Test
  void testCalculate2() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.026538);
    testSystem.addComponent("ethane", 0.16538);
    testSystem.addComponent("propane", 0.26538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.addComponent("nC10", 0.545);
    testSystem.addTBPfraction("C11", 0.545, 145.0 / 1000.0, 0.82);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.setPressure(100.0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setMethodRVP("VPCR4");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(3.604002003478, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(7.8448385024, standard.getValue("TVP", "bara"), 1e-3);

    standard.setMethodRVP("RVP_ASTM_D6377");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(3.00573767, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(7.8448385024, standard.getValue("TVP", "bara"), 1e-3);
  }
}
