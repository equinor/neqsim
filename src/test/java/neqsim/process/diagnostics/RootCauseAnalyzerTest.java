package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmLevel;
import neqsim.process.diagnostics.restart.RestartConstraintChecker;
import neqsim.process.diagnostics.restart.RestartOptimiser;
import neqsim.process.diagnostics.restart.RestartReadiness;
import neqsim.process.diagnostics.restart.RestartSequence;
import neqsim.process.diagnostics.restart.RestartSequenceGenerator;
import neqsim.process.diagnostics.restart.RestartStep;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the post-trip root cause analysis and restart optimisation classes.
 *
 * @author esol
 * @version 1.0
 */
class RootCauseAnalyzerTest {

  private ProcessSystem process;
  private Stream feed;
  private Separator separator;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    feed = new Stream("feed gas", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(80.0, "bara");

    separator = new Separator("HP separator", feed);

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();
  }

  // ─── TripType ──────────────────────────────────────────

  @Test
  void testTripTypeCategory() {
    assertTrue(TripType.COMPRESSOR_SURGE.isRotatingEquipmentRelated());
    assertTrue(TripType.ESD_ACTIVATED.isSafetyInitiated());
    assertFalse(TripType.COMPRESSOR_SURGE.isSafetyInitiated());
  }

  // ─── TripEvent Builder ────────────────────────────────

  @Test
  void testTripEventBuilder() {
    TripEvent event = new TripEvent.Builder().eventId("TEST-001").timestamp(120.5)
        .initiatingEquipment("Compressor-1").tripType(TripType.COMPRESSOR_SURGE)
        .severity(TripEvent.Severity.HIGH).description("Compressor surged at high load")
        .addEquipmentValue("Compressor-1.surgeMargin", 0.02).build();

    assertEquals("TEST-001", event.getEventId());
    assertEquals(120.5, event.getTimestamp(), 0.001);
    assertEquals("Compressor-1", event.getInitiatingEquipment());
    assertEquals(TripType.COMPRESSOR_SURGE, event.getTripType());
    assertEquals(TripEvent.Severity.HIGH, event.getSeverity());
    assertTrue(event.getDescription().contains("surged"));
    assertEquals(0.02, event.getEquipmentValues().get("Compressor-1.surgeMargin"), 0.001);
  }

  @Test
  void testTripEventToJson() {
    TripEvent event = new TripEvent.Builder().eventId("JSON-001").timestamp(60.0)
        .initiatingEquipment("Separator-1").tripType(TripType.HIGH_LEVEL).build();

    String json = event.toJson();
    assertNotNull(json);
    assertTrue(json.contains("JSON-001"));
    assertTrue(json.contains("HIGH_LEVEL"));
  }

  @Test
  void testTripEventWithAlarms() {
    AlarmEvent alarm = AlarmEvent.activated("PT-100", AlarmLevel.HIHI, 100.0, 95.0);

    TripEvent event = new TripEvent.Builder().eventId("ALARM-001").timestamp(100.0)
        .initiatingEquipment("HP separator").tripType(TripType.HIGH_PRESSURE).addAlarm(alarm)
        .build();

    assertEquals(1, event.getAssociatedAlarms().size());
  }

  // ─── UnifiedEventTimeline ─────────────────────────────

