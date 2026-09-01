package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Auditable physical fault hypothesis expressed as quantitative evidence rules.
 *
 * <p>
 * A hypothesis contains engineering expectations, not trained fault signatures. Each rule maps one item from
 * {@link RcaEvidence} to a signed support score in the range [-1, 1]. This makes the ranking reproducible and allows an
 * optional external language-model agent to explain or challenge the same numerical evidence without controlling the
 * underlying score.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RcaFaultHypothesis implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Evidence features available to a rule.
   */
  public enum Metric {
    /** Signed condition-specific mean shift. */
    MEAN_Z_SCORE,
    /** Natural logarithm of observed variance divided by normal variance. */
    LOG_VARIANCE_RATIO,
    /** Natural logarithm of observed range divided by normal range. */
    LOG_RANGE_RATIO,
    /** End-to-end slope normalized by normal signal variation. */
    NORMALIZED_SLOPE,
    /** Absolute observed-minus-normal correlation for a signal pair. */
    ABS_CORRELATION_CHANGE,
    /** Aggregate anomaly score across the complete window. */
    OVERALL_ANOMALY
  }

  /**
   * Expected direction or magnitude of a feature.
   */
  public enum Expectation {
    /** A positive value supports the hypothesis. */
    POSITIVE,
    /** A negative value supports the hypothesis. */
    NEGATIVE,
    /** A large absolute value supports the hypothesis. */
    LARGE_ABSOLUTE,
    /** A value close to zero supports the hypothesis. */
    NEAR_ZERO
  }

  private final String name;
  private final String description;
  private final List<EvidenceRule> rules;

  private RcaFaultHypothesis(Builder builder) {
    if (builder.name == null || builder.name.trim().isEmpty()) {
      throw new IllegalArgumentException("hypothesis name must not be blank");
    }
    if (builder.rules.isEmpty()) {
      throw new IllegalArgumentException("hypothesis must contain at least one evidence rule");
    }
    this.name = builder.name.trim();
    this.description = builder.description == null ? "" : builder.description.trim();
    this.rules = Collections.unmodifiableList(new ArrayList<EvidenceRule>(builder.rules));
  }

  /**
   * Returns the stable hypothesis name.
   *
   * @return hypothesis name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the physical fault description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the evidence rules.
   *
   * @return unmodifiable rule list
   */
  public List<EvidenceRule> getRules() {
    return rules;
  }

  /**
   * Creates a hypothesis builder.
   *
   * @param name stable hypothesis name
   * @param description physical fault description
   * @return builder
   */
  public static Builder builder(String name, String description) {
    return new Builder(name, description);
  }

  /**
   * Builder for fault hypotheses.
   */
  public static final class Builder {
    private final String name;
    private final String description;
    private final List<EvidenceRule> rules = new ArrayList<EvidenceRule>();

    private Builder(String name, String description) {
      this.name = name;
      this.description = description;
    }

    /**
     * Adds a signal-level evidence rule.
     *
     * @param signalName signal name
     * @param metric evidence metric
     * @param expectation expected direction or magnitude
     * @param scale positive feature magnitude corresponding to substantial support
     * @param weight positive relative rule weight
     * @param rationale engineering rationale
     * @return this builder
     */
    public Builder signalRule(String signalName, Metric metric, Expectation expectation, double scale, double weight,
        String rationale) {
      rules.add(new EvidenceRule(signalName, null, metric, expectation, scale, weight, rationale));
      return this;
    }

    /**
     * Adds a correlation-change rule.
     *
     * @param firstSignal first signal
     * @param secondSignal second signal
     * @param expectation expected discrepancy magnitude
     * @param scale positive discrepancy corresponding to substantial support
     * @param weight positive relative rule weight
     * @param rationale engineering rationale
     * @return this builder
     */
    public Builder correlationRule(String firstSignal, String secondSignal, Expectation expectation, double scale,
        double weight, String rationale) {
      rules.add(new EvidenceRule(firstSignal, secondSignal, Metric.ABS_CORRELATION_CHANGE, expectation, scale, weight,
          rationale));
      return this;
    }

    /**
     * Adds a process-wide anomaly rule.
     *
     * @param expectation expected anomaly magnitude
     * @param scale positive anomaly score corresponding to substantial support
     * @param weight positive relative rule weight
     * @param rationale engineering rationale
     * @return this builder
     */
    public Builder overallRule(Expectation expectation, double scale, double weight, String rationale) {
      rules.add(new EvidenceRule(null, null, Metric.OVERALL_ANOMALY, expectation, scale, weight, rationale));
      return this;
    }

    /**
     * Builds the immutable hypothesis.
     *
     * @return hypothesis
     */
    public RcaFaultHypothesis build() {
      return new RcaFaultHypothesis(this);
    }
  }

  /**
   * One weighted, auditable expectation about diagnostic evidence.
   */
  public static final class EvidenceRule implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String firstSignal;
    private final String secondSignal;
    private final Metric metric;
    private final Expectation expectation;
    private final double scale;
    private final double weight;
    private final String rationale;

    private EvidenceRule(String firstSignal, String secondSignal, Metric metric, Expectation expectation, double scale,
        double weight, String rationale) {
      if (metric == null || expectation == null) {
        throw new IllegalArgumentException("metric and expectation must not be null");
      }
      if (!Double.isFinite(scale) || scale <= 0.0) {
        throw new IllegalArgumentException("rule scale must be finite and > 0");
      }
      if (!Double.isFinite(weight) || weight <= 0.0) {
        throw new IllegalArgumentException("rule weight must be finite and > 0");
      }
      if (metric != Metric.OVERALL_ANOMALY && (firstSignal == null || firstSignal.trim().isEmpty())) {
        throw new IllegalArgumentException("signal name is required for " + metric);
      }
      if (metric == Metric.ABS_CORRELATION_CHANGE && (secondSignal == null || secondSignal.trim().isEmpty())) {
        throw new IllegalArgumentException("two signal names are required for a correlation rule");
      }
      this.firstSignal = firstSignal == null ? null : firstSignal.trim();
      this.secondSignal = secondSignal == null ? null : secondSignal.trim();
      this.metric = metric;
      this.expectation = expectation;
      this.scale = scale;
      this.weight = weight;
      this.rationale = rationale == null ? "" : rationale.trim();
    }

    /**
     * Returns the first signal, or null for a process-wide rule.
     *
     * @return first signal
     */
    public String getFirstSignal() {
      return firstSignal;
    }

    /**
     * Returns the second signal for a correlation rule.
     *
     * @return second signal or null
     */
    public String getSecondSignal() {
      return secondSignal;
    }

    /**
     * Returns the evidence metric.
     *
     * @return metric
     */
    public Metric getMetric() {
      return metric;
    }

    /**
     * Returns the expected feature behavior.
     *
     * @return expectation
     */
    public Expectation getExpectation() {
      return expectation;
    }

    /**
     * Returns the screening scale.
     *
     * @return scale
     */
    public double getScale() {
      return scale;
    }

    /**
     * Returns the relative rule weight.
     *
     * @return weight
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Returns the engineering rationale.
     *
     * @return rationale
     */
    public String getRationale() {
      return rationale;
    }

    RuleEvaluation evaluate(RcaEvidence evidence) {
      double value = getFeatureValue(evidence);
      double normalized = value / scale;
      double support;
      switch (expectation) {
      case POSITIVE:
        support = Math.tanh(normalized);
        break;
      case NEGATIVE:
        support = Math.tanh(-normalized);
        break;
      case LARGE_ABSOLUTE:
        support = Math.tanh(Math.abs(normalized));
        break;
      case NEAR_ZERO:
        support = 1.0 - 2.0 * Math.tanh(Math.abs(normalized));
        break;
      default:
        support = 0.0;
        break;
      }
      return new RuleEvaluation(this, value, support);
    }

    private double getFeatureValue(RcaEvidence evidence) {
      if (metric == Metric.OVERALL_ANOMALY) {
        return evidence.getOverallAnomalyScore();
      }
      if (metric == Metric.ABS_CORRELATION_CHANGE) {
        return Math.abs(evidence.getCorrelationDifference(firstSignal, secondSignal));
      }
      RcaEvidence.SignalEvidence signal = evidence.getSignalEvidence(firstSignal);
      switch (metric) {
      case MEAN_Z_SCORE:
        return signal.getMeanZScore();
      case LOG_VARIANCE_RATIO:
        return signal.getLogVarianceRatio();
      case LOG_RANGE_RATIO:
        return signal.getLogRangeRatio();
      case NORMALIZED_SLOPE:
        return signal.getNormalizedSlope();
      default:
        throw new IllegalStateException("unsupported signal metric " + metric);
      }
    }
  }

  static final class RuleEvaluation {
    final EvidenceRule rule;
    final double value;
    final double support;

    private RuleEvaluation(EvidenceRule rule, double value, double support) {
      this.rule = rule;
      this.value = value;
      this.support = support;
    }
  }
}
