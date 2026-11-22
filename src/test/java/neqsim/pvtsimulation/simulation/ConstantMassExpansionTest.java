package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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
    double[] pressures = new double[] {351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 197.3, 189.3, 183.3, 165.0,
        131.2, 108.3, 85.3, 55.6};

    CMEsim.setPressures(pressures);
    double[][] expData = {{0.95, 0.99, 1.12, 1.9}};
    CMEsim.setExperimentalData(expData);
    CMEsim.setTemperature(97.5, "C");
    // CMEsim.runTuning();
    CMEsim.runCalc();

    assertEquals(2.1873758493453708E-4, CMEsim.getIsoThermalCompressibility()[0], 0.00001);
    // Relative volumes use Peneloux-corrected (physical-property) volumes, see #5195.
    assertEquals(0.9551658657673409, CMEsim.getRelativeVolume()[0], 0.001);
    assertEquals(0.99569265437, CMEsim.getRelativeVolume()[6], 0.001);
    assertEquals(1.3787841166851758, CMEsim.getRelativeVolume()[12], 0.001);
    assertEquals(2.071653694591627, CMEsim.getYfactor()[12], 0.001);
  }

  /**
   * The CME saturation state must be the single-phase saturated fluid, so its volume and compressibility are those of
   * the whole cell and never of a trace incipient phase (#5209, reported by Patrick Mockridge).
   */
  @Test
  void testSaturationStateIsSinglePhase() {
    SystemInterface fluid = new SystemSrkEos(350.0, 300.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("nC10", 0.30);
    fluid.setMixingRule(2);
    ConstantMassExpansion cme = new ConstantMassExpansion(fluid);
    cme.setTemperaturesAndPressures(new double[] {350, 350, 350, 350, 350}, new double[] {300, 200, 150, 100, 50});
    cme.runCalc();

    SystemInterface saturated = fluid.clone();
    saturated.setTemperature(350.0);
    saturated.setPressure(cme.getSaturationPressure());
    new ThermodynamicOperations(saturated).TPflash();
    assertEquals(1, saturated.getNumberOfPhases(), "saturation state is single phase");
    saturated.initPhysicalProperties();
    assertEquals(saturated.getCorrectedVolume() * 1.0e5, cme.saturationVolume,
        saturated.getCorrectedVolume() * 1.0e5 * 1e-6);
    double liquidCompressibility = -1.0 / saturated.getPhase(0).getVolume() / saturated.getPhase(0).getdPdVTn();
    assertEquals(liquidCompressibility, cme.getSaturationIsoThermalCompressibility(),
        Math.abs(liquidCompressibility) * 1e-6);
    assertEquals(1.0, cme.getRelativeVolume()[0], 0.01);
  }
}
