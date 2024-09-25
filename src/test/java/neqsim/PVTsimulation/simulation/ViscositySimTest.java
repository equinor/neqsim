package neqsim.PVTsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ViscositySimTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(298.0, 10.0);
    tempSystem.addComponent("n-heptane", 6.78);
    tempSystem.addPlusFraction("C20", 10.62, 100.0 / 1000.0, 0.73);
    tempSystem.setMixingRule(2);
    tempSystem.init(0);

    ViscositySim sepSim = new ViscositySim(tempSystem);
    double[] temps = {300.15, 293.15, 283.15, 273.15, 264.15};
    double[] pres = {5, 5, 5, 5.0, 5.0};
    sepSim.setTemperaturesAndPressures(temps, pres);
    sepSim.runCalc();

    double[][] expData = {{2e-4, 3e-4, 4e-4, 5e-4, 6e-4},};
    sepSim.setExperimentalData(expData);
    // sepSim.runTuning();
    sepSim.runCalc();
    assertEquals(4.443002015621749E-4, sepSim.getOilViscosity()[0], 0.000001);
  }
}
