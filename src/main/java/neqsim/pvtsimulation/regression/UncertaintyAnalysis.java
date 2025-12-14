package neqsim.pvtsimulation.regression;

/**
 * Uncertainty analysis results for PVT regression.
 *
 * <p>
 * Provides parameter standard errors, confidence intervals, and correlation matrix from the
 * regression optimization.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class UncertaintyAnalysis {
  private double[] parameterValues;
  private double[] standardErrors;
  private double[][] correlationMatrix;
  private double[] confidenceIntervals95;
  private int degreesOfFreedom;
  private double residualVariance;

  /**
   * Create an uncertainty analysis result.
   *
   * @param parameterValues optimized parameter values
   * @param standardErrors standard errors for each parameter
   * @param correlationMatrix parameter correlation matrix
   * @param confidenceIntervals95 95% confidence interval half-widths
   * @param degreesOfFreedom degrees of freedom (N - p)
   * @param residualVariance residual variance (chi-square / DOF)
   */
  public UncertaintyAnalysis(double[] parameterValues, double[] standardErrors,
      double[][] correlationMatrix, double[] confidenceIntervals95, int degreesOfFreedom,
      double residualVariance) {
    this.parameterValues = parameterValues;
    this.standardErrors = standardErrors;
    this.correlationMatrix = correlationMatrix;
    this.confidenceIntervals95 = confidenceIntervals95;
    this.degreesOfFreedom = degreesOfFreedom;
    this.residualVariance = residualVariance;
  }

  /**
   * Get parameter values.
   *
   * @return parameter values
   */
  public double[] getParameterValues() {
    return parameterValues;
  }

  /**
   * Get parameter value by index.
   *
   * @param index parameter index
   * @return parameter value
   */
  public double getParameterValue(int index) {
    return parameterValues[index];
  }

  /**
   * Get standard errors.
   *
   * @return standard errors
   */
  public double[] getStandardErrors() {
    return standardErrors;
  }

  /**
   * Get standard error for a parameter.
   *
   * @param index parameter index
   * @return standard error
   */
  public double getStandardError(int index) {
    return standardErrors[index];
  }

  /**
   * Get the correlation matrix.
   *
   * @return correlation matrix
   */
  public double[][] getCorrelationMatrix() {
    return correlationMatrix;
  }

  /**
   * Get correlation between two parameters.
   *
   * @param i first parameter index
   * @param j second parameter index
   * @return correlation coefficient
   */
  public double getCorrelation(int i, int j) {
    return correlationMatrix[i][j];
  }

  /**
   * Get 95% confidence interval half-widths.
   *
   * @return confidence interval half-widths
   */
  public double[] getConfidenceIntervals95() {
    return confidenceIntervals95;
  }

  /**
   * Get 95% confidence interval half-width for a parameter.
   *
   * @param index parameter index
   * @return confidence interval half-width
   */
  public double getConfidenceInterval95(int index) {
    return confidenceIntervals95[index];
  }

  /**
   * Get 95% confidence interval bounds for a parameter.
   *
   * @param index parameter index
   * @return [lower, upper] bounds
   */
  public double[] getConfidenceIntervalBounds(int index) {
    double value = parameterValues[index];
    double ci = confidenceIntervals95[index];
    return new double[] {value - ci, value + ci};
  }

  /**
   * Get degrees of freedom.
   *
   * @return degrees of freedom
   */
  public int getDegreesOfFreedom() {
    return degreesOfFreedom;
  }

  /**
   * Get residual variance.
   *
   * @return residual variance
   */
  public double getResidualVariance() {
    return residualVariance;
  }

  /**
   * Get root mean square error.
   *
   * @return RMSE
   */
  public double getRMSE() {
    return Math.sqrt(residualVariance);
  }

  /**
   * Check if parameters are significantly correlated (|r| &gt; 0.8).
   *
   * @return true if high correlations exist
   */
  public boolean hasHighCorrelations() {
    for (int i = 0; i < correlationMatrix.length; i++) {
      for (int j = i + 1; j < correlationMatrix[i].length; j++) {
        if (Math.abs(correlationMatrix[i][j]) > 0.8) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Get relative uncertainty (standard error / value) as percentage.
   *
   * @param index parameter index
   * @return relative uncertainty %
   */
  public double getRelativeUncertainty(int index) {
    if (Math.abs(parameterValues[index]) < 1e-10) {
      return Double.NaN;
    }
    return 100.0 * standardErrors[index] / Math.abs(parameterValues[index]);
  }

  /**
   * Generate a summary report.
   *
   * @return summary string
   */
  public String generateSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Uncertainty Analysis ===\n\n");

    sb.append("Parameter Estimates:\n");
    for (int i = 0; i < parameterValues.length; i++) {
      sb.append(String.format("  Parameter %d: %.6f ± %.6f (%.1f%%)\n", i, parameterValues[i],
          confidenceIntervals95[i], getRelativeUncertainty(i)));
    }

    sb.append("\nStatistics:\n");
    sb.append(String.format("  Degrees of Freedom: %d\n", degreesOfFreedom));
    sb.append(String.format("  Residual Variance: %.6f\n", residualVariance));
    sb.append(String.format("  RMSE: %.6f\n", getRMSE()));

    if (hasHighCorrelations()) {
      sb.append("\nWarning: High parameter correlations detected (|r| > 0.8)\n");
    }

    sb.append("\nCorrelation Matrix:\n");
    for (int i = 0; i < correlationMatrix.length; i++) {
      sb.append("  ");
      for (int j = 0; j < correlationMatrix[i].length; j++) {
        sb.append(String.format("%7.3f ", correlationMatrix[i][j]));
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return generateSummary();
  }
}
