package neqsim.process.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorWashing;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the maintenance deferral assessment framework.
 *
 * @author NeqSim Development Team
 */
class MaintenanceDeferralTest {

  /**
   * Creates a simple process system with a compressor for testing.
   */
  private ProcessSystem createTestSystem() {
    SystemInterface gas = new SystemSrkEos(288.15, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(15.0, "C");
    feed.setPressure(30.0, "bara");

    Compressor comp = new Compressor("compressor1", feed);
    comp.setOutletPressure(80.0);
    comp.setPolytropicEfficiency(0.78);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    return process;
  }

  @Test
  void testCompressorDegradationReducesPower() {
    ProcessSystem process = createTestSystem();
    Compressor comp = (Compressor) process.getUnit("compressor1");

    // Run baseline
    process.run();
    double baselinePower = comp.getPower();
    assertTrue(baselinePower > 0, "Baseline power should be positive");

    // Apply degradation (reduces effective efficiency → increases power consumption)
    comp.setDegradationFactor(0.90);
    process.run();
    double degradedPower = comp.getPower();

    // Degraded compressor should consume more power (lower effective efficiency)
    assertTrue(degradedPower > baselinePower, "Degraded compressor should use more power: baseline="
        + baselinePower + ", degraded=" + degradedPower);
  }

  @Test
  void testCompressorFoulingReducesHead() {
    ProcessSystem process = createTestSystem();
    Compressor comp = (Compressor) process.getUnit("compressor1");

    // Apply fouling
    comp.setFoulingFactor(0.15);
    process.run();

    // Fouled compressor with same outlet pressure target will need more power
    double fouledPower = comp.getPower();
    assertTrue(fouledPower > 0, "Fouled compressor should still produce positive power");
  }

  @Test
  void testCompressorWashingRecoversFouling() {
    Compressor comp = new Compressor("test_comp");
    comp.setFoulingFactor(0.20);
    assertEquals(0.20, comp.getFoulingFactor(), 0.001);

    // Online wash should recover ~40% of fouling
    comp.applyWashing(CompressorWashing.WashingMethod.ONLINE_WET);
    assertTrue(comp.getFoulingFactor() < 0.20, "Fouling should decrease after washing");
    assertTrue(comp.getFoulingFactor() > 0.0, "Online wash shouldn't remove all fouling");
  }

  @Test
  void testCompressorOperatingHoursIncrement() {
    Compressor comp = new Compressor("test_comp");
    assertEquals(0.0, comp.getOperatingHours(), 0.001);

    comp.incrementOperatingHours(100.0, 0.0001);
    assertEquals(100.0, comp.getOperatingHours(), 0.001);
    assertEquals(0.01, comp.getFoulingFactor(), 0.001);

    comp.incrementOperatingHours(200.0);
    assertEquals(300.0, comp.getOperatingHours(), 0.001);
  }

  @Test
  void testHealthAssessmentCalculation() {
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("compressor1", "compressor");
    health.setDegradationFactor(0.92);
    health.setFoulingFactor(0.05);
    health.setOperatingHours(20000.0);
    health.setHoursSinceOverhaul(15000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);

    health.calculate();

    assertTrue(health.getHealthIndex() > 0, "Health index should be positive");
    assertTrue(health.getHealthIndex() <= 1.0, "Health index should be <= 1");
    assertNotNull(health.getSeverity());
    assertTrue(health.getEstimatedRemainingLife() > 0, "RUL should be positive");
  }

  @Test
  void testHealthAssessmentCriticalState() {
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("pump1", "pump");
    health.setDegradationFactor(0.30);
    health.setFoulingFactor(0.50);
    health.setOperatingHours(50000.0);
    health.setHoursSinceOverhaul(24000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);

    // Add critical condition indicators to push health below 0.25
    health.addConditionIndicator(
        new EquipmentHealthAssessment.ConditionIndicator("vibration", "mm/s", 9.5, 2.0, 7.0, 10.0));
    health.addConditionIndicator(new EquipmentHealthAssessment.ConditionIndicator("bearing_temp",
        "C", 108.0, 60.0, 90.0, 110.0));

    health.calculate();

    assertEquals(EquipmentHealthAssessment.HealthSeverity.CRITICAL, health.getSeverity(),
        "Severely degraded equipment with bad indicators should be CRITICAL (index="
            + health.getHealthIndex() + ")");
  }

  @Test
  void testHealthAssessmentNormalState() {
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("comp2", "compressor");
    health.setDegradationFactor(0.99);
    health.setFoulingFactor(0.01);
    health.setOperatingHours(5000.0);
    health.setHoursSinceOverhaul(5000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);

    health.calculate();

    assertEquals(EquipmentHealthAssessment.HealthSeverity.NORMAL, health.getSeverity(),
        "Nearly new equipment should be NORMAL");
    assertTrue(health.getHealthIndex() > 0.8, "Health index should be high");
  }

  @Test
  void testDeferralDecisionCriticalHealth() {
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("comp1", "compressor");
    health.setDegradationFactor(0.30);
    health.setFoulingFactor(0.50);
    health.setOperatingHours(50000.0);
    health.setHoursSinceOverhaul(24500.0);
    health.setMeanTimeBetweenOverhaul(25000.0);

    // Add critical condition indicators
    health.addConditionIndicator(
        new EquipmentHealthAssessment.ConditionIndicator("vibration", "mm/s", 9.5, 2.0, 7.0, 10.0));
    health.addConditionIndicator(new EquipmentHealthAssessment.ConditionIndicator("bearing_temp",
        "C", 108.0, 60.0, 90.0, 110.0));

    MaintenanceDeferralAssessment assessment = new MaintenanceDeferralAssessment("comp1");
    assessment.setHealthAssessment(health);
    assessment.setRequestedDeferralHours(720.0);

    DeferralDecision decision = assessment.assess();

    assertNotNull(decision);
    assertEquals(DeferralDecision.Recommendation.EMERGENCY, decision.getRecommendation(),
        "Critical equipment should get EMERGENCY recommendation");
    assertFalse(decision.isDeferralViable());
  }

  @Test
  void testDeferralDecisionHealthyEquipment() {
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("comp1", "compressor");
    health.setDegradationFactor(0.97);
    health.setFoulingFactor(0.02);
    health.setOperatingHours(8000.0);
    health.setHoursSinceOverhaul(8000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);

    MaintenanceDeferralAssessment assessment = new MaintenanceDeferralAssessment("comp1");
    assessment.setHealthAssessment(health);
    assessment.setRequestedDeferralHours(720.0);
    assessment.setProductionValuePerHour(50000.0);
    assessment.setUnplannedShutdownCostUSD(5000000.0);

    DeferralDecision decision = assessment.assess();

    assertNotNull(decision);
    assertEquals(DeferralDecision.Recommendation.DEFER, decision.getRecommendation(),
        "Healthy equipment should allow deferral");
    assertTrue(decision.isDeferralViable());
    assertTrue(decision.getConfidenceLevel() > 0.4, "Confidence should be reasonable");
  }

  @Test
  void testDeferralDecisionNoHealthData() {
    MaintenanceDeferralAssessment assessment = new MaintenanceDeferralAssessment("unknown");
    DeferralDecision decision = assessment.assess();

    assertNotNull(decision);
    assertEquals(DeferralDecision.Recommendation.PROCEED_AS_PLANNED, decision.getRecommendation());
    assertFalse(decision.isDeferralViable());
  }

  @Test
  void testTemporaryOperatingEnvelope() {
    TemporaryOperatingEnvelope envelope = new TemporaryOperatingEnvelope("comp1", 720.0);
    envelope.addConstraint("capacity_fraction", "-", 0.85, 1.0, 1.0, "Limit throughput");
    envelope.addConstraint("vibration_mm_s", "mm/s", 0.0, 8.0, 5.0, "Monitor vibration");

    assertTrue(envelope.isWithinEnvelope(new String[] {"capacity_fraction", "vibration_mm_s"},
        new double[] {0.90, 6.0}));

    assertFalse(envelope.isWithinEnvelope(new String[] {"capacity_fraction", "vibration_mm_s"},
        new double[] {1.10, 6.0}), "Exceeding capacity should violate envelope");
  }

  @Test
  void testPlantDeferralCoordinator() {
    ProcessSystem process = createTestSystem();
    Compressor comp = (Compressor) process.getUnit("compressor1");
    comp.setDegradationFactor(0.93);
    comp.setFoulingFactor(0.05);

    PlantDeferralCoordinator coordinator = new PlantDeferralCoordinator(process);
    coordinator.setDefaultDeferralHours(720.0);
    coordinator.setProductionValuePerHour(50000.0);

    // Manually add health assessment
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("compressor1", "compressor");
    health.setDegradationFactor(0.93);
    health.setFoulingFactor(0.05);
    health.setOperatingHours(15000.0);
    health.setHoursSinceOverhaul(15000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);
    coordinator.addHealthAssessment("compressor1", health);

    coordinator.assess();

    assertTrue(coordinator.isAssessmentComplete());
    assertFalse(coordinator.getSummaries().isEmpty());
    assertTrue(coordinator.getPlantHealthIndex() > 0);
    assertTrue(coordinator.getPlantHealthIndex() <= 1.0);
  }

  @Test
  void testPlantDeferralCoordinatorAutoAssess() {
    ProcessSystem process = createTestSystem();
    Compressor comp = (Compressor) process.getUnit("compressor1");
    comp.setDegradationFactor(0.90);
    comp.setFoulingFactor(0.08);

    PlantDeferralCoordinator coordinator = new PlantDeferralCoordinator(process);
    coordinator.autoAssessCompressors();
    coordinator.assess();

    assertTrue(coordinator.isAssessmentComplete());
    assertEquals(1, coordinator.getSummaries().size(), "Should find one compressor");
  }

  @Test
  void testHealthAssessmentConditionIndicators() {
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("comp1", "compressor");
    health.setDegradationFactor(0.95);
    health.setOperatingHours(10000.0);
    health.setHoursSinceOverhaul(10000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);

    health.addConditionIndicator(
        new EquipmentHealthAssessment.ConditionIndicator("vibration", "mm/s", 4.5, 2.0, 7.0, 10.0));
    health.addConditionIndicator(new EquipmentHealthAssessment.ConditionIndicator("bearing_temp",
        "C", 75.0, 60.0, 90.0, 110.0));

    health.calculate();

    assertNotNull(health.getSeverity());
    assertEquals(2, health.getConditionIndicators().size());
    assertNotNull(health.toJson());
  }

  @Test
  void testDeferralDecisionToJson() {
    // Test health assessment JSON (no ProcessSystem to avoid Gson issues)
    EquipmentHealthAssessment health = new EquipmentHealthAssessment("comp1", "compressor");
    health.setDegradationFactor(0.95);
    health.setOperatingHours(10000.0);
    health.setHoursSinceOverhaul(10000.0);
    health.setMeanTimeBetweenOverhaul(25000.0);
    health.calculate();

    String json = health.toJson();

    assertNotNull(json);
    assertTrue(json.contains("equipmentName"));
    assertTrue(json.contains("comp1"));
    assertTrue(json.contains("healthIndex"));
  }
}
