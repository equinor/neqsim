package neqsim.process.calibration;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents calibration quality metrics for model validation.
 *
 * <p>
 * Provides statistical measures to assess how well the calibrated model fits the measured data.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CalibrationQuality implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Instant timestamp;
  private final double rmse;
  private final double mse;
  private final double mae;
  private final double r2;
  private final int sampleCount;
  private final double coverage;

  /**
   * Constructor for CalibrationQuality.
   *
   * @param timestamp the time of quality assessment
   * @param rmse root mean square error
   * @param mse mean square error
   * @param mae mean absolute error
   * @param r2 coefficient of determination
   * @param sampleCount number of samples used
   * @param coverage percentage of operating range covered
   */
  public CalibrationQuality(Instant timestamp, double rmse, double mse, double mae, double r2,
      int sampleCount, double coverage) {
    this.timestamp = timestamp;
    this.rmse = rmse;
    this.mse = mse;
    this.mae = mae;
    this.r2 = r2;
    this.sampleCount = sampleCount;
    this.coverage = coverage;
  }

  /**
   * Gets the timestamp of quality assessment.
   *
   * @return timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the root mean square error.
   *
   * @return RMSE value
   */
  public double getRmse() {
    return rmse;
  }

  /**
   * Gets the mean square error.
   *
   * @return MSE value
   */
  public double getMse() {
    return mse;
  }

  /**
   * Gets the mean absolute error.
   *
   * @return MAE value
   */
  public double getMae() {
    return mae;
  }

  /**
   * Gets the coefficient of determination (R-squared).
   *
   * @return R2 value (0 to 1, higher is better)
   */
  public double getR2() {
    return r2;
  }

  /**
   * Gets the number of samples used in calibration.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return sampleCount;
  }

  /**
   * Gets the operating range coverage percentage.
   *
   * @return coverage (0 to 100)
   */
  public double getCoverage() {
    return coverage;
  }

  /**
   * Calculates an overall quality score (0-100).
   *
   * <p>
   * Score combines R2 (higher is better) and coverage metrics.
   * </p>
   *
   * @return overall score from 0 (poor) to 100 (excellent)
   */
  public double getOverallScore() {
    // Weight R2 (70%) and coverage (30%) for overall score
    double r2Score = Math.max(0, Math.min(1, r2)) * 70.0;
    double coverageScore = Math.max(0, Math.min(100, coverage)) * 0.3;
    return r2Score + coverageScore;
  }

  /**
   * Gets a qualitative rating of calibration quality.
   *
   * @return rating string: "Excellent", "Good", "Fair", or "Poor"
   */
  public String getRating() {
    double score = getOverallScore();
    if (score >= 90) {
      return "Excellent";
    } else if (score >= 70) {
      return "Good";
    } else if (score >= 50) {
      return "Fair";
    } else {
      return "Poor";
    }
  }

  /**
   * Determines if calibration quality is acceptable.
   *
   * @param r2Threshold minimum acceptable R2 value
   * @param rmseThreshold maximum acceptable RMSE
   * @return true if quality meets thresholds
   */
  public boolean isAcceptable(double r2Threshold, double rmseThreshold) {
    return r2 >= r2Threshold && rmse <= rmseThreshold;
  }

  @Override
  public String toString() {
    return String.format("CalibrationQuality[RMSE=%.4f, R2=%.4f, samples=%d, coverage=%.1f%%]",
        rmse, r2, sampleCount, coverage);
  }
}
