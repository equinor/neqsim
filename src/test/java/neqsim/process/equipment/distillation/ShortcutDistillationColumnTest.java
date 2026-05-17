package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ShortcutDistillationColumn (FUG method).
 */
class ShortcutDistillationColumnTest {

  @Test
  void testDeethanizer() {
    // Classic deethanizer: separate ethane (LK) from propane (HK)
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 20.0);
    fluid.addComponent("methane", 0.10);
    fluid.addComponent("ethane", 0.30);
    fluid.addComponent("propane", 0.30);
    fluid.addComponent("n-butane", 0.20);
    fluid.addComponent("n-pentane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Column Feed", fluid);
    feed.setFlowRate(100.0, "kmol/hr");
    feed.run();

    ShortcutDistillationColumn column = new ShortcutDistillationColumn("Deethanizer", feed);
    column.setLightKey("ethane");
    column.setHeavyKey("propane");
    column.setLightKeyRecoveryDistillate(0.99);
    column.setHeavyKeyRecoveryBottoms(0.99);
    column.setRefluxRatioMultiplier(1.3);
    column.run();

    assertTrue(column.isSolved(), "Column should converge");
    assertTrue(column.getMinimumNumberOfStages() > 1.0,
        "Nmin should be > 1, got: " + column.getMinimumNumberOfStages());
    assertTrue(column.getMinimumRefluxRatio() > 0.0,
        "Rmin should be positive, got: " + column.getMinimumRefluxRatio());
    assertTrue(column.getActualNumberOfStages() > column.getMinimumNumberOfStages(),
        "N_actual should exceed N_min");
    assertTrue(column.getActualRefluxRatio() > column.getMinimumRefluxRatio(),
        "R_actual should exceed R_min");
    assertTrue(column.getFeedTrayNumber() > 0, "Feed tray should be positive");
    assertTrue(column.getRelativeVolatility() > 1.0, "Alpha LK/HK should be > 1");

    assertNotNull(column.getDistillateStream(), "Distillate stream should not be null");
    assertNotNull(column.getBottomsStream(), "Bottoms stream should not be null");
  }

  @Test
  void testDepropanizer() {
    // Depropanizer: propane (LK), n-butane (HK)
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 15.0);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.35);
    fluid.addComponent("n-pentane", 0.25);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Depropanizer Feed", fluid);
    feed.setFlowRate(200.0, "kmol/hr");
    feed.run();

    ShortcutDistillationColumn column = new ShortcutDistillationColumn("Depropanizer", feed);
    column.setLightKey("propane");
    column.setHeavyKey("n-butane");
    column.setLightKeyRecoveryDistillate(0.95);
    column.setHeavyKeyRecoveryBottoms(0.95);
    column.setRefluxRatioMultiplier(1.5);
    column.run();

    assertTrue(column.isSolved(), "Depropanizer should converge");

    // Fenske-Underwood-Gilliland consistency check
    double nMin = column.getMinimumNumberOfStages();
    double rMin = column.getMinimumRefluxRatio();
    double nAct = column.getActualNumberOfStages();
    double rAct = column.getActualRefluxRatio();

    assertTrue(nMin > 0, "Nmin should be positive");
    assertTrue(nAct > nMin, "N_actual should exceed N_min");
    assertEquals(rMin * 1.5, rAct, 0.001, "R_actual should equal R_min * multiplier");
  }

  @Test
  void testJsonOutput() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    fluid.addComponent("methane", 0.20);
    fluid.addComponent("ethane", 0.40);
    fluid.addComponent("propane", 0.40);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50.0, "kmol/hr");
    feed.run();

    ShortcutDistillationColumn column = new ShortcutDistillationColumn("TestCol", feed);
    column.setLightKey("ethane");
    column.setHeavyKey("propane");
    column.run();

    String json = column.getResultsJson();
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("Fenske"), "JSON should mention Fenske method");
    assertTrue(json.contains("minimumStages"), "JSON should contain minimumStages");
    assertTrue(json.contains("actualStages"), "JSON should contain actualStages");
    assertTrue(json.contains("ethane"), "JSON should contain light key name");
  }
}
