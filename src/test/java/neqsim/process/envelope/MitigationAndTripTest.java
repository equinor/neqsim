package neqsim.process.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MitigationAction}, {@link TripPrediction}, {@link MitigationStrategy}.
 */
class MitigationAndTripTest {

  @Test
  void testMitigationActionCreation() {
    MitigationAction action = new MitigationAction("Reduce inlet flow rate", "Feed Valve",
        "valveOpening", 70.0, "%", MitigationAction.Priority.SOON, "Reduces pressure buildup",
        MitigationAction.Category.VALVE_ADJUSTMENT);

    assertEquals("Reduce inlet flow rate", action.getDescription());
    assertEquals("Feed Valve", action.getTargetEquipment());
    assertEquals(70.0, action.getSuggestedValue(), 1e-6);
    assertEquals(MitigationAction.Priority.SOON, action.getPriority());
  }

  @Test
  void testMitigationActionOptionalFields() {
    MitigationAction action = new MitigationAction("Increase MEG injection", "MEG Pump", "flowRate",
        500.0, "kg/hr", MitigationAction.Priority.IMMEDIATE, "Increase hydrate margin by 5C",
        MitigationAction.Category.CHEMICAL_INJECTION);

    action.setSideEffects("Increased MEG consumption and operating cost");
    action.setConfidenceLevel(0.85);
    action.setTriggeringMarginKey("Pipeline.hydrateSubcooling.LOW");

    assertEquals("Increase hydrate margin by 5C", action.getExpectedImprovement());
    assertEquals(0.85, action.getConfidenceLevel(), 1e-6);
    assertEquals("Pipeline.hydrateSubcooling.LOW", action.getTriggeringMarginKey());
  }

  @Test
  void testMitigationActionComparable() {
    MitigationAction immediate = new MitigationAction("A", "X", "v", 1.0, "u",
        MitigationAction.Priority.IMMEDIATE, "Improve");
    MitigationAction monitor =
        new MitigationAction("B", "Y", "v", 2.0, "u", MitigationAction.Priority.MONITOR, "Improve");

    assertTrue(immediate.compareTo(monitor) < 0, "IMMEDIATE should sort before MONITOR");
  }

  @Test
  void testTripPredictionAutoSeverity() {
    // ≤120s → IMMINENT
    TripPrediction imminent = new TripPrediction("Comp-1", OperatingMargin.MarginType.SURGE, 3.0,
        60.0, 0.9, "Rapidly degrading");
    assertEquals(TripPrediction.Severity.IMMINENT, imminent.getSeverity());

    // ≤600s → HIGH
    TripPrediction high = new TripPrediction("Comp-1", OperatingMargin.MarginType.SURGE, 8.0, 400.0,
        0.8, "Degrading");
    assertEquals(TripPrediction.Severity.HIGH, high.getSeverity());

    // ≤1800s → MEDIUM
    TripPrediction medium = new TripPrediction("Sep-1", OperatingMargin.MarginType.PRESSURE, 15.0,
        1200.0, 0.6, "Degrading");
    assertEquals(TripPrediction.Severity.MEDIUM, medium.getSeverity());

    // >1800s → LOW
    TripPrediction low = new TripPrediction("Sep-1", OperatingMargin.MarginType.PRESSURE, 18.0,
        3600.0, 0.5, "Slowly degrading");
    assertEquals(TripPrediction.Severity.LOW, low.getSeverity());
  }

  @Test
  void testTripPredictionExplicitSeverity() {
    TripPrediction pred = new TripPrediction("Comp-1", OperatingMargin.MarginType.SURGE, 4.0, 300.0,
        0.7, TripPrediction.Severity.HIGH, "Manual override");
    assertEquals(TripPrediction.Severity.HIGH, pred.getSeverity());
  }

  @Test
  void testTripPredictionMinutes() {
    TripPrediction pred = new TripPrediction("Sep-1", OperatingMargin.MarginType.LEVEL, 10.0, 600.0,
        0.8, "Degrading");
    assertEquals(10.0, pred.getEstimatedTimeToTripMinutes(), 1e-6);
  }

  @Test
  void testTripPredictionComparable() {
    TripPrediction imminent =
        new TripPrediction("A", OperatingMargin.MarginType.PRESSURE, 2.0, 60.0, 0.9, "Fast");
    TripPrediction low =
        new TripPrediction("B", OperatingMargin.MarginType.PRESSURE, 18.0, 3600.0, 0.5, "Slow");

    assertTrue(imminent.compareTo(low) < 0, "IMMINENT should sort before LOW");
  }

  @Test
  void testMitigationStrategyBuiltInPlaybooks() {
    MitigationStrategy strategy = new MitigationStrategy();

    List<MitigationAction> hydrate = strategy.getPlaybook(MitigationStrategy.HYDRATE_RISK);
    assertNotNull(hydrate);
    assertFalse(hydrate.isEmpty(), "Hydrate playbook should have actions");

    List<MitigationAction> surge = strategy.getPlaybook(MitigationStrategy.COMPRESSOR_SURGE);
    assertNotNull(surge);
    assertFalse(surge.isEmpty());

    List<MitigationAction> highLevel =
        strategy.getPlaybook(MitigationStrategy.SEPARATOR_HIGH_LEVEL);
    assertNotNull(highLevel);
    assertFalse(highLevel.isEmpty());
  }

  @Test
  void testMitigationStrategyCustomPlaybook() {
    MitigationStrategy strategy = new MitigationStrategy();

    MitigationAction custom = new MitigationAction("Custom action", "Custom-Eq", "var", 1.0, "unit",
        MitigationAction.Priority.MONITOR, "Improve", MitigationAction.Category.MONITORING);
    strategy.registerStrategy("CUSTOM_THREAT", Arrays.asList(custom));

    List<MitigationAction> playbook = strategy.getPlaybook("CUSTOM_THREAT");
    assertNotNull(playbook);
    assertEquals(1, playbook.size());
    assertEquals("Custom action", playbook.get(0).getDescription());
  }

  @Test
  void testMitigationStrategyMapToThreatType() {
    MitigationStrategy strategy = new MitigationStrategy();

    String threat =
        strategy.mapToThreatType(OperatingMargin.MarginType.HYDRATE, OperatingMargin.Direction.LOW);
    assertEquals(MitigationStrategy.HYDRATE_RISK, threat);

    String surgeThreat =
        strategy.mapToThreatType(OperatingMargin.MarginType.SURGE, OperatingMargin.Direction.LOW);
    assertEquals(MitigationStrategy.COMPRESSOR_SURGE, surgeThreat);
  }

  @Test
  void testMitigationStrategyGetActionsForMargin() {
    MitigationStrategy strategy = new MitigationStrategy();
    OperatingMargin margin = new OperatingMargin("Pipeline", "hydrateSubcooling",
        OperatingMargin.MarginType.HYDRATE, OperatingMargin.Direction.LOW, 3.0, 0.0, "C");

    List<MitigationAction> actions = strategy.getActionsForMargin(margin);
    assertNotNull(actions);
    assertFalse(actions.isEmpty(), "Should return hydrate risk actions");
  }

  @Test
  void testMitigationStrategyUnknownPlaybook() {
    MitigationStrategy strategy = new MitigationStrategy();
    List<MitigationAction> actions = strategy.getPlaybook("NONEXISTENT");
    assertNotNull(actions);
    assertTrue(actions.isEmpty());
  }
}
