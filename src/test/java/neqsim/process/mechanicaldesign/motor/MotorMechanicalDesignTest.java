package neqsim.process.mechanicaldesign.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.electricaldesign.components.ElectricalMotor;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.EquipmentDesignReport;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for MotorMechanicalDesign and EquipmentDesignReport.
 *
 * @author Even Solbraa
 */
public class MotorMechanicalDesignTest {

  private static SystemInterface testFluid;
  private static Stream testStream;

  @BeforeAll
  static void setUp() {
    testFluid = new SystemSrkEos(273.15 + 25.0, 10.0);
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");

    testStream = new Stream("feed", testFluid);
    testStream.setFlowRate(50000.0, "kg/hr");
    testStream.run();
  }

  // ============================================================================
  // MotorMechanicalDesign — Standalone Tests
  // ============================================================================

  @Test
  void testMotorMechanicalDesignStandalone() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(250.0);
    design.setPoles(4);
    design.setAmbientTemperatureC(40.0);
    design.setAltitudeM(0.0);
    design.setMotorStandard("IEC");
    design.calcDesign();

    // Foundation loads
    assertTrue(design.getTotalFoundationLoadKN() > 0, "Total foundation load should be positive");
    assertTrue(design.getRequiredFoundationMassKg() > 0,
        "Foundation mass should be positive: " + design.getRequiredFoundationMassKg());

    // Cooling
    assertNotNull(design.getCoolingCode(), "IC code should be assigned");

    // Bearings
    assertNotNull(design.getBearingType(), "Bearing type should be specified");
    assertTrue(design.getBearingL10LifeHours() > 0,
        "Bearing L10 life should be positive: " + design.getBearingL10LifeHours());

    // Vibration
    assertNotNull(design.getVibrationZone(), "Vibration zone should be classified");
    assertTrue(design.getMaxVibrationMmS() > 0, "Vibration limit should be positive");

    // Noise
    assertTrue(design.getSoundPressureLevelAt1mDbA() > 0,
        "Sound pressure level should be positive");

