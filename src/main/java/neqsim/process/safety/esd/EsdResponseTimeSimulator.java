package neqsim.process.safety.esd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic ESD response-time simulator per NOG 070 / IEC 61511.
 *
 * <p>
 * Builds an end-to-end emergency-shutdown response time from the standard contributions of a safety instrumented
 * function (SIF) loop:
 * <ul>
 * <li>process detection / sensor response delay</li>
 * <li>logic-solver scan and de-bounce / voting delay</li>
 * <li>final-element command and valve stroke (close) time, including any solenoid de-energise delay</li>
 * </ul>
 * The cumulative response time is compared against an allowable budget (e.g. the closure time required to keep the
 * downstream inventory and overpressure within limits, or a contractual NOG 070 ESD valve closure target).
 *
 * <p>
 * This is a budgeting / response-time-allocation tool. It does not replace certified SIS proof testing, FAT/SAT, or a
 * full dynamic SIS model.
 *
 * <p>
 * <b>References:</b> NOG 070 (Application of IEC 61508 and IEC 61511 in the Norwegian petroleum industry), IEC 61511-1
 * (process-industry SIS), API 521 §4 (isolation response time for relief-load mitigation).
 *
 * @author ESOL
 * @version 1.0
 */
public class EsdResponseTimeSimulator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** A single timed contribution to the ESD response time. */
  public static class Contribution implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final Stage stage;
    private final double timeS;

    /**
     * Create a response-time contribution.
     *
     * @param name descriptive name (e.g. "PT-1001 detection")
     * @param stage the ESD loop stage this contribution belongs to
     * @param timeS the time consumed by this contribution in s
     */
    public Contribution(String name, Stage stage, double timeS) {
      if (name == null || stage == null) {
	throw new IllegalArgumentException("name and stage must not be null");
      }
      if (timeS < 0.0) {
	throw new IllegalArgumentException("contribution time must be non-negative");
      }
      this.name = name;
      this.stage = stage;
      this.timeS = timeS;
    }

    /**
     * Contribution name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Loop stage.
     *
     * @return stage
     */
    public Stage getStage() {
      return stage;
    }

    /**
     * Time consumed in seconds.
     *
     * @return time in s
     */
    public double getTimeS() {
      return timeS;
    }
  }

  /** ESD loop stages, in execution order. */
  public enum Stage {
    /** Process sensing / transmitter response. */
    DETECTION,
    /** Logic solver scan, voting and de-bounce. */
    LOGIC,
    /** Final-element command, solenoid de-energise and valve stroke. */
    FINAL_ELEMENT
  }

  private final List<Contribution> contributions = new ArrayList<Contribution>();
  private double allowableResponseTimeS = Double.NaN;
  private String sifTag = "ESD-SIF";

  /**
   * Set the SIF tag/identifier for reporting.
   *
   * @param tag the SIF identifier
   * @return this simulator for chaining
   */
  public EsdResponseTimeSimulator setSifTag(String tag) {
    if (tag != null) {
      this.sifTag = tag;
    }
    return this;
  }

  /**
   * Add a detection (sensor/transmitter) delay contribution.
   *
   * @param name descriptive name
   * @param timeS detection delay in s
   * @return this simulator for chaining
   */
  public EsdResponseTimeSimulator addDetection(String name, double timeS) {
    contributions.add(new Contribution(name, Stage.DETECTION, timeS));
    return this;
  }

  /**
   * Add a logic-solver delay contribution (scan time, voting, de-bounce).
   *
   * @param name descriptive name
   * @param timeS logic delay in s
   * @return this simulator for chaining
   */
  public EsdResponseTimeSimulator addLogic(String name, double timeS) {
    contributions.add(new Contribution(name, Stage.LOGIC, timeS));
    return this;
  }

  /**
   * Add a final-element contribution (solenoid de-energise plus valve stroke/close time).
   *
   * @param name descriptive name (e.g. valve tag)
   * @param solenoidDelayS solenoid de-energise delay in s
   * @param valveStrokeS valve close/stroke time in s
   * @return this simulator for chaining
   */
  public EsdResponseTimeSimulator addValve(String name, double solenoidDelayS, double valveStrokeS) {
    contributions.add(new Contribution(name, Stage.FINAL_ELEMENT, solenoidDelayS + valveStrokeS));
    return this;
  }

  /**
   * Add an arbitrary contribution.
   *
   * @param contribution the contribution to add
   * @return this simulator for chaining
   */
  public EsdResponseTimeSimulator add(Contribution contribution) {
    if (contribution == null) {
      throw new IllegalArgumentException("contribution must not be null");
    }
    contributions.add(contribution);
    return this;
  }

  /**
   * Set the allowable end-to-end response-time budget (e.g. from NOG 070 ESD valve closure target or API 521 isolation
   * requirement).
   *
   * @param timeS allowable response time in s
   * @return this simulator for chaining
   */
  public EsdResponseTimeSimulator setAllowableResponseTimeS(double timeS) {
    this.allowableResponseTimeS = timeS;
    return this;
  }

  /**
   * Evaluate the cumulative response time and acceptance against the budget.
   *
   * @return the response-time result
   */
  public EsdResponseTimeResult evaluate() {
    if (contributions.isEmpty()) {
      throw new IllegalStateException("no response-time contributions added");
    }
    double detection = stageTotal(Stage.DETECTION);
    double logic = stageTotal(Stage.LOGIC);
    double finalElement = stageTotal(Stage.FINAL_ELEMENT);
    double total = detection + logic + finalElement;
    boolean withinBudget = Double.isNaN(allowableResponseTimeS) || total <= allowableResponseTimeS;
    double margin = Double.isNaN(allowableResponseTimeS) ? Double.NaN : allowableResponseTimeS - total;
    return new EsdResponseTimeResult(sifTag, new ArrayList<Contribution>(contributions), detection, logic, finalElement,
	total, allowableResponseTimeS, margin, withinBudget);
  }

  /**
   * Sum the contributions belonging to a stage.
   *
   * @param stage the stage to total
   * @return total time for the stage in s
   */
  private double stageTotal(Stage stage) {
    double sum = 0.0;
    for (Contribution c : contributions) {
      if (c.getStage() == stage) {
	sum += c.getTimeS();
      }
    }
    return sum;
  }

  /**
   * Immutable result of an ESD response-time evaluation.
   */
  public static class EsdResponseTimeResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sifTag;
    private final List<Contribution> contributions;
    private final double detectionTimeS;
    private final double logicTimeS;
    private final double finalElementTimeS;
    private final double totalResponseTimeS;
    private final double allowableResponseTimeS;
    private final double marginS;
    private final boolean withinBudget;

    EsdResponseTimeResult(String sifTag, List<Contribution> contributions, double detectionTimeS, double logicTimeS,
	double finalElementTimeS, double totalResponseTimeS, double allowableResponseTimeS, double marginS,
	boolean withinBudget) {
      this.sifTag = sifTag;
      this.contributions = contributions;
      this.detectionTimeS = detectionTimeS;
      this.logicTimeS = logicTimeS;
      this.finalElementTimeS = finalElementTimeS;
      this.totalResponseTimeS = totalResponseTimeS;
      this.allowableResponseTimeS = allowableResponseTimeS;
      this.marginS = marginS;
      this.withinBudget = withinBudget;
    }

    /**
     * SIF tag/identifier.
     *
     * @return SIF tag
     */
    public String getSifTag() {
      return sifTag;
    }

    /**
     * The list of contributions.
     *
     * @return contributions
     */
    public List<Contribution> getContributions() {
      return contributions;
    }

    /**
     * Total detection-stage time in s.
     *
     * @return detection time
     */
    public double getDetectionTimeS() {
      return detectionTimeS;
    }

    /**
     * Total logic-stage time in s.
     *
     * @return logic time
     */
    public double getLogicTimeS() {
      return logicTimeS;
    }

    /**
     * Total final-element-stage time in s.
     *
     * @return final-element time
     */
    public double getFinalElementTimeS() {
      return finalElementTimeS;
    }

    /**
     * End-to-end response time in s.
     *
     * @return total response time
     */
    public double getTotalResponseTimeS() {
      return totalResponseTimeS;
    }

    /**
     * Allowable response-time budget in s (NaN if not set).
     *
     * @return allowable response time
     */
    public double getAllowableResponseTimeS() {
      return allowableResponseTimeS;
    }

    /**
     * Margin to the budget in s (allowable minus total; NaN if no budget set).
     *
     * @return margin in s
     */
    public double getMarginS() {
      return marginS;
    }

    /**
     * Whether the total response time is within the allowable budget.
     *
     * @return true if within budget or no budget set
     */
    public boolean isWithinBudget() {
      return withinBudget;
    }

    /**
     * Build a brief human-readable summary.
     *
     * @return summary string
     */
    public String summary() {
      StringBuilder sb = new StringBuilder();
      sb.append("ESD response time (NOG 070 / IEC 61511) - ").append(sifTag).append(":\n");
      sb.append(String.format("  Detection      : %.2f s%n", detectionTimeS));
      sb.append(String.format("  Logic solver   : %.2f s%n", logicTimeS));
      sb.append(String.format("  Final element  : %.2f s%n", finalElementTimeS));
      sb.append(String.format("  TOTAL          : %.2f s%n", totalResponseTimeS));
      if (!Double.isNaN(allowableResponseTimeS)) {
	sb.append(String.format("  Allowable      : %.2f s (margin %.2f s) -> %s%n", allowableResponseTimeS, marginS,
	    withinBudget ? "OK" : "EXCEEDED"));
      }
      return sb.toString();
    }
  }
}
