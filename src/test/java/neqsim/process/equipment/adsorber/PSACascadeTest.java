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
 * Unit tests for {@link PSACascade}. Validates the cascade-level recovery uplift, bed-count
 * monotonicity, tail-gas mass balance, and configuration handling against textbook industrial
 * H2-PSA behaviour.
 */
class PSACascadeTest extends neqsim.NeqSimTest {

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
  void testDefaultsAndCascadeRecoveryUplift() {
    Stream feed = buildSyngasFeed();
    PSACascade cascade = new PSACascade("PSA-Cascade", feed);
    cascade.setPerBedRecoveryTarget(0.80);
    // BEDS_4 uplift = +0.05 → cascade target = 0.85.
    cascade.setConfiguration(PSACascade.CascadeConfiguration.BEDS_4);

    assertEquals(0.85, cascade.getCascadeRecoveryTarget(), 1e-9,
        "Cascade target should equal per-bed + uplift");
    assertEquals(4, cascade.getNumberOfBeds());

    cascade.run();

    double purity = cascade.getH2Purity();
    double recovery = cascade.getH2Recovery();
    assertTrue(purity > 0.85, "H2 purity should exceed 0.85, got " + purity);
    // The cascade caps recovery at the cascade target; allow small numerical slack.
    assertTrue(recovery <= 0.85 + 1e-6,
        "Cascade recovery should not exceed cascade target, got " + recovery);
    assertTrue(recovery > 0.80, "Cascade recovery should be near cascade target, got " + recovery);
  }

  @Test
  void testBedCountMonotonicallyImprovesRecovery() {
    Stream feed = buildSyngasFeed();

    PSACascade twoBeds = new PSACascade("PSA-2", buildSyngasFeed());
    twoBeds.setConfiguration(PSACascade.CascadeConfiguration.BEDS_2);
    twoBeds.setPerBedRecoveryTarget(0.75);
    twoBeds.run();

    PSACascade sixBeds = new PSACascade("PSA-6", feed);
    sixBeds.setConfiguration(PSACascade.CascadeConfiguration.BEDS_6);
    sixBeds.setPerBedRecoveryTarget(0.75);
    sixBeds.run();

    assertTrue(sixBeds.getH2Recovery() > twoBeds.getH2Recovery(),
        "6-bed cascade should out-recover 2-bed cascade: 6=" + sixBeds.getH2Recovery() + ", 2="
            + twoBeds.getH2Recovery());
  }

  @Test
  void testCascadeTargetCappedAtMax() {
    PSACascade cascade = new PSACascade("PSA");
    cascade.setPerBedRecoveryTarget(0.90);
    cascade.setConfiguration(PSACascade.CascadeConfiguration.BEDS_12); // +0.12
    // 0.90 + 0.12 = 1.02 → capped at 0.93.
    assertEquals(0.93, cascade.getCascadeRecoveryTarget(), 1e-9,
        "Cascade target must be capped at the industry-benchmark maximum 0.93");
  }

  @Test
  void testTailGasStreamMassBalance() {
    Stream feed = buildSyngasFeed();
    PSACascade cascade = new PSACascade("PSA", feed);
    cascade.setPerBedRecoveryTarget(0.80);
    cascade.setConfiguration(PSACascade.CascadeConfiguration.BEDS_6);
    cascade.run();

    Stream tail = cascade.getTailGasStream();
    assertNotNull(tail, "Tail-gas stream should exist after a non-trivial run");

    double productTotal = cascade.getOutletStream().getFlowRate("mole/sec");
    double tailTotal = tail.getFlowRate("mole/sec");
    double feedTotal = feed.getFlowRate("mole/sec");

    double imbalance = Math.abs(feedTotal - (productTotal + tailTotal)) / feedTotal;
    assertTrue(imbalance < 0.01,
        "Feed must equal product + tail within 1% (got imbalance " + imbalance + ")");
  }

  @Test
  void testSorbentPropagatesToTemplateBed() {
    PSACascade cascade = new PSACascade("PSA");
    cascade.setSorbent(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X);
    assertEquals(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X,
        cascade.getTemplateBed().getSorbent());
  }

  @Test
  void testInvalidConfigurationsRejected() {
    PSACascade cascade = new PSACascade("PSA");
    assertThrows(IllegalArgumentException.class, () -> cascade.setConfiguration(null));
    assertThrows(IllegalArgumentException.class, () -> cascade.setPerBedRecoveryTarget(0.0));
    assertThrows(IllegalArgumentException.class, () -> cascade.setPerBedRecoveryTarget(1.5));
    assertThrows(IllegalArgumentException.class, () -> cascade.setCycleTime(0.0));
    assertThrows(IllegalArgumentException.class, () -> cascade.setCycleTime(-100.0));
  }

  @Test
  void testRunWithoutInletStreamThrows() {
    PSACascade cascade = new PSACascade("PSA");
    assertThrows(IllegalStateException.class, () -> cascade.run());
  }

  @Test
  void testCycleTimeDefaultsAndSet() {
    PSACascade cascade = new PSACascade("PSA");
    assertEquals(300.0, cascade.getCycleTime(), 1e-9);
    cascade.setCycleTime(180.0);
    assertEquals(180.0, cascade.getCycleTime(), 1e-9);
  }

  @Test
  void testTemplateBedNotNull() {
    PSACascade cascade = new PSACascade("PSA");
    assertNotNull(cascade.getTemplateBed());
  }
}
