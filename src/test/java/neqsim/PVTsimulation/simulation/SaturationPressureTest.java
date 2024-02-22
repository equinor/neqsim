package neqsim.PVTsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class SaturationPressureTest extends neqsim.NeqSimTest {
  @BeforeAll
  static void setUpBeforeClass() throws Exception {}


  @Test
  void testCalcSaturationPressure() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 20, 10.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 0.59);
    tempSystem.addComponent("methane", 87.42);
    tempSystem.addComponent("ethane", 3.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 0.38);
    tempSystem.addTBPfraction("C7", 0.05, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 0.069, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 0.014, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.0078, 135.3 / 1000.0, 0.7864);
    tempSystem.setMixingRule(2);
    SimulationInterface satPresSim = new SaturationPressure(tempSystem);
    satPresSim.run();
    assertEquals(satPresSim.getThermoSystem().getPressure(), 126.1631355285644, 0.1);
  }
}
