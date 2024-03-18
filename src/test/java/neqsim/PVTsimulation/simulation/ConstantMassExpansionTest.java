package neqsim.PVTsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ConstantMassExpansionTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 73.0, 10.0);
    tempSystem.addComponent("nitrogen", 0.972);
    tempSystem.addComponent("CO2", 0.632);
    tempSystem.addComponent("methane", 95.111);
    tempSystem.addComponent("ethane", 2.553);
    tempSystem.addComponent("propane", 0.104);
    tempSystem.addComponent("i-butane", 0.121);
    tempSystem.addComponent("n-butane", 0.021);
    tempSystem.addComponent("i-pentane", 0.066);
    tempSystem.addComponent("n-pentane", 0.02);

    tempSystem.addTBPfraction("C6", 0.058, 86.18 / 1000.0, 664.0e-3);
    tempSystem.addTBPfraction("C7", 0.107, 96.0 / 1000.0, 738.0e-3);
    tempSystem.addTBPfraction("C8", 0.073, 107.0 / 1000.0, 765.0e-3);
    tempSystem.addTBPfraction("C9", 0.044, 121.0 / 1000.0, 781.0e-3);
    tempSystem.addPlusFraction("C10", 0.118, 190.0 / 1000.0, 813.30e-3);
    tempSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    tempSystem.getCharacterization().setLumpingModel("PVTlumpingModel");
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.init(0);

    ConstantMassExpansion CMEsim = new ConstantMassExpansion(tempSystem);
    CMEsim.setTemperaturesAndPressures(
        new double[] {273.15 + 73.9, 273.15 + 73.9, 273.15 + 73.9, 273.15 + 73.9, 273.15 + 73.9},
        new double[] {400, 300.0, 250.0, 200.0, 100.0});
    double[][] expData = {{0.95, 0.99, 1.12, 1.9}};
    CMEsim.setExperimentalData(expData);
    // CMEsim.runTuning();
    CMEsim.runCalc();
    assertEquals(2.300467604746, CMEsim.getRelativeVolume()[4], 0.001);
  }
}
