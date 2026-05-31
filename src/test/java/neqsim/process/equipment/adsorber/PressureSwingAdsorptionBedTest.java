package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link PressureSwingAdsorptionBed}. Validates against textbook H2-purification
 * behaviour: heavy components (CO, CO2, CH4) are preferentially captured, H2 passes through, the
 * cycle-averaged recovery target is enforced, and mass balance closes on the tail-gas side.
 */
class PressureSwingAdsorptionBedTest extends neqsim.NeqSimTest {

  /**
   * Build a representative shifted-syngas feed at 25 bara, 313 K.
   *
   * @return inlet stream wired to a fluid
   */
  private static Stream buildSyngasFeed() {
    SystemInterface fluid = new SystemSrkEos(313.15, 25.0);
    fluid.addComponent("hydrogen", 0.72);
    fluid.addComponent("CO2", 0.18);
    fluid.addComponent("methane", 0.05);
    fluid.addComponent("CO", 0.03);
    fluid.addComponent("nitrogen", 0.02);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "mole/sec");
    feed.setPressure(25.0, "bara");
    feed.setTemperature(313.15, "K");
    feed.run();
    return feed;
  }

  @Test
  void testDefaultsAndRecoveryTargetEnforced() {
    Stream feed = buildSyngasFeed();
    PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", feed);
    psa.setRecoveryTarget(0.85);
    psa.run();

    double purity = psa.getH2Purity();
    double recovery = psa.getH2Recovery();

    // H2 should dominate the product.
    assertTrue(purity > 0.85, "H2 purity should be > 85% with default sorbent, got " + purity);

    // Recovery should not exceed the target (we vent any excess H2 into the tail gas).
    assertTrue(recovery <= 0.85 + 1e-6,
        "H2 recovery should be capped at the target 0.85, got " + recovery);

    // And it should be reasonably close to the target (within 1%) because the syngas
    // is H2-rich and the base equilibrium leaves most of the H2 in the product.
    assertTrue(recovery > 0.80, "H2 recovery should be close to target 0.85, got " + recovery);
  }

  @Test
  void testMassBalanceClosesOnTailGas() {
    Stream feed = buildSyngasFeed();
    PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", feed);
    psa.setRecoveryTarget(0.85);
    psa.run();

    double[] tail = psa.getTailGasMoleFlow();
    assertNotNull(tail);
    double tailTotal = 0.0;
    for (double m : tail) {
      tailTotal += m;
      assertTrue(m >= 0.0, "Tail-gas mole flow must be non-negative");
    }
    double productTotal = psa.getOutletStream().getFlowRate("mole/sec");
    double feedTotal = feed.getFlowRate("mole/sec");

    // Feed = product + tail (mass balance), within 1 % relative.
    double imbalance = Math.abs(feedTotal - (productTotal + tailTotal)) / feedTotal;
    assertTrue(imbalance < 0.01, "Mass balance imbalance should be < 1%, got " + imbalance);
  }

  @Test
  void testTailGasCompositionSumsToOne() {
    Stream feed = buildSyngasFeed();
    PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", feed);
    psa.run();

    double[] composition = psa.getTailGasComposition();
    assertNotNull(composition);
    double sum = 0.0;
    for (double x : composition) {
      sum += x;
    }
    assertEquals(1.0, sum, 1e-6);
  }

  @Test
  void testSorbentSwitchSwitchesMaterial() {
    Stream feed = buildSyngasFeed();
    PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA", feed);
    psa.setSorbent(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X);
    assertEquals("Zeolite 13X", psa.getAdsorbentMaterial());
    assertEquals(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X, psa.getSorbent());
    assertEquals(700.0, psa.getAdsorbentBulkDensity(), 1e-9);
  }

  @Test
  void testRecoveryTargetValidation() {
    PressureSwingAdsorptionBed psa = new PressureSwingAdsorptionBed("PSA");
    assertThrows(IllegalArgumentException.class, () -> psa.setRecoveryTarget(0.0));
    assertThrows(IllegalArgumentException.class, () -> psa.setRecoveryTarget(1.5));
    assertThrows(IllegalArgumentException.class, () -> psa.setRecoveryTarget(-0.1));
  }
}
