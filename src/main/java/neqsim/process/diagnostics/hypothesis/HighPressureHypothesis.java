package neqsim.process.diagnostics.hypothesis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.diagnostics.HypothesisResult;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripHypothesis;
import neqsim.process.diagnostics.TripType;
import neqsim.process.diagnostics.UnifiedEventTimeline;

/**
 * Hypothesis: trip caused by excessive process pressure.
 *
 * <p>
 * Checks for evidence of pressure rise above design limits, blocked outlets, and pressure safety
 * device activations.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class HighPressureHypothesis extends TripHypothesis {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructs the high pressure hypothesis.
   */
  public HighPressureHypothesis() {
    super("High Pressure",
        "Trip caused by process pressure exceeding safety limits, triggering PSV/PSD "
            + "activation or high-pressure shutdown",
        TripType.HIGH_PRESSURE);
  }

  @Override
  public HypothesisResult evaluate(ProcessStateSnapshot snapshot, UnifiedEventTimeline timeline) {
    List<String> evidence = new ArrayList<>();
    double score = 0.0;

    if (snapshot == null) {
      return new HypothesisResult(getName(), 0.0, evidence, "No snapshot available for analysis");
    }

    ProcessStateSnapshot.StateDiff diff = snapshot.diff();

    // Check for pressure increases
    List<ProcessStateSnapshot.Deviation> pressureDeviations =
        diff.getDeviationsMatching("pressure");
    for (ProcessStateSnapshot.Deviation dev : pressureDeviations) {
      if (dev.getAbsoluteChange() > 0) {
        double relIncrease =
            Math.abs(dev.getGoodValue()) > 1e-10 ? dev.getAbsoluteChange() / dev.getGoodValue()
                : 0.0;
        if (relIncrease > 0.05) {
          evidence.add(String.format("Pressure increased %.1f%% at %s (from %.2f to %.2f)",
              relIncrease * 100.0, dev.getVariableAddress(), dev.getGoodValue(),
              dev.getTripValue()));
          score += relIncrease > 0.15 ? 0.30 : 0.20;
        }
      }
    }

    // Check for outlet valve closing (blocked outlet)
    List<ProcessStateSnapshot.Deviation> valveDeviations =
        diff.getDeviationsMatching("percentValveOpening");
    for (ProcessStateSnapshot.Deviation dev : valveDeviations) {
      if (dev.getAbsoluteChange() < -20.0) {
        evidence.add(String.format("Valve closing at %s (opened from %.0f%% to %.0f%%)",
            dev.getVariableAddress(), dev.getGoodValue(), dev.getTripValue()));
        score += 0.20;
      }
    }

    // Check timeline for pressure alarms and PSD trips
    if (timeline != null) {
      List<UnifiedEventTimeline.TimelineEntry> alarms =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.ALARM);
      for (UnifiedEventTimeline.TimelineEntry alarm : alarms) {
        String desc = alarm.getDescription().toLowerCase();
        if (desc.contains("pressure") && (desc.contains("hihi") || desc.contains("high"))) {
          evidence.add("High pressure alarm: " + alarm.getDescription());
          score += 0.20;
        }
      }

      List<UnifiedEventTimeline.TimelineEntry> trips =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.TRIP);
      for (UnifiedEventTimeline.TimelineEntry trip : trips) {
        String desc = trip.getDescription().toLowerCase();
        if (desc.contains("psd") || desc.contains("psv") || desc.contains("pressure")) {
          evidence.add("Pressure safety trip: " + trip.getDescription());
          score += 0.20;
        }
      }

      // Check for valve closure events (blocked outlet cause)
      List<UnifiedEventTimeline.TimelineEntry> valveActions =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.VALVE_ACTION);
      for (UnifiedEventTimeline.TimelineEntry entry : valveActions) {
        String desc = entry.getDescription().toLowerCase();
        if (desc.contains("closed") || desc.contains("closing")) {
          evidence.add("Downstream valve closure: " + entry.getDescription());
          score += 0.10;
        }
      }
    }

    score = Math.min(1.0, score);

    String action = score >= 0.5
        ? "Identify and relieve the source of overpressure. Check for blocked outlets, "
            + "closed valves, or upstream pressure build-up. Verify PSV has reseated. "
            + "Confirm pressure is below setpoint before restart."
        : "High pressure unlikely as root cause based on available evidence.";

    return new HypothesisResult(getName(), score, evidence, action);
  }
}
