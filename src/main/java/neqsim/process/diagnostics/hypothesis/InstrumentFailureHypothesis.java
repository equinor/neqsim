package neqsim.process.diagnostics.hypothesis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.diagnostics.HypothesisResult;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripHypothesis;
import neqsim.process.diagnostics.TripType;
import neqsim.process.diagnostics.UnifiedEventTimeline;

/**
 * Hypothesis: trip caused by instrument failure or spurious signal.
 *
 * <p>
 * Checks for evidence that a measurement device produced a sudden step change or out-of-range
 * reading that triggered a false trip, while the actual process conditions remained normal.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class InstrumentFailureHypothesis extends TripHypothesis {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructs the instrument failure hypothesis.
   */
  public InstrumentFailureHypothesis() {
    super("Instrument Failure",
        "Trip caused by a malfunctioning measurement device producing a spurious signal, "
            + "rather than an actual process upset",
        TripType.INSTRUMENT_FAILURE);
  }

  @Override
  public HypothesisResult evaluate(ProcessStateSnapshot snapshot, UnifiedEventTimeline timeline) {
    List<String> evidence = new ArrayList<>();
    double score = 0.0;

    if (snapshot == null) {
      return new HypothesisResult(getName(), 0.0, evidence, "No snapshot available for analysis");
    }

    ProcessStateSnapshot.StateDiff diff = snapshot.diff();

    // Instrument failure signature: one sensor shows large deviation while
    // correlated variables remain normal
    List<ProcessStateSnapshot.Deviation> highDeviations =
        diff.getDeviationsBySignificance(ProcessStateSnapshot.DeviationSignificance.HIGH);
    List<ProcessStateSnapshot.Deviation> allDeviations = diff.getDeviations();

    if (highDeviations.size() == 1 && allDeviations.size() < 5) {
      // Only one variable changed significantly, rest normal — suspicious
      ProcessStateSnapshot.Deviation singleDev = highDeviations.get(0);
      evidence.add(String.format(
          "Only one variable (%s) deviated significantly while rest of process appears normal",
          singleDev.getVariableAddress()));
      score += 0.35;
    }

    // Check for step changes (instantaneous jumps rather than gradual drift)
    for (ProcessStateSnapshot.Deviation dev : highDeviations) {
      double relChange = Math.abs(dev.getGoodValue()) > 1e-10
          ? Math.abs(dev.getAbsoluteChange() / dev.getGoodValue())
          : Math.abs(dev.getAbsoluteChange());
      if (relChange > 0.5) {
        evidence.add(String.format(
            "Very large deviation at %s (%.0f%%) — may be instrument failure rather than process",
            dev.getVariableAddress(), relChange * 100.0));
        score += 0.20;
      }
    }

    // Check timeline for patterns suggesting instrument issues
    if (timeline != null) {
      // Multiple alarms on same instrument in rapid succession suggest bouncing
      List<UnifiedEventTimeline.TimelineEntry> alarms =
          timeline.getEventsByType(UnifiedEventTimeline.EntryType.ALARM);

      // Group alarms by equipment and check for rapid succession
      for (int i = 0; i < alarms.size(); i++) {
        int rapidCount = 0;
        for (int j = i + 1; j < alarms.size(); j++) {
          if (alarms.get(j).getEquipmentName().equals(alarms.get(i).getEquipmentName())
              && (alarms.get(j).getTimestamp() - alarms.get(i).getTimestamp()) < 5.0) {
            rapidCount++;
          }
        }
        if (rapidCount >= 2) {
          evidence.add(String.format("Rapid alarm cycling on %s — possible instrument bounce",
              alarms.get(i).getEquipmentName()));
          score += 0.25;
          break;
        }
      }
    }

    // If many variables deviated significantly, it's probably a real process upset
    if (highDeviations.size() > 3) {
      evidence.add("Multiple variables deviated significantly — suggests real process upset");
      score = Math.max(0.0, score - 0.30);
    }

    score = Math.min(1.0, Math.max(0.0, score));

    String action = score >= 0.5
        ? "Check instrument calibration and signal integrity. "
            + "Verify measurement against redundant instruments or field checks. "
            + "Bypass faulty instrument and restart on backup if available."
        : "Instrument failure unlikely based on available evidence.";

    return new HypothesisResult(getName(), score, evidence, action);
  }
}
