package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the fused UMR-CPA equation of state on multicomponent associating mixtures.
 *
 * <p>
 * These tests exercise the combination of the Peng-Robinson physical term, the UMR universal mixing
 * rule (UNIFAC group contribution), the 3-parameter Mathias-Copeman alpha and the CPA association
 * term on systems relevant to natural-gas dehydration (water, methane, glycols).
 * </p>
 *
 * @author NeqSim
 */
class SystemUMRCPAEoSMixtureTest extends neqsim.NeqSimTest {
  /**
   * Two-phase water + methane flash should converge and place almost all methane in the gas phase
   * and almost all water in the aqueous liquid phase.
   */
  @Test
  void testWaterMethaneFlash() {
    SystemInterface fluid = new SystemUMRCPAEoS(298.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.init(3);

    assertTrue(fluid.getNumberOfPhases() >= 2, "expected at least a gas and a liquid phase");

    int gasPhase = 0;
    int aqueousPhase = fluid.getNumberOfPhases() - 1;

    double methaneInGas = fluid.getPhase(gasPhase).getComponent("methane").getx();
    double waterInAqueous = fluid.getPhase(aqueousPhase).getComponent("water").getx();

    assertTrue(methaneInGas > 0.9, "gas phase should be methane rich, was " + methaneInGas);
    assertTrue(waterInAqueous > 0.9, "liquid phase should be water rich, was " + waterInAqueous);

    // Water content in the gas phase should be a small positive mole fraction.
    double waterInGas = fluid.getPhase(gasPhase).getComponent("water").getx();
    assertTrue(waterInGas > 0.0 && waterInGas < 0.01,
        "gas-phase water content out of physical range: " + waterInGas);
  }

  /**
   * The dissolved water content of the gas should increase with temperature at fixed pressure, a
   * basic physical requirement for any dehydration model.
   */
  @Test
  void testGasWaterContentIncreasesWithTemperature() {
    double waterLowT = gasWaterMoleFraction(283.15, 70.0);
    double waterHighT = gasWaterMoleFraction(313.15, 70.0);
    assertTrue(waterHighT > waterLowT,
        "gas water content should increase with temperature: " + waterLowT + " -> " + waterHighT);
  }

  /**
   * The dissolved water content of the gas should decrease with pressure at fixed temperature.
   */
  @Test
  void testGasWaterContentDecreasesWithPressure() {
    double waterLowP = gasWaterMoleFraction(298.15, 30.0);
    double waterHighP = gasWaterMoleFraction(298.15, 120.0);
    assertTrue(waterHighP < waterLowP,
        "gas water content should decrease with pressure: " + waterLowP + " -> " + waterHighP);
  }

  /**
   * Triethylene glycol + water should mix as a single associating liquid phase and the CPA
   * association term should converge.
   */
  @Test
  void testTegWaterLiquid() {
    SystemInterface fluid = new SystemUMRCPAEoS(298.15, 1.0);
    fluid.addComponent("TEG", 0.7);
    fluid.addComponent("water", 0.3);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    assertEquals(1, fluid.getNumberOfPhases(), "TEG/water should be a single liquid phase");
    double density = fluid.getPhase(0).getDensity("kg/m3");
    assertTrue(density > 900.0 && density < 1200.0,
        "TEG/water liquid density out of expected range: " + density);
  }

  /**
   * Helper that returns the gas-phase water mole fraction for a water saturated methane stream at
   * the requested temperature and pressure.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return gas-phase water mole fraction
   */
  private double gasWaterMoleFraction(double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemUMRCPAEoS(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.97);
    fluid.addComponent("water", 0.03);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.init(3);
    return fluid.getPhase(0).getComponent("water").getx();
  }
}
