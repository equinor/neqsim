package neqsim.process.processmodel.biorefinery;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.AnaerobicDigester;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link WasteToEnergyCHPModule}.
 */
class WasteToEnergyCHPModuleTest {

  private Stream createWasteFeed() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 35.0, 1.01325);
    fluid.addComponent("water", 80.0);
    fluid.addComponent("methane", 0.01);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("wasteFeed", fluid);
    feed.setFlowRate(8000.0, "kg/hr");
    feed.run();
    return feed;
  }

  @Test
  void testFoodWasteCHP() {
    WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-1");
    module.setFeedStream(createWasteFeed());
    module.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    module.setElectricalEfficiency(0.38);
    module.setThermalEfficiency(0.45);
    module.setDigesterTemperatureC(37.0);
    module.run();

    assertTrue(module.getElectricalPowerKW() >= 0.0, "Electrical power should be non-negative");
    assertTrue(module.getHeatOutputKW() >= 0.0, "Heat output should be non-negative");
    assertTrue(module.getTotalCHPefficiency() <= 1.0, "CHP efficiency should not exceed 1");
    assertNotNull(module.getExhaustGasStream(), "Exhaust stream should exist");
    assertNotNull(module.getDigestateStream(), "Digestate stream should exist");
  }

  @Test
  void testSewageSludgeCHP() {
    WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-2");
    module.setFeedStream(createWasteFeed());
    module.setSubstrateType(AnaerobicDigester.SubstrateType.SEWAGE_SLUDGE);
    module.setElectricalEfficiency(0.35);
    module.setThermalEfficiency(0.50);
    module.run();

    assertTrue(module.getTotalCHPefficiency() == 0.85, "Total efficiency should be 0.85");
    assertTrue(module.getCO2EmissionsKgPerHr() >= 0.0, "CO2 emissions should be non-negative");
  }

  @Test
  void testAnnualProduction() {
    WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-3");
    module.setFeedStream(createWasteFeed());
    module.setOperatingHoursPerYear(8000.0);
    module.run();

    assertTrue(module.getAnnualElectricityMWh() >= 0.0);
    assertTrue(module.getAnnualHeatMWh() >= 0.0);
    // Annual = power_kW * hours / 1000
    if (module.getElectricalPowerKW() > 0) {
      double expected = module.getElectricalPowerKW() * 8000.0 / 1000.0;
      assertTrue(Math.abs(module.getAnnualElectricityMWh() - expected) < 0.1);
    }
  }

  @Test
  void testResultsMap() {
    WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-4");
    module.setFeedStream(createWasteFeed());
    module.run();

    Map<String, Object> results = module.getResults();
    assertTrue(results.containsKey("electricalPower_kW"));
    assertTrue(results.containsKey("heatOutput_kW"));
    assertTrue(results.containsKey("CO2emissions_kg_per_hr"));
    assertTrue(results.containsKey("annualElectricity_MWh"));
    assertTrue(results.containsKey("annualHeat_MWh"));
    assertTrue(results.containsKey("totalCHPefficiency"));
  }

  @Test
  void testToJson() {
    WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-5");
    module.setFeedStream(createWasteFeed());
    module.run();

    String json = module.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Waste-to-Energy CHP"));
    assertTrue(json.contains("electricalPower_kW"));
  }

  @Test
  void testCustomRetentionTime() {
    WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-6");
    module.setFeedStream(createWasteFeed());
    module.setHydraulicRetentionTimeDays(35.0);
    module.setSubstrateType(AnaerobicDigester.SubstrateType.MANURE);
    module.run();

    Map<String, Object> results = module.getResults();
    assertTrue(((Boolean) results.get("hasRun")));
    assertTrue(results.get("substrateType").equals("MANURE"));
  }
}
