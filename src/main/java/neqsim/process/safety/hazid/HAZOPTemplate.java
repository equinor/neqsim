package neqsim.process.safety.hazid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hazard and Operability (HAZOP) study template — applies guide-words to process variables for a
 * single node and stores deviations, causes, consequences, safeguards and recommendations.
 *
 * <p>
 * Standard HAZOP guide-words: NO, MORE, LESS, REVERSE, AS_WELL_AS, PART_OF, OTHER_THAN.
 * Standard parameters: FLOW, PRESSURE, TEMPERATURE, LEVEL, COMPOSITION, REACTION.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>IEC 61882 — HAZOP studies application guide</li>
 * <li>CCPS — Guidelines for Hazard Evaluation Procedures, 3rd Ed.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class HAZOPTemplate implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Standard HAZOP guide-words. */
  public enum GuideWord {
    /** Negation of design intent. */
    NO,
    /** Quantitative increase. */
    MORE,
    /** Quantitative decrease. */
    LESS,
    /** Logical opposite. */
    REVERSE,
    /** Qualitative increase (extra contamination). */
    AS_WELL_AS,
    /** Qualitative decrease (only some component). */
    PART_OF,
    /** Complete substitution. */
    OTHER_THAN
  }

  /** Standard HAZOP process parameters. */
  public enum Parameter {
    /** Volumetric or mass flow. */
    FLOW,
    /** Pressure. */
    PRESSURE,
    /** Temperature. */
    TEMPERATURE,
    /** Level. */
    LEVEL,
    /** Composition. */
    COMPOSITION,
    /** Reaction (rate, completion). */
    REACTION
  }

  private final String nodeId;
  private final String designIntent;
  private final List<HAZOPDeviation> deviations = new ArrayList<>();

  /**
   * Construct a HAZOP node template.
   *
   * @param nodeId HAZOP node identifier (e.g. "Node-12: Inlet line to V-100")
   * @param designIntent narrative of the design intent for the node
   */
  public HAZOPTemplate(String nodeId, String designIntent) {
    this.nodeId = nodeId;
    this.designIntent = designIntent;
  }

  /**
   * Add a deviation row to the worksheet.
   *
   * @param guideWord guide-word
   * @param parameter parameter
   * @param cause hypothesised cause
   * @param consequence consequence if no protection acts
   * @param safeguard existing safeguard (instrumentation, procedural, mechanical)
   * @param recommendation new recommendation (or null)
   * @return this template for chaining
   */
  public HAZOPTemplate addDeviation(GuideWord guideWord, Parameter parameter, String cause,
      String consequence, String safeguard, String recommendation) {
    deviations.add(new HAZOPDeviation(guideWord, parameter, cause, consequence, safeguard,
        recommendation));
    return this;
  }

  /**
   * Generate the full default deviation grid (all guide-word × parameter combinations) with empty
   * causes/consequences for the user to fill in.
   *
   * @param parameters list of parameters to include
   * @return this template for chaining
   */
  public HAZOPTemplate generateGrid(Parameter... parameters) {
    for (Parameter p : parameters) {
      for (GuideWord g : GuideWord.values()) {
        deviations.add(new HAZOPDeviation(g, p, "TBD", "TBD", "TBD", null));
      }
    }
    return this;
  }

  /**
   * @return immutable view of stored deviations
   */
  public List<HAZOPDeviation> getDeviations() {
    return new ArrayList<>(deviations);
  }

  /**
   * @return the HAZOP node identifier
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   * @return the node design intent
   */
  public String getDesignIntent() {
    return designIntent;
  }

  /**
   * Build a multi-line text report of all deviations.
   *
   * @return human-readable HAZOP worksheet
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("HAZOP node: ").append(nodeId).append('\n');
    sb.append("Design intent: ").append(designIntent).append('\n');
    sb.append("---------------------------------------------------\n");
    int row = 1;
    for (HAZOPDeviation d : deviations) {
      sb.append(String.format("[%02d] %s + %s%n", row, d.guideWord, d.parameter));
      sb.append("     Cause       : ").append(d.cause).append('\n');
      sb.append("     Consequence : ").append(d.consequence).append('\n');
      sb.append("     Safeguard   : ").append(d.safeguard).append('\n');
      if (d.recommendation != null) {
        sb.append("     Recommend.  : ").append(d.recommendation).append('\n');
      }
      row++;
    }
    return sb.toString();
  }

  /**
   * One row of a HAZOP worksheet.
   */
  public static class HAZOPDeviation implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Guide-word. */
    public final GuideWord guideWord;
    /** Parameter. */
    public final Parameter parameter;
    /** Cause. */
    public final String cause;
    /** Consequence. */
    public final String consequence;
    /** Existing safeguard. */
    public final String safeguard;
    /** Recommendation (may be null). */
    public final String recommendation;

    /**
     * @param guideWord guide-word
     * @param parameter process parameter
     * @param cause cause description
     * @param consequence consequence description
     * @param safeguard existing safeguard
     * @param recommendation new recommendation (or null)
     */
    public HAZOPDeviation(GuideWord guideWord, Parameter parameter, String cause,
        String consequence, String safeguard, String recommendation) {
      this.guideWord = guideWord;
      this.parameter = parameter;
      this.cause = cause;
      this.consequence = consequence;
      this.safeguard = safeguard;
      this.recommendation = recommendation;
    }
  }

  /**
   * Convenience getter for all standard parameters.
   *
   * @return list of all standard HAZOP parameters
   */
  public static List<Parameter> standardParameters() {
    return Arrays.asList(Parameter.values());
  }
}
