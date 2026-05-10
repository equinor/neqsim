package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.diagnostics.restart.RestartSequenceGenerator;
import neqsim.process.diagnostics.restart.RestartStep;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for post-trip analysis classes: TripEvent, TripEventDetector, FailurePropagationTracer, and
 * RestartSequenceGenerator.
 *
 * <p>
 * Uses a simple process: Feed -&gt; Separator -&gt; Compressor -&gt; Cooler
 * </p>
 *
 * @author NeqSim Development Team
 */
class PostTripAnalysisTest {

  private ProcessSystem process;
  private Separator separator;
  private Compressor compressor;
  private Cooler cooler;

  /**
   * Sets up a simple process system for testing.
   */
  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    separator = new Separator("HP Separator", feed);

    Stream gasOut = new Stream("Gas Out", separator.getGasOutStream());

    compressor = new Compressor("Export Compressor", gasOut);
    compressor.setOutletPressure(100.0, "bara");

    Stream compressorOut = new Stream("Compressor Out", compressor.getOutStream());

    cooler = new Cooler("Aftercooler", compressorOut);
    cooler.setOutTemperature(273.15 + 30.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(gasOut);
    process.add(compressor);
    process.add(compressorOut);
    process.add(cooler);
    process.run();
  }

  // ── TripEvent tests ────────────────────────────────────────────────

  @Test
  void testTripEventCreation() {
    TripEvent event =
        new TripEvent("Compressor", "pressure", 120.0, 125.0, true, 10.0, TripEvent.Severity.HIGH);

    assertEquals("Compressor", event.getEquipmentName());
    assertEquals("pressure", event.getParameterName());
    assertEquals(120.0, event.getThreshold(), 0.001);
    assertEquals(125.0, event.getActualValue(), 0.001);
    assertTrue(event.isHighTrip());
    assertTrue(event.getTimestampMillis() > 0);
    assertEquals(10.0, event.getSimulationTimeSeconds(), 0.001);
    assertEquals(TripEvent.Severity.HIGH, event.getSeverity());
  }

  @Test
  void testTripEventDeviation() {
    TripEvent event =
        new TripEvent("Compressor", "pressure", 100.0, 120.0, true, 0.0, TripEvent.Severity.MEDIUM);
    assertEquals(20.0, event.getDeviation(), 0.001);

    TripEvent lowTrip =
        new TripEvent("Compressor", "pressure", 10.0, 5.0, false, 0.0, TripEvent.Severity.LOW);
    assertEquals(5.0, lowTrip.getDeviation(), 0.001);
  }

