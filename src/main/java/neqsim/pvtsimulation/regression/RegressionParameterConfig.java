package neqsim.pvtsimulation.regression;

/**
 * Configuration for a regression parameter including bounds and initial guess.
 *
 * @author ESOL
 * @version 1.0
 */
public class RegressionParameterConfig {
  private RegressionParameter parameter;
  private double lowerBound;
  private double upperBound;
  private double initialGuess;
  private double optimizedValue = Double.NaN;

  /**
   * Create a regression parameter configuration.
   *
   * @param parameter the parameter type
   * @param lowerBound lower bound
   * @param upperBound upper bound
   * @param initialGuess initial guess
   */
  public RegressionParameterConfig(RegressionParameter parameter, double lowerBound,
      double upperBound, double initialGuess) {
    this.parameter = parameter;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.initialGuess = initialGuess;
  }

  /**
   * Get the parameter type.
   *
   * @return parameter type
   */
  public RegressionParameter getParameter() {
    return parameter;
  }

  /**
   * Get the lower bound.
   *
   * @return lower bound
   */
  public double getLowerBound() {
    return lowerBound;
  }

  /**
   * Set the lower bound.
   *
   * @param lowerBound lower bound
   */
  public void setLowerBound(double lowerBound) {
    this.lowerBound = lowerBound;
  }

  /**
   * Get the upper bound.
   *
   * @return upper bound
   */
  public double getUpperBound() {
    return upperBound;
  }

  /**
   * Set the upper bound.
   *
   * @param upperBound upper bound
   */
  public void setUpperBound(double upperBound) {
    this.upperBound = upperBound;
  }

  /**
   * Get the initial guess.
   *
   * @return initial guess
   */
  public double getInitialGuess() {
    return initialGuess;
  }

  /**
   * Set the initial guess.
   *
   * @param initialGuess initial guess
   */
  public void setInitialGuess(double initialGuess) {
    this.initialGuess = initialGuess;
  }

  /**
   * Get the optimized value after regression.
   *
   * @return optimized value
   */
  public double getOptimizedValue() {
    return optimizedValue;
  }

  /**
   * Set the optimized value.
   *
   * @param optimizedValue optimized value
   */
  public void setOptimizedValue(double optimizedValue) {
    this.optimizedValue = optimizedValue;
  }
}
