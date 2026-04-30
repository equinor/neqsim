package neqsim.statistics.parameterfitting;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult;

/**
 * High-level workflow for fitting model parameters to experimental data sets.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ParameterFittingStudy {
  private final ExperimentalDataSet dataSet;
  private final BaseFunction function;
  private final LevenbergMarquardt optimizer = new LevenbergMarquardt();
  private String[] parameterNames;
  private Result result;

  /**
   * Creates a parameter fitting study.
   *
   * @param dataSet experimental data set to fit
   * @param function fitting function that predicts the measured response
   * @throws IllegalArgumentException if dataSet or function is null
   */
  public ParameterFittingStudy(ExperimentalDataSet dataSet, BaseFunction function) {
    if (dataSet == null) {
      throw new IllegalArgumentException("dataSet cannot be null");
    }
    if (function == null) {
      throw new IllegalArgumentException("function cannot be null");
    }
    this.dataSet = dataSet;
    this.function = function;
  }

  /**
   * Sets the initial parameter guess on the fitting function.
   *
   * @param initialGuess initial parameter values
   * @return this study for fluent configuration
   * @throws IllegalArgumentException if initialGuess is null, empty or contains non-finite values
   */
  public ParameterFittingStudy setInitialGuess(double[] initialGuess) {
    validateFiniteArray("initialGuess", initialGuess);
    function.setInitialGuess(copyArray(initialGuess));
    if (parameterNames == null || parameterNames.length != initialGuess.length) {
      parameterNames = createDefaultParameterNames(initialGuess.length);
    }
    return this;
  }

  /**
   * Sets names for the fitted parameters.
   *
   * @param parameterNames names matching the fitting parameter order
   * @return this study for fluent configuration
   * @throws IllegalArgumentException if names are null, empty or inconsistent with current params
   */
  public ParameterFittingStudy setParameterNames(String[] parameterNames) {
    if (parameterNames == null || parameterNames.length == 0) {
      throw new IllegalArgumentException("parameterNames must contain at least one name");
    }
    if (function.getFittingParams() != null
        && parameterNames.length != function.getFittingParams().length) {
      throw new IllegalArgumentException("parameterNames must match the number of fitting params");
    }
    this.parameterNames = copyArray(parameterNames);
    return this;
  }

  /**
   * Sets lower and upper bounds for fitted parameters.
   *
   * @param bounds bounds array where each row is {@code [lower, upper]}
   * @return this study for fluent configuration
   * @throws IllegalArgumentException if bounds are malformed or inconsistent with current params
   */
  public ParameterFittingStudy setParameterBounds(double[][] bounds) {
    validateBounds(bounds);
    function.setBounds(copyMatrix(bounds));
    return this;
  }

  /**
   * Sets the maximum number of optimizer iterations.
   *
   * @param maxNumberOfIterations positive iteration limit
   * @return this study for fluent configuration
   * @throws IllegalArgumentException if the iteration limit is not positive
   */
  public ParameterFittingStudy setMaxNumberOfIterations(int maxNumberOfIterations) {
    if (maxNumberOfIterations <= 0) {
      throw new IllegalArgumentException("maxNumberOfIterations must be positive");
    }
    optimizer.setMaxNumberOfIterations(maxNumberOfIterations);
    return this;
  }

  /**
   * Runs the parameter fitting study.
   *
   * @return fitting result with optimizer status and residual metrics
   * @throws IllegalStateException if no data points or initial parameter guess are available
   */
  public Result run() {
    if (dataSet.size() == 0) {
      throw new IllegalStateException("dataSet must contain at least one point");
    }
    if (function.getFittingParams() == null || function.getFittingParams().length == 0) {
      throw new IllegalStateException("setInitialGuess must be called before run");
    }
    optimizer.setSampleSet(dataSet.toSampleSet(function));
    optimizer.solve();
    result = createResult(optimizer.getResult());
    return result;
  }

  /**
   * Alias for {@link #run()}.
   *
   * @return fitting result with optimizer status and residual metrics
   */
  public Result fit() {
    return run();
  }

  /**
   * Returns the underlying optimizer.
   *
   * @return Levenberg-Marquardt optimizer used by this study
   */
  public LevenbergMarquardt getOptimizer() {
    return optimizer;
  }

  /**
   * Returns the experimental data set.
   *
   * @return experimental data set
   */
  public ExperimentalDataSet getDataSet() {
    return dataSet;
  }

  /**
   * Returns the fitting function.
   *
   * @return fitting function
   */
  public BaseFunction getFunction() {
    return function;
  }

  /**
   * Returns the most recent study result.
   *
   * @return most recent result, or null if the study has not run
   */
  public Result getResult() {
    return result;
  }

  /**
   * Creates a result object from the final fitted function state.
   *
   * @param optimizerResult optimizer result to include
   * @return fitting study result
   */
  private Result createResult(LevenbergMarquardtResult optimizerResult) {
    double[] calculatedValues = new double[dataSet.size()];
    double[] residuals = new double[dataSet.size()];
    double[] weightedResiduals = new double[dataSet.size()];
    double sumSquared = 0.0;
    double sumAbsolute = 0.0;
    double sumWeightedSquared = 0.0;
    for (int i = 0; i < dataSet.size(); i++) {
      ExperimentalDataPoint point = dataSet.getPoint(i);
      calculatedValues[i] = function.calcValue(point.getDependentValues());
      residuals[i] = point.getMeasuredValue() - calculatedValues[i];
      weightedResiduals[i] = residuals[i] / point.getStandardDeviation();
      sumSquared += residuals[i] * residuals[i];
      sumAbsolute += Math.abs(residuals[i]);
      sumWeightedSquared += weightedResiduals[i] * weightedResiduals[i];
    }
    double rootMeanSquareError = Math.sqrt(sumSquared / dataSet.size());
    double meanAbsoluteError = sumAbsolute / dataSet.size();
    double weightedRootMeanSquareError = Math.sqrt(sumWeightedSquared / dataSet.size());
    return new Result(optimizerResult, copyArray(function.getFittingParams()),
        resolveParameterNames(), calculatedValues, residuals, weightedResiduals,
        rootMeanSquareError, meanAbsoluteError, weightedRootMeanSquareError);
  }

  /**
   * Resolves configured or default parameter names.
   *
   * @return parameter names matching the current parameter count
   */
  private String[] resolveParameterNames() {
    int parameterCount = function.getFittingParams().length;
    if (parameterNames == null || parameterNames.length != parameterCount) {
      return createDefaultParameterNames(parameterCount);
    }
    return copyArray(parameterNames);
  }

  /**
   * Validates a finite double array.
   *
   * @param name array name used in error messages
   * @param values values to validate
   * @throws IllegalArgumentException if values are null, empty or contain non-finite entries
   */
  private static void validateFiniteArray(String name, double[] values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException(name + " must contain at least one value");
    }
    for (int i = 0; i < values.length; i++) {
      if (Double.isNaN(values[i]) || Double.isInfinite(values[i])) {
        throw new IllegalArgumentException(name + "[" + i + "] must be finite");
      }
    }
  }

  /**
   * Validates parameter bounds.
   *
   * @param bounds bounds to validate
   * @throws IllegalArgumentException if bounds are malformed or inconsistent with current params
   */
  private void validateBounds(double[][] bounds) {
    if (bounds == null || bounds.length == 0) {
      throw new IllegalArgumentException("bounds must contain at least one row");
    }
    if (function.getFittingParams() != null
        && bounds.length != function.getFittingParams().length) {
      throw new IllegalArgumentException("bounds must match the number of fitting params");
    }
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] == null || bounds[i].length != 2) {
        throw new IllegalArgumentException("bounds[" + i + "] must contain lower and upper values");
      }
      if (Double.isNaN(bounds[i][0]) || Double.isInfinite(bounds[i][0])
          || Double.isNaN(bounds[i][1]) || Double.isInfinite(bounds[i][1])) {
        throw new IllegalArgumentException("bounds[" + i + "] values must be finite");
      }
      if (bounds[i][0] > bounds[i][1]) {
        throw new IllegalArgumentException("bounds[" + i + "] lower value exceeds upper value");
      }
    }
  }

  /**
   * Creates default parameter names.
   *
   * @param parameterCount number of parameters
   * @return default names p0, p1, ...
   */
  private static String[] createDefaultParameterNames(int parameterCount) {
    String[] names = new String[parameterCount];
    for (int i = 0; i < parameterCount; i++) {
      names[i] = "p" + i;
    }
    return names;
  }

  /**
   * Copies a double array.
   *
   * @param values values to copy
   * @return copied array
   */
  private static double[] copyArray(double[] values) {
    double[] copy = new double[values.length];
    System.arraycopy(values, 0, copy, 0, values.length);
    return copy;
  }

  /**
   * Copies a string array.
   *
   * @param values values to copy
   * @return copied array
   */
  private static String[] copyArray(String[] values) {
    String[] copy = new String[values.length];
    System.arraycopy(values, 0, copy, 0, values.length);
    return copy;
  }

  /**
   * Copies a matrix array.
   *
   * @param values matrix values to copy
   * @return copied matrix
   */
  private static double[][] copyMatrix(double[][] values) {
    double[][] copy = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      copy[i] = copyArray(values[i]);
    }
    return copy;
  }

  /**
   * Result from a parameter fitting study.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Result {
    private final LevenbergMarquardtResult optimizerResult;
    private final double[] fittedParameters;
    private final String[] parameterNames;
    private final double[] calculatedValues;
    private final double[] residuals;
    private final double[] weightedResiduals;
    private final double rootMeanSquareError;
    private final double meanAbsoluteError;
    private final double weightedRootMeanSquareError;

    /**
     * Creates a result object.
     *
     * @param optimizerResult optimizer result
     * @param fittedParameters final fitted parameter values
     * @param parameterNames final fitted parameter names
     * @param calculatedValues model calculated values for each data point
     * @param residuals measured minus calculated residuals
     * @param weightedResiduals residuals divided by standard deviations
     * @param rootMeanSquareError unweighted root mean square error
     * @param meanAbsoluteError unweighted mean absolute error
     * @param weightedRootMeanSquareError weighted root mean square error
     */
    private Result(LevenbergMarquardtResult optimizerResult, double[] fittedParameters,
        String[] parameterNames, double[] calculatedValues, double[] residuals,
        double[] weightedResiduals, double rootMeanSquareError, double meanAbsoluteError,
        double weightedRootMeanSquareError) {
      this.optimizerResult = optimizerResult;
      this.fittedParameters = copyArray(fittedParameters);
      this.parameterNames = copyArray(parameterNames);
      this.calculatedValues = copyArray(calculatedValues);
      this.residuals = copyArray(residuals);
      this.weightedResiduals = copyArray(weightedResiduals);
      this.rootMeanSquareError = rootMeanSquareError;
      this.meanAbsoluteError = meanAbsoluteError;
      this.weightedRootMeanSquareError = weightedRootMeanSquareError;
    }

    /**
     * Returns the optimizer result.
     *
     * @return Levenberg-Marquardt result
     */
    public LevenbergMarquardtResult getOptimizerResult() {
      return optimizerResult;
    }

    /**
     * Returns whether the optimizer converged.
     *
     * @return true if the optimizer result is converged
     */
    public boolean isConverged() {
      return optimizerResult.isConverged();
    }

    /**
     * Returns fitted parameter values.
     *
     * @return fitted parameter values
     */
    public double[] getFittedParameters() {
      return copyArray(fittedParameters);
    }

    /**
     * Returns one fitted parameter value.
     *
     * @param index zero-based parameter index
     * @return fitted parameter value
     * @throws IndexOutOfBoundsException if index is outside the parameter array
     */
    public double getFittedParameter(int index) {
      return fittedParameters[index];
    }

    /**
     * Returns one fitted parameter value by name.
     *
     * @param parameterName configured parameter name
     * @return fitted parameter value
     * @throws IllegalArgumentException if the parameter name is not found
     */
    public double getFittedParameter(String parameterName) {
      for (int i = 0; i < parameterNames.length; i++) {
        if (parameterNames[i].equals(parameterName)) {
          return fittedParameters[i];
        }
      }
      throw new IllegalArgumentException("Unknown parameter name: " + parameterName);
    }

    /**
     * Returns fitted parameter names.
     *
     * @return fitted parameter names
     */
    public String[] getParameterNames() {
      return copyArray(parameterNames);
    }

    /**
     * Returns calculated values for each data point.
     *
     * @return calculated values
     */
    public double[] getCalculatedValues() {
      return copyArray(calculatedValues);
    }

    /**
     * Returns measured minus calculated residuals.
     *
     * @return residuals
     */
    public double[] getResiduals() {
      return copyArray(residuals);
    }

    /**
     * Returns weighted residuals.
     *
     * @return residuals divided by standard deviations
     */
    public double[] getWeightedResiduals() {
      return copyArray(weightedResiduals);
    }

    /**
     * Returns the root mean square error.
     *
     * @return unweighted root mean square error
     */
    public double getRootMeanSquareError() {
      return rootMeanSquareError;
    }

    /**
     * Returns the mean absolute error.
     *
     * @return unweighted mean absolute error
     */
    public double getMeanAbsoluteError() {
      return meanAbsoluteError;
    }

    /**
     * Returns the weighted root mean square error.
     *
     * @return weighted root mean square error
     */
    public double getWeightedRootMeanSquareError() {
      return weightedRootMeanSquareError;
    }
  }
}
