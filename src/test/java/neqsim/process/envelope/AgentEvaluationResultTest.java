package neqsim.process.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentEvaluationResult} and {@link EnvelopeDashboardData}.
 */
class AgentEvaluationResultTest {

  @Test
  void testBuilderMinimal() {
    AgentEvaluationResult result =
        new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.NORMAL, 1)
            .summary("All OK").build();

    assertNotNull(result);
    assertEquals(ProcessOperatingEnvelope.EnvelopeStatus.NORMAL, result.getOverallStatus());
    assertEquals(1, result.getEvaluationCycleNumber());
    assertEquals("All OK", result.getSummaryMessage());
    assertEquals(0, result.getCriticalMarginCount());
    assertFalse(result.hasImminentTrip());
    assertFalse(result.hasImmediateActions());
  }

  @Test
  void testBuilderFull() {
    OperatingMargin critMargin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 97.0, 100.0, "bara");

    TripPrediction trip = new TripPrediction("Sep-1", OperatingMargin.MarginType.PRESSURE, 3.0,
        60.0, 0.9, "Rapidly degrading");

    MitigationAction action = new MitigationAction("Reduce pressure", "Sep-1", "setpoint", 80.0,
        "bara", MitigationAction.Priority.IMMEDIATE, "Reduces overpressure risk",
        MitigationAction.Category.SETPOINT_CHANGE);

    AgentEvaluationResult result =
        new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.CRITICAL, 5)
            .timestamp(System.currentTimeMillis()).evaluationTime(0.5)
            .margins(Arrays.asList(critMargin)).tripPredictions(Arrays.asList(trip))
            .mitigationActions(Arrays.asList(action)).summary("Critical: 1 margin exceeded")
            .build();

    assertEquals(1, result.getCriticalMarginCount());
    assertTrue(result.hasImminentTrip());
    assertTrue(result.hasImmediateActions());
    assertEquals(1, result.getRankedMargins().size());
    assertEquals(1, result.getTripPredictions().size());
    assertEquals(1, result.getMitigationActions().size());
  }

  @Test
  void testImmutableCollections() {
    OperatingMargin margin = new OperatingMargin("A", "x", OperatingMargin.MarginType.PRESSURE,
        OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    AgentEvaluationResult result =
        new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.NORMAL, 1)
            .margins(Arrays.asList(margin)).build();

    boolean threw = false;
    try {
      result.getRankedMargins().clear();
    } catch (UnsupportedOperationException e) {
      threw = true;
    }
    assertTrue(threw, "Margins list should be unmodifiable");
  }

  @Test
  void testToJson() {
    AgentEvaluationResult result =
        new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.WARNING, 3)
            .summary("Warning state").build();

    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"overallStatus\""));
    assertTrue(json.contains("WARNING"));
    assertTrue(json.contains("\"cycleNumber\": 3"));
  }

  @Test
  void testDashboardFromResult() {
    OperatingMargin margin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    AgentEvaluationResult result =
        new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.NORMAL, 1)
            .timestamp(System.currentTimeMillis()).margins(Arrays.asList(margin))
            .tripPredictions(Collections.<TripPrediction>emptyList())
            .mitigationActions(Collections.<MitigationAction>emptyList()).summary("OK").build();

    EnvelopeDashboardData dashboard = EnvelopeDashboardData.fromResult(result);
    assertNotNull(dashboard);
    assertEquals("NORMAL", dashboard.getOverallStatus());
    assertEquals(1, dashboard.getEquipmentCount());
    assertEquals(0, dashboard.getTripAlertCount());
    assertEquals(0, dashboard.getAdvisoryCount());
  }

  @Test
  void testDashboardToJson() {
    OperatingMargin margin = new OperatingMargin("Sep-1", "pressure",
        OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 50.0, 100.0, "bara");

    TripPrediction trip = new TripPrediction("Sep-1", OperatingMargin.MarginType.PRESSURE, 3.0,
        120.0, 0.8, "Degrading");

    AgentEvaluationResult result =
        new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.CRITICAL, 2)
            .timestamp(1000L).margins(Arrays.asList(margin)).tripPredictions(Arrays.asList(trip))
            .mitigationActions(Collections.<MitigationAction>emptyList()).summary("Critical")
            .build();

    EnvelopeDashboardData dashboard = EnvelopeDashboardData.fromResult(result);
    String json = dashboard.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"equipmentCards\""));
    assertTrue(json.contains("\"marginGauges\""));
    assertTrue(json.contains("\"tripAlerts\""));
    assertTrue(json.contains("Sep-1"));
    assertEquals(1, dashboard.getTripAlertCount());
  }
}
