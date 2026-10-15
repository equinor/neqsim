package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;

/**
 * Configured phase limits must survive initialization without activating uninitialized EOS states.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class SystemPhaseInitializationLimitTest {
  /** A one-phase reactive feed formerly acquired a second phase with all mole fractions equal to zero. */
  @Test
  void repeatedInitializationPreservesSinglePhaseLimitAndPressure() {
    for (boolean pengRobinson : new boolean[] {false, true}) {
      for (double scale : new double[] {1.0e-6, 1.0, 1.0e6}) {
        SystemInterface fluid = createFeed(pengRobinson, scale);
        fluid.setMaxNumberOfPhases(1);
        fluid.setNumberOfPhases(1);
        for (int cycle = 0; cycle < 3; cycle++) {
          for (int level : new int[] {0, 1, 2}) {
            fluid.init(level);
            assertEquals(1, fluid.getMaxNumberOfPhases(), "Initialization must preserve the configured maximum");
            assertEquals(1, fluid.getNumberOfPhases(), "Only the initialized phase may become active");
            assertInitializedComposition(fluid, level > 0);
            assertEquals(scale, fluid.getPhase(0).getNumberOfMolesInPhase(), 1.0e-12 * scale);
          }
        }
      }
    }
  }

  /** Ordinary two-phase initialization must continue to initialize and activate both phase slots. */
  @Test
  void ordinaryTwoPhaseInitializationStillActivatesBothInitializedStates() {
    for (boolean pengRobinson : new boolean[] {false, true}) {
      SystemInterface fluid = createFeed(pengRobinson, 1.0);
      fluid.setMaxNumberOfPhases(2);
      fluid.setNumberOfPhases(1);
      for (int cycle = 0; cycle < 2; cycle++) {
        for (int level : new int[] {0, 1, 2}) {
          fluid.init(level);
          assertEquals(2, fluid.getMaxNumberOfPhases());
          assertEquals(2, fluid.getNumberOfPhases());
          assertInitializedComposition(fluid, level > 0);
        }
      }
    }
  }

  /**
   * Creates the documentation's finite reactive feed before any reaction calculation.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param scale total feed moles
   * @return configured cubic system
   */
  private static SystemInterface createFeed(boolean pengRobinson, double scale) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(600.0, 1.0) : new SystemSrkEos(600.0, 1.0);
    for (String name : new String[] {"CO", "water", "CO2", "hydrogen"}) {
      fluid.addComponent(name, 0.25 * scale);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Checks active composition and actual EOS pressure rather than the stored pressure target.
   *
   * @param fluid system after initialization
   * @param propertiesInitialized whether the requested level initializes EOS properties
   */
  private static void assertInitializedComposition(SystemInterface fluid, boolean propertiesInitialized) {
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseEos phase = (PhaseEos) fluid.getPhase(phaseIndex);
      double sum = 0.0;
      for (int component = 0; component < phase.getNumberOfComponents(); component++) {
        assertEquals(0.25, phase.getComponent(component).getx(), 1.0e-14);
        sum += phase.getComponent(component).getx();
      }
      assertEquals(1.0, sum, 1.0e-14);
      if (propertiesInitialized) {
        assertTrue(phase.getA() > 0.0 && phase.getB() > 0.0, "Configured EOS parameters must be initialized");
        assertEquals(fluid.getPressure(), phase.calcPressure(), 1.0e-8);
        assertTrue(Double.isFinite(phase.calcPressuredV()) && phase.calcPressuredV() < 0.0);
      }
    }
  }
}
