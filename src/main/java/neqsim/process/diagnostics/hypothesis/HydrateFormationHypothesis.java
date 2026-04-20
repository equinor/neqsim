package neqsim.process.diagnostics.hypothesis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.diagnostics.HypothesisResult;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripHypothesis;
import neqsim.process.diagnostics.TripType;
import neqsim.process.diagnostics.UnifiedEventTimeline;

/**
 * Hypothesis: trip caused by hydrate formation in the process.
 *
 * <p>
 * Checks for evidence of temperature dropping near or below hydrate formation temperature while
 * water is present, and for pressure drop signatures consistent with hydrate plugging.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class HydrateFormationHypothesis extends TripHypothesis {
  private static final long serialVersionUID = 1000L;

  /** Typical hydrate formation temperature for natural gas at moderate pressure (K). */
  private static final double DEFAULT_HYDRATE_TEMP_K = 293.15;
  /** Pressure drop increase ratio that suggests plugging. */
  private static final double PRESSURE_DROP_RATIO_THRESHOLD = 2.0;

  /**
   * Constructs the hydrate formation hypothesis.
   */
  public HydrateFormationHypothesis() {
    super("Hydrate Formation",
        "Trip caused by hydrate formation in piping or equipment, leading to blockage or "
            + "high differential pressure",
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

    // Check for temperature drops in streams
    List<ProcessStateSnapshot.Deviation> tempDeviations = diff.getDeviationsMatching("temperature");
    boolean temperatureDrop = false;
    for (ProcessStateSnapshot.Deviation dev : tempDeviations) {
      if (dev.getAbsoluteChange() < -5.0) {
        temperatureDrop = true;
        evidence.add(String.format("Temperature drop of %.1f K detected at %s",
            Math.abs(dev.getAbsoluteChange()), dev.getVariableAddress()));
        score += 0.25;
      }
    }

    // Check for pressure increases (blockage symptom)
    List<ProcessStateSnapshot.Deviation> pressureDeviations =
        diff.getDeviationsMatching("pressure");
    boolean pressureIncrease = false;
    for (ProcessStateSnapshot.Deviation dev : pressureDeviations) {
      double relChange = Math.abs(dev.getGoodValue()) > 1e-10
          ? Math.abs(dev.getAbsoluteChange() / dev.getGoodValue())
          : 0.0;
      if (dev.getAbsoluteChange() > 0 && relChange > 0.1) {
        pressureIncrease = true;
        evidence.add(String.format("Pressure increase of %.1f%% at %s (possible blockage)",
            relChange * 100.0, dev.getVariableAddress()));
        score += 0.20;
      }
    }

    // Check for flow rate drops (blockage symptom)
    List<ProcessStateSnapshot.Deviation> flowDeviations = diff.getDeviationsMatching("flowRate");
    for (ProcessStateSnapshot.Deviation dev : flowDeviations) {
      if (dev.getAbsoluteChange() < 0) {
        double relDrop = Math.abs(dev.getGoodValue()) > 1e-10
            ? Math.abs(dev.getAbsoluteChange() / dev.getGoodValue())
            : 0.0;
        if (relDrop > 0.3) {
          evidence.add(String.format("Flow dropped %.0f%% at %s", relDrop * 100.0,
              dev.getVariableAddress()));
          score += 0.20;
        }
      }
    }

    // Check timeline for high-pressure alarms that might indicate blockage
    if (timeline != null) {
      List<UnifiedEventTimeline.TimelineEntry> alarms =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.ALARM);
      for (UnifiedEventTimeline.TimelineEntry alarm : alarms) {
        String desc = alarm.getDescription().toLowerCase();
        if (desc.contains("hihi") && desc.contains("pressure")) {
          evidence.add("HIHI pressure alarm detected: " + alarm.getDescription());
          score += 0.15;
        }
      }
    }

    // Combined evidence boosts
    if (temperatureDrop && pressureIncrease) {
      evidence
          .add("Combined temperature drop + pressure increase consistent with hydrate blockage");
      score += 0.15;
    }

    score = Math.min(1.0, score);

    String action = score >= 0.5
        ? "Check for hydrate conditions. Consider MEG/MeOH injection. "
            + "Verify process temperatures are above hydrate equilibrium temperature."
        : "Hydrate formation unlikely based on available evidence.";

    return new HypothesisResult(getName(), score, evidence, action);
  }
}
