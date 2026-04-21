package neqsim.process.diagnostics.hypothesis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.diagnostics.HypothesisResult;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripHypothesis;
import neqsim.process.diagnostics.TripType;
import neqsim.process.diagnostics.UnifiedEventTimeline;

/**
 * Hypothesis: trip caused by separator flooding (liquid level too high or demister flooding).
 *
 * <p>
 * Checks for evidence of rising liquid levels, reduced gas-liquid separation efficiency, and
 * high-level alarms.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SeparatorFloodingHypothesis extends TripHypothesis {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructs the separator flooding hypothesis.
   */
  public SeparatorFloodingHypothesis() {
    super("Separator Flooding",
        "Trip caused by separator liquid level exceeding design limits or demister "
            + "flooding, leading to high-level trip or liquid carry-over",
        TripType.HIGH_LEVEL);
  }

  @Override
  public HypothesisResult evaluate(ProcessStateSnapshot snapshot, UnifiedEventTimeline timeline) {
    List<String> evidence = new ArrayList<>();
    double score = 0.0;

    if (snapshot == null) {
      return new HypothesisResult(getName(), 0.0, evidence, "No snapshot available for analysis");
    }

    ProcessStateSnapshot.StateDiff diff = snapshot.diff();

    // Check for level increases
    List<ProcessStateSnapshot.Deviation> levelDeviations =
        diff.getDeviationsMatching("liquidLevel");
    for (ProcessStateSnapshot.Deviation dev : levelDeviations) {
      if (dev.getAbsoluteChange() > 0) {
        evidence.add(String.format("Liquid level increased at %s (good: %.3f, trip: %.3f)",
            dev.getVariableAddress(), dev.getGoodValue(), dev.getTripValue()));
        score += 0.25;
      }
    }

    // Check for retention time reduction
    List<ProcessStateSnapshot.Deviation> retTimeDeviations =
        diff.getDeviationsMatching("retentionTime");
    for (ProcessStateSnapshot.Deviation dev : retTimeDeviations) {
      if (dev.getAbsoluteChange() < 0) {
        evidence.add("Liquid retention time decreased (higher throughput or smaller volume)");
        score += 0.15;
      }
    }

    // Check for feed rate increases
    List<ProcessStateSnapshot.Deviation> flowDeviations = diff.getDeviationsMatching("flowRate");
    boolean feedIncrease = false;
    for (ProcessStateSnapshot.Deviation dev : flowDeviations) {
      if (dev.getAbsoluteChange() > 0
          && dev.getSignificance() == ProcessStateSnapshot.DeviationSignificance.HIGH) {
        feedIncrease = true;
        evidence.add(String.format("Feed flow increased at %s", dev.getVariableAddress()));
        score += 0.15;
      }
    }

    // Check timeline for level alarms
    if (timeline != null) {
      List<UnifiedEventTimeline.TimelineEntry> alarms =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.ALARM);
      boolean hasHighLevelAlarm = false;
      for (UnifiedEventTimeline.TimelineEntry alarm : alarms) {
        String desc = alarm.getDescription().toLowerCase();
        if (desc.contains("level") && (desc.contains("hihi") || desc.contains("high"))) {
          hasHighLevelAlarm = true;
          evidence.add("High level alarm: " + alarm.getDescription());
          score += 0.25;
        }
      }

      // Check for valve actions on liquid outlet
      List<UnifiedEventTimeline.TimelineEntry> valveActions =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.VALVE_ACTION);
      for (UnifiedEventTimeline.TimelineEntry entry : valveActions) {
        String desc = entry.getDescription().toLowerCase();
        if (desc.contains("liquid") && desc.contains("outlet")) {
          evidence.add("Liquid outlet valve action: " + entry.getDescription());
          score += 0.10;
        }
      }
    }

    score = Math.min(1.0, score);

    String action = score >= 0.5
        ? "Check separator liquid outlet valve and level controller. "
            + "Verify liquid disposal capacity. Review feed composition for slug potential. "
            + "Consider increasing liquid dump rate or reducing feed rate before restart."
        : "Separator flooding unlikely based on available evidence.";

    return new HypothesisResult(getName(), score, evidence, action);
  }
}
