package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A ranked root-cause candidate produced by root-cause analysis logic.
 *
 * <p>
 * Each hypothesis represents a possible root cause for an observed equipment symptom. It carries:
 * </p>
 * <ul>
 * <li>A prior probability from OREDA failure-rate data</li>
 * <li>Evidence items collected from historian/STID data</li>
 * <li>A simulation verification score from what-if analysis</li>
 * <li>A combined confidence score (prior x likelihood x verification)</li>
 * <li>Recommended corrective actions</li>
 * </ul>
 *
 * <p>
 * Use the {@link Builder} to construct instances:
 * </p>
 *
 * <pre>
 * {@code
 * Hypothesis h = Hypothesis.builder().name("Seal degradation").category(Category.MECHANICAL)
 *     .priorProbability(0.25).build();
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class Hypothesis implements Serializable, Comparable<Hypothesis> {

  private static final long serialVersionUID = 1000L;

  /**
   * Category of root cause.
   */
  public enum Category {
    /** Mechanical failure (bearings, seals, impeller, shaft). */
    MECHANICAL,
    /** Process condition issue (composition, flow, pressure, temperature). */
    PROCESS,
    /** Control system malfunction (sensor, logic, valve positioner). */
    CONTROL,
    /** External cause (utility loss, ambient, feed contamination). */
    EXTERNAL
  }

  /**
   * Strength of a piece of evidence.
   */
  public enum EvidenceStrength {
    /** Evidence strongly supports the hypothesis. */
    STRONG,
    /** Evidence moderately supports the hypothesis. */
    MODERATE,
    /** Evidence weakly supports the hypothesis. */
    WEAK,
    /** Evidence contradicts the hypothesis. */
    CONTRADICTORY
  }

  /**
   * Expected direction or event type for a diagnostic signal.
   */
  public enum ExpectedBehavior {
    /** The relevant historian value is expected to increase. */
    INCREASE,
    /** The relevant historian value is expected to decrease. */
    DECREASE,
    /** The value is expected to exceed a high design or alarm limit. */
    HIGH_LIMIT,
    /** The value is expected to fall below a low design or alarm limit. */
    LOW_LIMIT,
    /** A sudden step or discontinuity is expected. */
    STEP_CHANGE,
    /** Correlated movement with another tag is expected. */
    CORRELATION,
    /** Any abnormal change in the value supports the hypothesis. */
    ANY_CHANGE
  }

  /**
   * Expected historian, STID, or simulation signal for a hypothesis.
   */
  public static class ExpectedSignal implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String parameterPattern;
    private final ExpectedBehavior expectedBehavior;
    private final double weight;
    private final String rationale;

    /**
     * Creates an expected signal fingerprint.
     *
     * @param parameterPattern parameter alias or pipe-separated aliases to match
     * @param expectedBehavior expected signal behavior
     * @param weight relative importance, normally 0.1 to 5.0
     * @param rationale engineering reason for expecting this signal
     */
    public ExpectedSignal(String parameterPattern, ExpectedBehavior expectedBehavior, double weight,
        String rationale) {
      this.parameterPattern = parameterPattern == null ? "" : parameterPattern;
      this.expectedBehavior =
          expectedBehavior == null ? ExpectedBehavior.ANY_CHANGE : expectedBehavior;
      this.weight = Math.max(0.1, weight);
      this.rationale = rationale == null ? "" : rationale;
    }

    /**
     * Gets the parameter matching pattern.
     *
     * @return parameter pattern or aliases
     */
    public String getParameterPattern() {
      return parameterPattern;
    }

    /**
     * Gets the expected behavior.
     *
     * @return expected behavior
     */
    public ExpectedBehavior getExpectedBehavior() {
      return expectedBehavior;
    }

    /**
     * Gets the relative signal weight.
     *
     * @return signal weight
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Gets the engineering rationale.
     *
     * @return rationale text
     */
    public String getRationale() {
      return rationale;
    }
  }

  /**
   * A single piece of evidence for or against a hypothesis.
   */
  public static class Evidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String parameter;
    private final String observation;
    private final EvidenceStrength strength;
    private final String source;
    private final boolean supporting;
    private final double weight;
    private final String sourceReference;

    /**
     * Creates an evidence item.
     *
     * @param parameter parameter name (e.g., "discharge_temperature")
     * @param observation what was observed (e.g., "increasing trend, +5 C over 30 days")
     * @param strength how strongly this supports the hypothesis
     * @param source data source (e.g., "historian", "STID", "simulation")
     */
    public Evidence(String parameter, String observation, EvidenceStrength strength,
        String source) {
      this(parameter, observation, strength, source, strength != EvidenceStrength.CONTRADICTORY,
          1.0, "");
    }

    /**
     * Creates an evidence item with explicit support direction and weight.
     *
     * @param parameter parameter name, tag alias, or design key
     * @param observation what was observed
     * @param strength strength of the evidence
     * @param source data source or analysis method
     * @param supporting true if the evidence supports the hypothesis, false if contradictory
     * @param weight relative weight, normally 0.1 to 5.0
     * @param sourceReference optional document, tag, or data-window reference
     */
    public Evidence(String parameter, String observation, EvidenceStrength strength, String source,
        boolean supporting, double weight, String sourceReference) {
      this.parameter = parameter;
      this.observation = observation;
      this.strength = strength;
      this.source = source;
      this.supporting = supporting;
      this.weight = Math.max(0.1, weight);
      this.sourceReference = sourceReference == null ? "" : sourceReference;
    }

    /**
     * Gets the parameter name.
     *
     * @return parameter name
     */
    public String getParameter() {
      return parameter;
    }

    /**
     * Gets the observation description.
     *
     * @return observation text
     */
    public String getObservation() {
      return observation;
    }

    /**
     * Gets the evidence strength.
     *
     * @return evidence strength
     */
    public EvidenceStrength getStrength() {
      return strength;
    }

    /**
     * Gets the data source.
     *
     * @return source identifier
     */
    public String getSource() {
      return source;
    }

    /**
     * Checks if the evidence supports the hypothesis.
     *
     * @return true when supporting, false when contradictory
     */
    public boolean isSupporting() {
      return supporting;
    }

    /**
     * Gets the relative evidence weight.
     *
     * @return evidence weight
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Gets the source reference.
     *
     * @return document, tag, or time-window reference, or an empty string
     */
    public String getSourceReference() {
      return sourceReference;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s (%s)", strength, parameter, observation, source);
    }
  }

  // Fields
  private final String name;
  private final String description;
  private final Category category;
  private final String failureMode;
  private double priorProbability;
  private double likelihoodScore;
  private double verificationScore;
  private double confidenceScore;
  private final List<Evidence> evidenceList;
  private final List<ExpectedSignal> expectedSignals;
  private final List<String> recommendedActions;
  private String simulationSummary;

  private Hypothesis(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.category = builder.category;
    this.failureMode = builder.failureMode;
    this.priorProbability = builder.priorProbability;
    this.likelihoodScore = 0.0;
    this.verificationScore = 0.0;
    this.confidenceScore = builder.priorProbability;
    this.evidenceList = new ArrayList<>(builder.evidenceList);
    this.expectedSignals = new ArrayList<>(builder.expectedSignals);
    this.recommendedActions = new ArrayList<>(builder.recommendedActions);
    this.simulationSummary = "";
  }

  // --- Getters ---

  /**
   * Gets the hypothesis name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the hypothesis description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the root-cause category.
   *
   * @return category
   */
  public Category getCategory() {
    return category;
  }

  /**
   * Gets the failure mode name from OREDA/reliability data.
   *
   * @return failure mode name, may be empty
   */
  public String getFailureMode() {
    return failureMode;
  }

  /**
   * Gets the prior probability from OREDA failure rates.
   *
   * @return prior probability in range 0 to 1
   */
  public double getPriorProbability() {
    return priorProbability;
  }

  /**
   * Gets the likelihood score from historian evidence.
   *
   * @return likelihood score in range 0 to 1
   */
  public double getLikelihoodScore() {
    return likelihoodScore;
  }

  /**
   * Gets the simulation verification score.
   *
   * @return verification score in range 0 to 1
   */
  public double getVerificationScore() {
    return verificationScore;
  }

  /**
   * Gets the combined confidence score.
   *
   * @return confidence score in range 0 to 1
   */
  public double getConfidenceScore() {
    return confidenceScore;
  }

  /**
   * Gets the evidence list.
   *
   * @return unmodifiable list of evidence items
   */
  public List<Evidence> getEvidenceList() {
    return Collections.unmodifiableList(evidenceList);
  }

  /**
   * Gets the expected diagnostic signal fingerprints.
   *
   * @return unmodifiable list of expected signals
   */
  public List<ExpectedSignal> getExpectedSignals() {
    return Collections.unmodifiableList(expectedSignals);
  }

  /**
   * Gets the recommended corrective actions.
   *
   * @return unmodifiable list of action descriptions
   */
  public List<String> getRecommendedActions() {
    return Collections.unmodifiableList(recommendedActions);
  }

  /**
   * Gets the simulation verification summary.
   *
   * @return simulation summary text
   */
  public String getSimulationSummary() {
    return simulationSummary;
  }

  // --- Mutators used during analysis ---

  /**
   * Adds an evidence item.
   *
   * @param evidence evidence to add
   */
  public void addEvidence(Evidence evidence) {
    evidenceList.add(evidence);
  }

  /**
   * Updates the prior probability after reliability-data lookup.
   *
   * @param prior probability in range 0 to 1
   */
  public void setPriorProbability(double prior) {
    this.priorProbability = Math.max(0.0, Math.min(1.0, prior));
    updateConfidence();
  }

  /**
   * Sets the likelihood score from evidence analysis.
   *
   * @param score score in range 0 to 1
   */
  public void setLikelihoodScore(double score) {
    this.likelihoodScore = Math.max(0.0, Math.min(1.0, score));
    updateConfidence();
  }

  /**
   * Sets the simulation verification score.
   *
   * @param score score in range 0 to 1
   */
  public void setVerificationScore(double score) {
    this.verificationScore = Math.max(0.0, Math.min(1.0, score));
    updateConfidence();
  }

  /**
   * Sets the simulation summary text.
   *
   * @param summary simulation result description
   */
  public void setSimulationSummary(String summary) {
    this.simulationSummary = summary;
  }

  /**
   * Adds a recommended corrective action.
   *
   * @param action action description
   */
  public void addRecommendedAction(String action) {
    recommendedActions.add(action);
  }

  /**
   * Updates the combined confidence score using Bayesian-inspired formula: confidence = prior x
   * likelihood x verification. If likelihood or verification are 0 (not yet evaluated), they are
   * treated as 1.0 (neutral).
   */
  void updateConfidence() {
    double lik = likelihoodScore > 0 ? likelihoodScore : 1.0;
    double ver = verificationScore > 0 ? verificationScore : 1.0;
    this.confidenceScore = priorProbability * lik * ver;
  }

  /**
   * Sets the final confidence score directly. Used during Bayesian normalization across all
   * hypotheses.
   *
   * @param score normalized confidence score in range 0 to 1
   */
  void setConfidenceScore(double score) {
    this.confidenceScore = Math.max(0.0, Math.min(1.0, score));
  }

  /**
   * Compares hypotheses by confidence score (descending).
   *
   * @param other hypothesis to compare to
   * @return comparison result
   */
  @Override
  public int compareTo(Hypothesis other) {
    return Double.compare(other.confidenceScore, this.confidenceScore);
  }

  @Override
  public String toString() {
    return String.format("%.1f%% - %s [%s]: %s", confidenceScore * 100, name, category,
        description);
  }

  /**
   * Creates a new builder.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for {@link Hypothesis}.
   */
  public static class Builder {
    private String name = "";
    private String description = "";
    private Category category = Category.PROCESS;
    private String failureMode = "";
    private double priorProbability = 0.1;
    private List<Evidence> evidenceList = new ArrayList<>();
    private List<ExpectedSignal> expectedSignals = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();

    /**
     * Creates a builder.
     */
    public Builder() {}

    /**
     * Sets the hypothesis name.
     *
     * @param name hypothesis name
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the description.
     *
     * @param description hypothesis description
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets the category.
     *
     * @param category root cause category
     * @return this builder
     */
    public Builder category(Category category) {
      this.category = category;
      return this;
    }

    /**
     * Sets the failure mode name.
     *
     * @param failureMode OREDA failure mode name
     * @return this builder
     */
    public Builder failureMode(String failureMode) {
      this.failureMode = failureMode;
      return this;
    }

    /**
     * Sets the prior probability.
     *
     * @param prior probability in range 0 to 1
     * @return this builder
     */
    public Builder priorProbability(double prior) {
      this.priorProbability = Math.max(0.0, Math.min(1.0, prior));
      return this;
    }

    /**
     * Adds an evidence item.
     *
     * @param evidence evidence to add
     * @return this builder
     */
    public Builder addEvidence(Evidence evidence) {
      this.evidenceList.add(evidence);
      return this;
    }

    /**
     * Adds an expected diagnostic signal.
     *
     * @param parameterPattern parameter alias or pipe-separated aliases to match
     * @param behavior expected behavior for the matched parameter
     * @param weight relative importance, normally 0.1 to 5.0
     * @param rationale engineering rationale for the signal
     * @return this builder
     */
    public Builder addExpectedSignal(String parameterPattern, ExpectedBehavior behavior,
        double weight, String rationale) {
      this.expectedSignals.add(new ExpectedSignal(parameterPattern, behavior, weight, rationale));
      return this;
    }

    /**
     * Adds a recommended action.
     *
     * @param action corrective action
     * @return this builder
     */
    public Builder addAction(String action) {
      this.recommendedActions.add(action);
      return this;
    }

    /**
     * Creates a deep copy of this builder, so the original template remains unmodified.
     *
     * @return a new builder with copied state
     */
    public Builder copy() {
      Builder clone = new Builder();
      clone.name = this.name;
      clone.description = this.description;
      clone.category = this.category;
      clone.failureMode = this.failureMode;
      clone.priorProbability = this.priorProbability;
      clone.evidenceList = new ArrayList<>(this.evidenceList);
      clone.expectedSignals = new ArrayList<>(this.expectedSignals);
      clone.recommendedActions = new ArrayList<>(this.recommendedActions);
      return clone;
    }

    /**
     * Builds the hypothesis.
     *
     * @return constructed hypothesis
     */
    public Hypothesis build() {
      return new Hypothesis(this);
    }
  }
}
