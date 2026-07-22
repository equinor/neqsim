package neqsim.process.safety.vibration;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of an acoustic-induced vibration (AIV) screening for a single piping circuit downstream of a pressure-reducing
 * device.
 *
 * <p>
 * Carries the computed sound power level (PWL), the Carucci &amp; Mueller design-line allowable PWL, the screening
 * margin, the resulting likelihood band and the contributing factors.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AcousticInducedVibrationResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String circuitName;
  private final double pwlDb;
  private final double allowablePwlDb;
  private final double marginDb;
  private final PipingFivLikelihood likelihood;
  private final Map<String, Double> contributingFactors;
  private final String recommendation;

  /**
   * Creates an AIV screening result.
   *
   * @param circuitName circuit identifier
   * @param pwlDb computed sound power level, dB
   * @param allowablePwlDb Carucci &amp; Mueller design-line allowable PWL, dB
   * @param marginDb screening margin (pwlDb - allowablePwlDb), dB
   * @param likelihood derived likelihood band
   * @param contributingFactors factor name to factor value
   * @param recommendation recommended action
   */
  public AcousticInducedVibrationResult(String circuitName, double pwlDb, double allowablePwlDb, double marginDb,
      PipingFivLikelihood likelihood, Map<String, Double> contributingFactors, String recommendation) {
    this.circuitName = circuitName;
    this.pwlDb = pwlDb;
    this.allowablePwlDb = allowablePwlDb;
    this.marginDb = marginDb;
    this.likelihood = likelihood;
    this.contributingFactors = new LinkedHashMap<String, Double>(contributingFactors);
    this.recommendation = recommendation;
  }

  /**
   * Gets the circuit name.
   *
   * @return circuit name
   */
  public String getCircuitName() {
    return circuitName;
  }

  /**
   * Gets the computed sound power level.
   *
   * @return sound power level, dB
   */
  public double getPwlDb() {
    return pwlDb;
  }

  /**
   * Gets the Carucci &amp; Mueller design-line allowable PWL.
   *
   * @return allowable sound power level, dB
   */
  public double getAllowablePwlDb() {
    return allowablePwlDb;
  }

  /**
   * Gets the screening margin (computed minus allowable PWL).
   *
   * @return margin, dB (positive values indicate exceedance)
   */
  public double getMarginDb() {
    return marginDb;
  }

  /**
   * Gets the likelihood band.
   *
   * @return likelihood band
   */
  public PipingFivLikelihood getLikelihood() {
    return likelihood;
  }

  /**
   * Gets the contributing factors.
   *
   * @return contributing factors (defensive copy)
   */
  public Map<String, Double> getContributingFactors() {
    return new LinkedHashMap<String, Double>(contributingFactors);
  }

  /**
   * Gets the recommended action.
   *
   * @return recommended action
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * Serializes the result to pretty-printed JSON.
   *
   * @return result as pretty JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
