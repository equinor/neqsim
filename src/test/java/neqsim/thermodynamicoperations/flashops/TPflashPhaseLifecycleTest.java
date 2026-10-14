package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for root selection and logical phase inventories during endpoint refinement.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class TPflashPhaseLifecycleTest {
  /**
   * Verifies that a rejected iteration restores the selected root and physical mapping.
   *
   * @throws Exception if reflective access to the private rollback contract fails
   */
  @Test
  void rollbackPreservesRootSelectorAndPhaseMapping() throws Exception {
    SystemInterface fluid = sourGas();
    new ThermodynamicOperations(fluid).TPflash();
    SystemInterface reference = fluid.clone();
    // The low-density root can be classified GAS even though LIQUID selected the cubic root.
    assertNotEquals(fluid.getPhase(0).getType(), fluid.getPhaseType(0));
    FlashPhaseState snapshot = FlashPhaseState.captureWithGibbsEnergy(fluid);
    int firstSlot = fluid.getPhaseIndex(0);
    fluid.setPhaseIndex(0, fluid.getPhaseIndex(1));
    fluid.setPhaseIndex(1, firstSlot);
    fluid.setPhaseType(0, PhaseType.GAS);
    fluid.setPhaseType(1, PhaseType.GAS);
    fluid.setBeta(0, 0.1);
    fluid.setBeta(1, 0.9);
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        fluid.getPhase(phase).getComponent(component).setx(1.0 / fluid.getNumberOfComponents());
        fluid.getPhase(phase).getComponent(component).setK(9.0);
      }
    }
    Method restore = TPflash.class.getDeclaredMethod("restoreTwoPhaseIterationState", FlashPhaseState.class);
    restore.setAccessible(true);
    restore.invoke(new TPflash(fluid), snapshot);
    for (int phase = 0; phase < 2; phase++) {
      assertEquals(reference.getPhaseIndex(phase), fluid.getPhaseIndex(phase));
      assertEquals(reference.getPhaseType(phase), fluid.getPhaseType(phase));
      assertEquals(reference.getBeta(phase), fluid.getBeta(phase), 1.0e-14);
      assertEquals(reference.getPhase(phase).getZ(), fluid.getPhase(phase).getZ(), 1.0e-12);
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        assertEquals(reference.getPhase(phase).getComponent(component).getx(),
            fluid.getPhase(phase).getComponent(component).getx(), 1.0e-14);
        assertEquals(reference.getPhase(phase).getComponent(component).getK(),
            fluid.getPhase(phase).getComponent(component).getK(), 1.0e-14);
      }
    }
    assertEquals(reference.getGibbsEnergy(), fluid.getGibbsEnergy(), 1.0e-8);
  }

  /** Verifies that Newton variables address logical phases after a physical slot is recycled. */
  @Test
  void newtonUsesLogicalBetaForRecycledSlots() {
    SystemInterface ordinary = initializedSplit(false);
    SystemInterface reordered = initializedSplit(true);
    SysNewtonRhapsonTPflash first = new SysNewtonRhapsonTPflash(ordinary, 2, 3);
    SysNewtonRhapsonTPflash second = new SysNewtonRhapsonTPflash(reordered, 2, 3);
    for (int iteration = 0; iteration < 3; iteration++) {
      first.solve();
      first.init();
      second.solve();
      second.init();
      for (int phase = 0; phase < 2; phase++) {
        assertEquals(ordinary.getBeta(phase), reordered.getBeta(phase), 1.0e-12);
        for (int component = 0; component < 3; component++) {
          assertEquals(ordinary.getPhase(phase).getComponent(component).getx(),
              reordered.getPhase(phase).getComponent(component).getx(), 1.0e-12);
        }
      }
      assertEquals(1.0, reordered.getBeta(0) + reordered.getBeta(1), 1.0e-14);
    }
  }

  /**
   * Creates the same split on canonical or recycled physical slots.
   *
   * @param reordered whether logical phases use physical slots two and zero
   * @return initialized two-phase state
   */
  private SystemInterface initializedSplit(boolean reordered) {
    SystemInterface fluid = sourGas();
    fluid.init(0);
    if (reordered) {
      fluid.setPhaseIndex(0, 2);
      fluid.setPhaseIndex(1, 0);
      fluid.setPhaseIndex(2, 1);
    }
    double[][] composition = {{0.5982149750194442, 0.09534913476741402, 0.30643589021314166},
        {0.4505949120258768, 0.10037634294775685, 0.44902874502636636}};
    for (int phase = 0; phase < 2; phase++) {
      fluid.setPhaseType(phase, PhaseType.LIQUID);
      fluid.setBeta(phase, phase == 0 ? 0.3275623373883936 : 0.6724376626116064);
      for (int component = 0; component < 3; component++) {
        fluid.getPhase(phase).getComponent(component).setx(composition[phase][component]);
      }
    }
    fluid.init(1);
    return fluid;
  }

  /**
   * Creates the public synthetic sour-gas regression mixture.
   *
   * @return unflashed PR/classic fluid
   */
  private SystemInterface sourGas() {
    SystemInterface fluid = new SystemPrEos(240.0, 100.8);
    fluid.addComponent("methane", 49.88 / 99.97);
    fluid.addComponent("CO2", 9.87 / 99.97);
    fluid.addComponent("H2S", 40.22 / 99.97);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
