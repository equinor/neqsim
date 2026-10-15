package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Association workspaces must belong to each initialized electrolyte phase, including subsequently added phases.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPACloneIsolationTest {
  /**
   * Adding multiphase templates after hydrate initialization must not alias initialized liquid workspaces.
   */
  @Test
  void initializedMultiphaseTemplatesRetainIndependentPressures() {
    for (boolean hydrateFirst : new boolean[] {false, true}) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(278.15, 29.01);
      fluid.addComponent("CO2", 10.0);
      fluid.addComponent("water", 1.0 / 0.01801528);
      double salt = 14.97 / (100.0 - 14.97) / 0.0745513;
      fluid.addComponent("K+", salt);
      fluid.addComponent("Cl-", salt);
      fluid.setMixingRule(10);
      if (hydrateFirst) {
        fluid.setHydrateCheck(true);
        fluid.setMultiPhaseCheck(true);
      } else {
        fluid.setMultiPhaseCheck(true);
        fluid.setHydrateCheck(true);
      }
      fluid.setNumberOfPhases(3);
      double[] fractions = {0.02, 0.25, 0.73};
      double[][] composition = {{0.998, 0.002, 0.0, 0.0}, {0.425, 0.301, 0.137, 0.137},
          {0.028, 0.9714, 0.0003, 0.0003}};
      for (int phase = 0; phase < 3; phase++) {
        fluid.setPhaseType(phase, phase == 0 ? PhaseType.GAS : PhaseType.AQUEOUS);
        fluid.setBeta(phase, fractions[phase]);
        for (int component = 0; component < 4; component++) {
          fluid.getPhase(phase).getComponent(component).setx(composition[phase][component]);
        }
      }
      fluid.init(3);
      for (int phase : new int[] {2, 1, 0, 1, 2, 0}) {
        assertEquals(29.01, ((PhaseEos) fluid.getPhase(phase)).calcPressure(), 1e-6,
            "Association pressure must be independent of template creation and evaluation order");
      }
    }
  }

  /**
   * Evaluating a composition-perturbed clone must not change the original phase's pressure or derivatives.
   */
  @Test
  void clonedAssociationStateDoesNotChangeItsSourcePressure() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.setBeta(0, 1.0);
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    fluid.init(3);
    PhaseEos source = (PhaseEos) fluid.getPhase(0);
    double pressure = source.calcPressure();
    double derivative = source.calcPressuredV();
    double volume = source.getMolarVolume();
    PhaseEos sibling = source.clone();
    sibling.setTemperature(320.0);
    sibling.setPressure(25.0);
    double[] composition = {0.16, 0.80, 0.02, 0.02};
    for (int component = 0; component < composition.length; component++) {
      sibling.getComponent(component).setx(composition[component]);
    }
    for (int level : new int[] {1, 3, 1}) {
      sibling.init(fluid.getTotalNumberOfMoles(), composition.length, level, PhaseType.AQUEOUS, 1.0);
      assertEquals(25.0, sibling.calcPressure(), 1e-6);
      // Do not reinitialize the source here: doing so would hide a shared-workspace mutation.
      assertEquals(pressure, source.calcPressure(), 1e-7);
      assertEquals(derivative, source.calcPressuredV(), Math.abs(derivative) * 1e-10);
      assertEquals(volume, source.getMolarVolume(), 0.0);
    }
  }
}
