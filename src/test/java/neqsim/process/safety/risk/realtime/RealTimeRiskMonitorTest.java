package neqsim.process.safety.risk.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for Real-time Risk Monitoring package.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class RealTimeRiskMonitorTest {

  private RealTimeRiskMonitor monitor;
  private ProcessSystem processSystem;

  @BeforeEach
  void setUp() {
    // Create a simple process system
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    Separator separator = new Separator("HP Separator", feed);

    processSystem = new ProcessSystem();
    processSystem.add(feed);
    processSystem.add(separator);
    processSystem.run();

    monitor = new RealTimeRiskMonitor("Platform Alpha");
    monitor.setProcessSystem(processSystem);
  }

  @Test
  void testMonitorCreation() {
    assertNotNull(monitor, "Monitor should not be null");
    assertEquals("Platform Alpha", monitor.getName());
  }

  @Test
  void testMonitorCreationWithProcessSystem() {
    RealTimeRiskMonitor newMonitor = new RealTimeRiskMonitor("Test Monitor", processSystem);
    assertNotNull(newMonitor);
    assertEquals("Test Monitor", newMonitor.getName());
  }

  @Test
  void testAlertThresholdsCreation() {
    RealTimeRiskMonitor.AlertThresholds thresholds = new RealTimeRiskMonitor.AlertThresholds();
    assertNotNull(thresholds);

    // Test default values
    assertTrue(thresholds.getWarningRiskLevel() > 0);
    assertTrue(thresholds.getHighRiskLevel() > 0);
    assertTrue(thresholds.getCriticalRiskLevel() > 0);
  }

  @Test
  void testAlertThresholdsSetters() {
    RealTimeRiskMonitor.AlertThresholds thresholds = monitor.getAlertThresholds();

    thresholds.setWarningRiskLevel(5.0);
    thresholds.setHighRiskLevel(8.0);
    thresholds.setCriticalRiskLevel(12.0);
    thresholds.setTrendChangePercent(25.0);
    thresholds.setAnomalyStdDevs(2.5);

    assertEquals(5.0, thresholds.getWarningRiskLevel(), 0.01);
    assertEquals(8.0, thresholds.getHighRiskLevel(), 0.01);
    assertEquals(12.0, thresholds.getCriticalRiskLevel(), 0.01);
    assertEquals(25.0, thresholds.getTrendChangePercent(), 0.01);
    assertEquals(2.5, thresholds.getAnomalyStdDevs(), 0.01);
  }

  @Test
  void testEquipmentRiskStatusCreation() {
    RealTimeRiskMonitor.EquipmentRiskStatus status =
        new RealTimeRiskMonitor.EquipmentRiskStatus("EQ-001", "Test Equipment");

    assertNotNull(status);
    assertEquals("EQ-001", status.getEquipmentId());
    assertEquals("Test Equipment", status.getEquipmentName());
  }

  @Test
  void testEquipmentRiskStatusSetters() {
    RealTimeRiskMonitor.EquipmentRiskStatus status =
        new RealTimeRiskMonitor.EquipmentRiskStatus("EQ-001", "Pump");

    status.setFailureProbability(0.05);
    status.setRiskScore(3.5);
    status.setHealthStatus("DEGRADED");

    assertEquals(0.05, status.getFailureProbability(), 0.001);
    assertEquals(3.5, status.getRiskScore(), 0.01);
    assertEquals("DEGRADED", status.getHealthStatus());
  }

  @Test
  void testEquipmentRiskStatusToMap() {
    RealTimeRiskMonitor.EquipmentRiskStatus status =
        new RealTimeRiskMonitor.EquipmentRiskStatus("EQ-001", "Pump");
    status.setFailureProbability(0.05);

    java.util.Map<String, Object> map = status.toMap();

    assertNotNull(map);
    assertEquals("EQ-001", map.get("equipmentId"));
    assertEquals("Pump", map.get("equipmentName"));
    assertEquals(0.05, map.get("failureProbability"));
  }

  @Test
  void testRiskAlertCreation() {
    RealTimeRiskMonitor.RiskAlert alert =
        new RealTimeRiskMonitor.RiskAlert(RealTimeRiskMonitor.RiskAlert.AlertSeverity.WARNING,
            RealTimeRiskMonitor.RiskAlert.AlertType.RISK_THRESHOLD_EXCEEDED, "Pump-001",
            "Risk threshold exceeded");

    assertNotNull(alert);
    assertNotNull(alert.getAlertId());
    assertNotNull(alert.getTimestamp());
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertSeverity.WARNING, alert.getSeverity());
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertType.RISK_THRESHOLD_EXCEEDED, alert.getType());
    assertEquals("Pump-001", alert.getSource());
    assertEquals("Risk threshold exceeded", alert.getMessage());
  }

  @Test
  void testRiskAlertAcknowledge() {
    RealTimeRiskMonitor.RiskAlert alert =
        new RealTimeRiskMonitor.RiskAlert(RealTimeRiskMonitor.RiskAlert.AlertSeverity.HIGH,
            RealTimeRiskMonitor.RiskAlert.AlertType.EQUIPMENT_DEGRADATION, "Separator-001",
            "Equipment degradation detected");

    assertFalse(alert.isAcknowledged());
    alert.acknowledge();
    assertTrue(alert.isAcknowledged());
  }

  @Test
  void testRiskAlertToMap() {
    RealTimeRiskMonitor.RiskAlert alert =
        new RealTimeRiskMonitor.RiskAlert(RealTimeRiskMonitor.RiskAlert.AlertSeverity.CRITICAL,
            RealTimeRiskMonitor.RiskAlert.AlertType.SIF_DEMAND, "SIF-001", "SIF demand detected");
    alert.setCurrentValue(95.0);
    alert.setThresholdValue(90.0);

    java.util.Map<String, Object> map = alert.toMap();

    assertNotNull(map);
    assertEquals("SIF-001", map.get("source"));
    assertEquals("CRITICAL", map.get("severity"));
    assertEquals("SIF_DEMAND", map.get("type"));
    assertEquals(95.0, map.get("currentValue"));
    assertEquals(90.0, map.get("thresholdValue"));
  }

  @Test
  void testAlertSeverityEnumValues() {
    RealTimeRiskMonitor.RiskAlert.AlertSeverity[] severities =
        RealTimeRiskMonitor.RiskAlert.AlertSeverity.values();
    assertEquals(4, severities.length);
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertSeverity.INFO,
        RealTimeRiskMonitor.RiskAlert.AlertSeverity.valueOf("INFO"));
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertSeverity.WARNING,
        RealTimeRiskMonitor.RiskAlert.AlertSeverity.valueOf("WARNING"));
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertSeverity.HIGH,
        RealTimeRiskMonitor.RiskAlert.AlertSeverity.valueOf("HIGH"));
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertSeverity.CRITICAL,
        RealTimeRiskMonitor.RiskAlert.AlertSeverity.valueOf("CRITICAL"));
  }

  @Test
  void testAlertTypeEnumValues() {
    RealTimeRiskMonitor.RiskAlert.AlertType[] types =
        RealTimeRiskMonitor.RiskAlert.AlertType.values();
    assertEquals(6, types.length);
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertType.RISK_THRESHOLD_EXCEEDED,
        RealTimeRiskMonitor.RiskAlert.AlertType.valueOf("RISK_THRESHOLD_EXCEEDED"));
    assertEquals(RealTimeRiskMonitor.RiskAlert.AlertType.ANOMALY_DETECTED,
        RealTimeRiskMonitor.RiskAlert.AlertType.valueOf("ANOMALY_DETECTED"));
  }

  @Test
  void testAddAlertListener() {
    List<RealTimeRiskMonitor.RiskAlert> receivedAlerts = new ArrayList<>();
    monitor.addAlertListener(receivedAlerts::add);

    // Verify listener was added (no exception)
    assertNotNull(receivedAlerts);
  }

  @Test
  void testSetUpdateIntervalSeconds() {
    monitor.setUpdateIntervalSeconds(30);
    // No getter exists, but should not throw exception
    assertNotNull(monitor);
  }

  @Test
  void testRealTimeRiskAssessmentCreation() {
    RealTimeRiskAssessment assessment = new RealTimeRiskAssessment();
    assertNotNull(assessment);
  }

  @Test
  void testRealTimeRiskAssessmentSetters() {
    RealTimeRiskAssessment assessment = new RealTimeRiskAssessment();
    assessment.setOverallRiskScore(5.5);
    assessment.setExpectedProductionLoss(2.5);
    assessment.setAvailability(0.95);
    assessment.setRiskTrend("INCREASING");
    assessment.setTrendSlope(0.02);

    assertEquals(5.5, assessment.getOverallRiskScore(), 0.01);
    assertEquals(2.5, assessment.getExpectedProductionLoss(), 0.01);
    assertEquals(0.95, assessment.getAvailability(), 0.001);
    assertEquals("INCREASING", assessment.getRiskTrend());
    assertEquals(0.02, assessment.getTrendSlope(), 0.001);
  }

  @Test
  void testMonitoringActiveFlag() {
    assertFalse(monitor.isMonitoringActive());
  }

  @Test
  void testGetActiveAlerts() {
    List<RealTimeRiskMonitor.RiskAlert> alerts = monitor.getActiveAlerts();
    assertNotNull(alerts);
    assertTrue(alerts.isEmpty());
  }

  @Test
  void testGetUnacknowledgedAlerts() {
    List<RealTimeRiskMonitor.RiskAlert> alerts = monitor.getUnacknowledgedAlerts();
    assertNotNull(alerts);
    assertTrue(alerts.isEmpty());
  }

  @Test
  void testGetEquipmentStatus() {
    java.util.Map<String, RealTimeRiskMonitor.EquipmentRiskStatus> status =
        monitor.getEquipmentStatus();
    assertNotNull(status);
  }

  @Test
  void testGetAssessmentHistory() {
    List<RealTimeRiskAssessment> history = monitor.getAssessmentHistory();
    assertNotNull(history);
    assertTrue(history.isEmpty());
  }

  @Test
  void testSetBaseline() {
    monitor.setBaseline(5.0, 1.0);
    // Should not throw exception
    assertNotNull(monitor);
  }

  @Test
  void testCalculateBaseline() {
    monitor.calculateBaseline();
    // Should not throw exception even with empty history
    assertNotNull(monitor);
  }

  @Test
  void testMonitorToJson() {
    String json = monitor.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Platform Alpha"));
    assertTrue(json.contains("monitoringActive"));
  }

  @Test
  void testMonitorToString() {
    String str = monitor.toString();
    assertNotNull(str);
    assertTrue(str.contains("Platform Alpha"));
  }

  @Test
  void testProcessVariableStatus() {
    RealTimeRiskAssessment.ProcessVariableStatus pvStatus =
        new RealTimeRiskAssessment.ProcessVariableStatus("Pressure", 55.0, 50.0, "bara");

    assertEquals("Pressure", pvStatus.getVariableName());
    assertEquals(55.0, pvStatus.getCurrentValue(), 0.01);
    assertEquals(50.0, pvStatus.getNormalValue(), 0.01);
    assertEquals(5.0, pvStatus.getDeviation(), 0.01);
    assertEquals(10.0, pvStatus.getDeviationPercent(), 0.01);
    assertEquals("bara", pvStatus.getUnit());
    assertFalse(pvStatus.isAlarming()); // Exactly 10%, not > 10%
  }

  @Test
  void testProcessVariableStatusAlarming() {
    RealTimeRiskAssessment.ProcessVariableStatus pvStatus =
        new RealTimeRiskAssessment.ProcessVariableStatus("Pressure", 60.0, 50.0, "bara");

    assertTrue(pvStatus.isAlarming()); // 20% deviation > 10%
  }

  @Test
  void testProcessVariableStatusToMap() {
    RealTimeRiskAssessment.ProcessVariableStatus pvStatus =
        new RealTimeRiskAssessment.ProcessVariableStatus("Temperature", 80.0, 75.0, "C");

    java.util.Map<String, Object> map = pvStatus.toMap();

    assertNotNull(map);
    assertEquals("Temperature", map.get("variableName"));
    assertEquals(80.0, map.get("currentValue"));
    assertEquals("C", map.get("unit"));
  }
}

