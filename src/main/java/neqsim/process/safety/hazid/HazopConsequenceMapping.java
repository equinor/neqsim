package neqsim.process.safety.hazid;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.hazid.HAZOPTemplate.GuideWord;
import neqsim.process.safety.hazid.HAZOPTemplate.Parameter;

/**
 * Immutable mapping of a single HAZOP guide-word/parameter deviation to the NeqSim calculation that quantifies its
 * consequence, the applicable industry standard, and a typical safeguard.
 *
 * <p>
 * The mapping encodes engineering knowledge used by {@link HazopConsequenceAutoPopulator} to seed a HAZOP worksheet
 * with simulation-backed consequence descriptions instead of empty "TBD" placeholders. Each instance links one
 * {@link GuideWord}/{@link Parameter} combination to:
 * <ul>
 * <li>the physical consequence mechanism (what physically happens),</li>
 * <li>the recommended NeqSim calculator class that screens or sizes the scenario,</li>
 * <li>the governing standard reference, and</li>
 * <li>a typical engineered safeguard.</li>
 * </ul>
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
public final class HazopConsequenceMapping implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Guide-word the mapping applies to. */
  private final GuideWord guideWord;
  /** Process parameter the mapping applies to. */
  private final Parameter parameter;
  /** Physical consequence mechanism description. */
  private final String consequenceMechanism;
  /** Recommended NeqSim calculator (class or facade name) that quantifies the scenario. */
  private final String recommendedCalculator;
  /** Governing industry standard reference. */
  private final String standardReference;
  /** Typical engineered safeguard for the deviation. */
  private final String typicalSafeguard;

  /**
   * Construct an immutable consequence mapping.
   *
   * @param guideWord HAZOP guide-word (must not be null)
   * @param parameter HAZOP process parameter (must not be null)
   * @param consequenceMechanism physical consequence mechanism description (must not be null)
   * @param recommendedCalculator recommended NeqSim calculator name (must not be null)
   * @param standardReference governing industry standard reference (must not be null)
   * @param typicalSafeguard typical engineered safeguard (must not be null)
   */
  public HazopConsequenceMapping(GuideWord guideWord, Parameter parameter, String consequenceMechanism,
      String recommendedCalculator, String standardReference, String typicalSafeguard) {
    this.guideWord = guideWord;
    this.parameter = parameter;
    this.consequenceMechanism = consequenceMechanism;
    this.recommendedCalculator = recommendedCalculator;
    this.standardReference = standardReference;
    this.typicalSafeguard = typicalSafeguard;
  }

  /**
   * Get the guide-word the mapping applies to.
   *
   * @return the guide-word
   */
  public GuideWord getGuideWord() {
    return guideWord;
  }

  /**
   * Get the process parameter the mapping applies to.
   *
   * @return the parameter
   */
  public Parameter getParameter() {
    return parameter;
  }

  /**
   * Get the physical consequence mechanism description.
   *
   * @return the consequence mechanism
   */
  public String getConsequenceMechanism() {
    return consequenceMechanism;
  }

  /**
   * Get the recommended NeqSim calculator name that quantifies the scenario.
   *
   * @return the recommended calculator name
   */
  public String getRecommendedCalculator() {
    return recommendedCalculator;
  }

  /**
   * Get the governing industry standard reference.
   *
   * @return the standard reference
   */
  public String getStandardReference() {
    return standardReference;
  }

  /**
   * Get the typical engineered safeguard for the deviation.
   *
   * @return the typical safeguard
   */
  public String getTypicalSafeguard() {
    return typicalSafeguard;
  }

  /**
   * Serialise this mapping to a pretty-printed JSON object.
   *
   * @return JSON representation of this mapping
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
