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

  /** Executes the shortcut-column Java example in the process simulation guide. */
  @Test
  void testDocumentedShortcutExample() {

    // Feed: light hydrocarbons
    SystemInterface feed = new SystemSrkEos(273.15 + 60.0, 15.0);
    feed.addComponent("methane", 0.05);
    feed.addComponent("ethane", 0.25);
    feed.addComponent("propane", 0.35);
    feed.addComponent("n-butane", 0.20);
    feed.addComponent("n-pentane", 0.15);
    feed.setMixingRule("classic");

    Stream feedStream = new Stream("feed", feed);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.run();

    ShortcutDistillationColumn shortcut = new ShortcutDistillationColumn("Deethanizer", feedStream);
    shortcut.setLightKey("ethane");
    shortcut.setHeavyKey("propane");
    shortcut.setLightKeyRecoveryDistillate(0.99);
    shortcut.setHeavyKeyRecoveryBottoms(0.99);
    shortcut.setRefluxRatioMultiplier(1.5);
    shortcut.run();

    double minimumStages = shortcut.getMinimumNumberOfStages();
    double minimumReflux = shortcut.getMinimumRefluxRatio();
    double actualStages = shortcut.getActualNumberOfStages();
    int feedTrayFromTop = shortcut.getFeedTrayNumber();
    double condenserDutyKW = shortcut.getCondenserDuty() / 1000.0;
    double reboilerDutyKW = shortcut.getReboilerDuty() / 1000.0;
    assertTrue(shortcut.isSolved());
    assertTrue(Double.isFinite(minimumStages) && minimumStages > 0.0);
    assertTrue(Double.isFinite(minimumReflux) && minimumReflux > 0.0);
    assertTrue(Double.isFinite(actualStages) && actualStages >= minimumStages);
    assertTrue(feedTrayFromTop > 0 && feedTrayFromTop <= Math.ceil(actualStages) + 1);
    assertTrue(Double.isFinite(condenserDutyKW));
    assertTrue(Double.isFinite(reboilerDutyKW));
  }

  /** Verifies Kirkbride against an independent material-balance calculation with unequal recoveries. */
  @Test
  void testKirkbrideUsesProductCompositionsAndRectifyingRatio() {
    SystemInterface fluid = new SystemSrkEos(303.15, 20.0);
    fluid.addComponent("ethane", 0.30);
    fluid.addComponent("propane", 0.70);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Asymmetric feed", fluid);
    feed.setFlowRate(100.0, "kmol/hr");
    feed.run();
    ShortcutDistillationColumn column = new ShortcutDistillationColumn("Asymmetric column", feed);
    column.setLightKey("ethane");
    column.setHeavyKey("propane");
    column.setLightKeyRecoveryDistillate(0.98);
    column.setHeavyKeyRecoveryBottoms(0.95);
    column.setRefluxRatioMultiplier(1.5);
    column.run();

    // On a 100 mol feed basis: D = 29.4 + 3.5, B = 0.6 + 66.5.
    // Kirkbride uses normalized product mole fractions, not component recoveries.
    double compositionRatio = (0.6 / 67.1) / (3.5 / 32.9);
    double rectifyingToStripping = Math.pow((70.0 / 30.0) * compositionRatio * compositionRatio * (67.1 / 32.9), 0.206);
    double rectifyingFraction = rectifyingToStripping / (1.0 + rectifyingToStripping);
    assertTrue(column.isSolved());
    assertTrue(Double.isFinite(column.getActualNumberOfStages()));
    assertTrue(rectifyingFraction < 0.5, "This separation requires fewer rectifying than stripping stages");
    assertEquals((int) Math.round(column.getActualNumberOfStages() * rectifyingFraction) + 1,
        column.getFeedTrayNumber(), "Feed numbering is one-based from the top");
    assertEquals(32.9, column.getDistillateStream().getFlowRate("kmol/hr"), 1.0e-8);
    assertEquals(67.1, column.getBottomsStream().getFlowRate("kmol/hr"), 1.0e-8);
  }

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
    assertTrue(column.getMinimumRefluxRatio() > 0.0, "Rmin should be positive, got: " + column.getMinimumRefluxRatio());
    assertTrue(column.getActualNumberOfStages() > column.getMinimumNumberOfStages(), "N_actual should exceed N_min");
    assertTrue(column.getActualRefluxRatio() > column.getMinimumRefluxRatio(), "R_actual should exceed R_min");
    assertTrue(column.getFeedTrayNumber() > 0, "Feed tray should be positive");
    assertTrue(column.getRelativeVolatility() > 1.0, "Alpha LK/HK should be > 1");

    assertNotNull(column.getDistillateStream(), "Distillate stream should not be null");
    assertNotNull(column.getBottomsStream(), "Bottoms stream should not be null");

    double feedMolarFlow = feed.getFlowRate("mole/hr");
    double productMolarFlow = column.getDistillateStream().getFlowRate("mole/hr")
        + column.getBottomsStream().getFlowRate("mole/hr");
    assertEquals(feedMolarFlow, productMolarFlow, feedMolarFlow * 1.0e-10,
        "Shortcut products should conserve molar feed flow");

    double feedEthane = feed.getFluid().getComponent("ethane").getTotalFlowRate("mole/hr");
    double distEthane = column.getDistillateStream().getFluid().getComponent("ethane").getTotalFlowRate("mole/hr");
    double feedPropane = feed.getFluid().getComponent("propane").getTotalFlowRate("mole/hr");
    double bottomPropane = column.getBottomsStream().getFluid().getComponent("propane").getTotalFlowRate("mole/hr");
    assertEquals(0.99, distEthane / feedEthane, 1.0e-8, "Light key recovery should be reflected in distillate flow");
    assertEquals(0.99, bottomPropane / feedPropane, 1.0e-8, "Heavy key recovery should be reflected in bottoms flow");
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

  @Test
  void testInitializeRigorousColumnFromShortcut() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 20.0);
    fluid.addComponent("methane", 0.10);
    fluid.addComponent("ethane", 0.30);
    fluid.addComponent("propane", 0.30);
    fluid.addComponent("n-butane", 0.20);
    fluid.addComponent("n-pentane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Rigorous Shortcut Feed", fluid);
    feed.setFlowRate(100.0, "kmol/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("RigorousFromShortcut", 3, true, true);
    DistillationColumn.ShortcutInitializationResult result = column.initializeFromShortcut(feed, "ethane", "propane",
        0.95, 0.95, 1.3);

    assertTrue(result.isInitialized(), result.getMessage());
    assertEquals(result, column.getLastShortcutInitializationResult());
    assertTrue(column.getNumberOfTrays() >= 4, "Rigorous column should have endpoint stages");
    assertEquals(result.getFeedTrayNumber(), column.getFeedTrayNumber(feed));
    assertEquals(result.getActualRefluxRatio(), column.getCondenser().getRefluxRatio(), 1.0e-12);
    assertNotNull(column.getTopSpecification(), "Top recovery specification should be configured");
    assertNotNull(column.getBottomSpecification(), "Bottom recovery specification should be configured");
    assertTrue(column.validateSetup().isValid(), "Shortcut-initialized column should validate");
  }
}
