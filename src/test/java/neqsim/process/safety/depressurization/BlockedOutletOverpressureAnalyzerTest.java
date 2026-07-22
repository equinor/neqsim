package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link BlockedOutletOverpressureAnalyzer}.
 */
class BlockedOutletOverpressureAnalyzerTest {

  /**
   * Build a small lean-gas inventory for the trapped vessel.
   *
   * @return a configured SRK fluid
   */
  private static SystemInterface buildFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Verifies that a continued feed into a blocked vessel builds pressure up to the relief set point and reports a
   * relief demand with a finite time-to-set.
   */
  @Test
  void testBlockedOutletReachesReliefSetPressure() {
    BlockedOutletOverpressureAnalyzer analyzer = new BlockedOutletOverpressureAnalyzer(buildFluid(), 5.0);
    analyzer.setInletConditions(298.15, 90.0, 2.0).setReliefSetPressure(50.0).setTimeStep(1.0).setMaxTime(1800.0);

    BlockedOutletOverpressureAnalyzer.BlockedOutletResult result = analyzer.run();

    assertEquals(50.0, result.reliefSetPressureBara, 1e-9);
    assertTrue(result.reliefDemand);
    assertFalse(Double.isNaN(result.timeToReliefSetSeconds));
    assertTrue(result.timeToReliefSetSeconds > 0.0);
    assertTrue(result.maxPressureBara >= 50.0);
    assertNotNull(result.message);
    assertNotNull(result.toJson());
  }

  /**
   * Verifies that running without configuring inlet conditions throws.
   */
  @Test
  void testRunWithoutInletConditionsThrows() {
    BlockedOutletOverpressureAnalyzer analyzer = new BlockedOutletOverpressureAnalyzer(buildFluid(), 5.0);
    analyzer.setReliefSetPressure(50.0);

    assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        analyzer.run();
      }
    });
  }

  /**
   * Verifies constructor validation rejects a non-positive vessel volume.
   */
  @Test
  void testConstructorRejectsNonPositiveVolume() {
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new BlockedOutletOverpressureAnalyzer(buildFluid(), 0.0);
      }
    });
  }
}
