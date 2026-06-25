package neqsim.process.safety.vibration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result of an Energy Institute AVIFF flow-induced vibration screening for a single piping circuit.
 *
 * @author ESOL
 * @version 1.0
 */
public class FivLikelihoodResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String circuitName;
  private final double lofScore;
  private final PipingFivLikelihood likelihood;
  private final Map<String, Double> contributingFactors;
  private final String recommendation;

  /**
   * Creates a FIV LOF screening result.
   *
   * @param circuitName circuit identifier
   * @param lofScore normalised likelihood of failure
   * @param likelihood derived likelihood band
   * @param contributingFactors factor name to factor value
   * @param recommendation recommended action
   */
  public FivLikelihoodResult(String circuitName, double lofScore, PipingFivLikelihood likelihood,
      Map<String, Double> contributingFactors, String recommendation) {
    this.circuitName = circuitName;
    this.lofScore = lofScore;
    this.likelihood = likelihood;
    this.contributingFactors = new LinkedHashMap<String, Double>(contributingFactors);
    this.recommendation = recommendation;
  }

  /**
   * @return circuit name
   */
  public String getCircuitName() {
    return circuitName;
  }

  /**
   * @return normalised LOF score
   */
  public double getLofScore() {
    return lofScore;
  }

  /**
   * @return likelihood band
   */
  public PipingFivLikelihood getLikelihood() {
    return likelihood;
  }

  /**
   * @return contributing factors (defensive copy)
   */
  public Map<String, Double> getContributingFactors() {
    return new LinkedHashMap<String, Double>(contributingFactors);
  }

  /**
   * @return recommended action
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * @return result as pretty JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
