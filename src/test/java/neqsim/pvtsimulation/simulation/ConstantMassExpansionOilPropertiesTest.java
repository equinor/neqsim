package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks phase-named CCE properties against independent equilibrium states.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ConstantMassExpansionOilPropertiesTest {
  /**
   * Builds a synthetic live oil without petroleum characterization uncertainty.
   *
   * @return methane and decane fluid at 350 K
   */
  private SystemInterface liveOil() {
    SystemInterface fluid = new SystemPrEos(350.0, 350.0);
    fluid.addComponent("methane", 40.0);
    fluid.addComponent("n-decane", 60.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Checks single- and two-phase oil properties without changing legacy outputs. */
  @Test
  void oilPropertiesFollowNamedPhase() {
    SystemInterface fluid = liveOil();
    double[] pressures = {350.0, 100.0, 20.0};
    ConstantMassExpansion cce = new ConstantMassExpansion(fluid.clone());
    cce.setTemperature(350.0, "K");
    cce.setPressures(pressures);
    cce.runCalc();
    boolean sawSinglePhase = false;
    boolean sawTwoPhases = false;
    for (int i = 0; i < pressures.length; i++) {
      SystemInterface reference = fluid.clone();
      reference.setPressure(pressures[i]);
      new ThermodynamicOperations(reference).TPflash();
      reference.initProperties();
      assertTrue(reference.hasPhaseType("oil"));
      sawSinglePhase |= reference.getNumberOfPhases() == 1;
      sawTwoPhases |= reference.getNumberOfPhases() == 2;
      assertEquals(reference.getPhase("oil").getDensity("kg/m3"), cce.getOilDensity()[i], 1e-6);
      assertEquals(reference.getPhase("oil").getViscosity(), cce.getOilViscosity()[i], 1e-10);
      assertEquals(reference.getPhase(0).getDensity("kg/m3"), cce.getDensity()[i], 1e-6);
      if (reference.getNumberOfPhases() > 1) {
        assertTrue(Double.isNaN(cce.getViscosity()[i]));
        assertTrue(cce.getOilDensity()[i] > cce.getDensity()[i]);
      }
    }
    assertTrue(sawSinglePhase);
    assertTrue(sawTwoPhases);
  }

  /** Checks that a gas-only schedule has no fabricated oil properties. */
  @Test
  void absentOilIsNaN() {
    SystemInterface gas = new SystemPrEos(350.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    ConstantMassExpansion cce = new ConstantMassExpansion(gas);
    cce.setPressures(new double[] {50.0, 10.0});
    cce.setTemperature(350.0, "K");
    cce.runCalc();
    for (int i = 0; i < 2; i++) {
      assertTrue(Double.isNaN(cce.getOilDensity()[i]));
      assertTrue(Double.isNaN(cce.getOilViscosity()[i]));
      assertTrue(Double.isFinite(cce.getDensity()[i]));
    }
  }

  /** Checks that repeat calculations refresh the pressure-indexed oil arrays. */
  @Test
  void repeatedRunRefreshesOilArrays() {
    ConstantMassExpansion cce = new ConstantMassExpansion(liveOil());
    cce.setTemperature(350.0, "K");
    cce.setPressures(new double[] {350.0, 100.0, 20.0});
    cce.runCalc();
    cce.setPressures(new double[] {250.0});
    cce.runCalc();
    assertEquals(1, cce.getOilDensity().length);
    assertEquals(1, cce.getOilViscosity().length);
    SystemInterface reference = liveOil();
    reference.setPressure(250.0);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    assertEquals(reference.getPhase("oil").getDensity("kg/m3"), cce.getOilDensity()[0], 1e-6);
    assertEquals(reference.getPhase("oil").getViscosity(), cce.getOilViscosity()[0], 1e-10);
  }

  /** Checks that disappearance of oil on a reused simulation clears previously finite oil results. */
  @Test
  void oilToGasRerunClearsOilProperties() {
    SystemInterface fluid = liveOil();
    ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
    cce.setPressures(new double[] {350.0, 20.0});
    cce.setTemperature(350.0, "K");
    cce.runCalc();
    assertTrue(Double.isFinite(cce.getOilDensity()[1]));
    assertTrue(Double.isFinite(cce.getOilViscosity()[1]));
    fluid.setMolarComposition(new double[] {1.0, 0.0});
    cce.setPressures(new double[] {20.0, 10.0, 1.0});
    cce.setTemperature(450.0, "K");
    cce.runCalc();
    assertEquals(3, cce.getOilDensity().length);
    assertEquals(3, cce.getOilViscosity().length);
    assertTrue(fluid.hasPhaseType("gas"));
    assertTrue(!fluid.hasPhaseType("oil"));
    for (int i = 0; i < 3; i++) {
      assertTrue(Double.isNaN(cce.getOilDensity()[i]));
      assertTrue(Double.isNaN(cce.getOilViscosity()[i]));
      assertTrue(Double.isFinite(cce.getDensity()[i]));
      assertTrue(Double.isFinite(cce.getViscosity()[i]));
    }
  }

  /** Checks that an aqueous-only fluid cannot supply fabricated oil properties. */
  @Test
  void aqueousOnlyDoesNotSupplyOilProperties() {
    SystemInterface water = new SystemPrEos(350.0, 100.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    ConstantMassExpansion cce = new ConstantMassExpansion(water);
    cce.setPressures(new double[] {100.0, 20.0});
    cce.setTemperature(350.0, "K");
    cce.runCalc();
    assertTrue(water.hasPhaseType("aqueous"));
    for (int i = 0; i < 2; i++) {
      assertTrue(Double.isNaN(cce.getOilDensity()[i]));
      assertTrue(Double.isNaN(cce.getOilViscosity()[i]));
    }
  }
}
