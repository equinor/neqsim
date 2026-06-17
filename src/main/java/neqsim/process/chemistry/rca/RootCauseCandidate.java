package neqsim.process.chemistry.rca;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A candidate root cause produced by {@link RootCauseAnalyser}.
 *
 * <p>
 * Each candidate carries a code, a human-readable description, the supporting evidence chain, a
 * normalised score (0..1) and a tag indicating whether it is the PRIMARY cause, a CONTRIBUTING
 * factor, or merely POSSIBLE.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class RootCauseCandidate implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Severity tag for ranking.
   */
  public enum Tag {
    /** The most likely root cause given the evidence. */
    PRIMARY,
    /** A contributing factor that aggravates the primary cause. */
    CONTRIBUTING,
    /** A possible cause that could not be ruled out. */
    POSSIBLE,
    /** Considered and ruled out. */
    RULED_OUT;
  }

  private final String code;
  private final String description;
  private final double score;
  private Tag tag;
  private final String evidence;
  private final String recommendation;

  /**
   * Builds a candidate.
   *
   * @param code stable machine-readable code
   * @param description plain-language description
   * @param score normalised 0..1 score
   * @param evidence narrative evidence chain
   * @param recommendation suggested mitigation
   */
  public RootCauseCandidate(String code, String description, double score, String evidence,
      String recommendation) {
    this.code = code;
    this.description = description;
    this.score = score;
    this.evidence = evidence;
    this.recommendation = recommendation;
    if (score >= 0.7) {
      this.tag = Tag.PRIMARY;
    } else if (score >= 0.4) {
      this.tag = Tag.CONTRIBUTING;
    } else if (score >= 0.15) {
      this.tag = Tag.POSSIBLE;
    } else {
      this.tag = Tag.RULED_OUT;
    }
  }

  /**
   * Sets the tag explicitly.
   *
   * @param tag tag
   */
  public void setTag(Tag tag) {
    this.tag = tag;
  }

  /**
   * Returns the code.
   *
   * @return code
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the score.
   *
   * @return 0..1
   */
  public double getScore() {
    return score;
  }

  /**
   * Returns the tag.
   *
   * @return tag
   */
  public Tag getTag() {
    return tag;
  }

  /**
   * Returns the evidence narrative.
   *
   * @return evidence
   */
  public String getEvidence() {
    return evidence;
  }

  /**
   * Returns the recommendation.
   *
   * @return recommendation
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * Returns a structured map for JSON.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("code", code);
    map.put("description", description);
    map.put("score", score);
    map.put("tag", tag.name());
    map.put("evidence", evidence);
    map.put("recommendation", recommendation);
    return map;
  }
}