    // Enclosure
    assertNotNull(design.getIpRating(), "IP rating should be assigned");
  }

  @Test
  void testMotorMechanicalDesignSmallMotor() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(5.5);
    design.setPoles(4);
    design.calcDesign();

    assertTrue(design.getMotorWeightKg() > 0, "Motor weight should be positive");
    assertTrue(design.getMotorWeightKg() < 500, "Small motor weight should be reasonable");

    // Small motors should get IC411 (TEFC fan-cooled)
    assertEquals("IC411", design.getCoolingCode(), "Small motors should use IC411");

    // Bearings should be ball type
    assertTrue(design.getBearingType().contains("ball"), "Small motor should use ball bearings");
  }

  @Test
  void testMotorMechanicalDesignLargeMotor() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(3000.0);
    design.setPoles(2);
    design.calcDesign();

    assertTrue(design.getMotorWeightKg() > 1000, "Large motor should be heavy");

    // Large motors should get IC81W (water-cooled)
    assertEquals("IC81W", design.getCoolingCode(),
        "Large motors above 2000 kW should use water cooling");
  }

  @Test
  void testAltitudeDerating() {
    // At sea level — no derating
    MotorMechanicalDesign seaLevel = new MotorMechanicalDesign(100.0);
    seaLevel.setAltitudeM(0.0);
    seaLevel.setAmbientTemperatureC(40.0);
    seaLevel.calcDesign();

    // At 3000 m — significant derating
    MotorMechanicalDesign highAlt = new MotorMechanicalDesign(100.0);
    highAlt.setAltitudeM(3000.0);
    highAlt.setAmbientTemperatureC(40.0);
    highAlt.calcDesign();

    assertTrue(highAlt.getCombinedDeratingFactor() < seaLevel.getCombinedDeratingFactor(),
        "Higher altitude should produce greater derating");

    // 3000 m: (3000-1000)/100 * 1% = 20% derating from altitude
    assertTrue(highAlt.getCombinedDeratingFactor() < 0.85,
        "3000m altitude should derate significantly: " + highAlt.getCombinedDeratingFactor());
  }

  @Test
  void testTemperatureDerating() {
    MotorMechanicalDesign normal = new MotorMechanicalDesign(100.0);
    normal.setAmbientTemperatureC(40.0);
    normal.setAltitudeM(0.0);
    normal.calcDesign();

    MotorMechanicalDesign hot = new MotorMechanicalDesign(100.0);
    hot.setAmbientTemperatureC(55.0);
    hot.setAltitudeM(0.0);
    hot.calcDesign();

    assertTrue(hot.getCombinedDeratingFactor() < normal.getCombinedDeratingFactor(),
        "Higher temperature should produce greater derating");
  }

  @Test
  void testHazardousAreaEnclosure() {
    // Safe area
    MotorMechanicalDesign safe = new MotorMechanicalDesign(100.0);
    safe.setHazardousZone(-1);
    safe.calcDesign();
    assertEquals("IP55", safe.getIpRating(), "Safe area should use IP55");

    // Zone 1
    MotorMechanicalDesign zone1 = new MotorMechanicalDesign(100.0);
    zone1.setHazardousZone(1);
    zone1.setGasGroup("IIA");
    zone1.calcDesign();
    assertNotNull(zone1.getExMarking(), "Zone 1 motor should have Ex marking");
    assertTrue(zone1.getExMarking().length() > 0, "Ex marking should not be empty");

    // Zone 0
    MotorMechanicalDesign zone0 = new MotorMechanicalDesign(100.0);
    zone0.setHazardousZone(0);
    zone0.calcDesign();
    assertTrue(zone0.getIpRating().equals("IP66") || zone0.getIpRating().equals("IP67"),
        "Zone 0 should have high IP rating: " + zone0.getIpRating());
  }

  @Test
  void testVibrationZones() {
    // Small motor (Group 1: 0.16 - 15 kW)
    MotorMechanicalDesign small = new MotorMechanicalDesign(10.0);
    small.setPoles(4);
    small.calcDesign();
    assertNotNull(small.getVibrationZone());

    // Medium motor (Group 1: 15-75 kW or Group 2: above 75 kW)
    MotorMechanicalDesign medium = new MotorMechanicalDesign(200.0);
    medium.setPoles(4);
    medium.calcDesign();
    assertNotNull(medium.getVibrationZone());
    assertTrue(medium.getMaxVibrationMmS() > 0, "Should have positive vibration limit");
  }

  @Test
  void testNorsokNoiseLimit() {
    // Smaller motor should generally be within NORSOK S-002 limit
    MotorMechanicalDesign small = new MotorMechanicalDesign(11.0);
    small.setPoles(4);
    small.calcDesign();
    assertTrue(small.isNoiseWithinNorsokLimit(),
        "Small motor should comply with NORSOK S-002 noise limit");

    // Very large motor may exceed limit
    MotorMechanicalDesign large = new MotorMechanicalDesign(5000.0);
    large.setPoles(2);
    large.calcDesign();
    assertTrue(large.getSoundPressureLevelAt1mDbA() > 0,
        "Large motor should have a noise level assigned");
  }

  @Test
  void testBearingL10Life() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(250.0);
    design.setPoles(4);
    design.calcDesign();

    double l10Hours = design.getBearingL10LifeHours();
    assertTrue(l10Hours > 0, "Bearing L10 life should be positive");
    // IEEE 841 minimum is 3 years continuous = 26280 hours
    // Most properly sized motors should meet this
    assertTrue(l10Hours >= 26280, "Bearing L10 life should meet IEEE 841 minimum: " + l10Hours);
  }

  @Test
  void testDesignNotes() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(500.0);
    design.setHasVFD(true);
    design.calcDesign();

    assertNotNull(design.getDesignNotes());
    // VFD should add a noise note
    boolean hasVFDNote = false;
    for (String note : design.getDesignNotes()) {
      if (note.contains("VFD")) {
        hasVFDNote = true;
        break;
      }
    }
    assertTrue(hasVFDNote, "VFD should generate design notes about noise");
  }

  @Test
  void testAppliedStandards() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(100.0);
    design.calcDesign();

    List<String> standards = design.getAppliedStandards();
    assertNotNull(standards);
    assertTrue(standards.size() > 0, "Should have applied standards listed");
  }

  @Test
  void testFromElectricalDesign() {
    Compressor compressor = new Compressor("TestComp", testStream);
    compressor.setOutletPressure(50.0);
    compressor.run();

    ElectricalDesign elecDesign = compressor.getElectricalDesign();
    elecDesign.calcDesign();

    MotorMechanicalDesign motorDesign = new MotorMechanicalDesign(elecDesign);
    motorDesign.calcDesign();

    assertTrue(motorDesign.getShaftPowerKW() > 0, "Should inherit power from electrical design");
    assertTrue(motorDesign.getMotorWeightKg() > 0, "Motor weight should be positive");
    assertNotNull(motorDesign.toJson(), "JSON output should be available");
  }

  @Test
  void testJsonOutput() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(250.0);
    design.setPoles(4);
    design.setAmbientTemperatureC(45.0);
    design.setAltitudeM(500.0);
    design.setHazardousZone(1);
    design.setGasGroup("IIA");
    design.calcDesign();

    String json = design.toJson();
    assertNotNull(json);
    assertTrue(json.contains("foundation"), "JSON should contain foundation section");
    assertTrue(json.contains("cooling"), "JSON should contain cooling section");
    assertTrue(json.contains("bearings"), "JSON should contain bearings section");
    assertTrue(json.contains("vibration"), "JSON should contain vibration section");
    assertTrue(json.contains("noise"), "JSON should contain noise section");
    assertTrue(json.contains("enclosure"), "JSON should contain enclosure section");
    assertTrue(json.contains("appliedStandards"), "JSON should list applied standards");
  }

  @Test
  void testToMap() {
    MotorMechanicalDesign design = new MotorMechanicalDesign(250.0);
    design.calcDesign();

    Map<String, Object> map = design.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("designInput"), "Map should have designInput");
    assertTrue(map.containsKey("foundation"), "Map should have foundation");
    assertTrue(map.containsKey("cooling"), "Map should have cooling");
    assertTrue(map.containsKey("bearings"), "Map should have bearings");
    assertTrue(map.containsKey("vibration"), "Map should have vibration");
    assertTrue(map.containsKey("noise"), "Map should have noise");
    assertTrue(map.containsKey("enclosure"), "Map should have enclosure");
  }

  // ============================================================================
  // EquipmentDesignReport — Combined Report Tests
  // ============================================================================

  @Test
  void testCompressorDesignReport() {
    Compressor compressor = new Compressor("ExportComp", testStream);
    compressor.setOutletPressure(50.0);
    compressor.run();

    EquipmentDesignReport report = new EquipmentDesignReport(compressor);
    report.setUseVFD(true);
    report.setRatedVoltageV(6600.0);
    report.setHazardousZone(1);
    report.setAmbientTemperatureC(45.0);
    report.setAltitudeM(500.0);
    report.setMotorPoles(4);
    report.setCableLengthM(100.0);
    report.generateReport();

    assertNotNull(report.getVerdict(), "Verdict should be set");
    assertTrue(
        report.getVerdict().equals("FEASIBLE")
            || report.getVerdict().equals("FEASIBLE_WITH_WARNINGS")
            || report.getVerdict().equals("NOT_FEASIBLE"),
        "Verdict should be one of the valid values: " + report.getVerdict());

    // Check that sub-designs are present
    assertNotNull(report.getMechanicalDesign(), "Mechanical design should be present");
    assertNotNull(report.getElectricalDesign(), "Electrical design should be present");
    assertNotNull(report.getMotorMechanicalDesign(), "Motor mech design should be present");

    // JSON
    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("verdict"), "JSON should contain verdict");
    assertTrue(json.contains("mechanicalDesign"), "JSON should contain mechanicalDesign");
    assertTrue(json.contains("electricalDesign"), "JSON should contain electricalDesign");
    assertTrue(json.contains("motorMechanicalDesign"), "JSON should contain motorMechanicalDesign");
  }

  @Test
  void testCompressorDesignReportNoVFD() {
    Compressor compressor = new Compressor("DOLComp", testStream);
    compressor.setOutletPressure(30.0);
    compressor.run();

    EquipmentDesignReport report = new EquipmentDesignReport(compressor);
    report.setUseVFD(false);
    report.setRatedVoltageV(400.0);
    report.setHazardousZone(-1);
    report.generateReport();

    assertNotNull(report.getVerdict());
    assertTrue(report.isReportGenerated());

    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"equipmentType\""));
  }

  @Test
  void testSeparatorDesignReport() {
    Separator separator = new Separator("HPSep", testStream);
    separator.run();

    EquipmentDesignReport report = new EquipmentDesignReport(separator);
    report.setHazardousZone(2);
    report.generateReport();

    assertNotNull(report.getVerdict());
    assertNotNull(report.getMechanicalDesign());

    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("HPSep"), "JSON should contain equipment name");
  }

  @Test
  void testDesignReportLoadListEntry() {
    Compressor compressor = new Compressor("LoadComp", testStream);
    compressor.setOutletPressure(40.0);
    compressor.run();

    EquipmentDesignReport report = new EquipmentDesignReport(compressor);
    report.generateReport();

    Map<String, Object> loadEntry = report.toLoadListEntry();
    assertNotNull(loadEntry);
    assertEquals("LoadComp", loadEntry.get("equipmentName"));
    assertTrue(loadEntry.containsKey("ratedMotorPowerKW"));
    assertTrue(loadEntry.containsKey("absorbedPowerKW"));
    assertTrue(loadEntry.containsKey("electricalInputKW"));
  }

  @Test
  void testDesignReportIssuesTracking() {
    Compressor compressor = new Compressor("IssueComp", testStream);
    compressor.setOutletPressure(50.0);
    compressor.run();

    EquipmentDesignReport report = new EquipmentDesignReport(compressor);
    report.setAmbientTemperatureC(60.0); // Very hot environment
    report.setAltitudeM(3000.0); // Very high altitude
    report.setHazardousZone(1);
    report.generateReport();

    // With extreme derating, should generate warnings
    assertNotNull(report.getIssues());
    assertNotNull(report.getVerdict());

    String json = report.toJson();
    assertTrue(json.contains("issues"), "JSON should contain issues array");
  }

  @Test
  void testDesignReportDefaultValues() {
    Compressor compressor = new Compressor("DefaultComp", testStream);
    compressor.setOutletPressure(30.0);
    compressor.run();

    EquipmentDesignReport report = new EquipmentDesignReport(compressor);
    // Use all defaults
    report.generateReport();

    assertNotNull(report.getVerdict());
    assertEquals(400.0, report.getRatedVoltageV(), 0.01);
    assertEquals(-1, report.getHazardousZone());
  }

  @Test
  void testAutoReportOnJsonCall() {
    Compressor compressor = new Compressor("AutoComp", testStream);
    compressor.setOutletPressure(35.0);
    compressor.run();

    EquipmentDesignReport report = new EquipmentDesignReport(compressor);
    // toJson() should auto-generate report if not done
    String json = report.toJson();

    assertNotNull(json);
    assertTrue(report.isReportGenerated(), "Report should auto-generate on toJson()");
    assertTrue(json.contains("verdict"));
  }
}