  @Test
  void testTripEventToJson() {
    TripEvent event =
        new TripEvent("Compressor", "pressure", 120.0, 125.0, true, 0.0, TripEvent.Severity.HIGH);
    String json = event.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"equipmentName\""));
    assertTrue(json.contains("Compressor"));
    assertTrue(json.contains("\"severity\": \"HIGH\""));
  }

  // ── TripEventDetector tests ────────────────────────────────────────

  @Test
  void testTripConditionDetection() {
    TripEventDetector detector = new TripEventDetector(process);

    // Add a trip condition that the compressor discharge pressure exceeds 150 bara
    detector.addTripCondition("Export Compressor", "pressure", 150.0, true,
        TripEvent.Severity.HIGH);

    // Should not trigger — compressor outlet is at 100 bara
    List<TripEvent> trips = detector.check(0.0);
    assertTrue(trips.isEmpty(), "Should not trip at normal conditions");

    // Add a trip condition that the pressure is above 80 bara — should trigger
    detector.addTripCondition("Export Compressor", "pressure", 80.0, true,
        TripEvent.Severity.MEDIUM);

    trips = detector.check(1.0);
    assertFalse(trips.isEmpty(), "Should detect trip when threshold exceeded");

    TripEvent trip = trips.get(0);
    assertEquals("Export Compressor", trip.getEquipmentName());
    assertTrue(trip.isHighTrip());
    assertEquals(TripEvent.Severity.MEDIUM, trip.getSeverity());
  }

  @Test
  void testTripDetectorLowTrip() {
    TripEventDetector detector = new TripEventDetector(process);

    // Low trip: pressure below 200 bara — should trigger since compressor outlet < 200
    detector.addTripCondition("Export Compressor", "pressure", 200.0, false,
        TripEvent.Severity.LOW);

    List<TripEvent> trips = detector.check(0.0);
    assertFalse(trips.isEmpty(), "Should detect low trip");
    assertFalse(trips.get(0).isHighTrip());
  }

  @Test
  void testTripDetectorFirstTripOnly() {
    TripEventDetector detector = new TripEventDetector(process);
    detector.setFirstTripOnly(true);

    // Add condition that will trigger
    detector.addTripCondition("Export Compressor", "pressure", 80.0, true, TripEvent.Severity.HIGH);

    List<TripEvent> first = detector.check(0.0);
    assertFalse(first.isEmpty(), "First check should detect trip");

    // Second check should return empty (first trip only mode)
    List<TripEvent> second = detector.check(1.0);
    assertTrue(second.isEmpty(), "Second check should not re-trigger in firstTripOnly mode");
  }

  @Test
  void testTripDetectorReset() {
    TripEventDetector detector = new TripEventDetector(process);
    detector.setFirstTripOnly(true);

    detector.addTripCondition("Export Compressor", "pressure", 80.0, true, TripEvent.Severity.HIGH);

    detector.check(0.0);
    assertFalse(detector.getDetectedTrips().isEmpty());

    detector.reset();
    assertTrue(detector.getDetectedTrips().isEmpty(), "Detected trips should be cleared on reset");
  }

  @Test
  void testTripDetectorToJson() {
    TripEventDetector detector = new TripEventDetector(process);
    detector.addTripCondition("Export Compressor", "pressure", 80.0, true, TripEvent.Severity.HIGH);
    detector.check(0.0);

    String json = detector.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"trips\""));
  }

  // ── FailurePropagationTracer tests ─────────────────────────────────

  @Test
  void testFailurePropagationByName() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult result = tracer.trace("Export Compressor");

    assertNotNull(result);
    assertEquals("Export Compressor", result.getInitiatingEquipment());
    assertTrue(result.getSteps().size() > 0, "Should have at least one propagation step");
  }

  @Test
  void testFailurePropagationByTripEvent() {
    TripEvent trip = new TripEvent("Export Compressor", "pressure", 120.0, 130.0, true, 0.0,
        TripEvent.Severity.HIGH);

    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult result = tracer.trace(trip);

    assertNotNull(result);
    assertEquals("Export Compressor", result.getInitiatingEquipment());
    assertNotNull(result.getInitiatingTripEvent());
    assertEquals(TripEvent.Severity.HIGH, result.getInitiatingTripEvent().getSeverity());
  }

  @Test
  void testPropagationResultJson() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult result = tracer.trace("HP Separator");

    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"initiatingEquipment\""));
    assertTrue(json.contains("HP Separator"));
  }

  @Test
  void testPropagationResultTextSummary() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult result = tracer.trace("Export Compressor");

    String text = result.toTextSummary();
    assertNotNull(text);
    assertTrue(text.contains("Export Compressor"));
  }

  @Test
  void testPropagationMaxDepth() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    tracer.setMaxCascadeDepth(1);
    FailurePropagationTracer.PropagationResult result = tracer.trace("HP Separator");

    assertNotNull(result);
    for (FailurePropagationTracer.PropagationStep step : result.getSteps()) {
      assertTrue(step.getCascadeDepth() <= 1, "Should not exceed max cascade depth of 1");
    }
  }

  // ── RestartSequenceGenerator tests ─────────────────────────────────

  @Test
  void testRestartPlanGeneration() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace("Export Compressor");

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);

    assertNotNull(plan);
    assertEquals("Export Compressor", plan.getInitiatingEquipment());
    assertTrue(plan.getStepCount() > 0, "Plan should have steps");
    assertTrue(plan.getEstimatedTotalTimeSeconds() > 0, "Plan should have estimated time");
  }

  @Test
  void testRestartPlanHasSafetyStep() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace("Export Compressor");

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);

    // First step should be safety verification
    RestartStep firstStep = plan.getSteps().get(0);
    assertEquals(RestartStep.Priority.CRITICAL, firstStep.getPriority());
    assertTrue(firstStep.getAction().toLowerCase().contains("safety"));
  }

  @Test
  void testRestartPlanFromTripEvent() {
    TripEvent trip = new TripEvent("Export Compressor", "pressure", 120.0, 130.0, true, 0.0,
        TripEvent.Severity.HIGH);

    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace(trip);

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);

    assertNotNull(plan.getInitiatingTrip());
    assertEquals(TripEvent.Severity.HIGH, plan.getInitiatingTrip().getSeverity());
  }

  @Test
  void testRestartPlanJson() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace("HP Separator");

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);

    String json = plan.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"steps\""));
    assertTrue(json.contains("\"estimatedTotalTimeMinutes\""));
  }

  @Test
  void testRestartPlanTextReport() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace("Export Compressor");

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);

    String text = plan.toTextReport();
    assertNotNull(text);
    assertTrue(text.contains("Restart Plan"));
    assertTrue(text.contains("Export Compressor"));
  }

  @Test
  void testRestartPlanCustomRampUpTime() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace("Export Compressor");

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    generator.setCustomRampUpTime("Export Compressor", 300.0);

    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);

    // Find the compressor restart step (not the root cause verification step) and verify custom
    // time
    boolean found = false;
    for (RestartStep step : plan.getSteps()) {
      if ("Export Compressor".equals(step.getEquipmentName())
          && step.getRecommendedDelaySeconds() > 0) {
        assertEquals(300.0, step.getRecommendedDelaySeconds(), 0.001);
        found = true;
        break;
      }
    }
    assertTrue(found, "Should have a restart step for Export Compressor with custom ramp-up time");
  }

  @Test
  void testRestartPlanFromEquipmentList() {
    java.util.List<String> tripped = java.util.Arrays.asList("Export Compressor", "Aftercooler");

    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(tripped);

    assertNotNull(plan);
    assertTrue(plan.getStepCount() > 2, "Should have safety + root cause + equipment steps");
  }

  // ── Full integration: Detect → Trace → Restart ────────────────────

  @Test
  void testFullPostTripWorkflow() {
    // Step 1: Detect trip
    TripEventDetector detector = new TripEventDetector(process);
    detector.addTripCondition("Export Compressor", "pressure", 80.0, true, TripEvent.Severity.HIGH);

    List<TripEvent> trips = detector.check(0.0);
    assertFalse(trips.isEmpty(), "Should detect trip");
    TripEvent trip = trips.get(0);

    // Step 2: Trace failure propagation
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationResult propagation = tracer.trace(trip);
    assertNotNull(propagation);
    assertTrue(propagation.getSteps().size() > 0);

    // Step 3: Generate restart plan
    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);
    RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);
    assertNotNull(plan);
    assertTrue(plan.getStepCount() > 3); // safety + root cause + utilities + at least one
                                         // equipment

    // Verify the plan is reasonable
    assertTrue(plan.getEstimatedTotalTimeMinutes() > 0);
    String report = plan.toTextReport();
    assertNotNull(report);
    assertTrue(report.length() > 50);
  }
}
