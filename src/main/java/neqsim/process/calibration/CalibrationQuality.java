package neqsim.process.calibration;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Metrics for assessing the quality of model calibration.
 *
 * @author ESOL
 * @version 1.0
 */
public class CalibrationQuality implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Instant calibrationTime;
  private final double meanAbsoluteError;
  private final double rootMeanSquareError;
  private final double maxAbsoluteError;
  private final double r2Score;
  private final int sampleCount;
  private final double coveragePercent;

  /**
   * Creates calibration quality metrics.
   *
   * @param calibrationTime when calibration was performed
   * @param mae mean absolute error
   * @param rmse root mean square error
   * @param maxError maximum absolute error
   * @param r2 R-squared score
   * @param samples number of samples used
   * @param coverage percentage of operating range covered
   */
  public CalibrationQuality(Instant calibrationTime, double mae, double rmse, double maxError,
      double r2, int samples, double coverage) {
    this.calibrationTime = calibrationTime;
    this.meanAbsoluteError = mae;
    this.rootMeanSquareError = rmse;
    this.maxAbsoluteError = maxError;
    this.r2Score = r2;
    this.sampleCount = samples;
    this.coveragePercent = coverage;
  }

  /**
   * Gets the calibration timestamp.
   *
   * @return calibration time
   */
  public Instant getCalibrationTime() {
    return calibrationTime;
  }

  /**
   * Gets the age of the calibration.
   *
   * @return duration since calibration
   */
  public Duration getCalibrationAge() {
    return Duration.between(calibrationTime, Instant.now());
  }

  /**
   * Gets the mean absolute error.
   *
   * @return MAE
   */
  public double getMeanAbsoluteError() {
    return meanAbsoluteError;
  }

  /**
   * Gets the root mean square error.
   *
   * @return RMSE
   */
  public double getRootMeanSquareError() {
    return rootMeanSquareError;
  }

  /**
   * Gets the maximum absolute error.
   *
   * @return max error
   */
  public double getMaxAbsoluteError() {
    return maxAbsoluteError;
  }

  /**
   * Gets the R-squared score.
   *
   * @return R2 (0-1, higher is better)
   */
  public double getR2Score() {
    return r2Score;
  }

  /**
   * Gets the number of samples used for calibration.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return sampleCount;
  }

  /**
   * Gets the operating range coverage percentage.
   *
   * @return coverage percent
   */
  public double getCoveragePercent() {
    return coveragePercent;
  }

  /**
   * Calculates an overall quality score (0-100).
   *
   * @return quality score
   */
  public double getOverallScore() {
    double score = 0;

    // R2 contribution (40%)
    score += 40 * Math.max(0, r2Score);

    // Sample count contribution (20%)
    score += 20 * Math.min(1, sampleCount / 100.0);

    // Coverage contribution (20%)
    score += 20 * (coveragePercent / 100.0);

    // Age contribution (20%) - degrades over time
    long ageDays = getCalibrationAge().toDays();
    if (ageDays < 7) {
      score += 20;
    } else if (ageDays < 30) {
      score += 15;
    } else if (ageDays < 90) {
      score += 10;
    } else {
      score += 5;
    }

    return Math.max(0, Math.min(100, score));
  }

  /**
   * Gets a quality rating based on overall score.
   *
   * @return quality rating
   */
  public Rating getRating() {
    double score = getOverallScore();
    if (score >= 80) {
      return Rating.EXCELLENT;
    } else if (score >= 60) {
      return Rating.GOOD;
    } else if (score >= 40) {
      return Rating.FAIR;
    } else {
      return Rating.POOR;
    }
  }

  /**
   * Checks if recalibration is recommended.
   *
   * @return true if recalibration is needed
   */
  public boolean needsRecalibration() {
    return getOverallScore() < 50 || getCalibrationAge().toDays() > 30;
  }

  /**
   * Quality rating levels.
   */
  public enum Rating {
    EXCELLENT, GOOD, FAIR, POOR
  }

  @Override
  public String toString() {
    return String.format(
        "CalibrationQuality[RMSE=%.4f, R2=%.4f, samples=%d, coverage=%.1f%%, score=%.1f (%s)]",
        rootMeanSquareError, r2Score, sampleCount, coveragePercent, getOverallScore(), getRating());
  }
}