  @Test
  void testTimelineAddAndQuery() {
    UnifiedEventTimeline timeline = new UnifiedEventTimeline();

    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(10.0,
        UnifiedEventTimeline.EntryType.ALARM, "PT-100", "HIHI alarm activated"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(12.0,
        UnifiedEventTimeline.EntryType.STATE_CHANGE, "Compressor-1", "Tripped"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(11.0,
        UnifiedEventTimeline.EntryType.MEASUREMENT, "TT-200", "Temperature spike"));

    assertEquals(3, timeline.size());

    // getSortedEntries returns sorted by timestamp
    List<UnifiedEventTimeline.TimelineEntry> sorted = timeline.getSortedEntries();
    assertEquals(10.0, sorted.get(0).getTimestamp(), 0.001);
    assertEquals(11.0, sorted.get(1).getTimestamp(), 0.001);
    assertEquals(12.0, sorted.get(2).getTimestamp(), 0.001);
  }

  @Test
  void testTimelineEventsAround() {
    UnifiedEventTimeline timeline = new UnifiedEventTimeline();
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(5.0,
        UnifiedEventTimeline.EntryType.ALARM, "PT-100", "Alarm 1"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(10.0,
        UnifiedEventTimeline.EntryType.TRIP, "Compressor-1", "Trip"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(50.0,
        UnifiedEventTimeline.EntryType.MEASUREMENT, "FT-100", "Flow reading"));

    // Window of +/- 6 seconds around t=10
    List<UnifiedEventTimeline.TimelineEntry> around = timeline.getEventsAround(10.0, 6.0);
    assertEquals(2, around.size()); // t=5 and t=10
  }

  @Test
  void testTimelineByType() {
    UnifiedEventTimeline timeline = new UnifiedEventTimeline();
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(1.0,
        UnifiedEventTimeline.EntryType.ALARM, "PT-100", "Alarm"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(2.0,
        UnifiedEventTimeline.EntryType.ALARM, "LT-100", "Alarm 2"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(3.0,
        UnifiedEventTimeline.EntryType.TRIP, "Compressor-1", "Trip"));

    List<UnifiedEventTimeline.TimelineEntry> alarms =
        timeline.getEventsByType(UnifiedEventTimeline.EntryType.ALARM);
    assertEquals(2, alarms.size());
  }

  @Test
  void testTimelineByEquipment() {
    UnifiedEventTimeline timeline = new UnifiedEventTimeline();
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(1.0,
        UnifiedEventTimeline.EntryType.ALARM, "PT-100", "High pressure"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(2.0,
        UnifiedEventTimeline.EntryType.ALARM, "LT-100", "High level"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(3.0,
        UnifiedEventTimeline.EntryType.STATE_CHANGE, "PT-100", "State changed"));

    List<UnifiedEventTimeline.TimelineEntry> ptEvents = timeline.getEventsForEquipment("PT-100");
    assertEquals(2, ptEvents.size());
  }

  @Test
  void testTimelineToJson() {
    UnifiedEventTimeline timeline = new UnifiedEventTimeline();
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(1.0,
        UnifiedEventTimeline.EntryType.TRIP, "Compressor-1", "Surge trip"));
    String json = timeline.toJson();
    assertNotNull(json);
    assertTrue(json.contains("TRIP"));
    assertTrue(json.contains("Compressor-1"));
  }

  // ─── HypothesisResult ─────────────────────────────────

  @Test
  void testHypothesisResultConfidence() {
    HypothesisResult confirmed = new HypothesisResult("Compressor Surge", 0.95,
        Arrays.asList("Surge margin below 5%"), "Reduce speed");
    assertEquals(HypothesisResult.Confidence.CONFIRMED, confirmed.getConfidence());
    assertEquals(0.95, confirmed.getScore(), 0.001);

    HypothesisResult unlikely = new HypothesisResult("Hydrate Formation", 0.20,
        Arrays.asList("Temperature above hydrate range"), "None");
    assertEquals(HypothesisResult.Confidence.UNLIKELY, unlikely.getConfidence());
  }

  @Test
  void testHypothesisResultFromScore() {
    assertEquals(HypothesisResult.Confidence.CONFIRMED,
        HypothesisResult.Confidence.fromScore(0.95));
    assertEquals(HypothesisResult.Confidence.LIKELY, HypothesisResult.Confidence.fromScore(0.75));
    assertEquals(HypothesisResult.Confidence.POSSIBLE, HypothesisResult.Confidence.fromScore(0.50));
    assertEquals(HypothesisResult.Confidence.UNLIKELY, HypothesisResult.Confidence.fromScore(0.20));
    assertEquals(HypothesisResult.Confidence.RULED_OUT, HypothesisResult.Confidence.fromScore(0.0));
  }

  @Test
  void testHypothesisResultScoreClamped() {
    HypothesisResult overOne = new HypothesisResult("Test", 1.5, null, null);
    assertEquals(1.0, overOne.getScore(), 0.001);

    HypothesisResult underZero = new HypothesisResult("Test", -0.5, null, null);
    assertEquals(0.0, underZero.getScore(), 0.001);
  }

  // ─── RootCauseAnalyzer ────────────────────────────────

  @Test
  void testRootCauseAnalyzerDefaultHypotheses() {
    RootCauseAnalyzer analyzer = new RootCauseAnalyzer(process);
    List<TripHypothesis> hypotheses = analyzer.getHypotheses();
    assertFalse(hypotheses.isEmpty());
    assertTrue(hypotheses.size() >= 6, "Should have at least 6 default hypotheses");
  }

  @Test
  void testRootCauseAnalyzerAddRemoveHypothesis() {
    RootCauseAnalyzer analyzer = new RootCauseAnalyzer(process);
    int initialCount = analyzer.getHypotheses().size();

    // Add a custom hypothesis
    TripHypothesis custom = new TripHypothesis("Custom Test", "Test hypothesis", TripType.UNKNOWN) {
      @Override
      public HypothesisResult evaluate(ProcessStateSnapshot snapshot,
          UnifiedEventTimeline timeline) {
        return new HypothesisResult("Custom Test", 0.5, null, null);
      }

      @Override
      public boolean isApplicableTo(TripType tripType) {
        return true;
      }
    };
    analyzer.addHypothesis(custom);
    assertEquals(initialCount + 1, analyzer.getHypotheses().size());

    // Remove it
    boolean removed = analyzer.removeHypothesis("Custom Test");
    assertTrue(removed);
    assertEquals(initialCount, analyzer.getHypotheses().size());
  }

  @Test
  void testRootCauseAnalyzerAnalyze() {
    RootCauseAnalyzer analyzer = new RootCauseAnalyzer(process);

    TripEvent tripEvent = new TripEvent.Builder().eventId("RCA-001").timestamp(60.0)
        .initiatingEquipment("HP separator").tripType(TripType.HIGH_PRESSURE)
        .severity(TripEvent.Severity.HIGH).build();

    UnifiedEventTimeline timeline = new UnifiedEventTimeline();
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(58.0,
        UnifiedEventTimeline.EntryType.ALARM, "PT-100", "High pressure alarm"));
    timeline.addEntry(new UnifiedEventTimeline.TimelineEntry(60.0,
        UnifiedEventTimeline.EntryType.TRIP, "HP separator", "Trip activated"));

    // Create a simple snapshot (both states from same process — differences will be minimal)
    ProcessStateSnapshot snapshot = new ProcessStateSnapshot(null, null, 60.0);

    RootCauseReport report = analyzer.analyze(tripEvent, snapshot, timeline);
    assertNotNull(report);
    assertNotNull(report.toTextSummary());
    assertNotNull(report.toJson());
    assertEquals(tripEvent, report.getTripEvent());
  }

  // ─── FailurePropagationTracer ─────────────────────────

  @Test
  void testFailurePropagationTracerDownstream() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationChain chain = tracer.traceDownstream("feed gas");

    assertNotNull(chain);
    assertFalse(chain.getSteps().isEmpty());
    assertTrue(chain.getSteps().get(0).getEquipmentName().equals("feed gas"));
  }

  @Test
  void testFailurePropagationTracerUpstream() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationChain chain = tracer.traceUpstream("HP separator");

    assertNotNull(chain);
    assertFalse(chain.getSteps().isEmpty());
  }

