package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class DifferentialLiberationTest {
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

    SimulationInterface satPresSim = new SaturationPressure(tempSystem);
    satPresSim.setTemperature(97.5, "C");
    satPresSim.run();
    assertEquals(193.24093, satPresSim.getThermoSystem().getPressure(), 0.1);
    // tempSystem.prettyPrint();

    double[] pressures = new double[] {351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 179.1,
        154.6, 132.1, 109.0, 78.6, 53.6, 22.0, 1.0};
    DifferentialLiberation differentialLiberation = new DifferentialLiberation(tempSystem);
    differentialLiberation.setPressures(pressures);
    differentialLiberation.setTemperature(97.5, "C");
    differentialLiberation.runCalc();

    assertEquals(1.689644811955, differentialLiberation.getBo()[0], 0.001);
    assertEquals(212.71942595049242, differentialLiberation.getRs()[0], 0.001);
    assertEquals(677.5970918499921, differentialLiberation.getOilDensity()[0], 0.001);
    assertEquals(1.7616805, differentialLiberation.getBo()[pressures.length - 9], 0.001);
    assertEquals(1.3111545517, differentialLiberation.getBo()[pressures.length - 2], 0.001);
    assertEquals(55.10252632079461, differentialLiberation.getRs()[pressures.length - 2], 0.001);
    assertEquals(0.0556167850, differentialLiberation.getBg()[pressures.length - 2], 0.001);
    assertEquals(1.0533007759, differentialLiberation.getBo()[pressures.length - 1], 0.001);
    assertEquals(0.0, differentialLiberation.getRs()[pressures.length - 1], 0.001);
    assertEquals(805.6468027140055, differentialLiberation.getOilDensity()[pressures.length - 1],
        0.001);
  }
}
