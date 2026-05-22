package neqsim.process.safety.inherent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Inherent Safety Evaluator using the four pillars (Kletz 1978):
 * Substitute, Minimize, Moderate, Simplify.
 *
 * <p>
 * Each pillar accepts a 0–10 score; the overall index is the simple average. Recommendations are
 * collected per pillar and surfaced in {@link #report()}.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>Kletz T.A. (1978) — What you don't have, can't leak. Chem. Ind., 287–292</li>
 * <li>CCPS — Inherently Safer Chemical Processes: A Life Cycle Approach, 2nd Ed.</li>
 * <li>UK HSE — Inherently safer process design</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class InherentSafetyEvaluator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** The four inherent-safety pillars. */
  public enum Pillar {
    /** Substitute hazardous materials with less hazardous ones. */
    SUBSTITUTE,
    /** Minimize inventory of hazardous material. */
    MINIMIZE,
    /** Moderate process conditions (lower P, T, dilution). */
    MODERATE,
    /** Simplify equipment, layout and procedures. */
    SIMPLIFY
  }

  private final String designName;
  private final double[] scores = new double[4];
  private final List<String>[] notes;

  /**
   * Construct an inherent-safety evaluator for a design / option.
   *
   * @param designName design or option name
   */
  @SuppressWarnings("unchecked")
  public InherentSafetyEvaluator(String designName) {
    this.designName = designName;
    this.notes = new ArrayList[4];
    for (int i = 0; i < 4; i++) {
      this.notes[i] = new ArrayList<String>();
    }
  }

  /**
   * Score one pillar 0..10 (10 = best inherent-safety performance).
   *
   * @param pillar pillar to score
   * @param score score in [0, 10]
   * @param note rationale text
   * @return this evaluator for chaining
   */
  public InherentSafetyEvaluator score(Pillar pillar, double score, String note) {
    if (score < 0.0 || score > 10.0) {
      throw new IllegalArgumentException("score must be in [0, 10]");
    }
    scores[pillar.ordinal()] = score;
    if (note != null) {
      notes[pillar.ordinal()].add(note);
    }
    return this;
  }

  /**
   * @param pillar pillar
   * @return current score for the pillar
   */
  public double getScore(Pillar pillar) {
    return scores[pillar.ordinal()];
  }

  /**
   * Overall inherent-safety index (average of the four scores).
   *
   * @return index in [0, 10]
   */
  public double overallIndex() {
    double s = 0.0;
    for (double v : scores) {
      s += v;
    }
    return s / 4.0;
  }

  /**
   * Build a multi-line text report.
   *
   * @return human-readable report
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("Inherent Safety: ").append(designName).append('\n');
    sb.append("---------------------------------------------------\n");
    for (Pillar p : Pillar.values()) {
      sb.append(String.format("  %-10s : %4.1f / 10%n", p.name(), getScore(p)));
      for (String n : notes[p.ordinal()]) {
        sb.append("       - ").append(n).append('\n');
      }
    }
    sb.append(String.format("  OVERALL    : %4.1f / 10%n", overallIndex()));
    return sb.toString();
  }
}
