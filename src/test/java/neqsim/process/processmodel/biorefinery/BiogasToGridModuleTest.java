package neqsim.process.processmodel.biorefinery;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.AnaerobicDigester;
import neqsim.process.equipment.splitter.BiogasUpgrader;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link BiogasToGridModule}.
 */
class BiogasToGridModuleTest {

  private Stream createOrganicFeed() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 35.0, 1.01325);
    fluid.addComponent("water", 75.0);
    fluid.addComponent("methane", 0.01);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("organicFeed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();
    return feed;
  }

  @Test
  void testBiogasToGridWithMembrane() {
    BiogasToGridModule module = new BiogasToGridModule("BTG-1");
    module.setFeedStream(createOrganicFeed());
    module.setUpgradingTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
    module.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    module.setGridPressureBara(40.0);
    module.setGridTemperatureC(25.0);
    module.setDigesterTemperatureC(37.0);
    module.run();

    assertNotNull(module.getBiomethaneOutStream(), "Biomethane stream should exist");
    assertNotNull(module.getOffgasStream(), "Offgas stream should exist");
    assertNotNull(module.getDigestateStream(), "Digestate stream should exist");
  }

  @Test
  void testBiogasToGridWithPSA() {
    BiogasToGridModule module = new BiogasToGridModule("BTG-2");
    module.setFeedStream(createOrganicFeed());
    module.setUpgradingTechnology(BiogasUpgrader.UpgradingTechnology.PSA);
    module.setSubstrateType(AnaerobicDigester.SubstrateType.SEWAGE_SLUDGE);
    module.setGridPressureBara(50.0);
    module.run();

    Map<String, Object> results = module.getResults();
    assertNotNull(results);
    assertTrue(results.containsKey("biomethaneFlow_Nm3_per_hr"));
    assertTrue(results.containsKey("compressorPower_kW"));
    assertTrue(results.containsKey("coolerDuty_kW"));
    assertTrue(((Boolean) results.get("hasRun")));
  }

  @Test
  void testBiogasToGridWithAmine() {
    BiogasToGridModule module = new BiogasToGridModule("BTG-3");
    module.setFeedStream(createOrganicFeed());
    module.setUpgradingTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
    module.setGridPressureBara(40.0);
    module.run();

    String json = module.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Biogas-to-Grid"));
    assertTrue(json.contains("AMINE_SCRUBBING"));
  }

  @Test
  void testBiogasToGridResults() {
    BiogasToGridModule module = new BiogasToGridModule("BTG-4");
    module.setFeedStream(createOrganicFeed());
    module.setHydraulicRetentionTimeDays(30.0);
    module.run();

    Map<String, Object> results = module.getResults();
    assertTrue(results.containsKey("moduleName"));
    assertTrue(results.containsKey("processType"));
    assertTrue(results.containsKey("gridPressure_bara"));
    assertTrue(results.containsKey("upgradingTechnology"));
  }
}
