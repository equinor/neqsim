package neqsim.process.safety.risk.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Condition-Based Reliability package.
 */
class ConditionBasedReliabilityTest {

  private ConditionBasedReliability pump;

  @BeforeEach
  void setUp() {
    // Create CBR model with base OREDA failure rate
    pump = new ConditionBasedReliability("P-101", "Main Export Pump", 5e-5); // per hour

    // Add condition indicators
    pump.addVibrationIndicator("V1", "Drive End Bearing", 2.0, 4.0, 7.0); // mm/s RMS
    pump.addTemperatureIndicator("T1", "Bearing Temperature", 45.0, 65.0, 80.0); // Celsius
  }

  @Test
  void testBasicCreation() {
    assertEquals("P-101", pump.getEquipmentId());
    assertEquals("Main Export Pump", pump.getEquipmentName());
    assertEquals(5e-5, pump.getBaseFailureRate(), 1e-10);
  }

  @Test
  void testIndicatorCreation() {
    List<ConditionBasedReliability.ConditionIndicator> indicators = pump.getIndicators();
    assertEquals(2, indicators.size());

    ConditionBasedReliability.ConditionIndicator vibration = indicators.get(0);
    assertEquals("V1", vibration.getIndicatorId());
    assertEquals("Drive End Bearing", vibration.getName());
    assertEquals(ConditionBasedReliability.ConditionIndicator.IndicatorType.VIBRATION,
        vibration.getType());
  }

  @Test
  void testNormalConditions() {
    // Update with normal values
    pump.updateIndicator("V1", 2.0); // Normal vibration
    pump.updateIndicator("T1", 45.0); // Normal temperature

    assertEquals(1.0, pump.getHealthIndex(), 0.01);
    assertEquals(pump.getBaseFailureRate(), pump.getAdjustedFailureRate(), 1e-8);
  }

  @Test
  void testElevatedConditions() {
    // Update with elevated values
    pump.updateIndicator("V1", 5.5); // Between warning and critical
    pump.updateIndicator("T1", 60.0); // Elevated but below warning

    assertTrue(pump.getHealthIndex() < 1.0, "Health should decrease with elevated conditions");
    assertTrue(pump.getAdjustedFailureRate() > pump.getBaseFailureRate(),
        "Failure rate should increase");
  }

  @Test
  void testCriticalConditions() {
    // Update with critical values
    pump.updateIndicator("V1", 8.0); // Above critical
    pump.updateIndicator("T1", 85.0); // Above critical

    assertTrue(pump.getHealthIndex() < 0.3, "Health should be very low at critical conditions");

    List<ConditionBasedReliability.ConditionIndicator> critical = pump.getCriticalIndicators();
    assertEquals(2, critical.size(), "Both indicators should be critical");
  }

  @Test
  void testAlarmingIndicators() {
    pump.updateIndicator("V1", 5.0); // Above warning (4.0) but below critical (7.0)
    pump.updateIndicator("T1", 45.0); // Normal

    List<ConditionBasedReliability.ConditionIndicator> alarming = pump.getAlarmingIndicators();
    assertEquals(1, alarming.size());
    assertEquals("V1", alarming.get(0).getIndicatorId());
  }

  @Test
  void testBatchUpdate() {
    Map<String, Double> values = new HashMap<>();
    values.put("V1", 3.5);
    values.put("T1", 55.0);

    pump.updateIndicators(values);

    assertTrue(pump.getHealthIndex() > 0.5 && pump.getHealthIndex() < 1.0);
  }

  @Test
  void testIndicatorHealthContribution() {
    pump.updateIndicator("V1", 2.0); // Normal - should be 1.0
    pump.updateIndicator("T1", 65.0); // At warning threshold

    List<ConditionBasedReliability.ConditionIndicator> indicators = pump.getIndicators();

    ConditionBasedReliability.ConditionIndicator vibration = indicators.get(0);
    assertEquals(1.0, vibration.getHealthContribution(), 0.01);

    ConditionBasedReliability.ConditionIndicator temp = indicators.get(1);
    assertTrue(temp.getHealthContribution() < 1.0);
  }

  @Test
  void testMTTF() {
    pump.updateIndicator("V1", 2.0);
    pump.updateIndicator("T1", 45.0);

    double mttf = pump.getMTTF();
    assertEquals(1.0 / 5e-5, mttf, 1); // MTTF = 1/lambda = 20000 hours
  }

