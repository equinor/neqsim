package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies that neutral multiphase TP flashes are invariant to total feed inventory.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TPflashMultiphaseInventoryScaleTest {

  /**
   * Requires strict conservation, equilibrium, and repeatability over nearby pressure changes.
   */
  @Test
  void nearbyPressureReturnLifecycleIsInventoryScaleInvariant() {
    for (double inventory : new double[] {0.01, 1.0, 100.0}) {
      SystemInterface fluid = createFluid(inventory);
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
      operations.TPflash();

      for (double pressure : new double[] {81.1, 81.15, 81.2, 81.0, 81.1, 81.1}) {
        fluid.setPressure(pressure);
        operations.TPflash();
        assertStrictEndpoint(fluid, inventory, "pressure=" + pressure);
      }

      double firstPhaseFraction = fluid.getBeta(0);
      double firstPhaseMethaneFraction = fluid.getPhase(0).getComponent(0).getx();
      operations.TPflash();
      assertStrictEndpoint(fluid, inventory, "final repeat");
      assertEquals(firstPhaseFraction, fluid.getBeta(0), 1.0e-12);
      assertEquals(firstPhaseMethaneFraction, fluid.getPhase(0).getComponent(0).getx(), 1.0e-12);
    }
  }

  /**
   * Creates the frozen Peng-Robinson binary lifecycle case.
   *
   * @param inventory total feed inventory in mol
   * @return configured fluid
   */
  private SystemInterface createFluid(double inventory) {
    SystemInterface fluid = new SystemPrEos(194.0, 81.0);
    fluid.addComponent("methane", 0.7 * inventory);
    fluid.addComponent("n-heptane", 0.3 * inventory);
    fluid.setMixingRule("classic");
    for (int phaseIndex = 0; phaseIndex < fluid.getMaxNumberOfPhases(); phaseIndex++) {
      ((EosMixingRulesInterface) fluid.getPhase(phaseIndex).getMixingRule()).setBinaryInteractionParameter(0, 1, 0.05);
    }
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Checks topology, normalization, original-feed closure, and phase equilibrium.
   *
   * @param fluid endpoint to inspect
   * @param inventory original total feed inventory in mol
   * @param stage lifecycle stage
   */
  private void assertStrictEndpoint(SystemInterface fluid, double inventory, String stage) {
    assertEquals(2, fluid.getNumberOfPhases(), "inventory=" + inventory + ", " + stage);
    double phaseFractionTotal = 0.0;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      phaseFractionTotal += fluid.getBeta(phaseIndex);
      double compositionTotal = 0.0;
      for (int componentIndex = 0; componentIndex < 2; componentIndex++) {
        compositionTotal += fluid.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(1.0, compositionTotal, 1.0e-12, "phase composition " + phaseIndex);
    }
    assertEquals(1.0, phaseFractionTotal, 1.0e-12, "phase fractions");

    for (int componentIndex = 0; componentIndex < 2; componentIndex++) {
      assertComponentClosure(fluid, inventory, stage, componentIndex);
      assertFugacityEquality(fluid, inventory, stage, componentIndex);
    }
  }

  /**
   * Checks component material balance against the original feed.
   *
   * @param fluid endpoint to inspect
   * @param inventory original total feed inventory in mol
   * @param stage lifecycle stage
   * @param componentIndex component index
   */
  private void assertComponentClosure(SystemInterface fluid, double inventory, String stage, int componentIndex) {
    double recoveredComposition = 0.0;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      recoveredComposition += fluid.getBeta(phaseIndex)
          * fluid.getPhase(phaseIndex).getComponent(componentIndex).getx();
      assertEquals(
          fluid.getBeta(phaseIndex) * inventory * fluid.getPhase(phaseIndex).getComponent(componentIndex).getx(),
          fluid.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase(), inventory * 1.0e-10);
    }
    assertEquals(componentIndex == 0 ? 0.7 : 0.3, recoveredComposition, 1.0e-10,
        "inventory=" + inventory + ", " + stage + ", original component inventory");
  }

  /**
   * Checks equality of component fugacity between the two phases.
   *
   * @param fluid endpoint to inspect
   * @param inventory original total feed inventory in mol
   * @param stage lifecycle stage
   * @param componentIndex component index
   */
  private void assertFugacityEquality(SystemInterface fluid, double inventory, String stage, int componentIndex) {
    double firstFugacity = fluid.getPhase(0).getComponent(componentIndex).getx()
        * fluid.getPhase(0).getComponent(componentIndex).getFugacityCoefficient();
    double secondFugacity = fluid.getPhase(1).getComponent(componentIndex).getx()
        * fluid.getPhase(1).getComponent(componentIndex).getFugacityCoefficient();
    double residual = Math.abs(Math.log(firstFugacity / secondFugacity));
    assertTrue(residual < 1.0e-8,
        "inventory=" + inventory + ", " + stage + ", component=" + componentIndex + ", residual=" + residual);
  }
}
