package neqsim.process.equipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link BiogasUpgrader}.
 */
class BiogasUpgraderTest {

  /**
   * Creates a typical raw biogas stream (60% CH4, 38% CO2, 0.2% H2S, rest water).
   *
   * @return raw biogas stream
   */
  private StreamInterface createRawBiogasStream() {
    SystemInterface biogas = new SystemSrkEos(273.15 + 35.0, 1.01325);
    biogas.addComponent("methane", 0.60);
    biogas.addComponent("CO2", 0.38);
    biogas.addComponent("H2S", 0.002);
    biogas.addComponent("water", 0.018);
    biogas.setMixingRule("classic");
    biogas.setTotalFlowRate(500.0, "Sm3/hr");
    Stream stream = new Stream("raw biogas", biogas);
    stream.run();
    return stream;
  }

  @Test
  void testWaterScrubbing() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-1", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.WATER_SCRUBBING);
    upgrader.run();

    assertTrue(upgrader.getBiomethaneMethanePercent() > 90.0,
        "Biomethane should have >90% CH4, got " + upgrader.getBiomethaneMethanePercent());
    assertTrue(upgrader.getBiomethaneCO2Percent() < 5.0, "CO2 in biomethane should be <5%");
    assertTrue(upgrader.getMethaneSlipPercent() < 5.0,
        "Methane slip should be <5% for water scrubbing");
    assertTrue(upgrader.getEnergyConsumptionKW() > 0.0, "Energy consumption should be positive");
  }

  @Test
  void testAmineScrubbing() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-2", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
    upgrader.run();

    assertTrue(upgrader.getBiomethaneMethanePercent() > 95.0,
        "Amine scrubbing should give >95% CH4, got " + upgrader.getBiomethaneMethanePercent());
    assertTrue(upgrader.getMethaneSlipPercent() < 1.0,
        "Amine scrubbing should have <1% methane slip");
  }

  @Test
  void testMembraneSeparation() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-3", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
    upgrader.run();

    assertTrue(upgrader.getBiomethaneMethanePercent() > 90.0, "Membrane should give >90% CH4");
    assertTrue(upgrader.getMethaneSlipPercent() < 2.0, "Membrane should have <2% methane slip");
  }

  @Test
  void testPSA() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-4", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.PSA);
    upgrader.run();

    assertTrue(upgrader.getBiomethaneMethanePercent() > 90.0, "PSA should give >90% CH4");
    assertTrue(upgrader.getEnergyConsumptionKW() > 0.0, "PSA should consume energy");
  }

  @Test
  void testAmineGivesHigherPurityThanWater() {
    StreamInterface biogas1 = createRawBiogasStream();
    BiogasUpgrader amine = new BiogasUpgrader("amine", biogas1);
    amine.setTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
    amine.run();

    StreamInterface biogas2 = createRawBiogasStream();
    BiogasUpgrader water = new BiogasUpgrader("water", biogas2);
    water.setTechnology(BiogasUpgrader.UpgradingTechnology.WATER_SCRUBBING);
    water.run();

    assertTrue(amine.getBiomethaneMethanePercent() > water.getBiomethaneMethanePercent(),
        "Amine scrubbing should give higher purity than water scrubbing");
    assertTrue(amine.getMethaneSlipPercent() < water.getMethaneSlipPercent(),
        "Amine scrubbing should have lower methane slip");
  }

  @Test
  void testOutletStreams() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-out", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
    upgrader.run();

    assertNotNull(upgrader.getBiomethaneOutStream(), "Biomethane stream should exist");
    assertNotNull(upgrader.getOffgasOutStream(), "Off-gas stream should exist");

    assertEquals(2, upgrader.getOutletStreams().size(), "Should have 2 outlet streams");
    assertEquals(1, upgrader.getInletStreams().size(), "Should have 1 inlet stream");

    // Off-gas should be CO2-rich
    SystemInterface offgas = upgrader.getOffgasOutStream().getThermoSystem();
    assertTrue(offgas.hasComponent("CO2"), "Off-gas should contain CO2");
  }

  @Test
  void testWobbeIndex() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-wobbe", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
    upgrader.run();

    double wobbe = upgrader.getWobbeIndex();
    assertTrue(wobbe > 30.0 && wobbe < 60.0,
        "Wobbe index should be in reasonable range (30-60 MJ/Nm3), got " + wobbe);
  }

  @Test
  void testCustomRemovalEfficiency() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-custom", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.WATER_SCRUBBING);
    upgrader.setCO2RemovalEfficiency(0.995);
    upgrader.setMethaneRecovery(0.995);
    upgrader.run();

    assertTrue(upgrader.getBiomethaneMethanePercent() > 98.0,
        "Custom high-efficiency should give >98% CH4");
    assertTrue(upgrader.getMethaneSlipPercent() < 1.0, "Custom high-recovery should give <1% slip");
  }

  @Test
  void testFlowRateConservation() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-balance", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
    upgrader.run();

    double inletFlow = rawBiogas.getThermoSystem().getFlowRate("mole/hr");
    double biomethaneFlow =
        upgrader.getBiomethaneOutStream().getThermoSystem().getFlowRate("mole/hr");
    double offgasFlow = upgrader.getOffgasOutStream().getThermoSystem().getFlowRate("mole/hr");

    double balance = Math.abs(inletFlow - biomethaneFlow - offgasFlow) / inletFlow;
    assertTrue(balance < 0.01,
        "Molar balance should be conserved within 1%, got " + (balance * 100) + "% error");
  }

  @Test
  void testGetResults() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-results", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.AMINE_SCRUBBING);
    upgrader.run();

    Map<String, Object> results = upgrader.getResults();
    assertNotNull(results);
    assertTrue(results.containsKey("technology"));
    assertTrue(results.containsKey("biomethaneMethane_volPercent"));
    assertTrue(results.containsKey("wobbeIndex_MJperNm3"));
    assertTrue(results.containsKey("meetsGridInjectionSpec"));
    assertEquals("AMINE_SCRUBBING", results.get("technology"));
  }

  @Test
  void testToJson() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-json", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.PSA);
    upgrader.run();

    String json = upgrader.toJson();
    assertNotNull(json);
    assertTrue(json.contains("PSA"), "JSON should contain technology name");
    assertTrue(json.contains("wobbeIndex"), "JSON should contain Wobbe index");
  }

  @Test
  void testToString() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-str", rawBiogas);

    // Before run
    String beforeRun = upgrader.toString();
    assertTrue(beforeRun.contains("not yet run"), "Should say not yet run");

    // After run
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.WATER_SCRUBBING);
    upgrader.run();
    String afterRun = upgrader.toString();
    assertTrue(afterRun.contains("WATER_SCRUBBING"), "Should contain technology name");
    assertTrue(afterRun.contains("Nm3/hr"), "Should contain flow rate unit");
  }

  @Test
  void testOutletPressureSetting() {
    StreamInterface rawBiogas = createRawBiogasStream();
    BiogasUpgrader upgrader = new BiogasUpgrader("BGU-press", rawBiogas);
    upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
    upgrader.setOutletPressure(8.0); // 8 bara for grid injection
    upgrader.run();

    double outPressure = upgrader.getBiomethaneOutStream().getThermoSystem().getPressure();
    assertEquals(8.0, outPressure, 0.1, "Outlet pressure should be 8 bara");
  }
}
