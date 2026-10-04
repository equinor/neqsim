package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for ratio flashes starting outside the two-phase region.
 *
 * @author NeqSim
 * @version 1.0
 */
class PVrefluxflashTest {
  /** Verify a condenser can condense an initially vapor-only feed to its specified L/D. */
  @Test
  void condenserCoolsVaporFeedToRefluxRatio() {
    assertRatio(400.0, 1.8, 0);
  }

  /** Verify a reboiler can vaporize an initially liquid-only feed to its specified V/B. */
  @Test
  void reboilerHeatsLiquidFeedToBoilupRatio() {
    assertRatio(280.0, 1.2, 1);
  }

  /** Verify both terminal ratios from cold liquid and hot vapor initial states. */
  @Test
  void ratioTargetsAreIndependentOfInitialPhase() {
    for (double temperature : new double[] {280.0, 400.0}) {
      for (double ratio : new double[] {0.1, 1.0, 5.0}) {
        assertRatio(temperature, ratio, 0);
        assertRatio(temperature, ratio, 1);
      }
    }
  }

  /** Reject unsupported targets before mutating the fluid. */
  @Test
  void rejectsInvalidTargets() {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    for (double ratio : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new PVrefluxflash(fluid, ratio, 0));
    }
    assertThrows(IllegalArgumentException.class, () -> new PVrefluxflash(fluid, 1.0, 2));
    assertEquals(300.0, fluid.getTemperature(), 0.0);
  }

  /**
   * Run the ratio flash and verify actual phase flow rates.
   *
   * @param temperature initial temperature in kelvin
   * @param ratio requested ratio
   * @param phase denominator phase index (gas zero, liquid one)
   */
  private void assertRatio(double temperature, double ratio, int phase) {
    SystemInterface fluid = new SystemSrkEos(temperature, 10.0);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).PVrefluxFlash(ratio, phase);
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(ratio,
        fluid.getPhase(1 - phase).getNumberOfMolesInPhase() / fluid.getPhase(phase).getNumberOfMolesInPhase(), 1.0e-6);
  }
}
