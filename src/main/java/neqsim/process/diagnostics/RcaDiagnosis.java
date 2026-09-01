package neqsim.process.diagnostics;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ranked, evidence-grounded root-cause diagnosis.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RcaDiagnosis implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final RcaEvidence evidence;
  private final List<RankedHypothesis> rankedHypotheses;

  RcaDiagnosis(RcaEvidence evidence, List<RankedHypothesis> rankedHypotheses) {
    this.evidence = evidence;
    this.rankedHypotheses = Collections.unmodifiableList(new ArrayList<RankedHypothesis>(rankedHypotheses));
  }

  /**
   * Returns the complete numerical evidence.
   *
   * @return evidence report
   */
  public RcaEvidence getEvidence() {
    return evidence;
  }

  /**
   * Returns hypotheses from strongest to weakest support.
   *
   * @return unmodifiable ranked list
   */
  public List<RankedHypothesis> getRankedHypotheses() {
    return rankedHypotheses;
  }

  /**
   * Returns the highest-ranked hypothesis.
   *
   * @return top hypothesis
   */
  public RankedHypothesis getTopHypothesis() {
    return rankedHypotheses.get(0);
  }

  /**
   * Returns whether a named hypothesis appears in the first {@code count} positions.
   *
   * @param hypothesisName hypothesis name
   * @param count number of positions to inspect
   * @return true if found
   */
  public boolean isInTop(String hypothesisName, int count) {
    int limit = Math.min(Math.max(count, 0), rankedHypotheses.size());
    for (int i = 0; i < limit; i++) {
      if (rankedHypotheses.get(i).getName().equals(hypothesisName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Serializes evidence, ranking and rule traces for an audit log or optional external language-model agent.
   *
   * @return JSON diagnosis
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(this);
  }

  /**
   * One scored hypothesis and its rule-level trace.
   */
  public static final class RankedHypothesis implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final String description;
    private final double score;
    private final List<RuleTrace> ruleTraces;

    RankedHypothesis(String name, String description, double score, List<RuleTrace> ruleTraces) {
      this.name = name;
      this.description = description;
      this.score = score;
      this.ruleTraces = Collections.unmodifiableList(new ArrayList<RuleTrace>(ruleTraces));
    }

    /**
     * Returns the hypothesis name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the physical description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns the weighted support score in [-1, 1].
     *
     * @return score
     */
    public double getScore() {
      return score;
    }

    /**
     * Returns every supporting or contradicting rule evaluation.
     *
     * @return unmodifiable trace list
     */
    public List<RuleTrace> getRuleTraces() {
      return ruleTraces;
    }
  }

  /**
   * Auditable evaluation of one hypothesis rule.
   */
  public static final class RuleTrace implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String metric;
    private final String firstSignal;
    private final String secondSignal;
    private final String expectation;
    private final double observedValue;
    private final double scale;
    private final double weight;
    private final double support;
    private final String rationale;

    RuleTrace(RcaFaultHypothesis.EvidenceRule rule, double observedValue, double support) {
      this.metric = rule.getMetric().name();
      this.firstSignal = rule.getFirstSignal();
      this.secondSignal = rule.getSecondSignal();
      this.expectation = rule.getExpectation().name();
      this.observedValue = observedValue;
      this.scale = rule.getScale();
      this.weight = rule.getWeight();
      this.support = support;
      this.rationale = rule.getRationale();
    }

    /**
     * Returns the evidence metric.
     *
     * @return metric name
     */
    public String getMetric() {
      return metric;
    }

    /**
     * Returns the first signal.
     *
     * @return signal name or null
     */
    public String getFirstSignal() {
      return firstSignal;
    }

    /**
     * Returns the second signal for a correlation rule.
     *
     * @return signal name or null
     */
    public String getSecondSignal() {
      return secondSignal;
    }

    /**
     * Returns the expected feature behavior.
     *
     * @return expectation name
     */
    public String getExpectation() {
      return expectation;
    }

    /**
     * Returns the observed feature value.
     *
     * @return observed value
     */
    public double getObservedValue() {
      return observedValue;
    }

    /**
     * Returns the configured screening scale.
     *
     * @return scale
     */
    public double getScale() {
      return scale;
    }

    /**
     * Returns the configured relative weight.
     *
     * @return weight
     */
    public double getWeight() {
      return weight;
    }

    /**
     * Returns signed rule support in [-1, 1].
     *
     * @return support
     */
    public double getSupport() {
      return support;
    }

    /**
     * Returns the engineering rationale.
     *
     * @return rationale
     */
    public String getRationale() {
      return rationale;
    }
  }
}
