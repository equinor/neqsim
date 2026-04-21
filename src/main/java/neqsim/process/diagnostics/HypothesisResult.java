package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of evaluating a {@link TripHypothesis} against process evidence.
 *
 * <p>
 * Contains a confidence classification, a numeric confidence score (0.0 to 1.0), supporting
 * evidence descriptions, and a recommended action.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class HypothesisResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Classification of hypothesis confidence.
   */
  public enum Confidence {
    /** Strong evidence confirms this hypothesis. */
    CONFIRMED(0.90),
    /** Substantial evidence supports this hypothesis. */
    LIKELY(0.70),
    /** Some evidence, but not conclusive. */
    POSSIBLE(0.40),
    /** Little evidence supports this hypothesis. */
    UNLIKELY(0.15),
    /** Evidence contradicts this hypothesis. */
    RULED_OUT(0.0);

    private final double threshold;

    Confidence(double threshold) {
      this.threshold = threshold;
    }

    /**
     * Returns the minimum score for this confidence level.
     *
     * @return threshold value
     */
    public double getThreshold() {
      return threshold;
    }

    /**
     * Determines the confidence level from a numeric score.
     *
     * @param score confidence score between 0.0 and 1.0
     * @return corresponding confidence level
     */
    public static Confidence fromScore(double score) {
      if (score >= CONFIRMED.threshold) {
        return CONFIRMED;
      } else if (score >= LIKELY.threshold) {
        return LIKELY;
      } else if (score >= POSSIBLE.threshold) {
        return POSSIBLE;
      } else if (score >= UNLIKELY.threshold) {
        return UNLIKELY;
      } else {
        return RULED_OUT;
      }
    }
  }

  private final String hypothesisName;
  private final Confidence confidence;
  private final double score;
  private final List<String> evidence;
  private final String recommendedAction;

  /**
   * Constructs a hypothesis result.
   *
   * @param hypothesisName name of the hypothesis that produced this result
   * @param score confidence score between 0.0 and 1.0
   * @param evidence list of evidence descriptions
   * @param recommendedAction recommended corrective action
   */
  public HypothesisResult(String hypothesisName, double score, List<String> evidence,
      String recommendedAction) {
    this.hypothesisName = hypothesisName;
    this.score = Math.max(0.0, Math.min(1.0, score));
    this.confidence = Confidence.fromScore(this.score);
    this.evidence = evidence != null ? new ArrayList<>(evidence) : new ArrayList<>();
    this.recommendedAction = recommendedAction != null ? recommendedAction : "";
  }

  /**
   * Returns the hypothesis name.
   *
   * @return hypothesis name
   */
  public String getHypothesisName() {
    return hypothesisName;
  }

  /**
   * Returns the confidence classification.
   *
   * @return confidence enum value
   */
  public Confidence getConfidence() {
    return confidence;
  }

  /**
   * Returns the numeric confidence score (0.0 to 1.0).
   *
   * @return score
   */
  public double getScore() {
    return score;
  }

  /**
   * Returns the list of evidence descriptions.
   *
   * @return unmodifiable list of evidence strings
   */
  public List<String> getEvidence() {
    return Collections.unmodifiableList(evidence);
  }

  /**
   * Returns the recommended corrective action.
   *
   * @return action string
   */
  public String getRecommendedAction() {
    return recommendedAction;
  }

  @Override
  public String toString() {
    return String.format("%s: %s (%.0f%%) - %s", hypothesisName, confidence, score * 100.0,
        recommendedAction);
  }
}
