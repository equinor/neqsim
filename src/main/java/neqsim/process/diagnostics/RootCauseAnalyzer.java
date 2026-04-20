package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import neqsim.process.diagnostics.hypothesis.CompressorSurgeHypothesis;
import neqsim.process.diagnostics.hypothesis.HighPressureHypothesis;
import neqsim.process.diagnostics.hypothesis.HydrateFormationHypothesis;
import neqsim.process.diagnostics.hypothesis.InstrumentFailureHypothesis;
import neqsim.process.diagnostics.hypothesis.LiquidCarryOverHypothesis;
import neqsim.process.diagnostics.hypothesis.SeparatorFloodingHypothesis;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Orchestrates root cause analysis for a trip event.
 *
 * <p>
 * The analyzer evaluates a set of {@link TripHypothesis} implementations against the captured
 * evidence ({@link ProcessStateSnapshot} and {@link UnifiedEventTimeline}), ranks them by
 * confidence, traces the failure propagation, and produces a {@link RootCauseReport}.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * RootCauseAnalyzer analyzer = new RootCauseAnalyzer(processSystem);
 * RootCauseReport report = analyzer.analyze(tripEvent, snapshot, timeline);
 * System.out.println(report.toTextSummary());
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class RootCauseAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private final List<TripHypothesis> hypotheses;

  /**
   * Constructs an analyzer with the default set of hypotheses.
   *
   * @param processSystem the process system where the trip occurred
   */
  public RootCauseAnalyzer(ProcessSystem processSystem) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem must not be null");
    this.hypotheses = new ArrayList<>();
    registerDefaultHypotheses();
  }

  /**
   * Registers the default set of hypothesis implementations.
   */
  private void registerDefaultHypotheses() {
    hypotheses.add(new CompressorSurgeHypothesis());
    hypotheses.add(new HighPressureHypothesis());
    hypotheses.add(new HydrateFormationHypothesis());
    hypotheses.add(new LiquidCarryOverHypothesis());
    hypotheses.add(new SeparatorFloodingHypothesis());
    hypotheses.add(new InstrumentFailureHypothesis());
  }

  /**
   * Adds a custom hypothesis to the analyzer.
   *
   * @param hypothesis the hypothesis to add
   */
  public void addHypothesis(TripHypothesis hypothesis) {
    if (hypothesis != null) {
      hypotheses.add(hypothesis);
    }
  }

  /**
   * Removes a hypothesis by name.
   *
   * @param hypothesisName the name of the hypothesis to remove
   * @return true if a hypothesis was removed
   */
  public boolean removeHypothesis(String hypothesisName) {
    for (int i = 0; i < hypotheses.size(); i++) {
      if (hypotheses.get(i).getName().equals(hypothesisName)) {
        hypotheses.remove(i);
        return true;
      }
    }
    return false;
  }

  /**
   * Returns all registered hypotheses.
   *
   * @return unmodifiable list of hypotheses
   */
  public List<TripHypothesis> getHypotheses() {
    return Collections.unmodifiableList(hypotheses);
  }

  /**
   * Performs root cause analysis for a trip event.
   *
   * <p>
   * Evaluates all applicable hypotheses, ranks them by confidence, traces failure propagation, and
   * produces a comprehensive report.
   * </p>
   *
   * @param tripEvent the trip event to analyse
   * @param snapshot the process state snapshot (last good vs trip)
   * @param timeline the unified event timeline
   * @return a root cause report with ranked hypotheses and recommendations
   */
  public RootCauseReport analyze(TripEvent tripEvent, ProcessStateSnapshot snapshot,
      UnifiedEventTimeline timeline) {
    Objects.requireNonNull(tripEvent, "tripEvent must not be null");

    // Evaluate all applicable hypotheses
    List<HypothesisResult> results = new ArrayList<>();
    for (TripHypothesis hypothesis : hypotheses) {
      if (hypothesis.isApplicableTo(tripEvent.getTripType())) {
        try {
          HypothesisResult result = hypothesis.evaluate(snapshot, timeline);
          results.add(result);
        } catch (Exception e) {
          System.err.println(
              "Error evaluating hypothesis " + hypothesis.getName() + ": " + e.getMessage());
        }
      }
    }

    // Sort by confidence score descending
    Collections.sort(results, new Comparator<HypothesisResult>() {
      @Override
      public int compare(HypothesisResult a, HypothesisResult b) {
        return Double.compare(b.getScore(), a.getScore());
      }
    });

    // Trace failure propagation
    FailurePropagationTracer tracer = new FailurePropagationTracer(processSystem);
    FailurePropagationTracer.PropagationChain chain =
        tracer.traceBidirectional(tripEvent.getInitiatingEquipment());

    return new RootCauseReport(tripEvent, results, chain, timeline, snapshot);
  }

  /**
   * Convenience method that performs analysis using the detector's captured data.
   *
   * @param detector the trip event detector that captured the trip
   * @return a root cause report, or null if no trip has been detected
   */
  public RootCauseReport analyzeFromDetector(TripEventDetector detector) {
    Objects.requireNonNull(detector, "detector must not be null");
    TripEvent lastTrip = detector.getLastTrip();
    if (lastTrip == null) {
      return null;
    }
    ProcessStateSnapshot snapshot = detector.getLatestSnapshot();
    UnifiedEventTimeline timeline = detector.getTimeline();
    return analyze(lastTrip, snapshot, timeline);
  }
}
