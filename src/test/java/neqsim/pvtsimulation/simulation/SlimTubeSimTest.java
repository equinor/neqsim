package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class SlimTubeSimTest {
  @Test
  void testRun() {
    SystemInterface gasSystem = new SystemSrkEos(298.0, 200.0);
    gasSystem.addComponent("CO2", 10.0);
    // gasSystem.addComponent("ethane", 2.0);
    gasSystem.setMixingRule(2);

    SystemInterface oilSystem = new SystemSrkEos(298.0, 200.0);
    oilSystem.addComponent("CO2", 0.1);
    oilSystem.addComponent("methane", 1.5);
    oilSystem.addComponent("ethane", 1.5);
    oilSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    oilSystem.addTBPfraction("C8", 1.06, 104.6 / 1000.0, 0.7602);
    oilSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
    oilSystem.addTBPfraction("C10", 0.57, 133.0 / 1000.0, 0.79);
    oilSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
    oilSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806);
    oilSystem.addTBPfraction("C13", 0.32, 177.0 / 1000.0, 0.824);
    oilSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835);
    oilSystem.addTBPfraction("C15", 0.23, 202.0 / 1000.0, 0.84);
    oilSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
    oilSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84);
    oilSystem.addTBPfraction("C18", 0.13, 251.0 / 1000.0, 0.844);
    oilSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854);
    oilSystem.addPlusFraction("C20", 20.62, 381.0 / 1000.0, 0.88);
    oilSystem.getCharacterization().characterisePlusFraction();
    oilSystem.setMixingRule(2);

    SlimTubeSim sepSim = new SlimTubeSim(oilSystem, gasSystem);
    sepSim.setTemperature(273.15 + 70);
    sepSim.setPressure(380.0);
    sepSim.setNumberOfSlimTubeNodes(40);
    sepSim.run();
    assertEquals(242.3, sepSim.getPressures()[3], 0.001);
  }
}
