package neqsim.process.diagnostics.hypothesis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.diagnostics.HypothesisResult;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripHypothesis;
import neqsim.process.diagnostics.TripType;
import neqsim.process.diagnostics.UnifiedEventTimeline;

/**
 * Hypothesis: trip caused by liquid carry-over from a separator.
 *
 * <p>
 * Checks for evidence of separator level rising towards high-high, gas outlet stream showing liquid
 * contamination (density increase), and downstream compressor issues.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class LiquidCarryOverHypothesis extends TripHypothesis {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructs the liquid carry-over hypothesis.
   */
  public LiquidCarryOverHypothesis() {
    super("Liquid Carry-Over",
        "Trip caused by liquid carry-over from a separator, leading to downstream "
            + "equipment damage (e.g. compressor surge or high vibration)",
        null);
  }

  @Override
  public HypothesisResult evaluate(ProcessStateSnapshot snapshot, UnifiedEventTimeline timeline) {
    List<String> evidence = new ArrayList<>();
    double score = 0.0;

    if (snapshot == null) {
      return new HypothesisResult(getName(), 0.0, evidence, "No snapshot available for analysis");
    }

    ProcessStateSnapshot.StateDiff diff = snapshot.diff();

    // Check for level increases in separators
    List<ProcessStateSnapshot.Deviation> levelDeviations =
        diff.getDeviationsMatching("liquidLevel");
    for (ProcessStateSnapshot.Deviation dev : levelDeviations) {
      if (dev.getAbsoluteChange() > 0
          && dev.getSignificance() == ProcessStateSnapshot.DeviationSignificance.HIGH) {
        evidence.add(String.format("Separator level increased significantly at %s",
            dev.getVariableAddress()));
        score += 0.25;
      }
    }

    // Check for flow rate increases (liquid in gas line)
    List<ProcessStateSnapshot.Deviation> flowDeviations = diff.getDeviationsMatching("flowRate");
    for (ProcessStateSnapshot.Deviation dev : flowDeviations) {
      if (dev.getAbsoluteChange() > 0
          && dev.getSignificance() == ProcessStateSnapshot.DeviationSignificance.HIGH) {
        evidence.add(String.format("Flow rate increase at %s (possible liquid slugging)",
            dev.getVariableAddress()));
        score += 0.15;
      }
    }

    // Check timeline for level alarms
    if (timeline != null) {
      List<UnifiedEventTimeline.TimelineEntry> alarms =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.ALARM);
      for (UnifiedEventTimeline.TimelineEntry alarm : alarms) {
        String desc = alarm.getDescription().toLowerCase();
        if (desc.contains("level") && (desc.contains("hihi") || desc.contains("high"))) {
          evidence.add("High level alarm detected: " + alarm.getDescription());
          score += 0.20;
        }
      }

      // Check for compressor trip following separator alarms
      List<UnifiedEventTimeline.TimelineEntry> trips =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.TRIP);
      for (UnifiedEventTimeline.TimelineEntry trip : trips) {
        String desc = trip.getDescription().toLowerCase();
        if (desc.contains("compressor") && (desc.contains("surge") || desc.contains("vibration"))) {
          evidence.add("Compressor trip detected: " + trip.getDescription());
          score += 0.20;
        }
      }
    }

    // Check for gas load factor changes suggesting demister flooding
    List<ProcessStateSnapshot.Deviation> kFactorDeviations =
        diff.getDeviationsMatching("gasLoadFactor");
    for (ProcessStateSnapshot.Deviation dev : kFactorDeviations) {
      if (dev.getAbsoluteChange() > 0) {
        evidence.add("Gas load factor increased — demister may be flooded");
        score += 0.15;
      }
    }

    score = Math.min(1.0, score);

    String action = score >= 0.5
        ? "Check separator level control. Verify demister condition. "
            + "Inspect gas outlet for liquid presence. Review separator sizing."
        : "Liquid carry-over unlikely based on available evidence.";

    return new HypothesisResult(getName(), score, evidence, action);
  }
}
