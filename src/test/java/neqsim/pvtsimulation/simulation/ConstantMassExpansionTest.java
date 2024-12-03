package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ConstantMassExpansionTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 83.5, 350.0);
    tempSystem.addComponent("nitrogen", 0.39);
    tempSystem.addComponent("CO2", 0.3);
    tempSystem.addComponent("methane", 40.2);
    tempSystem.addComponent("ethane", 7.61);
    tempSystem.addComponent("propane", 7.95);
    tempSystem.addComponent("i-butane", 1.19);
    tempSystem.addComponent("n-butane", 4.08);
    tempSystem.addComponent("i-pentane", 1.39);
    tempSystem.addComponent("n-pentane", 2.15);
    tempSystem.addComponent("n-hexane", 2.79);
    tempSystem.addTBPfraction("C7", 4.28, 95 / 1000.0, 0.729);
    tempSystem.addTBPfraction("C8", 4.31, 106 / 1000.0, 0.749);
    tempSystem.addTBPfraction("C9", 3.08, 121 / 1000.0, 0.77);
    tempSystem.addTBPfraction("C10", 2.47, 135 / 1000.0, 0.786);
    tempSystem.addTBPfraction("C11", 1.91, 148 / 1000.0, 0.792);
    tempSystem.addTBPfraction("C12", 1.69, 161 / 1000.0, 0.804);
    tempSystem.addTBPfraction("C13", 1.59, 175 / 1000.0, 0.819);
    tempSystem.addTBPfraction("C14", 1.22, 196 / 1000.0, 0.833);
    tempSystem.addTBPfraction("C15", 1.25, 206 / 1000.0, 0.836);
    tempSystem.addTBPfraction("C16", 1.0, 225 / 1000.0, 0.843);
    tempSystem.addTBPfraction("C17", 0.99, 236 / 1000.0, 0.840);
    tempSystem.addTBPfraction("C18", 0.92, 245 / 1000.0, 0.846);
    tempSystem.addTBPfraction("C19", 0.6, 265 / 1000.0, 0.857);
    tempSystem.addPlusFraction("C20", 6.64, 453 / 1000.0, 0.918);
    tempSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.setMixingRule("classic");

    ConstantMassExpansion CMEsim = new ConstantMassExpansion(tempSystem);
    double[] pressures = new double[] {351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 197.3,
        189.3, 183.3, 165.0, 131.2, 108.3, 85.3, 55.6};

    CMEsim.setPressures(pressures);
    double[][] expData = {{0.95, 0.99, 1.12, 1.9}};
    CMEsim.setExperimentalData(expData);
    CMEsim.setTemperature(97.5, "C");
    // CMEsim.runTuning();
    CMEsim.runCalc();

    assertEquals(2.1873758493453708E-4, CMEsim.getIsoThermalCompressibility()[0], 0.00001);
    assertEquals(0.95756922523, CMEsim.getRelativeVolume()[0], 0.001);
    assertEquals(0.99569265437, CMEsim.getRelativeVolume()[6], 0.001);
    assertEquals(1.3572659252241415, CMEsim.getRelativeVolume()[12], 0.001);
    assertEquals(2.19059853842015, CMEsim.getYfactor()[12], 0.001);
  }
}

