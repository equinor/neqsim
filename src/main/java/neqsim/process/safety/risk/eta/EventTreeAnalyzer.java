package neqsim.process.safety.risk.eta;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Event Tree Analysis (ETA) — quantitative outcome-frequency calculator.
 *
 * <p>
 * An event tree starts from an initiating event and branches at each safety barrier or pivotal
 * event into a "success" path and a "failure" path with given probabilities. Each leaf
 * (consequence) frequency = initiating frequency × ∏ branch probabilities.
 *
 * <p>
 * Typical use: ignition probability tree (immediate, delayed, no-ignition), barrier failure tree
 * (ESD success/fail × blowdown success/fail × deluge success/fail).
 *
 * <p>
 * <b>Reference:</b> ISO 31010 §B.18; CCPS — Guidelines for Hazard Evaluation Procedures, 3rd Ed.
 *
 * @author ESOL
 * @version 1.0
 */
public class EventTreeAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String initiatingEventName;
  private final double initiatingFrequencyPerYear;
  private final List<Branch> branches = new ArrayList<>();

  /**
   * Construct an event tree.
   *
   * @param initiatingEventName description of the initiating event
   * @param initiatingFrequencyPerYear frequency of the initiating event in 1/year
   */
  public EventTreeAnalyzer(String initiatingEventName, double initiatingFrequencyPerYear) {
    if (initiatingFrequencyPerYear < 0.0) {
      throw new IllegalArgumentException("frequency must be non-negative");
    }
    this.initiatingEventName = initiatingEventName;
    this.initiatingFrequencyPerYear = initiatingFrequencyPerYear;
  }

  /**
   * Add a pivotal-event branch (barrier, decision). Branches are evaluated in the order added.
   *
   * @param name pivotal event name (e.g. "Immediate ignition", "ESD success")
   * @param probabilityOfYes probability that the pivotal event occurs (true branch)
   * @return this analyzer for chaining
   */
  public EventTreeAnalyzer addBranch(String name, double probabilityOfYes) {
    if (probabilityOfYes < 0.0 || probabilityOfYes > 1.0) {
      throw new IllegalArgumentException("probability must be in [0,1]");
    }
    branches.add(new Branch(name, probabilityOfYes));
    return this;
  }

  /**
   * Compute all 2^n end-state outcomes with their frequencies.
   *
   * @return list of outcomes (each carrying the path string, probability and frequency)
   */
  public List<Outcome> evaluate() {
    List<Outcome> outcomes = new ArrayList<>();
    int n = branches.size();
    int total = 1 << n;
    for (int i = 0; i < total; i++) {
      double prob = 1.0;
      StringBuilder path = new StringBuilder();
      for (int b = 0; b < n; b++) {
        boolean yes = ((i >> b) & 1) == 1;
        Branch br = branches.get(b);
        prob *= yes ? br.probabilityOfYes : (1.0 - br.probabilityOfYes);
        path.append(br.name).append("=").append(yes ? "Y" : "N");
        if (b < n - 1) {
          path.append("; ");
        }
      }
      outcomes.add(
          new Outcome(path.toString(), prob, prob * initiatingFrequencyPerYear));
    }
    return outcomes;
  }

  /**
   * Get a brief multi-line text report.
   *
   * @return human-readable report
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("Event Tree: ").append(initiatingEventName).append("  (")
        .append(initiatingFrequencyPerYear).append(" /yr)\n");
    for (Outcome o : evaluate()) {
      sb.append(String.format("  P=%.4e  F=%.4e /yr  | %s%n",
          o.probability, o.frequencyPerYear, o.path));
    }
    return sb.toString();
  }

  /** Pivotal-event branch container. */
  private static class Branch implements Serializable {
    private static final long serialVersionUID = 1L;
    final String name;
    final double probabilityOfYes;

    Branch(String name, double pYes) {
      this.name = name;
      this.probabilityOfYes = pYes;
    }
  }

  /**
   * One end-state outcome of the event tree.
   */
  public static class Outcome implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Branch path string (e.g. "Ignition=Y; ESD=N"). */
    public final String path;
    /** Joint probability of this path. */
    public final double probability;
    /** Outcome frequency in 1/year. */
    public final double frequencyPerYear;

    Outcome(String path, double probability, double frequencyPerYear) {
      this.path = path;
      this.probability = probability;
      this.frequencyPerYear = frequencyPerYear;
    }
  }
}