  @Test
  void testProbabilityOfFailure() {
    pump.updateIndicator("V1", 2.0);
    pump.updateIndicator("T1", 45.0);

    double pof24h = pump.getProbabilityOfFailure(24);
    double pof720h = pump.getProbabilityOfFailure(720); // 30 days

    assertTrue(pof24h > 0 && pof24h < 1);
    assertTrue(pof720h > pof24h, "Longer time should have higher failure probability");
  }

  @Test
  void testFailureRateMultiplier() {
    pump.updateIndicator("V1", 2.0);
    pump.updateIndicator("T1", 45.0);
    assertEquals(1.0, pump.getFailureRateMultiplier(), 0.01);

    pump.updateIndicator("V1", 8.0); // Critical
    assertTrue(pump.getFailureRateMultiplier() > 1.0);
  }

  @Test
  void testDegradationModels() {
    pump.updateIndicator("V1", 5.0);
    pump.updateIndicator("T1", 60.0);

    // Test linear model
    pump.setDegradationModel(ConditionBasedReliability.DegradationModel.LINEAR);
    double linearRate = pump.getAdjustedFailureRate();

    // Test exponential model
    pump.setDegradationModel(ConditionBasedReliability.DegradationModel.EXPONENTIAL);
    double expRate = pump.getAdjustedFailureRate();

    // Both should increase failure rate
    assertTrue(linearRate > pump.getBaseFailureRate());
    assertTrue(expRate > pump.getBaseFailureRate());
  }

  @Test
  void testIndicatorWeights() {
    // Get indicators and set different weights
    List<ConditionBasedReliability.ConditionIndicator> indicators = pump.getIndicators();
    indicators.get(0).setWeight(2.0); // Vibration weighted higher
    indicators.get(1).setWeight(1.0); // Temperature normal weight

    pump.updateIndicator("V1", 6.0); // Bad vibration
    pump.updateIndicator("T1", 45.0); // Good temperature

    // Health should be weighted toward poor vibration
    double health = pump.getHealthIndex();
    assertTrue(health < 0.7); // More weighted toward bad indicator
  }

  @Test
  void testJsonSerialization() {
    pump.updateIndicator("V1", 4.5);
    pump.updateIndicator("T1", 55.0);

    String json = pump.toJson();

    assertNotNull(json);
    assertTrue(json.contains("equipmentId"));
    assertTrue(json.contains("healthIndex"));
    assertTrue(json.contains("reliability"));
    assertTrue(json.contains("indicators"));
  }

  @Test
  void testReport() {
    pump.updateIndicator("V1", 4.5);
    pump.updateIndicator("T1", 55.0);

    String report = pump.toReport();

    assertNotNull(report);
    assertTrue(report.contains("CONDITION-BASED RELIABILITY REPORT"));
    assertTrue(report.contains("P-101"));
    assertTrue(report.contains("Health Index"));
    assertTrue(report.contains("CONDITION INDICATORS"));
  }

  @Test
  void testHealthStatus() {
    // Good health
    pump.updateIndicator("V1", 2.0);
    pump.updateIndicator("T1", 45.0);
    Map<String, Object> map = pump.toMap();
    Map<String, Object> health = (Map<String, Object>) map.get("health");
    assertEquals("GOOD", health.get("status"));

    // Fair health
    pump.updateIndicator("V1", 4.5);
    pump.updateIndicator("T1", 60.0);
    map = pump.toMap();
    health = (Map<String, Object>) map.get("health");
    assertTrue(health.get("status").toString().matches("GOOD|FAIR"));
  }

  @Test
  void testAddCustomIndicator() {
    ConditionBasedReliability.ConditionIndicator efficiency =
        new ConditionBasedReliability.ConditionIndicator("E1", "Pump Efficiency",
            ConditionBasedReliability.ConditionIndicator.IndicatorType.EFFICIENCY);
    efficiency.setThresholds(85.0, 75.0, 65.0); // Lower is worse for efficiency

    pump.addIndicator(efficiency);
    pump.updateIndicator("E1", 80.0); // Between normal and warning

    assertEquals(3, pump.getIndicators().size());
    assertTrue(pump.getHealthIndex() < 1.0);
  }

  @Test
  void testToString() {
    pump.updateIndicator("V1", 3.0);
    pump.updateIndicator("T1", 50.0);

    String str = pump.toString();
    assertTrue(str.contains("ConditionBasedReliability"));
    assertTrue(str.contains("Main Export Pump"));
  }
}
