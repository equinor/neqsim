package neqsim.PVTsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ConstantVolumeDepletionTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 150.5, 350.0);
    tempSystem.addComponent("nitrogen", 0.64);
    tempSystem.addComponent("CO2", 3.53);
    tempSystem.addComponent("methane", 70.78);
    tempSystem.addComponent("ethane", 8.94);
    tempSystem.addComponent("propane", 5.05);
    tempSystem.addComponent("i-butane", 0.85);
    tempSystem.addComponent("n-butane", 1.68);
    tempSystem.addComponent("i-pentane", 0.62);
    tempSystem.addComponent("n-pentane", 0.79);
    tempSystem.addComponent("n-hexane", 0.83);
    tempSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    tempSystem.addTBPfraction("C8", 1.06, 104.6 / 1000.0, 0.7602);
    tempSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
    tempSystem.addTBPfraction("C10", 0.57, 134 / 1000.0, 0.790);
    tempSystem.addTBPfraction("C11", 0.38, 155 / 1000.0, 0.795);
    tempSystem.addTBPfraction("C12", 0.37, 162 / 1000.0, 0.806);
    tempSystem.addTBPfraction("C13", 0.32, 177 / 1000.0, 0.824);
    tempSystem.addTBPfraction("C14", 0.27, 198 / 1000.0, 0.835);
    tempSystem.addTBPfraction("C15", 0.23, 202 / 1000.0, 0.84);
    tempSystem.addTBPfraction("C16", 0.19, 215 / 1000.0, 0.846);
    tempSystem.addTBPfraction("C17", 0.17, 234 / 1000.0, 0.84);
    tempSystem.addTBPfraction("C18", 0.13, 251 / 1000.0, 0.844);
    tempSystem.addTBPfraction("C19", 0.13, 270 / 1000.0, 0.854);
    tempSystem.addPlusFraction("C20", 0.62, 381 / 1000.0, 0.88);
    tempSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.setMixingRule("classic");
    tempSystem.setMultiPhaseCheck(true);

    SaturationPressure satsim = new SaturationPressure(tempSystem);
    satsim.setTemperature(150.5, "C");
    double satpres = satsim.calcSaturationPressure();
    assertEquals(407.08520, satpres, 0.001);

    ConstantVolumeDepletion CVDsim = new ConstantVolumeDepletion(tempSystem);
    CVDsim.setTemperature(150.5, "C");
    CVDsim
        .setPressures(new double[] {420.0, satpres, 338.9, 290.6, 242.3, 194.1, 145.8, 97.6, 49.3});
    CVDsim.runCalc();
    // CVDsim.setTemperaturesAndPressures(new double[] {313, 313, 313, 313},
    // new double[] {400, 300.0, 200.0, 100.0});
    double[][] expData = {{0.95, 0.99, 1.0, 1.1}};
    // CVDsim.setExperimentalData(expData);
    assertEquals(1.419637379033296, CVDsim.getCummulativeMolePercDepleted()[7], 0.001);

  }
}