  @Test
  void testFailurePropagationTracerBidirectional() {
    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationChain chain = tracer.traceBidirectional("feed gas");

    assertNotNull(chain);
    assertNotNull(chain.toJson());
  }

  // ─── RestartStep ──────────────────────────────────────

  @Test
  void testRestartStepCreation() {
    RestartStep step = new RestartStep(1, "Open inlet valve to 50%",
        RestartStep.ActionType.VALVE_ACTION, "XV-100", "position", 50.0, "%");

    assertEquals(1, step.getStepNumber());
    assertEquals("Open inlet valve to 50%", step.getDescription());
    assertEquals(RestartStep.ActionType.VALVE_ACTION, step.getActionType());
    assertEquals("XV-100", step.getTargetEquipment());
    assertEquals(50.0, step.getTargetValue(), 0.001);
    assertFalse(step.isCompleted());

    step.markCompleted();
    assertTrue(step.isCompleted());
  }

  @Test
  void testRestartStepWithRampRate() {
    RestartStep step = new RestartStep(2, "Ramp compressor to 80%",
        RestartStep.ActionType.COMPRESSOR_RAMP, "K-100", "speed", 80.0, "%");
    step.setRampRate(5.0);
    step.setDurationSeconds(120.0);

    assertEquals(5.0, step.getRampRate(), 0.001);
    assertEquals(120.0, step.getDurationSeconds(), 0.001);
  }

