package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for the shortcut reflux-domain report in issue 4223.
 *
 * @author NeqSim
 * @version 1.0
 */
class ShortcutRefluxDomainTest {
  /** Rejects inputs that cannot define an operating reflux above minimum. */
  @Test
  void rejectsInvalidMultiplier() {
    ShortcutDistillationColumn column = new ShortcutDistillationColumn("column");
    for (double multiplier : new double[] {-1.0, 0.0, 0.5, 0.999999, 1.0, Double.NaN, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> column.setRefluxRatioMultiplier(multiplier),
          "Invalid multiplier: " + multiplier);
      assertFalse(column.isSolved());
    }
  }

  /** Checks independent reported values, the reflux trend, and product material balance. */
  @Test
  void preservesOrdinaryRefluxResults() {
    ShortcutDistillationColumn column = createColumn();
    column.setRefluxRatioMultiplier(1.2);
    column.run();
    assertTrue(column.isSolved());
    assertEquals(15.202865640529591, column.getActualNumberOfStages(), 1.0e-8);
    assertEquals(0.7930958829860458, column.getActualRefluxRatio(), 1.0e-8);
    assertEquals(8, column.getFeedTrayNumber());
    double moreStages = column.getActualNumberOfStages();

    column.setRefluxRatioMultiplier(1.5);
    column.run();
    assertTrue(column.isSolved());
    assertEquals(12.318198276671831, column.getActualNumberOfStages(), 1.0e-8);
    assertEquals(0.9913698537325574, column.getActualRefluxRatio(), 1.0e-8);
    assertEquals(7, column.getFeedTrayNumber());
    assertTrue(column.getActualNumberOfStages() < moreStages);
    assertEquals(100.0,
        column.getDistillateStream().getFlowRate("kg/hr") + column.getBottomsStream().getFlowRate("kg/hr"), 1.0e-8);
    assertTrue(Double.isFinite(column.getCondenserDuty()) && column.getCondenserDuty() < 0.0);
  }

  /** A failed rerun must not retain the preceding solved flag or publish an invalid feed tray. */
  @Test
  void rejectsUnrepresentableNearMinimumStageCountOnRerun() {
    ShortcutDistillationColumn column = createColumn();
    column.setRefluxRatioMultiplier(1.5);
    column.run();
    assertTrue(column.isSolved());
    column.setRefluxRatioMultiplier(1.000001);
    IllegalStateException failure = assertThrows(IllegalStateException.class, column::run);
    assertTrue(failure.getMessage().contains("reflux"));
    assertFalse(column.isSolved());
    assertEquals(0, column.getFeedTrayNumber());
  }

  /** Stage counts beyond the integer tray range must not overflow Kirkbride numbering. */
  @Test
  void rejectsFiniteStageCountBeyondTrayRange() {
    ShortcutDistillationColumn column = createColumn();
    column.setRefluxRatioMultiplier(1.00003);
    assertThrows(IllegalStateException.class, column::run);
    assertFalse(column.isSolved());
    assertEquals(0, column.getFeedTrayNumber());
  }

  /** Preserves the rigorous initializer's failed-result contract without rebuilding its trays. */
  @Test
  void rigorousInitializerReportsInvalidShortcutWithoutMutatingDesign() {
    Stream feed = createFeed();
    DistillationColumn rigorous = new DistillationColumn("rigorous", 8, true, true);
    int originalTrays = rigorous.getNumberOfTrays();
    for (double multiplier : new double[] {0.5, Double.NaN, 1.000001}) {
      DistillationColumn.ShortcutInitializationResult result = rigorous.initializeFromShortcut(feed, "propane",
          "n-butane", 0.98, 0.98, multiplier);
      assertFalse(result.isInitialized());
      assertEquals(originalTrays, rigorous.getNumberOfTrays());
      assertTrue(rigorous.getFeedStreams().isEmpty());
    }
  }

  /**
   * Creates the public propane/butane/pentane reproducer with 98 percent key recoveries.
   *
   * @return configured, unrun shortcut column
   */
  private ShortcutDistillationColumn createColumn() {
    Stream feed = createFeed();
    ShortcutDistillationColumn column = new ShortcutDistillationColumn("column", feed);
    column.setLightKey("propane");
    column.setHeavyKey("n-butane");
    column.setLightKeyRecoveryDistillate(0.98);
    column.setHeavyKeyRecoveryBottoms(0.98);
    column.setCondenserPressure(19.0);
    column.setReboilerPressure(20.0);
    return column;
  }

  /**
   * Creates the public reproducer feed.
   *
   * @return flashed feed with a mass flow of 100 kg/hr
   */
  private Stream createFeed() {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 20.0);
    fluid.addComponent("propane", 0.4);
    fluid.addComponent("n-butane", 0.4);
    fluid.addComponent("n-pentane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }
}
