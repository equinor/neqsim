package neqsim.process.safety.hazid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Failure Mode and Effects Analysis (FMEA) worksheet with Risk Priority Number (RPN) scoring.
 *
 * <p>
 * For each component / failure mode, capture:
 * <ul>
 * <li>S — Severity (1..10)</li>
 * <li>O — Occurrence likelihood (1..10)</li>
 * <li>D — Detection difficulty (1..10)</li>
 * </ul>
 * RPN = S × O × D. Higher RPN means higher priority for mitigation.
 *
 * <p>
 * <b>Reference:</b> IEC 60812 — Failure modes and effects analysis (FMEA and FMECA).
 *
 * @author ESOL
 * @version 1.0
 */
public class FMEAWorksheet implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String studyName;
  private final List<FMEAEntry> entries = new ArrayList<>();

  /**
   * Construct an FMEA worksheet.
   *
   * @param studyName name of the FMEA study (e.g. "Compressor train K-100 FMEA")
   */
  public FMEAWorksheet(String studyName) {
    this.studyName = studyName;
  }

  /**
   * Add a failure-mode entry.
   *
   * @param component equipment / item being analysed
   * @param failureMode failure mode description
   * @param effect effect on system
   * @param cause root cause
   * @param severity 1..10
   * @param occurrence 1..10
   * @param detection 1..10
   * @param mitigation recommended mitigation (may be null)
   * @return this worksheet for chaining
   */
  public FMEAWorksheet addEntry(String component, String failureMode, String effect,
      String cause, int severity, int occurrence, int detection, String mitigation) {
    if (severity < 1 || severity > 10 || occurrence < 1 || occurrence > 10
        || detection < 1 || detection > 10) {
      throw new IllegalArgumentException("S, O, D must each be in [1, 10]");
    }
    entries.add(new FMEAEntry(component, failureMode, effect, cause, severity, occurrence,
        detection, mitigation));
    return this;
  }

  /**
   * @return all entries sorted by RPN descending
   */
  public List<FMEAEntry> sortedByRPN() {
    List<FMEAEntry> copy = new ArrayList<>(entries);
    Collections.sort(copy, new Comparator<FMEAEntry>() {
      @Override
      public int compare(FMEAEntry a, FMEAEntry b) {
        return Integer.compare(b.rpn(), a.rpn());
      }
    });
    return copy;
  }

  /**
   * @return all entries with RPN above a threshold (e.g. 100, 200)
   * @param rpnThreshold RPN minimum
   */
  public List<FMEAEntry> entriesAboveRPN(int rpnThreshold) {
    List<FMEAEntry> out = new ArrayList<>();
    for (FMEAEntry e : entries) {
      if (e.rpn() >= rpnThreshold) {
        out.add(e);
      }
    }
    return out;
  }

  /**
   * Build a text report sorted by RPN descending.
   *
   * @return human-readable worksheet
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("FMEA: ").append(studyName).append('\n');
    sb.append("---------------------------------------------------\n");
    for (FMEAEntry e : sortedByRPN()) {
      sb.append(String.format("[RPN %4d]  %s — %s%n", e.rpn(), e.component, e.failureMode));
      sb.append("    Cause       : ").append(e.cause).append('\n');
      sb.append("    Effect      : ").append(e.effect).append('\n');
      sb.append(String.format("    S=%d O=%d D=%d%n", e.severity, e.occurrence, e.detection));
      if (e.mitigation != null) {
        sb.append("    Mitigation  : ").append(e.mitigation).append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * One row of an FMEA worksheet.
   */
  public static class FMEAEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Component / item analysed. */
    public final String component;
    /** Failure mode description. */
    public final String failureMode;
    /** Effect on system. */
    public final String effect;
    /** Root cause. */
    public final String cause;
    /** Severity score 1..10. */
    public final int severity;
    /** Occurrence score 1..10. */
    public final int occurrence;
    /** Detection difficulty score 1..10. */
    public final int detection;
    /** Recommended mitigation. */
    public final String mitigation;

    /**
     * @param component component
     * @param failureMode failure mode
     * @param effect effect description
     * @param cause root cause
     * @param severity severity score 1-10
     * @param occurrence occurrence score 1-10
     * @param detection detection score 1-10
     * @param mitigation mitigation
     */
    public FMEAEntry(String component, String failureMode, String effect, String cause,
        int severity, int occurrence, int detection, String mitigation) {
      this.component = component;
      this.failureMode = failureMode;
      this.effect = effect;
      this.cause = cause;
      this.severity = severity;
      this.occurrence = occurrence;
      this.detection = detection;
      this.mitigation = mitigation;
    }

    /**
     * Risk Priority Number = S · O · D.
     *
     * @return RPN
     */
    public int rpn() {
      return severity * occurrence * detection;
    }
  }
}
