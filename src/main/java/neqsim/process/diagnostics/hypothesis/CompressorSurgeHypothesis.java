package neqsim.process.diagnostics.hypothesis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.diagnostics.HypothesisResult;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripHypothesis;
import neqsim.process.diagnostics.TripType;
import neqsim.process.diagnostics.UnifiedEventTimeline;

/**
 * Hypothesis: trip caused by compressor surge.
 *
 * <p>
 * Checks for evidence of the operating point approaching the surge line, anti-surge valve opening,
 * flow rate drops, and pressure oscillations characteristic of surge cycles.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CompressorSurgeHypothesis extends TripHypothesis {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructs the compressor surge hypothesis.
   */
  public CompressorSurgeHypothesis() {
    super("Compressor Surge", "Trip caused by compressor operating at or beyond its surge limit, "
        + "resulting in flow reversal and mechanical damage risk", TripType.COMPRESSOR_SURGE);
  }

  @Override
  public HypothesisResult evaluate(ProcessStateSnapshot snapshot, UnifiedEventTimeline timeline) {
    List<String> evidence = new ArrayList<>();
    double score = 0.0;

    if (snapshot == null) {
      return new HypothesisResult(getName(), 0.0, evidence, "No snapshot available for analysis");
    }

    ProcessStateSnapshot.StateDiff diff = snapshot.diff();

    // Check for flow rate drops (low flow triggers surge)
    List<ProcessStateSnapshot.Deviation> flowDeviations = diff.getDeviationsMatching("flowRate");
    for (ProcessStateSnapshot.Deviation dev : flowDeviations) {
      if (dev.getAbsoluteChange() < 0) {
        double relDrop = Math.abs(dev.getGoodValue()) > 1e-10
            ? Math.abs(dev.getAbsoluteChange() / dev.getGoodValue())
            : 0.0;
        if (relDrop > 0.2) {
          evidence.add(String.format("Flow drop of %.0f%% at %s (low flow can trigger surge)",
              relDrop * 100.0, dev.getVariableAddress()));
          score += 0.20;
        }
      }
    }

    // Check for pressure ratio changes
    List<ProcessStateSnapshot.Deviation> pressureDeviations =
        diff.getDeviationsMatching("pressure");
    boolean pressureOscillation = false;
    for (ProcessStateSnapshot.Deviation dev : pressureDeviations) {
      if (dev.getSignificance() == ProcessStateSnapshot.DeviationSignificance.HIGH) {
        evidence.add(String.format("Significant pressure change at %s", dev.getVariableAddress()));
        score += 0.15;
        pressureOscillation = true;
      }
    }

    // Check for speed changes
    List<ProcessStateSnapshot.Deviation> speedDeviations = diff.getDeviationsMatching("speed");
    for (ProcessStateSnapshot.Deviation dev : speedDeviations) {
      if (dev.getSignificance() == ProcessStateSnapshot.DeviationSignificance.HIGH) {
        evidence.add("Compressor speed changed significantly");
        score += 0.10;
      }
    }

    // Check timeline for surge-related events
    if (timeline != null) {
      List<UnifiedEventTimeline.TimelineEntry> stateChanges =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.STATE_CHANGE);
      for (UnifiedEventTimeline.TimelineEntry entry : stateChanges) {
        String desc = entry.getDescription().toLowerCase();
        if (desc.contains("surge")) {
          evidence.add("Surge protection state change detected: " + entry.getDescription());
          score += 0.25;
        }
      }

      // Check for anti-surge valve actions
      List<UnifiedEventTimeline.TimelineEntry> valveActions =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.VALVE_ACTION);
      for (UnifiedEventTimeline.TimelineEntry entry : valveActions) {
        String desc = entry.getDescription().toLowerCase();
        if (desc.contains("anti-surge") || desc.contains("antisurge")) {
          evidence.add("Anti-surge valve action: " + entry.getDescription());
          score += 0.20;
        }
      }

      // Check for compressor trip event specifically
      List<UnifiedEventTimeline.TimelineEntry> trips =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.TRIP);
      for (UnifiedEventTimeline.TimelineEntry entry : trips) {
        String desc = entry.getDescription().toLowerCase();
        if (desc.contains("compressor")) {
          evidence.add("Compressor trip event: " + entry.getDescription());
          score += 0.15;
        }
      }
    }

    score = Math.min(1.0, score);

    String action = score >= 0.5
        ? "Check suction conditions and flow rate. Verify anti-surge controller tuning. "
            + "Review compressor operating map position relative to surge line. "
            + "Consider increasing recycle flow or reducing discharge pressure before restart."
        : "Compressor surge unlikely based on available evidence.";

    return new HypothesisResult(getName(), score, evidence, action);
  }
}
