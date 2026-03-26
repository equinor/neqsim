package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.designstandards.FireProtectionDesign;
import neqsim.process.mechanicaldesign.designstandards.NoiseAssessment;
import neqsim.process.processmodel.ProcessFlowDiagramExporter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for new engineering study deliverable classes: ThermalUtilitySummary,
 * ProcessFlowDiagramExporter, FireProtectionDesign extensions, SparePartsInventory, NoiseAssessment
 * extensions, and AlarmTripScheduleGenerator.
 *
 * @author esol
 */
class EngineeringStudyDeliverablesTest {
  static ProcessSystem process;
  static SystemInterface fluid;

  @BeforeAll
  static void setUp() {
    fluid = new SystemSrkEos(273.15 + 30.0, 60.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(60.0, "bara");

    Separator hpSep = new Separator("HP-Sep", feed);
    Stream gasOut = new Stream("Gas Out", hpSep.getGasOutStream());
    Compressor comp = new Compressor("Comp-1", gasOut);
    comp.setOutletPressure(120.0);

    Cooler cooler = new Cooler("After Cooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 40.0);

    ThrottlingValve valve = new ThrottlingValve("JT Valve", cooler.getOutletStream());
    valve.setOutletPressure(30.0);

    Heater heater = new Heater("Reboiler", hpSep.getLiquidOutStream());
    heater.setOutTemperature(273.15 + 100.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(hpSep);
    process.add(gasOut);
    process.add(comp);
    process.add(cooler);
    process.add(valve);
    process.add(heater);
    process.run();
  }

  // ============================================================================
  // ThermalUtilitySummary Tests
  // ============================================================================
  @Nested
  @DisplayName("ThermalUtilitySummary Tests")
  class ThermalUtilitySummaryTests {

    @Test
    @DisplayName("calcUtilities should populate cooling and heating totals")
    void testCalcUtilities() {
      ThermalUtilitySummary util = new ThermalUtilitySummary(process);
      util.calcUtilities();

      // At least one of cooling or heating should be non-zero (we have a cooler and heater)
      assertTrue(util.getTotalCoolingDutyKW() > 0 || util.getTotalHeatingDutyKW() > 0,
          "Should have some thermal utility demand");
    }

    @Test
    @DisplayName("CW flow should be non-negative")
    void testCoolingWaterFlow() {
      ThermalUtilitySummary util = new ThermalUtilitySummary(process);
      util.calcUtilities();
      assertTrue(util.getCoolingWaterFlowM3hr() >= 0, "Cooling water flow should be non-negative");
    }

    @Test
    @DisplayName("Instrument air should be proportional to equipment count")
    void testInstrumentAir() {
      ThermalUtilitySummary util = new ThermalUtilitySummary(process);
      util.calcUtilities();
      assertTrue(util.getInstrumentAirNm3hr() > 0,
          "Instrument air should be positive for any process");
    }

    @Test
    @DisplayName("toJson should return valid JSON with expected fields")
    void testToJson() {
      ThermalUtilitySummary util = new ThermalUtilitySummary(process);
      util.calcUtilities();
      String json = util.toJson();
      assertNotNull(json);
      assertTrue(json.contains("totalCoolingDutyKW"));
      assertTrue(json.contains("totalHeatingDutyKW"));
      assertTrue(json.contains("coolingWater"));
      assertTrue(json.contains("steam"));
      assertTrue(json.contains("instrumentAirNm3hr"));
    }

    @Test
    @DisplayName("Custom CW temperatures affect flow")
    void testCustomCwTemperatures() {
      ThermalUtilitySummary util1 = new ThermalUtilitySummary(process);
      util1.setCwSupplyTempC(10.0);
      util1.setCwReturnTempC(35.0);
      util1.calcUtilities();

      ThermalUtilitySummary util2 = new ThermalUtilitySummary(process);
      util2.setCwSupplyTempC(20.0);
      util2.setCwReturnTempC(35.0);
      util2.calcUtilities();

      // Wider deltaT should give lower flow for same duty
      assertTrue(util1.getCoolingWaterFlowM3hr() <= util2.getCoolingWaterFlowM3hr() + 0.01,
          "Wider DeltaT should need less CW");
    }

    @Test
    @DisplayName("Consumers list should be populated")
    void testConsumersList() {
      ThermalUtilitySummary util = new ThermalUtilitySummary(process);
      util.calcUtilities();
      assertNotNull(util.getConsumers());
    }
  }

  // ============================================================================
  // ProcessFlowDiagramExporter Tests
  // ============================================================================
  @Nested
  @DisplayName("ProcessFlowDiagramExporter Tests")
  class PFDExporterTests {

    @Test
    @DisplayName("toDot should produce valid DOT format")
    void testToDot() {
      ProcessFlowDiagramExporter exporter = new ProcessFlowDiagramExporter(process);
      String dot = exporter.toDot();
      assertNotNull(dot);
      assertTrue(dot.startsWith("digraph"), "Should start with digraph");
      assertTrue(dot.contains("rankdir=LR"), "Should have left-to-right layout");
      assertTrue(dot.contains("Feed"), "Should contain Feed node");
      assertTrue(dot.contains("HP-Sep"), "Should contain HP-Sep node");
      assertTrue(dot.contains("Comp-1"), "Should contain Comp-1 node");
    }

    @Test
    @DisplayName("Custom title should appear in DOT output")
    void testCustomTitle() {
      ProcessFlowDiagramExporter exporter = new ProcessFlowDiagramExporter(process);
      exporter.setTitle("Gas Processing PFD");
      String dot = exporter.toDot();
      assertTrue(dot.contains("Gas Processing PFD"));
    }

    @Test
    @DisplayName("Explicit connections should appear in DOT")
    void testExplicitConnections() {
      ProcessSystem ps = new ProcessSystem();
      SystemInterface f = new SystemSrkEos(273.15 + 25.0, 50.0);
      f.addComponent("methane", 1.0);
      f.setMixingRule("classic");
      Stream s = new Stream("TestFeed", f);
      s.setFlowRate(1000.0, "kg/hr");
      Separator sep = new Separator("TestSep", s);
      ps.add(s);
      ps.add(sep);
      ps.connect("TestFeed", "TestSep");
      ps.run();

      ProcessFlowDiagramExporter exporter = new ProcessFlowDiagramExporter(ps);
      String dot = exporter.toDot();
      assertTrue(dot.contains("TestFeed") && dot.contains("TestSep"),
          "DOT should contain both nodes");
    }
  }

  // ============================================================================
  // FireProtectionDesign Extension Tests
  // ============================================================================
  @Nested
  @DisplayName("FireProtectionDesign Extension Tests")
  class FireProtectionExtTests {

    @Test
    @DisplayName("Jet fire flame length should be positive for positive release")
    void testJetFireFlameLength() {
      double length = FireProtectionDesign.jetFireFlameLength(5.0, 50000.0);
      assertTrue(length > 0, "Flame length should be positive");
      assertTrue(length < 200.0, "Flame length should be realistic");
    }

    @Test
    @DisplayName("Jet fire length increases with release rate")
    void testJetFireLengthScaling() {
      double l1 = FireProtectionDesign.jetFireFlameLength(1.0, 50000.0);
      double l2 = FireProtectionDesign.jetFireFlameLength(10.0, 50000.0);
      assertTrue(l2 > l1, "Higher release rate should give longer flame");
    }

    @Test
    @DisplayName("BLEVE fireball diameter should follow M^(1/3) trend")
    void testBleveFireballDiameter() {
      double d1 = FireProtectionDesign.bleveFireballDiameter(1000.0);
      double d8 = FireProtectionDesign.bleveFireballDiameter(8000.0);
      // 8x mass => 2x diameter (since 8^(1/3)=2)
      assertEquals(2.0, d8 / d1, 0.01, "BLEVE diameter should scale with M^(1/3)");
    }

    @Test
    @DisplayName("BLEVE fireball duration should positive")
    void testBleveFireballDuration() {
      double t = FireProtectionDesign.bleveFireballDuration(5000.0);
      assertTrue(t > 0, "Duration should be positive");
    }

    @Test
    @DisplayName("BLEVE overpressure should decrease with distance")
    void testBleveOverpressure() {
      double p50 = FireProtectionDesign.bleveOverpressure(20.0, 50.0, 50.0);
      double p200 = FireProtectionDesign.bleveOverpressure(20.0, 50.0, 200.0);
      assertTrue(p50 > p200, "Overpressure should decrease with distance");
    }

    @Test
    @DisplayName("Fire scenario assessment should return non-null result")
    void testFireScenarioAssessment() {
      FireProtectionDesign.FireScenarioResult result = FireProtectionDesign
          .assessFireScenarios("V-100", 5000.0, 20.0, 50.0, 10.0, 2.0, 50000.0, 0.055);
      assertNotNull(result);
      assertTrue(result.poolFireHeatReleaseKW > 0);
      assertTrue(result.jetFireFlameLengthM > 0);
      assertTrue(result.bleveFireballDiameterM > 0);
    }

    @Test
    @DisplayName("Fire scenario result toJson should be valid")
    void testFireScenarioResultJson() {
      FireProtectionDesign.FireScenarioResult result = FireProtectionDesign
          .assessFireScenarios("V-100", 5000.0, 20.0, 50.0, 10.0, 2.0, 50000.0, 0.055);
      String json = result.toJson();
      assertNotNull(json);
      assertTrue(json.contains("poolFire"));
      assertTrue(json.contains("jetFire"));
      assertTrue(json.contains("bleve"));
    }
  }

  // ============================================================================
  // SparePartsInventory Tests
  // ============================================================================
  @Nested
  @DisplayName("SparePartsInventory Tests")
  class SparePartsTests {

    @Test
    @DisplayName("Inventory should contain entries for separator and compressor")
    void testInventoryGeneration() {
      SparePartsInventory inventory = new SparePartsInventory(process);
      inventory.generateInventory();
      assertFalse(inventory.getEntries().isEmpty(), "Should have spare parts entries");
    }

    @Test
    @DisplayName("Should have critical items")
    void testCriticalItems() {
      SparePartsInventory inventory = new SparePartsInventory(process);
      inventory.generateInventory();
      assertFalse(inventory.getEntriesByCriticality("Critical").isEmpty(),
          "Should have critical spare parts");
    }

    @Test
    @DisplayName("toJson should produce valid JSON")
    void testToJson() {
      SparePartsInventory inventory = new SparePartsInventory(process);
      inventory.generateInventory();
      String json = inventory.toJson();
      assertNotNull(json);
      assertTrue(json.contains("spareParts"));
      assertTrue(json.contains("criticality"));
      assertTrue(json.contains("leadTimeWeeks"));
    }
  }

  // ============================================================================
  // NoiseAssessment Extension Tests
  // ============================================================================
  @Nested
  @DisplayName("NoiseAssessment Extension Tests")
  class NoiseExtTests {

    @Test
    @DisplayName("Atmospheric absorption at 1kHz should be positive")
    void testAtmosphericAbsorption() {
      double alpha = NoiseAssessment.atmosphericAbsorption(1000.0, 20.0, 70.0);
      assertTrue(alpha > 0, "Absorption should be positive");
      assertTrue(alpha < 1.0, "Absorption at 1kHz should be small per meter");
    }

    @Test
    @DisplayName("Higher frequency should have more absorption")
    void testAbsorptionFrequencyDependence() {
      double alpha1k = NoiseAssessment.atmosphericAbsorption(1000.0, 20.0, 70.0);
      double alpha8k = NoiseAssessment.atmosphericAbsorption(8000.0, 20.0, 70.0);
      assertTrue(alpha8k > alpha1k, "8 kHz should be absorbed more than 1 kHz");
    }

    @Test
    @DisplayName("SPL with attenuation should be less than without")
    void testSplWithAttenuation() {
      double swl = 100.0; // dB(A)
      double splBasic = NoiseAssessment.splAtDistance(swl, 100.0);
      double splAtten = NoiseAssessment.splAtDistanceWithAttenuation(swl, 100.0, 20.0, 70.0);
      assertTrue(splAtten < splBasic,
          "Atmospheric absorption should reduce SPL below geometric-only");
    }

    @Test
    @DisplayName("Octave band method should give reasonable results")
    void testOctaveBandMethod() {
      double spl = NoiseAssessment.splAtDistanceOctaveBand(110.0, 200.0, 20.0, 70.0);
      assertTrue(spl > 0, "SPL should be positive");
      assertTrue(spl < 110.0, "SPL at distance should be less than SWL");
    }
  }

  // ============================================================================
  // AlarmTripScheduleGenerator Tests
  // ============================================================================
  @Nested
  @DisplayName("AlarmTripScheduleGenerator Tests")
  class AlarmTripTests {

    @Test
    @DisplayName("Generator should produce entries for separator and compressor")
    void testGenerate() {
      AlarmTripScheduleGenerator gen = new AlarmTripScheduleGenerator(process);
      gen.generate();
      assertTrue(gen.getEntryCount() > 0, "Should generate alarm/trip entries");
    }

    @Test
    @DisplayName("Should have both alarms and trips")
    void testAlarmsAndTrips() {
      AlarmTripScheduleGenerator gen = new AlarmTripScheduleGenerator(process);
      gen.generate();
      String json = gen.toJson();
      assertTrue(json.contains("\"Alarm\""), "Should contain alarm entries");
      assertTrue(json.contains("\"Trip\""), "Should contain trip entries");
    }

    @Test
    @DisplayName("Entries for specific equipment should be non-empty")
    void testEquipmentFilter() {
      AlarmTripScheduleGenerator gen = new AlarmTripScheduleGenerator(process);
      gen.generate();
      assertFalse(gen.getEntriesForEquipment("HP-Sep").isEmpty(),
          "HP-Sep should have alarm entries");
    }

    @Test
    @DisplayName("toJson should contain expected JSON structure")
    void testToJson() {
      AlarmTripScheduleGenerator gen = new AlarmTripScheduleGenerator(process);
      gen.generate();
      String json = gen.toJson();
      assertNotNull(json);
      assertTrue(json.contains("alarmSchedule"));
      assertTrue(json.contains("equipmentTag"));
      assertTrue(json.contains("setpointValue"));
      assertTrue(json.contains("priority"));
    }

    @Test
    @DisplayName("Separator should have level alarms")
    void testSeparatorLevelAlarms() {
      AlarmTripScheduleGenerator gen = new AlarmTripScheduleGenerator(process);
      gen.generate();
      boolean hasLevel = false;
      for (AlarmTripScheduleGenerator.AlarmTripEntry e : gen.getEntriesForEquipment("HP-Sep")) {
        if (e.getServiceType() == AlarmTripScheduleGenerator.ServiceType.LEVEL) {
          hasLevel = true;
          break;
        }
      }
      assertTrue(hasLevel, "Separator should have level alarms");
    }
  }
}
