package neqsim.process.calibration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Container for batch parameter estimation results.
 *
 * <p>
 * This class holds the results from a Levenberg-Marquardt batch optimization including:
 * </p>
 * <ul>
 * <li>Optimized parameter values</li>
 * <li>Parameter uncertainties (standard deviations from covariance matrix)</li>
 * <li>95% confidence intervals</li>
 * <li>Goodness-of-fit statistics (chi-square, RMSE, R-squared)</li>
 * <li>Convergence information</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * BatchResult result = estimator.solve();
 * 
 * double[] estimates = result.getEstimates();
 * double[] uncertainties = result.getUncertainties();
 * double rmse = result.getRMSE();
 * 
 * // Get 95% confidence intervals
 * double[] lowerCI = result.getConfidenceIntervalLower();
 * double[] upperCI = result.getConfidenceIntervalUpper();
 * 
 * // Check convergence
 * if (result.isConverged()) {
 *   System.out.println("Optimization converged in " + result.getIterations() + " iterations");
 * }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see BatchParameterEstimator
 */
public class BatchResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Parameter names in order. */
  private final String[] parameterNames;

  /** Optimized parameter values. */
  private final double[] estimates;

  /** Standard deviations of parameter estimates. */
  private final double[] uncertainties;

  /** 95% confidence interval lower bounds. */
  private final double[] confidenceIntervalLower;

  /** 95% confidence interval upper bounds. */
  private final double[] confidenceIntervalUpper;

  /** Chi-square value (sum of squared weighted residuals). */
  private final double chiSquare;

  /** Root mean square error. */
  private final double rmse;

  /** Mean absolute deviation. */
  private final double meanAbsoluteDeviation;

  /** Bias (mean signed deviation). */
  private final double bias;

  /** R-squared (coefficient of determination). */
  private final double rSquared;

  /** Number of iterations used. */
  private final int iterations;

  /** Number of data points used. */
  private final int dataPointCount;

  /** Whether the optimization converged. */
  private final boolean converged;

  /** Covariance matrix of parameter estimates. */
  private final double[][] covarianceMatrix;

  /** Correlation matrix of parameter estimates. */
  private final double[][] correlationMatrix;

  /**
   * Creates a new batch result.
   *
   * @param parameterNames names of the parameters
   * @param estimates optimized parameter values
   * @param uncertainties standard deviations
   * @param chiSquare chi-square value
   * @param iterations number of iterations
   * @param dataPointCount number of data points
   * @param converged whether optimization converged
   */
  public BatchResult(String[] parameterNames, double[] estimates, double[] uncertainties,
      double chiSquare, int iterations, int dataPointCount, boolean converged) {
    this(parameterNames, estimates, uncertainties, chiSquare, iterations, dataPointCount, converged,
        null, null, Double.NaN, Double.NaN, Double.NaN);
  }

  /**
   * Full constructor with all statistics.
   *
   * @param parameterNames names of the parameters
   * @param estimates optimized parameter values
   * @param uncertainties standard deviations
   * @param chiSquare chi-square value
   * @param iterations number of iterations
   * @param dataPointCount number of data points
   * @param converged whether optimization converged
   * @param covarianceMatrix parameter covariance matrix
   * @param correlationMatrix parameter correlation matrix
   * @param meanAbsoluteDeviation mean absolute deviation
   * @param bias mean signed deviation
   * @param rSquared coefficient of determination
   */
  public BatchResult(String[] parameterNames, double[] estimates, double[] uncertainties,
      double chiSquare, int iterations, int dataPointCount, boolean converged,
      double[][] covarianceMatrix, double[][] correlationMatrix, double meanAbsoluteDeviation,
      double bias, double rSquared) {
    this.parameterNames = parameterNames != null ? parameterNames.clone() : new String[0];
    this.estimates = estimates != null ? estimates.clone() : new double[0];
    this.uncertainties = uncertainties != null ? uncertainties.clone() : new double[0];
    this.chiSquare = chiSquare;
    this.iterations = iterations;
    this.dataPointCount = dataPointCount;
    this.converged = converged;
    this.covarianceMatrix = covarianceMatrix;
    this.correlationMatrix = correlationMatrix;
    this.meanAbsoluteDeviation = meanAbsoluteDeviation;
    this.bias = bias;
    this.rSquared = rSquared;

    // Compute RMSE from chi-square
    this.rmse = dataPointCount > 0 ? Math.sqrt(chiSquare / dataPointCount) : Double.NaN;

    // Compute 95% confidence intervals (1.96 * std for normal distribution)
    this.confidenceIntervalLower = new double[this.estimates.length];
    this.confidenceIntervalUpper = new double[this.estimates.length];
    for (int i = 0; i < this.estimates.length; i++) {
      double halfWidth = 1.96 * (i < this.uncertainties.length ? this.uncertainties[i] : 0);
      this.confidenceIntervalLower[i] = this.estimates[i] - halfWidth;
      this.confidenceIntervalUpper[i] = this.estimates[i] + halfWidth;
    }
  }

  /**
   * Gets the optimized parameter estimates.
   *
   * @return array of parameter values
   */
  public double[] getEstimates() {
    return estimates.clone();
  }

  /**
   * Gets a specific parameter estimate.
   *
   * @param index parameter index
   * @return the parameter value
   */
  public double getEstimate(int index) {
    return estimates[index];
  }

  /**
   * Gets a specific parameter estimate by name.
   *
   * @param name parameter name
   * @return the parameter value, or NaN if not found
   */
  public double getEstimate(String name) {
    for (int i = 0; i < parameterNames.length; i++) {
      if (parameterNames[i].equals(name)) {
        return estimates[i];
      }
    }
    return Double.NaN;
  }

  /**
   * Gets the parameter uncertainties (standard deviations).
   *
   * @return array of standard deviations
   */
  public double[] getUncertainties() {
    return uncertainties.clone();
  }

  /**
   * Gets a specific parameter uncertainty.
   *
   * @param index parameter index
   * @return the standard deviation
   */
  public double getUncertainty(int index) {
    return uncertainties[index];
  }

  /**
   * Gets the 95% confidence interval lower bounds.
   *
   * @return array of lower bounds
   */
  public double[] getConfidenceIntervalLower() {
    return confidenceIntervalLower.clone();
  }

  /**
   * Gets the 95% confidence interval upper bounds.
   *
   * @return array of upper bounds
   */
  public double[] getConfidenceIntervalUpper() {
    return confidenceIntervalUpper.clone();
  }

  /**
   * Gets the parameter names.
   *
   * @return array of parameter names
   */
  public String[] getParameterNames() {
    return parameterNames.clone();
  }

  /**
   * Gets the chi-square value.
   *
   * @return sum of squared weighted residuals
   */
  public double getChiSquare() {
    return chiSquare;
  }

  /**
   * Gets the root mean square error.
   *
   * @return RMSE
   */
  public double getRMSE() {
    return rmse;
  }

  /**
   * Gets the mean absolute deviation.
   *
   * @return mean absolute deviation
   */
  public double getMeanAbsoluteDeviation() {
    return meanAbsoluteDeviation;
  }

  /**
   * Gets the bias (mean signed deviation).
   *
   * @return bias
   */
  public double getBias() {
    return bias;
  }

  /**
   * Gets the R-squared (coefficient of determination).
   *
   * @return R-squared value
   */
  public double getRSquared() {
    return rSquared;
  }

  /**
   * Gets the number of iterations used.
   *
   * @return number of iterations
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Gets the number of data points used.
   *
   * @return number of data points
   */
  public int getDataPointCount() {
    return dataPointCount;
  }

  /**
   * Checks if the optimization converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Gets the covariance matrix.
   *
   * @return the covariance matrix, or null if not computed
   */
  public double[][] getCovarianceMatrix() {
    return covarianceMatrix;
  }

  /**
   * Gets the correlation matrix.
   *
   * @return the correlation matrix, or null if not computed
   */
  public double[][] getCorrelationMatrix() {
    return correlationMatrix;
  }

  /**
   * Gets the degrees of freedom (data points - parameters).
   *
   * @return degrees of freedom
   */
  public int getDegreesOfFreedom() {
    return dataPointCount - estimates.length;
  }

  /**
   * Gets the reduced chi-square (chi-square per degree of freedom).
   *
   * @return reduced chi-square
   */
  public double getReducedChiSquare() {
    int dof = getDegreesOfFreedom();
    return dof > 0 ? chiSquare / dof : Double.NaN;
  }

  /**
   * Converts to a map of parameter names to values.
   *
   * @return map of parameter estimates
   */
  public Map<String, Double> toMap() {
    Map<String, Double> map = new LinkedHashMap<>();
    for (int i = 0; i < parameterNames.length; i++) {
      map.put(parameterNames[i], estimates[i]);
    }
    return map;
  }

  /**
   * Converts to a {@link CalibrationResult} for API compatibility.
   *
   * @return calibration result
   */
  public CalibrationResult toCalibrationResult() {
    if (converged) {
      return CalibrationResult.success(toMap(), rmse, iterations, dataPointCount);
    } else {
      return CalibrationResult.failure("Optimization did not converge after " + iterations
          + " iterations. Chi-square: " + chiSquare);
    }
  }

  /**
   * Prints a summary of the results.
   */
  public void printSummary() {
    System.out.println("=== Batch Parameter Estimation Results ===");
    System.out.println("Converged: " + converged);
    System.out.println("Iterations: " + iterations);
    System.out.println("Data points: " + dataPointCount);
    System.out.println("Degrees of freedom: " + getDegreesOfFreedom());
    System.out.println();
    System.out.println("Goodness of Fit:");
    System.out.printf("  Chi-square: %.6f%n", chiSquare);
    System.out.printf("  Reduced chi-square: %.6f%n", getReducedChiSquare());
    System.out.printf("  RMSE: %.6f%n", rmse);
    if (!Double.isNaN(meanAbsoluteDeviation)) {
      System.out.printf("  Mean absolute deviation: %.6f%n", meanAbsoluteDeviation);
    }
    if (!Double.isNaN(bias)) {
      System.out.printf("  Bias: %.6f%n", bias);
    }
    if (!Double.isNaN(rSquared)) {
      System.out.printf("  R-squared: %.6f%n", rSquared);
    }
    System.out.println();
    System.out.println("Parameter Estimates:");
    System.out.printf("%-40s %12s %12s %20s%n", "Parameter", "Value", "Std Dev", "95% CI");
    printSeparator(86);
    for (int i = 0; i < parameterNames.length; i++) {
      System.out.printf("%-40s %12.6f %12.6f [%8.4f, %8.4f]%n", parameterNames[i], estimates[i],
          uncertainties[i], confidenceIntervalLower[i], confidenceIntervalUpper[i]);
    }
  }

  /**
   * Prints a separator line.
   *
   * @param length length of the separator
   */
  private void printSeparator(int length) {
    System.out.println(StringUtils.repeat("-", length));
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "BatchResult{converged=" + converged + ", iterations=" + iterations + ", chiSquare="
        + chiSquare + ", rmse=" + rmse + ", estimates=" + Arrays.toString(estimates) + "}";
  }
}
