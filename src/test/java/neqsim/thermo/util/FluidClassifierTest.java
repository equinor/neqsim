package neqsim.thermo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for FluidClassifier - Whitson-style fluid classification.
 */
class FluidClassifierTest {
  @Test
  void testDryGasClassification() {
    // Dry gas: very light, high GOR
    SystemInterface fluid = new SystemSrkEos(333.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("propane", 0.02);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ReservoirFluidType type = FluidClassifier.classify(fluid);
    assertEquals(ReservoirFluidType.DRY_GAS, type, "Should classify as dry gas");
  }

  @Test
  void testWetGasClassification() {
    // Wet gas: mostly light with small C7+ fraction
    SystemInterface fluid = new SystemSrkEos(333.15, 50.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addTBPfraction("C7", 0.02, 100.0 / 1000.0, 0.72);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ReservoirFluidType type = FluidClassifier.classify(fluid);
    assertTrue(type == ReservoirFluidType.WET_GAS || type == ReservoirFluidType.DRY_GAS,
        "Should classify as wet gas or dry gas");
  }

  @Test
  void testGasCondensateClassification() {
    // Gas condensate: C7+ around 4-12.5%
    SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.02);
    fluid.addTBPfraction("C7", 0.10, 120.0 / 1000.0, 0.75);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ReservoirFluidType type = FluidClassifier.classify(fluid);
    assertTrue(type == ReservoirFluidType.GAS_CONDENSATE || type == ReservoirFluidType.VOLATILE_OIL,
        "Should classify as gas condensate or volatile oil");
  }

  @Test
  void testBlackOilClassification() {
    // Black oil: high C7+ content (>30%)
    SystemInterface fluid = new SystemSrkEos(353.15, 150.0);
    fluid.addComponent("methane", 0.30);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-hexane", 0.03);
    fluid.addTBPfraction("C7", 0.15, 120.0 / 1000.0, 0.75);
    fluid.addTBPfraction("C10", 0.15, 180.0 / 1000.0, 0.80);
    fluid.addTBPfraction("C15", 0.18, 250.0 / 1000.0, 0.85);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ReservoirFluidType type = FluidClassifier.classify(fluid);
    // With heavy fractions this should be black oil, volatile oil, or heavy oil
    assertTrue(
        type == ReservoirFluidType.BLACK_OIL || type == ReservoirFluidType.VOLATILE_OIL
            || type == ReservoirFluidType.HEAVY_OIL,
        "Should classify as black oil, volatile oil, or heavy oil. Got: " + type);
  }

  @Test
  void testClassifyByGOR() {
    // Very high GOR should be dry gas
    ReservoirFluidType highGOR = FluidClassifier.classifyByGOR(150000.0);
    assertEquals(ReservoirFluidType.DRY_GAS, highGOR, "High GOR should be dry gas");

    // Very low GOR should be heavy oil
    ReservoirFluidType lowGOR = FluidClassifier.classifyByGOR(100.0);
    assertEquals(ReservoirFluidType.HEAVY_OIL, lowGOR, "Very low GOR should be heavy oil");

    // Black oil range
    ReservoirFluidType blackOilGOR = FluidClassifier.classifyByGOR(500.0);
    assertEquals(ReservoirFluidType.BLACK_OIL, blackOilGOR, "GOR ~500 should be black oil");

    // Gas condensate range
    ReservoirFluidType gcGOR = FluidClassifier.classifyByGOR(5000.0);
    assertEquals(ReservoirFluidType.GAS_CONDENSATE, gcGOR, "GOR ~5000 should be gas condensate");
  }

  @Test
  void testC7PlusCalculation() {
    SystemInterface fluid = new SystemSrkEos(333.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addTBPfraction("C7", 0.20, 100.0 / 1000.0, 0.72);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    double c7Plus = FluidClassifier.calculateC7PlusContent(fluid);
    // Should be close to 0.20 (20%)
    assertTrue(c7Plus > 15 && c7Plus < 25, "C7+ content should be around 20%");
  }

  @Test
  void testGenerateClassificationReport() {
    SystemInterface fluid = new SystemSrkEos(333.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("propane", 0.02);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    String report = FluidClassifier.generateClassificationReport(fluid);

    assertNotNull(report, "Report should not be null");
    assertTrue(report.contains("Fluid Classification Report"), "Report should contain header");
    assertTrue(report.contains("C7+ Content"), "Report should contain C7+ content");
    assertTrue(report.contains("Fluid Type"), "Report should contain fluid type");
  }

  @Test
  void testReservoirFluidTypeEnum() {
    // Test all enum values exist
    assertNotNull(ReservoirFluidType.DRY_GAS);
    assertNotNull(ReservoirFluidType.WET_GAS);
    assertNotNull(ReservoirFluidType.GAS_CONDENSATE);
    assertNotNull(ReservoirFluidType.VOLATILE_OIL);
    assertNotNull(ReservoirFluidType.BLACK_OIL);
    assertNotNull(ReservoirFluidType.HEAVY_OIL);
    assertNotNull(ReservoirFluidType.UNKNOWN);

    // Test display names exist
    assertTrue(ReservoirFluidType.DRY_GAS.getDisplayName().toLowerCase().contains("dry"));
    assertTrue(ReservoirFluidType.GAS_CONDENSATE.getDisplayName().toLowerCase().contains("gas"));
  }

  @Test
  void testNullFluidHandling() {
    assertEquals(ReservoirFluidType.UNKNOWN, FluidClassifier.classify(null));
    // classifyByC7Plus takes double, so test with invalid value
    assertEquals(ReservoirFluidType.UNKNOWN, FluidClassifier.classifyByC7Plus(-1.0));
    assertEquals(0.0, FluidClassifier.calculateC7PlusContent(null), 1e-10);
  }
}
