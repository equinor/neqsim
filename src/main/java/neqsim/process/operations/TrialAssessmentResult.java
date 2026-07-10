package neqsim.process.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of an {@link OperatingTrialAssessment}.
 *
 * <p>
 * The result reports the descriptive before/after effect for every signal, the data-quality and confounder quality
 * gates, and whether a causal interpretation of the effect is defensible. Unless every gate passes the result is
 * explicitly labelled {@code DESCRIPTIVE} so that downstream pressure/power optimizers do not treat a confounded plant
 * trial as calibration truth.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TrialAssessmentResult implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Stable schema version for auditable JSON evidence. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Result classification when a causal reading is not defensible. */
  public static final String RESULT_TYPE_DESCRIPTIVE = "DESCRIPTIVE";

  /** Result classification when all data-quality and confounder gates pass. */
  public static final String RESULT_TYPE_CAUSAL_SUPPORTED = "CAUSAL_SUPPORTED";

  private final String schemaVersion = SCHEMA_VERSION;
  private final String studyName;
  private final String interventionSetpoint;
  private final double interventionOldValue;
  private final double interventionNewValue;
  private final double interventionTransitionTime;
  private final boolean interventionApplied;
  private final List<SignalEffect> signalEffects;
  private final List<QualityGate> qualityGates;
  private final boolean causalClaimAllowed;
  private final String resultType;
  private final List<String> warnings;

  /**
   * Creates a trial assessment result.
   *
   * @param studyName study or operating-case name
   * @param interventionSetpoint name of the setpoint that was changed
   * @param interventionOldValue setpoint value before the change
   * @param interventionNewValue setpoint value after the change
   * @param interventionTransitionTime time of the setpoint transition
   * @param interventionApplied true when the observed setpoint signal confirms the change happened
   * @param signalEffects per-signal before/after effects
   * @param qualityGates evaluated quality gates
   * @param causalClaimAllowed true only when all gates pass and the intervention is confirmed
   * @param warnings human-readable warnings, may be empty
   */
  TrialAssessmentResult(String studyName, String interventionSetpoint, double interventionOldValue,
      double interventionNewValue, double interventionTransitionTime, boolean interventionApplied,
      List<SignalEffect> signalEffects, List<QualityGate> qualityGates, boolean causalClaimAllowed,
      List<String> warnings) {
    this.studyName = studyName == null ? "" : studyName;
    this.interventionSetpoint = interventionSetpoint == null ? "" : interventionSetpoint;
    this.interventionOldValue = interventionOldValue;
    this.interventionNewValue = interventionNewValue;
    this.interventionTransitionTime = interventionTransitionTime;
    this.interventionApplied = interventionApplied;
    this.signalEffects = signalEffects == null ? new ArrayList<SignalEffect>()
        : new ArrayList<SignalEffect>(signalEffects);
    this.qualityGates = qualityGates == null ? new ArrayList<QualityGate>() : new ArrayList<QualityGate>(qualityGates);
    this.causalClaimAllowed = causalClaimAllowed;
    this.resultType = causalClaimAllowed ? RESULT_TYPE_CAUSAL_SUPPORTED : RESULT_TYPE_DESCRIPTIVE;
    this.warnings = warnings == null ? new ArrayList<String>() : new ArrayList<String>(warnings);
  }

  /**
   * Returns the schema version of this result.
   *
   * @return schema version string
   */
  public String getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * Returns the study name.
   *
   * @return study name
   */
  public String getStudyName() {
    return studyName;
  }

  /**
   * Returns the name of the setpoint that defines the intervention.
   *
   * @return intervention setpoint name
   */
  public String getInterventionSetpoint() {
    return interventionSetpoint;
  }

  /**
   * Returns the setpoint value before the change.
   *
   * @return old setpoint value
   */
  public double getInterventionOldValue() {
    return interventionOldValue;
  }

  /**
   * Returns the setpoint value after the change.
   *
   * @return new setpoint value
   */
  public double getInterventionNewValue() {
    return interventionNewValue;
  }

  /**
   * Returns the time of the setpoint transition.
   *
   * @return transition time
   */
  public double getInterventionTransitionTime() {
    return interventionTransitionTime;
  }

  /**
   * Checks whether the observed data confirm the intervention actually happened.
   *
   * @return true when the intervention is confirmed by data
   */
  public boolean isInterventionApplied() {
    return interventionApplied;
  }

  /**
   * Returns the per-signal before/after effects.
   *
   * @return unmodifiable list of signal effects
   */
  public List<SignalEffect> getSignalEffects() {
    return Collections.unmodifiableList(signalEffects);
  }

  /**
   * Returns the evaluated quality gates.
   *
   * @return unmodifiable list of quality gates
   */
  public List<QualityGate> getQualityGates() {
    return Collections.unmodifiableList(qualityGates);
  }

  /**
   * Checks whether a causal interpretation of the effect is defensible.
   *
   * @return true only when all gates pass and the intervention is confirmed
   */
  public boolean isCausalClaimAllowed() {
    return causalClaimAllowed;
  }

  /**
   * Returns the result classification.
   *
   * @return {@link #RESULT_TYPE_CAUSAL_SUPPORTED} or {@link #RESULT_TYPE_DESCRIPTIVE}
   */
  public String getResultType() {
    return resultType;
  }

  /**
   * Returns the warnings raised during assessment.
   *
   * @return unmodifiable list of warnings
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Returns the effect for a named signal.
   *
   * @param signalName signal name to look up
   * @return the matching signal effect, or null when the signal is not present
   */
  public SignalEffect getSignalEffect(String signalName) {
    for (SignalEffect effect : signalEffects) {
      if (effect.getName().equals(signalName)) {
        return effect;
      }
    }
    return null;
  }

  /**
   * Serializes the result to formatted, schema-versioned JSON.
   *
   * @return JSON evidence document
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }

  /**
   * Before/after effect for a single signal.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class SignalEffect implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String unit;
    private final double preMean;
    private final double postMean;
    private final double delta;
    private final double normalizedChange;
    private final int preSampleCount;
    private final int postSampleCount;
    private final double preGoodFraction;
    private final double postGoodFraction;
    private final boolean dataQualityOk;

    /**
     * Creates a signal effect.
     *
     * @param name signal name
     * @param unit engineering unit
     * @param preMean mean of good samples in the pre window
     * @param postMean mean of good samples in the post window
     * @param delta post mean minus pre mean
     * @param normalizedChange delta divided by the magnitude of the pre mean
     * @param preSampleCount number of samples in the pre window
     * @param postSampleCount number of samples in the post window
     * @param preGoodFraction fraction of good-quality samples in the pre window from 0 to 1
     * @param postGoodFraction fraction of good-quality samples in the post window from 0 to 1
     * @param dataQualityOk true when both windows meet the minimum good-data fraction
     */
    SignalEffect(String name, String unit, double preMean, double postMean, double delta, double normalizedChange,
        int preSampleCount, int postSampleCount, double preGoodFraction, double postGoodFraction,
        boolean dataQualityOk) {
      this.name = name == null ? "" : name;
      this.unit = unit == null ? "" : unit;
      this.preMean = preMean;
      this.postMean = postMean;
      this.delta = delta;
      this.normalizedChange = normalizedChange;
      this.preSampleCount = preSampleCount;
      this.postSampleCount = postSampleCount;
      this.preGoodFraction = preGoodFraction;
      this.postGoodFraction = postGoodFraction;
      this.dataQualityOk = dataQualityOk;
    }

    /**
     * Returns the signal name.
     *
     * @return signal name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the engineering unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Returns the mean of good samples in the pre window.
     *
     * @return pre-window mean
     */
    public double getPreMean() {
      return preMean;
    }

    /**
     * Returns the mean of good samples in the post window.
     *
     * @return post-window mean
     */
    public double getPostMean() {
      return postMean;
    }

    /**
     * Returns the absolute change between windows.
     *
     * @return post mean minus pre mean
     */
    public double getDelta() {
      return delta;
    }

    /**
     * Returns the normalized change.
     *
     * @return delta divided by the magnitude of the pre mean
     */
    public double getNormalizedChange() {
      return normalizedChange;
    }

    /**
     * Returns the number of samples in the pre window.
     *
     * @return pre-window sample count
     */
    public int getPreSampleCount() {
      return preSampleCount;
    }

    /**
     * Returns the number of samples in the post window.
     *
     * @return post-window sample count
     */
    public int getPostSampleCount() {
      return postSampleCount;
    }

    /**
     * Returns the fraction of good-quality samples in the pre window.
     *
     * @return pre-window good fraction from 0 to 1
     */
    public double getPreGoodFraction() {
      return preGoodFraction;
    }

    /**
     * Returns the fraction of good-quality samples in the post window.
     *
     * @return post-window good fraction from 0 to 1
     */
    public double getPostGoodFraction() {
      return postGoodFraction;
    }

    /**
     * Checks whether both windows meet the minimum good-data fraction.
     *
     * @return true when data quality is acceptable
     */
    public boolean isDataQualityOk() {
      return dataQualityOk;
    }
  }

  /**
   * Outcome of a single quality gate.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class QualityGate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String category;
    private final boolean passed;
    private final String message;

    /**
     * Creates a quality gate outcome.
     *
     * @param name gate name
     * @param category gate category, for example {@code DATA_QUALITY}, {@code CONFOUNDER}, or {@code INTERVENTION}
     * @param passed true when the gate passed
     * @param message human-readable explanation
     */
    QualityGate(String name, String category, boolean passed, String message) {
      this.name = name == null ? "" : name;
      this.category = category == null ? "" : category;
      this.passed = passed;
      this.message = message == null ? "" : message;
    }

    /**
     * Returns the gate name.
     *
     * @return gate name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the gate category.
     *
     * @return gate category
     */
    public String getCategory() {
      return category;
    }

    /**
     * Checks whether the gate passed.
     *
     * @return true when the gate passed
     */
    public boolean isPassed() {
      return passed;
    }

    /**
     * Returns the explanation for the gate outcome.
     *
     * @return gate message
     */
    public String getMessage() {
      return message;
    }
  }
}