  // ─── RestartSequence ──────────────────────────────────

  @Test
  void testRestartSequenceAddSteps() {
    RestartSequence seq = new RestartSequence("Test Restart");
    assertEquals("Test Restart", seq.getName());
    assertEquals(0, seq.size());

    seq.addStep(new RestartStep(1, "Step 1", RestartStep.ActionType.VALVE_ACTION, "XV-100",
        "position", 100.0, "%"));
    seq.addStep(
        new RestartStep(2, "Wait 60s", RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, ""));

    RestartStep waitStep = seq.getSteps().get(1);
    waitStep.setDurationSeconds(60.0);

    assertEquals(2, seq.size());
    assertEquals(1.0, seq.getEstimatedDurationMinutes(), 0.1);
  }

  @Test
  void testRestartSequenceGetNextStep() {
    RestartSequence seq = new RestartSequence("Sequence Test");
    RestartStep step1 = new RestartStep(1, "Step 1", RestartStep.ActionType.VALVE_ACTION, "XV-100",
        "position", 100.0, "%");
    RestartStep step2 = new RestartStep(2, "Step 2", RestartStep.ActionType.COMPRESSOR_START,
        "K-100", "state", 1.0, "");

    seq.addStep(step1);
    seq.addStep(step2);

    assertFalse(seq.isComplete());

    RestartStep next = seq.getNextStep();
    assertNotNull(next);
    assertEquals(1, next.getStepNumber());

    step1.markCompleted();
    next = seq.getNextStep();
    assertEquals(2, next.getStepNumber());

    step2.markCompleted();
    assertTrue(seq.isComplete());
  }

  // ─── RestartConstraintChecker ─────────────────────────

  @Test
  void testRestartConstraintChecker() {
    RestartConstraintChecker checker = new RestartConstraintChecker(process);

    TripEvent tripEvent = new TripEvent.Builder().eventId("CHECK-001").timestamp(60.0)
        .initiatingEquipment("HP separator").tripType(TripType.HIGH_PRESSURE).build();

    RestartReadiness readiness = checker.check(tripEvent, 120.0);
    assertNotNull(readiness);
    assertNotNull(readiness.getStatus());
    assertNotNull(readiness.getResults());
    assertNotNull(readiness.toJson());
  }

  // ─── RestartSequenceGenerator ─────────────────────────

  @Test
  void testRestartSequenceGeneratorCompressorSurge() {
    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);

    TripEvent tripEvent = new TripEvent.Builder().eventId("SEQ-001").timestamp(60.0)
        .initiatingEquipment("Compressor-1").tripType(TripType.COMPRESSOR_SURGE).build();

