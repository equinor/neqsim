package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test VUFlash.
 */
class VUFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(VUFlashTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static neqsim.thermo.system.SystemInterface testSystem2 = null;
  static ThermodynamicOperations testOps = null;

  @Test
  void testVUflash() {
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 23.5);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("ethane", 0.01);
    testSystem.addComponent("n-pentane", 0.01);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double volume = testSystem.getVolume("m3");
    double internalenergy = testSystem.getInternalEnergy("J");

    testOps.VUflash(volume * 1.1, internalenergy, "m3", "J");

    assertEquals(21.387, testSystem.getPressure(), 0.01);
  }

  /**
   * A sequence of nearby CPA VU flashes, as used by dynamic separators, must reuse the previous equilibrium state
   * instead of cold-starting every time. The iteration bound is deterministic and protects the transient path from the
   * multi-hour regression seen in the scrubber suite.
   */
  @Test
  void testDynamicCpaVUflashUsesBoundedWarmStarts() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 50.0, 15.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 15.0);
    fluid.addComponent("water", 50.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    for (int step = 0; step < 5; step++) {
      double volume = fluid.getVolume("m3");
      double internalEnergy = fluid.getInternalEnergy("J");
      double energyIncrement = Math.max(Math.abs(internalEnergy) * 1.0e-5, 1.0);

      operations = new ThermodynamicOperations(fluid);
      operations.VUflash(volume, internalEnergy + energyIncrement, "m3", "J", true);

      assertTrue(operations.getOperation() instanceof OptimizedVUflash);
      OptimizedVUflash flash = (OptimizedVUflash) operations.getOperation();
      assertTrue(flash.isLastRunConverged(), "dynamic CPA VU flash must converge at step " + step);
      assertTrue(flash.getLastIterationCount() <= 20,
          "dynamic CPA VU flash must remain bounded at step " + step + ", iterations=" + flash.getLastIterationCount());
      assertFalse(flash.wasColdFallbackUsed(), "nearby dynamic state must not require a cold fallback");
      assertTrue(fluid.hasPhaseType("gas"), "gas phase retained at step " + step);
      assertTrue(fluid.hasPhaseType("oil"), "oil phase retained at step " + step);
      assertTrue(fluid.hasPhaseType("aqueous"), "aqueous phase retained at step " + step);
    }
  }
}
