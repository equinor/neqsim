package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AnaerobicDigester}.
 */
class AnaerobicDigesterTest {

  @Test
  void testFoodWasteDigestion() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-1");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    digester.setFeedRate(10000.0, 0.25); // 10 t/hr at 25% TS
    digester.setVesselVolume(5000.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.run();

    assertTrue(digester.getBiogasFlowRateNm3PerDay() > 0.0, "Biogas flow should be positive");
    assertTrue(digester.getMethaneProductionNm3PerDay() > 0.0,
        "Methane production should be positive");
    assertEquals(60.0, digester.getMethaneContentPercent(), 1e-6,
        "Default methane content should be 60%");
  }

  @Test
  void testSewageSludge() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-2");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.SEWAGE_SLUDGE);
    digester.setFeedRate(5000.0, 0.05); // 5 t/hr at 5% TS
    digester.setVesselVolume(3000.0);
    digester.setDigesterTemperature(35.0, "C");
    digester.run();

    assertTrue(digester.getBiogasFlowRateNm3PerDay() > 0.0);
    assertTrue(digester.getOrganicLoadingRate() > 0.0, "OLR should be positive");
    assertTrue(digester.getHydraulicRetentionTimeDays() > 0.0, "HRT should be positive");
  }

  @Test
  void testManureDigestion() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-3");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.MANURE);
    digester.setFeedRate(8000.0, 0.08);
    digester.setVesselVolume(4000.0);
    digester.setDigesterTemperature(38.0, "C");
    digester.run();

    // Manure has lower methane yield than food waste
    double manureCH4 = digester.getMethaneProductionNm3PerDay();

    AnaerobicDigester foodDigester = new AnaerobicDigester("AD-food");
    foodDigester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    foodDigester.setFeedRate(8000.0, 0.08);
    foodDigester.setVesselVolume(4000.0);
    foodDigester.setDigesterTemperature(38.0, "C");
    foodDigester.run();

    double foodCH4 = foodDigester.getMethaneProductionNm3PerDay();
    assertTrue(foodCH4 > manureCH4,
        "Food waste should produce more methane per unit feed than manure");
  }

  @Test
  void testThermophilicRegime() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-thermo");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    digester.setFeedRate(5000.0, 0.20);
    digester.setVesselVolume(2000.0);
    digester.setDigesterTemperature(55.0, "C");
    digester.run();

    assertEquals(AnaerobicDigester.TemperatureRegime.THERMOPHILIC, digester.getTemperatureRegime(),
        "55 C should be thermophilic");
    assertTrue(digester.getBiogasFlowRateNm3PerDay() > 0.0);
  }

  @Test
  void testMesophilicRegime() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-meso");
    digester.setDigesterTemperature(37.0, "C");
    assertEquals(AnaerobicDigester.TemperatureRegime.MESOPHILIC, digester.getTemperatureRegime(),
        "37 C should be mesophilic");
  }

  @Test
  void testCustomParameters() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-custom");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.CUSTOM);
    digester.setFeedRate(5000.0, 0.10);
    digester.setVesselVolume(3000.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.setSpecificMethaneYield(0.40);
    digester.setVSDestruction(0.70);
    digester.setMethaneFraction(0.65);
    digester.run();

    assertEquals(65.0, digester.getMethaneContentPercent(), 1e-6);
    assertEquals(0.70, digester.getActualVsDestruction(), 1e-6);
    assertTrue(digester.getMethaneProductionNm3PerDay() > 0.0);
  }

  @Test
  void testBiogasAndDigestateStreams() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-streams");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.ENERGY_CROP);
    digester.setFeedRate(3000.0, 0.30);
    digester.setVesselVolume(2000.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.run();

    assertNotNull(digester.getBiogasOutStream(), "Biogas stream should exist");
    assertNotNull(digester.getDigestateOutStream(), "Digestate stream should exist");

    // Biogas should contain methane and CO2
    assertTrue(digester.getBiogasOutStream().getThermoSystem().hasComponent("methane"));
    assertTrue(digester.getBiogasOutStream().getThermoSystem().hasComponent("CO2"));

    // Digestate should contain water
    assertTrue(digester.getDigestateOutStream().getThermoSystem().hasComponent("water"));
  }

  @Test
  void testOutletStreams() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-outlets");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    digester.setFeedRate(1000.0, 0.15);
    digester.setVesselVolume(500.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.run();

    assertEquals(2, digester.getOutletStreams().size(), "Should have 2 outlet streams");
  }

  @Test
  void testGetResults() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-results");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.CROP_RESIDUE);
    digester.setFeedRate(5000.0, 0.15);
    digester.setVesselVolume(3000.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.run();

    Map<String, Object> results = digester.getResults();
    assertNotNull(results);
    assertTrue(results.containsKey("biogasFlowRate_Nm3PerDay"));
    assertTrue(results.containsKey("methaneProduction_Nm3PerDay"));
    assertTrue(results.containsKey("organicLoadingRate_kgVSperM3Day"));
    assertTrue(results.containsKey("hydraulicRetentionTime_days"));
    assertTrue(results.containsKey("vsDestruction"));
  }

  @Test
  void testToJson() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-json");
    digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    digester.setFeedRate(2000.0, 0.20);
    digester.setVesselVolume(1000.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.run();

    String json = digester.toJson();
    assertNotNull(json);
    assertTrue(json.contains("biogasFlowRate_Nm3PerDay"));
    assertTrue(json.contains("methaneProduction_Nm3PerDay"));
  }

  @Test
  void testToStringBeforeAndAfterRun() {
    AnaerobicDigester digester = new AnaerobicDigester("AD-str");

    String beforeRun = digester.toString();
    assertTrue(beforeRun.contains("not yet run"));

    digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
    digester.setFeedRate(2000.0, 0.20);
    digester.setVesselVolume(1000.0);
    digester.setDigesterTemperature(37.0, "C");
    digester.run();

    String afterRun = digester.toString();
    assertTrue(afterRun.contains("Biogas"));
    assertTrue(afterRun.contains("CH4"));
  }

  @Test
  void testAllSubstrateTypes() {
    AnaerobicDigester.SubstrateType[] types = AnaerobicDigester.SubstrateType.values();
    for (AnaerobicDigester.SubstrateType type : types) {
      AnaerobicDigester digester = new AnaerobicDigester("AD-" + type.name());
      digester.setSubstrateType(type);
      digester.setFeedRate(1000.0, 0.10);
      digester.setVesselVolume(500.0);
      digester.setDigesterTemperature(37.0, "C");
      digester.run();

      assertTrue(digester.getBiogasFlowRateNm3PerDay() > 0.0,
          "Biogas flow should be positive for " + type.name());
      assertTrue(digester.getMethaneProductionNm3PerDay() > 0.0,
          "CH4 production should be positive for " + type.name());
    }
  }
}
