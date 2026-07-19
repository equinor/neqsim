package neqsim.process.equipment.reactor.digestion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fits first-order hydrolysis parameters to measured volatile-solids destruction.
 *
 * <p>
 * For each candidate hydrolysis rate, the least-squares optimum maximum destruction is obtained analytically and
 * bounded to the physical interval. A coarse logarithmic search followed by golden-section refinement makes the fit
 * deterministic and independent of optional numerical libraries. At least two distinct effective exposures are required
 * to identify both fitted parameters.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class FirstOrderHydrolysisModelCalibrator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Golden-ratio conjugate used by the bounded one-dimensional search. */
  private static final double GOLDEN_RATIO_CONJUGATE = 0.3819660112501051;

  /** Calibration observations. */
  private final List<CalibrationObservation> observations = new ArrayList<CalibrationObservation>();
  /** Reference temperature in Kelvin. */
  private double referenceTemperatureK = 308.15;
  /** Q10 temperature coefficient. */
  private double q10 = 2.0;
  /** Lower hydrolysis-rate search bound in 1/day. */
  private double minimumHydrolysisRatePerDay = 1.0e-4;
  /** Upper hydrolysis-rate search bound in 1/day. */
  private double maximumHydrolysisRatePerDay = 2.0;

  /**
   * Adds a measured conversion observation.
   *
   * @param hydraulicRetentionTimeDays hydraulic retention time in days
   * @param temperatureK operating temperature in Kelvin
   * @param measuredVsDestruction measured destroyed-VS fraction
   * @return this calibrator
   */
  public FirstOrderHydrolysisModelCalibrator addObservation(double hydraulicRetentionTimeDays, double temperatureK,
      double measuredVsDestruction) {
    observations.add(new CalibrationObservation(hydraulicRetentionTimeDays, temperatureK, measuredVsDestruction));
    return this;
  }

  /**
   * Sets the temperature-correction basis used during fitting and prediction.
   *
   * @param referenceTemperatureK reference temperature in Kelvin
   * @param q10 temperature coefficient
   */
  public void setTemperatureCorrection(double referenceTemperatureK, double q10) {
    if (!Double.isFinite(referenceTemperatureK) || referenceTemperatureK <= 0.0 || !Double.isFinite(q10)
        || q10 <= 0.0) {
      throw new IllegalArgumentException("Temperature-correction values must be finite and positive");
    }
    this.referenceTemperatureK = referenceTemperatureK;
    this.q10 = q10;
  }

  /**
   * Sets physical search bounds for the hydrolysis rate.
   *
   * @param minimumRatePerDay lower positive bound in 1/day
   * @param maximumRatePerDay upper bound in 1/day
   */
  public void setHydrolysisRateBounds(double minimumRatePerDay, double maximumRatePerDay) {
    if (!Double.isFinite(minimumRatePerDay) || !Double.isFinite(maximumRatePerDay) || minimumRatePerDay <= 0.0
        || maximumRatePerDay <= minimumRatePerDay) {
      throw new IllegalArgumentException("Hydrolysis-rate bounds must be finite, positive, and increasing");
    }
    minimumHydrolysisRatePerDay = minimumRatePerDay;
    maximumHydrolysisRatePerDay = maximumRatePerDay;
  }

  /**
   * Fits the model and attaches the supplied evidence reference.
   *
   * @param evidenceReference data-set, laboratory campaign, pilot, or operating-data reference
   * @return calibration result and ready-to-use calibrated model
   */
  public CalibrationResult calibrate(String evidenceReference) {
    validateCalibrationBasis(evidenceReference);
    double logMinimum = Math.log(minimumHydrolysisRatePerDay);
    double logMaximum = Math.log(maximumHydrolysisRatePerDay);
    int coarseIntervals = 160;
    int bestIndex = 0;
    FitPoint best = null;
    for (int i = 0; i <= coarseIntervals; i++) {
      double logRate = logMinimum + (logMaximum - logMinimum) * i / coarseIntervals;
      FitPoint candidate = evaluate(logRate);
      if (best == null || candidate.sumSquaredError < best.sumSquaredError) {
        best = candidate;
        bestIndex = i;
      }
    }

    double interval = (logMaximum - logMinimum) / coarseIntervals;
    double left = Math.max(logMinimum, logMinimum + (bestIndex - 1) * interval);
    double right = Math.min(logMaximum, logMinimum + (bestIndex + 1) * interval);
    FitPoint refined = goldenSectionSearch(left, right);
    if (refined.sumSquaredError < best.sumSquaredError) {
      best = refined;
    }

    double minimumRetentionTime = Double.POSITIVE_INFINITY;
    double maximumRetentionTime = 0.0;
    double minimumTemperature = Double.POSITIVE_INFINITY;
    double maximumTemperature = 0.0;
    double mean = 0.0;
    for (CalibrationObservation observation : observations) {
      minimumRetentionTime = Math.min(minimumRetentionTime, observation.hydraulicRetentionTimeDays);
      maximumRetentionTime = Math.max(maximumRetentionTime, observation.hydraulicRetentionTimeDays);
      minimumTemperature = Math.min(minimumTemperature, observation.temperatureK);
      maximumTemperature = Math.max(maximumTemperature, observation.temperatureK);
      mean += observation.measuredVsDestruction;
    }
    mean /= observations.size();
    double totalSumOfSquares = 0.0;
    for (CalibrationObservation observation : observations) {
      double residual = observation.measuredVsDestruction - mean;
      totalSumOfSquares += residual * residual;
    }
    double rSquared = totalSumOfSquares <= 1.0e-20 ? (best.sumSquaredError <= 1.0e-20 ? 1.0 : 0.0)
        : 1.0 - best.sumSquaredError / totalSumOfSquares;
    double rmse = Math.sqrt(best.sumSquaredError / observations.size());
    boolean atSearchBoundary = best.hydrolysisRatePerDay <= minimumHydrolysisRatePerDay * 1.0001
        || best.hydrolysisRatePerDay >= maximumHydrolysisRatePerDay / 1.0001;

    CalibratedFirstOrderHydrolysisDigestionModel model = new CalibratedFirstOrderHydrolysisDigestionModel(
        best.maximumVsDestruction, best.hydrolysisRatePerDay, evidenceReference, minimumRetentionTime,
        maximumRetentionTime, minimumTemperature, maximumTemperature, referenceTemperatureK, q10);
    return new CalibrationResult(model, observations.size(), rmse, rSquared, atSearchBoundary);
  }

  /**
   * Performs a bounded golden-section minimization in logarithmic hydrolysis-rate space.
   *
   * @param left left log-rate bound
   * @param right right log-rate bound
   * @return best fit point
   */
  private FitPoint goldenSectionSearch(double left, double right) {
    double x1 = left + GOLDEN_RATIO_CONJUGATE * (right - left);
    double x2 = right - GOLDEN_RATIO_CONJUGATE * (right - left);
    FitPoint f1 = evaluate(x1);
    FitPoint f2 = evaluate(x2);
    for (int iteration = 0; iteration < 80; iteration++) {
      if (f1.sumSquaredError <= f2.sumSquaredError) {
        right = x2;
        x2 = x1;
        f2 = f1;
        x1 = left + GOLDEN_RATIO_CONJUGATE * (right - left);
        f1 = evaluate(x1);
      } else {
        left = x1;
        x1 = x2;
        f1 = f2;
        x2 = right - GOLDEN_RATIO_CONJUGATE * (right - left);
        f2 = evaluate(x2);
      }
    }
    return f1.sumSquaredError <= f2.sumSquaredError ? f1 : f2;
  }

  /**
   * Evaluates the least-squares fit at one logarithmic hydrolysis rate.
   *
   * @param logarithmicRate natural logarithm of the rate in 1/day
   * @return fitted maximum destruction and residual error
   */
  private FitPoint evaluate(double logarithmicRate) {
    double rate = Math.exp(logarithmicRate);
    double coefficientObservationSum = 0.0;
    double coefficientSquaredSum = 0.0;
    for (CalibrationObservation observation : observations) {
      double coefficient = conversionCoefficient(rate, observation);
      coefficientObservationSum += coefficient * observation.measuredVsDestruction;
      coefficientSquaredSum += coefficient * coefficient;
    }
    double maximumDestruction = coefficientSquaredSum <= 1.0e-30 ? 1.0
        : coefficientObservationSum / coefficientSquaredSum;
    maximumDestruction = Math.max(1.0e-9, Math.min(1.0, maximumDestruction));
    double sumSquaredError = 0.0;
    for (CalibrationObservation observation : observations) {
      double predicted = maximumDestruction * conversionCoefficient(rate, observation);
      double residual = predicted - observation.measuredVsDestruction;
      sumSquaredError += residual * residual;
    }
    return new FitPoint(rate, maximumDestruction, sumSquaredError);
  }

  /**
   * Calculates the bounded kinetic coefficient multiplying maximum destruction.
   *
   * @param rate hydrolysis rate in 1/day
   * @param observation calibration observation
   * @return coefficient between zero and one
   */
  private double conversionCoefficient(double rate, CalibrationObservation observation) {
    double temperatureFactor = Math.pow(q10, (observation.temperatureK - referenceTemperatureK) / 10.0);
    temperatureFactor = Math.max(0.25, Math.min(2.0, temperatureFactor));
    double kineticConversion = 1.0 - Math.exp(-rate * observation.hydraulicRetentionTimeDays);
    return Math.max(0.0, Math.min(1.0, kineticConversion * temperatureFactor));
  }

  /**
   * Validates evidence and parameter identifiability.
   *
   * @param evidenceReference evidence reference
   */
  private void validateCalibrationBasis(String evidenceReference) {
    if (evidenceReference == null || evidenceReference.trim().isEmpty()) {
      throw new IllegalArgumentException("A calibration evidence reference must be provided");
    }
    if (observations.size() < 2) {
      throw new IllegalStateException("At least two calibration observations are required");
    }
    CalibrationObservation first = observations.get(0);
    boolean distinctRetentionTime = false;
    for (int i = 1; i < observations.size(); i++) {
      CalibrationObservation candidate = observations.get(i);
      if (Math.abs(candidate.hydraulicRetentionTimeDays - first.hydraulicRetentionTimeDays) > 1.0e-12) {
        distinctRetentionTime = true;
        break;
      }
    }
    if (!distinctRetentionTime) {
      throw new IllegalStateException("Calibration observations must include distinct retention times");
    }
  }

  /** @return immutable calibration observations */
  public List<CalibrationObservation> getObservations() {
    return Collections.unmodifiableList(observations);
  }

  /** Immutable measured conversion point. */
  public static final class CalibrationObservation implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Hydraulic retention time in days. */
    private final double hydraulicRetentionTimeDays;
    /** Operating temperature in Kelvin. */
    private final double temperatureK;
    /** Measured VS destruction fraction. */
    private final double measuredVsDestruction;

    /**
     * Creates a measured conversion point.
     *
     * @param hydraulicRetentionTimeDays hydraulic retention time in days
     * @param temperatureK temperature in Kelvin
     * @param measuredVsDestruction measured VS destruction fraction
     */
    private CalibrationObservation(double hydraulicRetentionTimeDays, double temperatureK,
        double measuredVsDestruction) {
      if (!Double.isFinite(hydraulicRetentionTimeDays) || hydraulicRetentionTimeDays <= 0.0
          || !Double.isFinite(temperatureK) || temperatureK <= 0.0 || !Double.isFinite(measuredVsDestruction)
          || measuredVsDestruction < 0.0 || measuredVsDestruction > 1.0) {
        throw new IllegalArgumentException("Calibration observations must be finite and physically bounded");
      }
      this.hydraulicRetentionTimeDays = hydraulicRetentionTimeDays;
      this.temperatureK = temperatureK;
      this.measuredVsDestruction = measuredVsDestruction;
    }

    /** @return hydraulic retention time in days */
    public double getHydraulicRetentionTimeDays() {
      return hydraulicRetentionTimeDays;
    }

    /** @return temperature in Kelvin */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return measured VS destruction fraction */
    public double getMeasuredVsDestruction() {
      return measuredVsDestruction;
    }
  }

  /** Immutable calibration summary. */
  public static final class CalibrationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Fitted model. */
    private final CalibratedFirstOrderHydrolysisDigestionModel model;
    /** Number of fitted observations. */
    private final int observationCount;
    /** Root-mean-square error in VS-destruction fraction. */
    private final double rootMeanSquaredError;
    /** Coefficient of determination. */
    private final double rSquared;
    /** Whether the fitted rate reached a configured search boundary. */
    private final boolean hydrolysisRateAtSearchBoundary;

    /**
     * Creates a calibration summary.
     *
     * @param model fitted model
     * @param observationCount number of observations
     * @param rootMeanSquaredError RMSE in VS-destruction fraction
     * @param rSquared coefficient of determination
     * @param hydrolysisRateAtSearchBoundary true when the optimum is at a search bound
     */
    private CalibrationResult(CalibratedFirstOrderHydrolysisDigestionModel model, int observationCount,
        double rootMeanSquaredError, double rSquared, boolean hydrolysisRateAtSearchBoundary) {
      this.model = model;
      this.observationCount = observationCount;
      this.rootMeanSquaredError = rootMeanSquaredError;
      this.rSquared = rSquared;
      this.hydrolysisRateAtSearchBoundary = hydrolysisRateAtSearchBoundary;
    }

    /** @return fitted calibrated model */
    public CalibratedFirstOrderHydrolysisDigestionModel getModel() {
      return model;
    }

    /** @return number of calibration observations */
    public int getObservationCount() {
      return observationCount;
    }

    /** @return root-mean-square error in VS-destruction fraction */
    public double getRootMeanSquaredError() {
      return rootMeanSquaredError;
    }

    /** @return coefficient of determination */
    public double getRSquared() {
      return rSquared;
    }

    /** @return true when fitted hydrolysis rate reached a configured search boundary */
    public boolean isHydrolysisRateAtSearchBoundary() {
      return hydrolysisRateAtSearchBoundary;
    }
  }

  /** Internal least-squares evaluation. */
  private static final class FitPoint {
    /** Hydrolysis rate in 1/day. */
    private final double hydrolysisRatePerDay;
    /** Fitted maximum destruction. */
    private final double maximumVsDestruction;
    /** Sum of squared residuals. */
    private final double sumSquaredError;

    /**
     * Creates a fit point.
     *
     * @param hydrolysisRatePerDay hydrolysis rate in 1/day
     * @param maximumVsDestruction maximum destruction fraction
     * @param sumSquaredError residual sum of squares
     */
    private FitPoint(double hydrolysisRatePerDay, double maximumVsDestruction, double sumSquaredError) {
      this.hydrolysisRatePerDay = hydrolysisRatePerDay;
      this.maximumVsDestruction = maximumVsDestruction;
      this.sumSquaredError = sumSquaredError;
    }
  }
}
