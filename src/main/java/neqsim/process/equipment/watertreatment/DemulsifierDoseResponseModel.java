package neqsim.process.equipment.watertreatment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calibratable dose-response model for demulsifier impact on oil-in-water concentration.
 *
 * <p>
 * The model uses a Hill saturation curve for normal demulsifier response and an overdose penalty
 * above an optimum dose. It is intended for screening, reconciliation to bottle tests, and hybrid
 * physics-data workflows where NeqSim supplies fluid and separator features while plant data
 * calibrates the field response.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class DemulsifierDoseResponseModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Maximum fractional OIW removal achievable by the chemical response. */
  private double maxRemovalFraction = 0.70;

  /** Dose giving half of the maximum response in ppm by produced-water mass. */
  private double halfEffectDosePpm = 20.0;

  /** Hill coefficient controlling dose-response steepness. */
  private double hillCoefficient = 1.5;

  /** Dose above which overdosing may start to reduce performance. */
  private double optimumDosePpm = 60.0;

  /** Dimensionless strength of the overdose penalty. */
  private double overdoseSensitivity = 0.25;

  /** Practical minimum OIW floor after treatment in mg/L. */
  private double minimumOilInWaterMgL = 2.0;

  /** Last calculated effective removal fraction. */
  private double lastEffectiveRemovalFraction = 0.0;

  /** Last calculated predicted OIW concentration. */
  private double lastPredictedOilInWaterMgL = Double.NaN;

  /** Last calculated root mean square calibration error. */
  private double lastRootMeanSquareError = Double.NaN;

  /** Last dose evaluated by the model. */
  private double lastDosePpm = 0.0;

  /**
   * Creates a dose-response model with conservative default parameters.
   */
  public DemulsifierDoseResponseModel() {}

  /**
   * Calculates the fractional OIW removal from an effective demulsifier dose.
   *
   * @param effectiveDosePpm effective demulsifier dose in ppm by produced-water mass
   * @return fractional OIW removal in the range 0 to 1
   */
  public double calculateRemovalFraction(double effectiveDosePpm) {
    double safeDose = Math.max(0.0, effectiveDosePpm);
    lastDosePpm = safeDose;
    if (safeDose <= 0.0 || maxRemovalFraction <= 0.0) {
      lastEffectiveRemovalFraction = 0.0;
      return lastEffectiveRemovalFraction;
    }

    double safeHalfDose = Math.max(1.0e-9, halfEffectDosePpm);
    double safeHill = Math.max(0.1, hillCoefficient);
    double dosePower = Math.pow(safeDose, safeHill);
    double halfDosePower = Math.pow(safeHalfDose, safeHill);
    double baseRemoval = clamp01(maxRemovalFraction * dosePower / (halfDosePower + dosePower));
    double penalty = calculateOverdosePenalty(safeDose);
    lastEffectiveRemovalFraction = clamp01(baseRemoval * (1.0 - penalty));
    return lastEffectiveRemovalFraction;
  }

  /**
   * Predicts outlet OIW from untreated OIW and effective demulsifier dose.
   *
   * @param untreatedOilInWaterMgL untreated or pre-chemical OIW concentration in mg/L
   * @param effectiveDosePpm effective demulsifier dose in ppm by produced-water mass
   * @return predicted OIW concentration in mg/L
   */
  public double predictOilInWater(double untreatedOilInWaterMgL, double effectiveDosePpm) {
    double untreated = Math.max(0.0, untreatedOilInWaterMgL);
    double floor = Math.min(Math.max(0.0, minimumOilInWaterMgL), untreated);
    double removal = calculateRemovalFraction(effectiveDosePpm);
    double predicted = floor + (untreated - floor) * (1.0 - removal);
    double penalty = calculateOverdosePenalty(Math.max(0.0, effectiveDosePpm));
    if (penalty > 0.0) {
      predicted *= 1.0 + 0.25 * penalty;
    }
    lastPredictedOilInWaterMgL = Math.max(0.0, predicted);
    return lastPredictedOilInWaterMgL;
  }

  /**
   * Calibrates the model parameters to paired dose and OIW field or bottle-test data.
   *
   * <p>
   * The calibration uses a deterministic grid search so it remains dependency-free and Java 8
   * compatible. It is suitable for initial model fitting before a more advanced external optimizer
   * is used.
   * </p>
   *
   * @param dosePpm demulsifier doses in ppm
   * @param observedOilInWaterMgL observed OIW concentrations in mg/L
   * @param untreatedOilInWaterMgL untreated or zero-chemical reference OIW in mg/L
   * @return root mean square error after calibration in mg/L
   * @throws IllegalArgumentException if the arrays are null, have different lengths, or contain
   *         fewer than two points
   */
  public double calibrate(double[] dosePpm, double[] observedOilInWaterMgL,
      double untreatedOilInWaterMgL) {
    validateCalibrationData(dosePpm, observedOilInWaterMgL);
    double minPositiveDose = findMinPositiveDose(dosePpm);
    double maxDose = findMaxDose(dosePpm);
    if (maxDose <= 0.0) {
      throw new IllegalArgumentException("At least one positive dose is required for calibration");
    }

    double[] halfDoseCandidates = buildHalfDoseCandidates(minPositiveDose, maxDose);
    double[] hillCandidates = {0.8, 1.0, 1.5, 2.0, 3.0};
    double[] optimumCandidates = {0.6 * maxDose, 0.8 * maxDose, maxDose, 1.25 * maxDose};
    double[] overdoseCandidates = {0.0, 0.15, 0.30, 0.60, 1.0};

    double bestError = Double.POSITIVE_INFINITY;
    double bestMaxRemoval = maxRemovalFraction;
    double bestHalfDose = halfEffectDosePpm;
    double bestHill = hillCoefficient;
    double bestOptimum = optimumDosePpm;
    double bestOverdose = overdoseSensitivity;

    for (double maxRemoval = 0.10; maxRemoval <= 0.951; maxRemoval += 0.05) {
      for (int halfIndex = 0; halfIndex < halfDoseCandidates.length; halfIndex++) {
        for (int hillIndex = 0; hillIndex < hillCandidates.length; hillIndex++) {
          for (int optimumIndex = 0; optimumIndex < optimumCandidates.length; optimumIndex++) {
            for (int overdoseIndex =
                0; overdoseIndex < overdoseCandidates.length; overdoseIndex++) {
              maxRemovalFraction = maxRemoval;
              halfEffectDosePpm = halfDoseCandidates[halfIndex];
              hillCoefficient = hillCandidates[hillIndex];
              optimumDosePpm = Math.max(minPositiveDose, optimumCandidates[optimumIndex]);
              overdoseSensitivity = overdoseCandidates[overdoseIndex];
              double error = calculateRootMeanSquareError(dosePpm, observedOilInWaterMgL,
                  untreatedOilInWaterMgL);
              if (error < bestError) {
                bestError = error;
                bestMaxRemoval = maxRemovalFraction;
                bestHalfDose = halfEffectDosePpm;
                bestHill = hillCoefficient;
                bestOptimum = optimumDosePpm;
                bestOverdose = overdoseSensitivity;
              }
            }
          }
        }
      }
    }

    maxRemovalFraction = bestMaxRemoval;
    halfEffectDosePpm = bestHalfDose;
    hillCoefficient = bestHill;
    optimumDosePpm = bestOptimum;
    overdoseSensitivity = bestOverdose;
    lastRootMeanSquareError = bestError;
    return lastRootMeanSquareError;
  }

  /**
   * Returns model parameters as a map for programmatic reporting.
   *
   * @return parameter map
   */
  public Map<String, Object> getModelParameters() {
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    parameters.put("maxRemovalFraction", maxRemovalFraction);
    parameters.put("halfEffectDosePpm", halfEffectDosePpm);
    parameters.put("hillCoefficient", hillCoefficient);
    parameters.put("optimumDosePpm", optimumDosePpm);
    parameters.put("overdoseSensitivity", overdoseSensitivity);
    parameters.put("minimumOilInWaterMgL", minimumOilInWaterMgL);
    parameters.put("lastRootMeanSquareError", lastRootMeanSquareError);
    return parameters;
  }

  /**
   * Serializes the model state and last result to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("parameters", getModelParameters());
    data.put("lastDosePpm", lastDosePpm);
    data.put("lastEffectiveRemovalFraction", lastEffectiveRemovalFraction);
    data.put("lastPredictedOilInWaterMgL", lastPredictedOilInWaterMgL);
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(data);
  }

  /**
   * Calculates the overdose penalty for a dose.
   *
   * @param dosePpm dose in ppm
   * @return penalty fraction in the range 0 to 0.95
   */
  private double calculateOverdosePenalty(double dosePpm) {
    if (dosePpm <= optimumDosePpm || optimumDosePpm <= 0.0 || overdoseSensitivity <= 0.0) {
      return 0.0;
    }
    double relativeExcess = (dosePpm - optimumDosePpm) / optimumDosePpm;
    return Math.min(0.95, overdoseSensitivity * relativeExcess * relativeExcess);
  }

  /**
   * Validates calibration arrays before fitting.
   *
   * @param dosePpm dose array
   * @param observedOilInWaterMgL observed OIW array
   * @throws IllegalArgumentException if the arrays cannot be calibrated
   */
  private void validateCalibrationData(double[] dosePpm, double[] observedOilInWaterMgL) {
    if (dosePpm == null || observedOilInWaterMgL == null) {
      throw new IllegalArgumentException("Calibration arrays cannot be null");
    }
    if (dosePpm.length != observedOilInWaterMgL.length) {
      throw new IllegalArgumentException("Calibration arrays must have the same length");
    }
    if (dosePpm.length < 2) {
      throw new IllegalArgumentException("At least two calibration points are required");
    }
  }

  /**
   * Calculates RMSE for the current parameters.
   *
   * @param dosePpm dose array
   * @param observedOilInWaterMgL observed OIW array
   * @param untreatedOilInWaterMgL untreated OIW reference
   * @return root mean square error in mg/L
   */
  private double calculateRootMeanSquareError(double[] dosePpm, double[] observedOilInWaterMgL,
      double untreatedOilInWaterMgL) {
    double sumSquares = 0.0;
    for (int index = 0; index < dosePpm.length; index++) {
      double predicted = predictOilInWater(untreatedOilInWaterMgL, dosePpm[index]);
      double error = predicted - observedOilInWaterMgL[index];
      sumSquares += error * error;
    }
    return Math.sqrt(sumSquares / dosePpm.length);
  }

  /**
   * Builds half-dose candidates for the deterministic grid search.
   *
   * @param minPositiveDose minimum positive dose in the data
   * @param maxDose maximum dose in the data
   * @return array of candidate half-effect doses
   */
  private double[] buildHalfDoseCandidates(double minPositiveDose, double maxDose) {
    double low = Math.max(0.1, minPositiveDose * 0.25);
    double high = Math.max(low * 1.1, maxDose * 1.50);
    double[] candidates = new double[10];
    for (int index = 0; index < candidates.length; index++) {
      double fraction = index / (double) (candidates.length - 1);
      candidates[index] = low * Math.pow(high / low, fraction);
    }
    return candidates;
  }

  /**
   * Finds the smallest positive dose in an array.
   *
   * @param dosePpm dose array
   * @return smallest positive dose, or 1 if none exists
   */
  private double findMinPositiveDose(double[] dosePpm) {
    double minDose = Double.POSITIVE_INFINITY;
    for (int index = 0; index < dosePpm.length; index++) {
      if (dosePpm[index] > 0.0 && dosePpm[index] < minDose) {
        minDose = dosePpm[index];
      }
    }
    return Double.isInfinite(minDose) ? 1.0 : minDose;
  }

  /**
   * Finds the maximum dose in an array.
   *
   * @param dosePpm dose array
   * @return maximum dose
   */
  private double findMaxDose(double[] dosePpm) {
    double maxDose = 0.0;
    for (int index = 0; index < dosePpm.length; index++) {
      maxDose = Math.max(maxDose, dosePpm[index]);
    }
    return maxDose;
  }

  /**
   * Clamps a value to the zero-to-one interval.
   *
   * @param value raw value
   * @return clamped value
   */
  private double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  /**
   * Sets the maximum chemical removal fraction.
   *
   * @param maxRemovalFraction maximum removal fraction from 0 to 1
   */
  public void setMaxRemovalFraction(double maxRemovalFraction) {
    this.maxRemovalFraction = clamp01(maxRemovalFraction);
  }

  /**
   * Gets the maximum chemical removal fraction.
   *
   * @return maximum removal fraction
   */
  public double getMaxRemovalFraction() {
    return maxRemovalFraction;
  }

  /**
   * Sets the half-effect dose.
   *
   * @param halfEffectDosePpm half-effect dose in ppm
   */
  public void setHalfEffectDosePpm(double halfEffectDosePpm) {
    this.halfEffectDosePpm = Math.max(1.0e-9, halfEffectDosePpm);
  }

  /**
   * Gets the half-effect dose.
   *
   * @return half-effect dose in ppm
   */
  public double getHalfEffectDosePpm() {
    return halfEffectDosePpm;
  }

  /**
   * Sets the Hill coefficient.
   *
   * @param hillCoefficient Hill coefficient, usually 0.8 to 3.0
   */
  public void setHillCoefficient(double hillCoefficient) {
    this.hillCoefficient = Math.max(0.1, hillCoefficient);
  }

  /**
   * Gets the Hill coefficient.
   *
   * @return Hill coefficient
   */
  public double getHillCoefficient() {
    return hillCoefficient;
  }

  /**
   * Sets the optimum dose before overdose penalty starts.
   *
   * @param optimumDosePpm optimum dose in ppm
   */
  public void setOptimumDosePpm(double optimumDosePpm) {
    this.optimumDosePpm = Math.max(0.0, optimumDosePpm);
  }

  /**
   * Gets the optimum dose.
   *
   * @return optimum dose in ppm
   */
  public double getOptimumDosePpm() {
    return optimumDosePpm;
  }

  /**
   * Sets overdose sensitivity.
   *
   * @param overdoseSensitivity dimensionless overdose sensitivity
   */
  public void setOverdoseSensitivity(double overdoseSensitivity) {
    this.overdoseSensitivity = Math.max(0.0, overdoseSensitivity);
  }

  /**
   * Gets overdose sensitivity.
   *
   * @return overdose sensitivity
   */
  public double getOverdoseSensitivity() {
    return overdoseSensitivity;
  }

  /**
   * Sets the minimum reachable OIW floor.
   *
   * @param minimumOilInWaterMgL minimum OIW in mg/L
   */
  public void setMinimumOilInWaterMgL(double minimumOilInWaterMgL) {
    this.minimumOilInWaterMgL = Math.max(0.0, minimumOilInWaterMgL);
  }

  /**
   * Gets the minimum reachable OIW floor.
   *
   * @return minimum OIW in mg/L
   */
  public double getMinimumOilInWaterMgL() {
    return minimumOilInWaterMgL;
  }

  /**
   * Gets the last calculated effective removal fraction.
   *
   * @return last removal fraction
   */
  public double getLastEffectiveRemovalFraction() {
    return lastEffectiveRemovalFraction;
  }

  /**
   * Gets the last predicted OIW concentration.
   *
   * @return last predicted OIW in mg/L
   */
  public double getLastPredictedOilInWaterMgL() {
    return lastPredictedOilInWaterMgL;
  }

  /**
   * Gets the last calibration RMSE.
   *
   * @return last RMSE in mg/L
   */
  public double getLastRootMeanSquareError() {
    return lastRootMeanSquareError;
  }
}
