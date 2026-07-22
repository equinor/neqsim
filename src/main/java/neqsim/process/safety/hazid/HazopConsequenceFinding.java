package neqsim.process.safety.hazid;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.hazid.HAZOPTemplate.GuideWord;
import neqsim.process.safety.hazid.HAZOPTemplate.Parameter;

/**
 * A single quantified HAZOP consequence finding produced by
 * {@link HazopConsequenceAutoPopulator#quantify(neqsim.process.processmodel.ProcessSystem, HazopQuantificationLimits)}.
 *
 * <p>
 * Where the static catalogue in {@link HazopConsequenceAutoPopulator} only states <em>which</em> NeqSim calculator
 * applies to a guide-word/parameter deviation, a finding carries the <em>computed number</em> from running that
 * calculation against the live flowsheet state, the design limit it was compared against, and a deterministic
 * {@link Verdict}. This is the part of the HAZOP that an annotation- or rule-based P&amp;ID checker cannot produce on
 * its own — the consequence value comes from the thermodynamic engine (for example, a compressor discharge temperature
 * from polytropic compression plus a flash, or a valve outlet temperature from an isenthalpic Joule-Thomson flash).
 * </p>
 *
 * <p>
 * Findings are immutable. They are intended as screening evidence for a HAZOP facilitator and a competent review team,
 * not as a finished HAZOP record.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HazopConsequenceFinding implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Deterministic verdict for a quantified deviation.
   */
  public enum Verdict {
    /** The computed consequence stays within the supplied design limit. */
    PASS,
    /** The computed consequence breaches the supplied design limit. */
    EXCEEDS,
    /** The consequence could not be quantified from the available flowsheet state. */
    NOT_EVALUATED
  }

  private final String nodeId;
  private final String unitName;
  private final GuideWord guideWord;
  private final Parameter parameter;
  private final double computedValue;
  private final double designLimit;
  private final String valueUnit;
  private final Verdict verdict;
  private final String calculator;
  private final String standardReference;
  private final String message;
  private final String limitBasis;

  /**
   * Construct a quantified HAZOP consequence finding without an explicit limit basis. The limit basis defaults to
   * {@code "not specified"}; prefer the
   * {@linkplain #HazopConsequenceFinding(String, String, GuideWord, Parameter, double, double, String, Verdict, String, String, String, String)
   * twelve-argument constructor} so the verdict carries an auditable explanation of which policy threshold was applied.
   *
   * @param nodeId the HAZOP node identifier, in the same format as {@link HAZOPTemplate#fromProcessSystem}
   * @param unitName the name of the unit operation the finding relates to
   * @param guideWord the HAZOP guide-word of the deviation
   * @param parameter the HAZOP parameter of the deviation
   * @param computedValue the consequence value computed from the flowsheet state; NaN when not evaluated
   * @param designLimit the design limit the consequence was compared against; NaN when not evaluated
   * @param valueUnit the unit of both the computed value and the design limit, for example "C"
   * @param verdict the deterministic verdict
   * @param calculator a short description of the NeqSim calculation that produced the value
   * @param standardReference the governing standard reference, for example "API 617 / API 521"
   * @param message a human-readable explanation of the finding
   */
  public HazopConsequenceFinding(String nodeId, String unitName, GuideWord guideWord, Parameter parameter,
      double computedValue, double designLimit, String valueUnit, Verdict verdict, String calculator,
      String standardReference, String message) {
    this(nodeId, unitName, guideWord, parameter, computedValue, designLimit, valueUnit, verdict, calculator,
        standardReference, message, "not specified");
  }

  /**
   * Construct a quantified HAZOP consequence finding with an explicit limit basis.
   *
   * @param nodeId the HAZOP node identifier, in the same format as {@link HAZOPTemplate#fromProcessSystem}
   * @param unitName the name of the unit operation the finding relates to
   * @param guideWord the HAZOP guide-word of the deviation
   * @param parameter the HAZOP parameter of the deviation
   * @param computedValue the consequence value computed from the flowsheet state; NaN when not evaluated
   * @param designLimit the design limit the consequence was compared against; NaN when not evaluated
   * @param valueUnit the unit of both the computed value and the design limit, for example "C"
   * @param verdict the deterministic verdict
   * @param calculator a short description of the NeqSim calculation that produced the value
   * @param standardReference the governing standard reference, for example "API 617 / API 521"
   * @param message a human-readable explanation of the finding
   * @param limitBasis an auditable explanation of where the design limit came from (default value, per-unit override,
   * equipment data sheet, governing standard), so the green/red verdict is traceable
   */
  public HazopConsequenceFinding(String nodeId, String unitName, GuideWord guideWord, Parameter parameter,
      double computedValue, double designLimit, String valueUnit, Verdict verdict, String calculator,
      String standardReference, String message, String limitBasis) {
    this.nodeId = nodeId;
    this.unitName = unitName;
    this.guideWord = guideWord;
    this.parameter = parameter;
    this.computedValue = computedValue;
    this.designLimit = designLimit;
    this.valueUnit = valueUnit;
    this.verdict = verdict;
    this.calculator = calculator;
    this.standardReference = standardReference;
    this.message = message;
    this.limitBasis = limitBasis;
  }

  /**
   * Gets the HAZOP node identifier.
   *
   * @return the node identifier
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   * Gets the name of the unit operation the finding relates to.
   *
   * @return the unit name
   */
  public String getUnitName() {
    return unitName;
  }

  /**
   * Gets the HAZOP guide-word of the deviation.
   *
   * @return the guide-word
   */
  public GuideWord getGuideWord() {
    return guideWord;
  }

  /**
   * Gets the HAZOP parameter of the deviation.
   *
   * @return the parameter
   */
  public Parameter getParameter() {
    return parameter;
  }

  /**
   * Gets the consequence value computed from the flowsheet state.
   *
   * @return the computed value, or NaN when the finding was not evaluated
   */
  public double getComputedValue() {
    return computedValue;
  }

  /**
   * Gets the design limit the consequence was compared against.
   *
   * @return the design limit, or NaN when the finding was not evaluated
   */
  public double getDesignLimit() {
    return designLimit;
  }

  /**
   * Gets the unit of the computed value and design limit.
   *
   * @return the value unit, for example "C"
   */
  public String getValueUnit() {
    return valueUnit;
  }

  /**
   * Gets the deterministic verdict.
   *
   * @return the verdict
   */
  public Verdict getVerdict() {
    return verdict;
  }

  /**
   * Indicates whether the computed consequence breaches the design limit.
   *
   * @return true if the verdict is {@link Verdict#EXCEEDS}
   */
  public boolean exceedsLimit() {
    return verdict == Verdict.EXCEEDS;
  }

  /**
   * Gets the short description of the NeqSim calculation that produced the value.
   *
   * @return the calculator description
   */
  public String getCalculator() {
    return calculator;
  }

  /**
   * Gets the governing standard reference.
   *
   * @return the standard reference
   */
  public String getStandardReference() {
    return standardReference;
  }

  /**
   * Gets the human-readable explanation of the finding.
   *
   * @return the message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the auditable basis for the design limit used in this finding.
   *
   * @return an explanation of where the design limit came from (default, per-unit override, data sheet, standard)
   */
  public String getLimitBasis() {
    return limitBasis;
  }

  /**
   * Serialise this finding to a pretty-printed JSON object.
   *
   * @return JSON representation of the finding
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }

  /**
   * Render the finding as a single human-readable line.
   *
   * @return a one-line summary string
   */
  @Override
  public String toString() {
    return verdict + " | " + nodeId + " | " + guideWord + " " + parameter + " | " + message;
  }
}