    RestartSequence sequence = generator.generate(tripEvent, null);
    assertNotNull(sequence);
    assertTrue(sequence.size() > 0, "Should generate at least one step");
    assertTrue(sequence.getEstimatedDurationMinutes() > 0,
        "Should have a positive estimated duration");
  }

  @Test
  void testRestartSequenceGeneratorHighPressure() {
    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);

    TripEvent tripEvent = new TripEvent.Builder().eventId("SEQ-002").timestamp(60.0)
        .initiatingEquipment("HP separator").tripType(TripType.HIGH_PRESSURE).build();

    RestartSequence sequence = generator.generate(tripEvent, null);
    assertNotNull(sequence);
    assertTrue(sequence.size() > 0);
  }

  @Test
  void testRestartSequenceGeneratorESD() {
    RestartSequenceGenerator generator = new RestartSequenceGenerator(process);

    TripEvent tripEvent =
        new TripEvent.Builder().eventId("SEQ-003").timestamp(60.0).initiatingEquipment("ESD system")
            .tripType(TripType.ESD_ACTIVATED).severity(TripEvent.Severity.CRITICAL).build();

    RestartSequence sequence = generator.generate(tripEvent, null);
    assertNotNull(sequence);
    assertTrue(sequence.size() > 0);
  }

  // ─── RestartOptimiser ─────────────────────────────────

  @Test
  void testRestartOptimiserImprovesMTTR() {
    RestartOptimiser optimiser = new RestartOptimiser(process);

    // Build a baseline sequence with some wait steps
    RestartSequence baseSeq = new RestartSequence("Base Restart");
    RestartStep wait1 = new RestartStep(1, "Wait for settling",
        RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, "");
    wait1.setDurationSeconds(120.0);
    baseSeq.addStep(wait1);

    RestartStep ramp = new RestartStep(2, "Ramp compressor", RestartStep.ActionType.COMPRESSOR_RAMP,
        "K-100", "speed", 80.0, "%");
    ramp.setRampRate(2.0);
    ramp.setDurationSeconds(180.0);
    baseSeq.addStep(ramp);

    RestartStep wait2 = new RestartStep(3, "Wait for stability",
        RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, "");
    wait2.setDurationSeconds(60.0);
    baseSeq.addStep(wait2);

    RestartOptimiser.OptimisationResult result = optimiser.optimise(baseSeq);
    assertNotNull(result);
    assertTrue(result.getImprovementPercent() > 0, "Optimiser should reduce the estimated MTTR");
    assertNotNull(result.getOptimisedSequence());
    assertTrue(result.getOptimisedEstimatedMinutes() < result.getBaseEstimatedMinutes());
    assertFalse(result.getRecommendations().isEmpty());
  }

  @Test
  void testRestartOptimiserToJson() {
    RestartOptimiser optimiser = new RestartOptimiser(process);
    RestartSequence baseSeq = new RestartSequence("Test");

    RestartStep step =
        new RestartStep(1, "Wait", RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, "");
    step.setDurationSeconds(300.0);
    baseSeq.addStep(step);

    RestartOptimiser.OptimisationResult result = optimiser.optimise(baseSeq);
    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("improvementPercent"));
  }

  // ─── ProcessSystem integration ────────────────────────

  @Test
  void testProcessSystemGetTripEventDetector() {
    TripEventDetector detector = process.getTripEventDetector();
    assertNotNull(detector);

    // Should return same instance on second call
    TripEventDetector detector2 = process.getTripEventDetector();
    assertTrue(detector == detector2, "Should return the same detector instance");
  }

  // ─── RootCauseReport ─────────────────────────────────

  @Test
  void testRootCauseReportRecommendedActions() {
    List<HypothesisResult> results = Arrays.asList(
        new HypothesisResult("High Pressure", 0.85, Arrays.asList("Pressure 10% above setpoint"),
            "Reduce inlet valve opening"),
        new HypothesisResult("Instrument Failure", 0.30,
            Arrays.asList("No sensor deviations found"), "Check instrument calibration"));

    TripEvent tripEvent = new TripEvent.Builder().eventId("RPT-001").timestamp(60.0)
        .initiatingEquipment("HP separator").tripType(TripType.HIGH_PRESSURE).build();

    FailurePropagationTracer tracer = new FailurePropagationTracer(process);
    FailurePropagationTracer.PropagationChain chain = tracer.traceBidirectional("HP separator");

    UnifiedEventTimeline timeline = new UnifiedEventTimeline();

    RootCauseReport report = new RootCauseReport(tripEvent, results, chain, timeline, null);

    assertNotNull(report.getMostLikelyHypothesis());
    assertEquals("High Pressure", report.getMostLikelyHypothesis().getHypothesisName());

    List<HypothesisResult> above70 = report.getHypothesesAbove(HypothesisResult.Confidence.LIKELY);
    assertEquals(1, above70.size());

    List<String> actions = report.getRecommendedActions();
    assertFalse(actions.isEmpty());
    assertTrue(actions.get(0).contains("Reduce inlet valve opening"));
  }
}
