package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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


  /**
   * ~Seawater: 35 g NaCl per 1 kg water (≈3.5 wt%) at 25 °C, 1 bar. Expected ≈ 0.94 mPa·s → 9.4e-4
   * Pa·s (tolerance ±1.5e-4).
   */
  @Test
  void testNaCl_35gPerKg_25C_1bar() {
    SystemInterface sys = new SystemSrkEos(298.15, 1.0); // K, bar
    // 35 g NaCl per 1000 g water:
    double nWater = 1000.0 / 18.01528; // mol
    double nNaCl = 35.0 / 58.443; // mol

    sys.addComponent("water", nWater);
    sys.addComponent("NaCl", nNaCl);
    sys.init(0);
    sys.setPhysicalPropertyModel(PhysicalPropertyModel.SALT_WATER);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash(); // ensure single liquid phase
    sys.initPhysicalProperties();

    double mu = sys.getPhase(PhaseType.AQUEOUS).getPhysicalProperties().getViscosity(); // Pa·s
    assertEquals(9.38e-4, mu, 1.5e-6);
  }

  /**
   * 30 wt% CaCl2 brine at 25 °C, 1 bar. Expected ≈ 3.07 mPa·s → 3.07e-3 Pa·s (tolerance ±6e-4).
   */
  @Test
  void testCaCl2_30wt_25C_1bar() {
    SystemInterface sys = new SystemSrkEos(298.15, 1.0); // K, bar
    // Choose 700 g water + 300 g CaCl2 → 30 wt% CaCl2:
    double nWater = 700.0 / 18.01528; // mol
    double nCaCl2 = 300.0 / 110.98; // mol (CaCl2 M = 110.98 g/mol)

    sys.addComponent("water", nWater);
    sys.addComponent("CaCl2", nCaCl2);
    sys.setMixingRule("classic");
    sys.init(0);

    sys.setPhysicalPropertyModel(PhysicalPropertyModel.SALT_WATER);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();
    double mu = sys.getPhase("aqueous").getPhysicalProperties().getViscosity(); // Pa·s
    assertEquals(3.07e-3, mu, 6e-6);

    sys.addComponent("methane", 1);
    sys.setMixingRule("classic");

    ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();
    mu = sys.getPhase("aqueous").getPhysicalProperties().getViscosity(); // Pa·s
    assertEquals(3.07e-3, mu, 6e-6);
  }
}
