package neqsim.statistics.parameterfitting;

import java.util.Random;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult;

/**
 * High-level workflow for fitting model parameters to experimental data sets.
 *
 * @author Even Solbraa
 * @version 1.1
 */
public class ParameterFittingStudy {
  /** Default robust objective tuning constant. */
  private static final double DEFAULT_ROBUST_TUNING_CONSTANT = 1.345;

  /** Default maximum number of robust reweighting passes. */
  private static final int DEFAULT_MAX_ROBUST_ITERATIONS = 5;

  /** Small residual and weight guard used by robust objective calculations. */
  private static final double NUMERICAL_EPSILON = 1.0e-12;

  private final ExperimentalDataSet dataSet;
  private final BaseFunction function;
  private final LevenbergMarquardt optimizer = new LevenbergMarquardt();
  private String[] parameterNames;
  private ParameterFittingSpec spec;
  private ObjectiveFunctionType objectiveFunctionType =
      ObjectiveFunctionType.WEIGHTED_LEAST_SQUARES;
  private double robustTuningConstant = DEFAULT_ROBUST_TUNING_CONSTANT;
  private int maxRobustIterations = DEFAULT_MAX_ROBUST_ITERATIONS;
  private int multiStartCount = 1;
  private long randomSeed = 12345L;
  private ExperimentalDataSet validationDataSet;
  private ParameterUpdateAdapter parameterUpdateAdapter;
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
   * Creates a parameter fitting study using a complete fitting specification.
   *
   * @param dataSet experimental data set to fit
   * @param function fitting function that predicts the measured response
   * @param spec fitting specification
   * @throws IllegalArgumentException if any argument is null or invalid
   */
  public ParameterFittingStudy(ExperimentalDataSet dataSet, BaseFunction function,
      ParameterFittingSpec spec) {
    this(dataSet, function);
    setSpec(spec);
  }

  /**
   * Sets a complete fitting specification.
   *
   * @param spec fitting specification
   * @return this study for fluent configuration
   * @throws IllegalArgumentException if spec is null or invalid
   */
  public ParameterFittingStudy setSpec(ParameterFittingSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec cannot be null");
    }
    spec.validate();
    this.spec = spec;
    this.parameterNames = spec.getParameterNames();
    this.objectiveFunctionType = spec.getObjectiveFunctionType();
    this.robustTuningConstant = spec.getRobustTuningConstant();
    this.maxRobustIterations = spec.getMaxRobustIterations();
    this.multiStartCount = spec.getMultiStartCount();
    this.randomSeed = spec.getRandomSeed();
    this.optimizer.setMaxNumberOfIterations(spec.getMaxNumberOfIterations());
    setInitialGuess(spec.getInitialGuess());
    if (spec.hasTransformedParameters()) {
      function.setBounds(null);
    } else {
      setParameterBounds(spec.getBounds());
    }
    return this;
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
    applyParameterAdapter(initialGuess);
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
   * Sets the objective function used by the high-level study.
   *
   * @param objectiveFunctionType objective function type
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setObjectiveFunctionType(
      ObjectiveFunctionType objectiveFunctionType) {
    this.objectiveFunctionType =
        objectiveFunctionType == null ? ObjectiveFunctionType.WEIGHTED_LEAST_SQUARES
            : objectiveFunctionType;
    return this;
  }

  /**
   * Sets the robust objective tuning constant.
   *
   * @param robustTuningConstant positive robust tuning constant
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setRobustTuningConstant(double robustTuningConstant) {
    if (robustTuningConstant <= 0.0 || Double.isNaN(robustTuningConstant)
        || Double.isInfinite(robustTuningConstant)) {
      throw new IllegalArgumentException("robustTuningConstant must be positive and finite");
    }
    this.robustTuningConstant = robustTuningConstant;
    return this;
  }

  /**
   * Sets the maximum number of robust reweighting passes.
   *
   * @param maxRobustIterations positive robust iteration limit
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setMaxRobustIterations(int maxRobustIterations) {
    if (maxRobustIterations <= 0) {
      throw new IllegalArgumentException("maxRobustIterations must be positive");
    }
    this.maxRobustIterations = maxRobustIterations;
    return this;
  }

  /**
   * Sets the number of deterministic multi-start candidates.
   *
   * @param multiStartCount positive multi-start count
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setMultiStartCount(int multiStartCount) {
    if (multiStartCount <= 0) {
      throw new IllegalArgumentException("multiStartCount must be positive");
    }
    this.multiStartCount = multiStartCount;
    return this;
  }

  /**
   * Sets the random seed used for deterministic multi-start sampling.
   *
   * @param randomSeed random seed
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setRandomSeed(long randomSeed) {
    this.randomSeed = randomSeed;
    return this;
  }

  /**
   * Sets an explicit validation data set evaluated after fitting.
   *
   * @param validationDataSet validation data set, or null to clear
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setValidationDataSet(ExperimentalDataSet validationDataSet) {
    this.validationDataSet = validationDataSet;
    return this;
  }

  /**
   * Sets a model update adapter invoked whenever physical parameter values change.
   *
   * @param parameterUpdateAdapter parameter update adapter, or null to clear
   * @return this study for fluent configuration
   */
  public ParameterFittingStudy setParameterUpdateAdapter(
      ParameterUpdateAdapter parameterUpdateAdapter) {
    this.parameterUpdateAdapter = parameterUpdateAdapter;
    if (parameterUpdateAdapter != null && parameterUpdateAdapter.getParameters() != null
        && parameterUpdateAdapter.getParameters().length > 0 && spec == null) {
      ParameterFittingSpec adapterSpec = new ParameterFittingSpec("adapter fitting study");
      FittingParameter[] parameters = parameterUpdateAdapter.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        adapterSpec.addParameter(parameters[i]);
      }
      setSpec(adapterSpec);
    }
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
      if (spec == null) {
        throw new IllegalStateException("setInitialGuess must be called before run");
      }
      setInitialGuess(spec.getInitialGuess());
    }
    ExperimentalDataSet fittingDataSet = dataSet;
    ExperimentalDataSet validation = validationDataSet;
    if (spec != null && !Double.isNaN(spec.getTrainingFraction())) {
      ExperimentalDataSet[] split = dataSet.split(spec.getTrainingFraction());
      fittingDataSet = split[0];
      validation = split[1];
    }
    Random random = new Random(randomSeed);
    Result bestResult = null;
    double[] firstInitialGuess = currentPhysicalParameters();
    for (int start = 0; start < multiStartCount; start++) {
      double[] initialGuess = start == 0 ? firstInitialGuess : createRandomInitialGuess(random);
      Result candidate = runSingleStart(fittingDataSet, validation, initialGuess);
      if (bestResult == null || candidate.getObjectiveValue() < bestResult.getObjectiveValue()) {
        bestResult = candidate;
      }
    }
    setPhysicalParameters(bestResult.getFittedParameters());
    result = bestResult;
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
   * Creates a report from the most recent fitting result.
   *
   * @return parameter fitting report
   * @throws IllegalStateException if the study has not been run
   */
  public ParameterFittingReport createReport() {
    if (result == null) {
      throw new IllegalStateException("run must be called before createReport");
    }
    return new ParameterFittingReport(this);
  }

  /**
   * Runs the study and returns a report for the completed result.
   *
   * @return parameter fitting report
   */
  public ParameterFittingReport fitAndCreateReport() {
    fit();
    return createReport();
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
   * Returns the optional fitting specification.
   *
   * @return fitting specification, or null if none was configured
   */
  public ParameterFittingSpec getSpec() {
    return spec;
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
   * Runs one multi-start candidate.
   *
   * @param fittingDataSet data set used for fitting
   * @param validation data set used for validation, or null
   * @param initialGuess physical initial parameter values
   * @return candidate result
   */
  private Result runSingleStart(ExperimentalDataSet fittingDataSet, ExperimentalDataSet validation,
      double[] initialGuess) {
    setPhysicalParameters(initialGuess);
    BaseFunction optimizationFunction = createOptimizationFunction();
    SampleSet sampleSet = fittingDataSet.toSampleSet(optimizationFunction);
    int robustPasses = objectiveFunctionType.isRobust() ? maxRobustIterations : 1;
    int completedRobustPasses = 0;
    for (int robustPass = 0; robustPass < robustPasses; robustPass++) {
      completedRobustPasses = robustPass + 1;
      optimizer.setSampleSet(sampleSet);
      optimizer.solve();
      setPhysicalParameters(resolvePhysicalParameters(optimizationFunction));
      if (!objectiveFunctionType.isRobust()) {
        break;
      }
      double[] weights = calculateRobustWeights(fittingDataSet);
      sampleSet = createWeightedSampleSet(fittingDataSet, optimizationFunction, weights);
    }
    return createResult(fittingDataSet, validation, optimizer.getResult(), completedRobustPasses,
        optimizationFunction);
  }

  /**
   * Creates the function passed to the optimizer.
   *
   * @return fitting function in optimizer parameter space
   */
  private BaseFunction createOptimizationFunction() {
    if (spec == null) {
      return function;
    }
    if (spec.hasTransformedParameters() || parameterUpdateAdapter != null) {
      return new SpecBackedFunction(function, spec, parameterUpdateAdapter);
    }
    function.setBounds(spec.getBounds());
    return function;
  }

  /**
   * Creates a result object from the final fitted function state.
   *
   * @param fittingDataSet data set used for fitting
   * @param validation data set used for validation, or null
   * @param optimizerResult optimizer result to include
   * @param robustPasses number of robust reweighting passes completed
   * @param optimizationFunction function used by the optimizer
   * @return fitting study result
   */
  private Result createResult(ExperimentalDataSet fittingDataSet, ExperimentalDataSet validation,
      LevenbergMarquardtResult optimizerResult, int robustPasses,
      BaseFunction optimizationFunction) {
    double[] fittedParameters = resolvePhysicalParameters(optimizationFunction);
    setPhysicalParameters(fittedParameters);
    Metrics fittingMetrics = calculateMetrics(fittingDataSet);
    Metrics validationMetrics =
        validation == null ? Metrics.notAvailable() : calculateMetrics(validation);
    double objectiveValue = calculateObjectiveValue(fittingDataSet, fittedParameters);
    return new Result(optimizerResult, fittedParameters, resolveParameterNames(),
        fittingMetrics.calculatedValues, fittingMetrics.residuals, fittingMetrics.weightedResiduals,
        fittingMetrics.rootMeanSquareError, fittingMetrics.meanAbsoluteError,
        fittingMetrics.weightedRootMeanSquareError, fittingMetrics.reducedChiSquare,
        objectiveFunctionType, objectiveValue, robustPasses, validationMetrics.calculatedValues,
        validationMetrics.residuals, validationMetrics.weightedResiduals,
        validationMetrics.rootMeanSquareError, validationMetrics.meanAbsoluteError,
        validationMetrics.weightedRootMeanSquareError);
  }

  /**
   * Calculates fit metrics for a data set using the physical fitting function.
   *
   * @param metricsDataSet data set to evaluate
   * @return calculated metrics
   */
  private Metrics calculateMetrics(ExperimentalDataSet metricsDataSet) {
    double[] calculatedValues = new double[metricsDataSet.size()];
    double[] residuals = new double[metricsDataSet.size()];
    double[] weightedResiduals = new double[metricsDataSet.size()];
    double sumSquared = 0.0;
    double sumAbsolute = 0.0;
    double sumWeightedSquared = 0.0;
    for (int i = 0; i < metricsDataSet.size(); i++) {
      ExperimentalDataPoint point = metricsDataSet.getPoint(i);
      calculatedValues[i] = function.calcValue(point.getDependentValues());
      residuals[i] = point.getMeasuredValue() - calculatedValues[i];
      weightedResiduals[i] = residuals[i] / point.getStandardDeviation();
      sumSquared += residuals[i] * residuals[i];
      sumAbsolute += Math.abs(residuals[i]);
      sumWeightedSquared += weightedResiduals[i] * weightedResiduals[i];
    }
    double rootMeanSquareError = Math.sqrt(sumSquared / metricsDataSet.size());
    double meanAbsoluteError = sumAbsolute / metricsDataSet.size();
    double weightedRootMeanSquareError = Math.sqrt(sumWeightedSquared / metricsDataSet.size());
    int degreesOfFreedom = Math.max(1, metricsDataSet.size() - currentPhysicalParameters().length);
    double reducedChiSquare = sumWeightedSquared / degreesOfFreedom;
    return new Metrics(calculatedValues, residuals, weightedResiduals, rootMeanSquareError,
        meanAbsoluteError, weightedRootMeanSquareError, reducedChiSquare);
  }

  /**
   * Calculates robust weights from current physical residuals.
   *
   * @param fittingDataSet data set used for fitting
   * @return robust weights for each data point
   */
  private double[] calculateRobustWeights(ExperimentalDataSet fittingDataSet) {
    double[] weights = new double[fittingDataSet.size()];
    for (int i = 0; i < fittingDataSet.size(); i++) {
      ExperimentalDataPoint point = fittingDataSet.getPoint(i);
      double residual = point.getMeasuredValue() - function.calcValue(point.getDependentValues());
      double standardized = residual / point.getStandardDeviation();
      weights[i] = robustWeight(standardized);
    }
    return weights;
  }

  /**
   * Calculates one robust weight from a standardized residual.
   *
   * @param standardizedResidual residual divided by experimental standard deviation
   * @return robust weight
   */
  private double robustWeight(double standardizedResidual) {
    double absResidual = Math.abs(standardizedResidual);
    double c = robustTuningConstant;
    if (objectiveFunctionType == ObjectiveFunctionType.ABSOLUTE_DEVIATION) {
      return Math.max(NUMERICAL_EPSILON, 1.0 / Math.max(absResidual, NUMERICAL_EPSILON));
    } else if (objectiveFunctionType == ObjectiveFunctionType.HUBER) {
      return absResidual <= c ? 1.0 : Math.max(NUMERICAL_EPSILON, c / absResidual);
    } else if (objectiveFunctionType == ObjectiveFunctionType.CAUCHY) {
      double scaled = standardizedResidual / c;
      return Math.max(NUMERICAL_EPSILON, 1.0 / (1.0 + scaled * scaled));
    } else if (objectiveFunctionType == ObjectiveFunctionType.TUKEY_BIWEIGHT) {
      if (absResidual >= c) {
        return NUMERICAL_EPSILON;
      }
      double scaled = standardizedResidual / c;
      double factor = 1.0 - scaled * scaled;
      return Math.max(NUMERICAL_EPSILON, factor * factor);
    }
    return 1.0;
  }

  /**
   * Creates a sample set with standard deviations adjusted by robust weights.
   *
   * @param fittingDataSet source data set
   * @param optimizationFunction function to attach to each sample
   * @param weights robust weights
   * @return weighted sample set
   */
  private SampleSet createWeightedSampleSet(ExperimentalDataSet fittingDataSet,
      BaseFunction optimizationFunction, double[] weights) {
    SampleSet sampleSet = new SampleSet();
    for (int i = 0; i < fittingDataSet.size(); i++) {
      ExperimentalDataPoint point = fittingDataSet.getPoint(i);
      double adjustedStandardDeviation =
          point.getStandardDeviation() / Math.sqrt(Math.max(NUMERICAL_EPSILON, weights[i]));
      SampleValue sample = new SampleValue(point.getMeasuredValue(), adjustedStandardDeviation,
          point.getDependentValues());
      sample.setReference(point.getReference());
      sample.setDescription(point.getDescription());
      sample.setFunction(optimizationFunction);
      sampleSet.add(sample);
    }
    return sampleSet;
  }

  /**
   * Calculates the configured objective value from physical residuals and priors.
   *
   * @param fittingDataSet data set used for fitting
   * @param fittedParameters physical fitted parameter values
   * @return objective value
   */
  private double calculateObjectiveValue(ExperimentalDataSet fittingDataSet,
      double[] fittedParameters) {
    double objectiveValue = 0.0;
    for (int i = 0; i < fittingDataSet.size(); i++) {
      ExperimentalDataPoint point = fittingDataSet.getPoint(i);
      double residual = point.getMeasuredValue() - function.calcValue(point.getDependentValues());
      double standardized = residual / point.getStandardDeviation();
      objectiveValue += robustLoss(standardized);
    }
    if (spec != null) {
      for (int i = 0; i < spec.getParameters().size(); i++) {
        FittingParameter parameter = spec.getParameters().get(i);
        if (parameter.hasPrior()) {
          double standardized = (fittedParameters[i] - parameter.getPriorValue())
              / parameter.getPriorStandardDeviation();
          objectiveValue += standardized * standardized;
        }
      }
    }
    return objectiveValue;
  }

  /**
   * Calculates one robust loss contribution.
   *
   * @param standardizedResidual residual divided by experimental standard deviation
   * @return robust loss contribution
   */
  private double robustLoss(double standardizedResidual) {
    double absResidual = Math.abs(standardizedResidual);
    double c = robustTuningConstant;
    if (objectiveFunctionType == ObjectiveFunctionType.ABSOLUTE_DEVIATION) {
      return absResidual;
    } else if (objectiveFunctionType == ObjectiveFunctionType.HUBER) {
      return absResidual <= c ? standardizedResidual * standardizedResidual
          : 2.0 * c * absResidual - c * c;
    } else if (objectiveFunctionType == ObjectiveFunctionType.CAUCHY) {
      double scaled = standardizedResidual / c;
      return c * c * Math.log(1.0 + scaled * scaled);
    } else if (objectiveFunctionType == ObjectiveFunctionType.TUKEY_BIWEIGHT) {
      if (absResidual >= c) {
        return c * c / 3.0;
      }
      double scaled = standardizedResidual / c;
      double factor = 1.0 - scaled * scaled;
      return c * c / 3.0 * (1.0 - factor * factor * factor);
    }
    return standardizedResidual * standardizedResidual;
  }

  /**
   * Creates a deterministic random physical initial guess inside configured bounds.
   *
   * @param random random number generator
   * @return physical initial guess
   */
  private double[] createRandomInitialGuess(Random random) {
    if (spec == null) {
      return currentPhysicalParameters();
    }
    double[][] bounds = spec.getBounds();
    double[] values = new double[bounds.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = bounds[i][0] + random.nextDouble() * (bounds[i][1] - bounds[i][0]);
    }
    return values;
  }

  /**
   * Returns the current physical parameter values from the original function.
   *
   * @return physical parameter values
   */
  private double[] currentPhysicalParameters() {
    return copyArray(function.getFittingParams());
  }

  /**
   * Resolves physical parameter values from the active optimization function.
   *
   * @param optimizationFunction active optimization function
   * @return physical parameter values
   */
  private double[] resolvePhysicalParameters(BaseFunction optimizationFunction) {
    if (optimizationFunction instanceof SpecBackedFunction) {
      return ((SpecBackedFunction) optimizationFunction).getPhysicalParameters();
    }
    return copyArray(optimizationFunction.getFittingParams());
  }

  /**
   * Sets physical parameter values on the original function and optional adapter.
   *
   * @param values physical parameter values
   */
  private void setPhysicalParameters(double[] values) {
    if (function.getFittingParams() == null
        || function.getFittingParams().length != values.length) {
      function.setInitialGuess(copyArray(values));
    } else {
      for (int i = 0; i < values.length; i++) {
        function.setFittingParams(i, values[i]);
      }
    }
    applyParameterAdapter(values);
  }

  /**
   * Applies physical parameter values to the optional adapter.
   *
   * @param values physical parameter values
   */
  private void applyParameterAdapter(double[] values) {
    if (parameterUpdateAdapter != null) {
      parameterUpdateAdapter.applyParameters(copyArray(values));
    }
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
   * Metrics calculated for one data set.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Metrics {
    private final double[] calculatedValues;
    private final double[] residuals;
    private final double[] weightedResiduals;
    private final double rootMeanSquareError;
    private final double meanAbsoluteError;
    private final double weightedRootMeanSquareError;
    private final double reducedChiSquare;

    /**
     * Creates metrics.
     *
     * @param calculatedValues calculated values
     * @param residuals residuals
     * @param weightedResiduals weighted residuals
     * @param rootMeanSquareError root mean square error
     * @param meanAbsoluteError mean absolute error
     * @param weightedRootMeanSquareError weighted root mean square error
     * @param reducedChiSquare reduced chi-square
     */
    private Metrics(double[] calculatedValues, double[] residuals, double[] weightedResiduals,
        double rootMeanSquareError, double meanAbsoluteError, double weightedRootMeanSquareError,
        double reducedChiSquare) {
      this.calculatedValues = copyArray(calculatedValues);
      this.residuals = copyArray(residuals);
      this.weightedResiduals = copyArray(weightedResiduals);
      this.rootMeanSquareError = rootMeanSquareError;
      this.meanAbsoluteError = meanAbsoluteError;
      this.weightedRootMeanSquareError = weightedRootMeanSquareError;
      this.reducedChiSquare = reducedChiSquare;
    }

    /**
     * Creates empty metrics for missing validation data.
     *
     * @return empty metrics
     */
    private static Metrics notAvailable() {
      return new Metrics(new double[0], new double[0], new double[0], Double.NaN, Double.NaN,
          Double.NaN, Double.NaN);
    }
  }

  /**
   * Optimizer-space wrapper that maps transformed parameters back to the physical function.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class SpecBackedFunction extends BaseFunction {
    private final BaseFunction delegate;
    private final ParameterFittingSpec spec;
    private final ParameterUpdateAdapter adapter;

    /**
     * Creates a spec-backed fitting function.
     *
     * @param delegate physical fitting function
     * @param spec fitting specification
     * @param adapter optional parameter update adapter
     */
    private SpecBackedFunction(BaseFunction delegate, ParameterFittingSpec spec,
        ParameterUpdateAdapter adapter) {
      this.delegate = delegate;
      this.spec = spec;
      this.adapter = adapter;
      this.params = toInternal(delegate.getFittingParams(), spec);
      this.bounds = spec.getInternalBounds();
      syncDelegate();
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      syncDelegate();
      return delegate.calcValue(dependentValues);
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
      params[i] = value;
      syncDelegate();
    }

    /** {@inheritDoc} */
    @Override
    public void setInitialGuess(double[] guess) {
      validateFiniteArray("guess", guess);
      params = copyArray(guess);
      syncDelegate();
    }

    /**
     * Returns current physical parameter values.
     *
     * @return physical parameter values
     */
    private double[] getPhysicalParameters() {
      return spec.toExternalValues(params);
    }

    /**
     * Synchronizes the delegate function and optional adapter.
     */
    private void syncDelegate() {
      double[] physicalValues = getPhysicalParameters();
      for (int i = 0; i < physicalValues.length; i++) {
        delegate.setFittingParams(i, physicalValues[i]);
      }
      if (adapter != null) {
        adapter.applyParameters(physicalValues);
      }
    }

    /**
     * Converts physical values to optimizer-space values.
     *
     * @param values physical parameter values
     * @param spec fitting specification
     * @return optimizer-space parameter values
     */
    private static double[] toInternal(double[] values, ParameterFittingSpec spec) {
      if (values == null || values.length != spec.getParameters().size()) {
        return spec.getInternalInitialGuess();
      }
      double[] internalValues = new double[values.length];
      for (int i = 0; i < values.length; i++) {
        FittingParameter parameter = spec.getParameters().get(i);
        internalValues[i] = parameter.getTransform().toInternal(values[i],
            parameter.getLowerBound(), parameter.getUpperBound());
      }
      return internalValues;
    }
  }

  /**
   * Result from a parameter fitting study.
   *
   * @author Even Solbraa
   * @version 1.1
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
    private final double reducedChiSquare;
    private final ObjectiveFunctionType objectiveFunctionType;
    private final double objectiveValue;
    private final int robustIterations;
    private final double[] validationCalculatedValues;
    private final double[] validationResiduals;
    private final double[] validationWeightedResiduals;
    private final double validationRootMeanSquareError;
    private final double validationMeanAbsoluteError;
    private final double validationWeightedRootMeanSquareError;

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
     * @param reducedChiSquare reduced chi-square using training residuals
     * @param objectiveFunctionType objective function type
     * @param objectiveValue final objective value
     * @param robustIterations number of robust reweighting passes
     * @param validationCalculatedValues validation calculated values
     * @param validationResiduals validation residuals
     * @param validationWeightedResiduals validation weighted residuals
     * @param validationRootMeanSquareError validation root mean square error
     * @param validationMeanAbsoluteError validation mean absolute error
     * @param validationWeightedRootMeanSquareError validation weighted root mean square error
     */
    private Result(LevenbergMarquardtResult optimizerResult, double[] fittedParameters,
        String[] parameterNames, double[] calculatedValues, double[] residuals,
        double[] weightedResiduals, double rootMeanSquareError, double meanAbsoluteError,
        double weightedRootMeanSquareError, double reducedChiSquare,
        ObjectiveFunctionType objectiveFunctionType, double objectiveValue, int robustIterations,
        double[] validationCalculatedValues, double[] validationResiduals,
        double[] validationWeightedResiduals, double validationRootMeanSquareError,
        double validationMeanAbsoluteError, double validationWeightedRootMeanSquareError) {
      this.optimizerResult = optimizerResult;
      this.fittedParameters = copyArray(fittedParameters);
      this.parameterNames = copyArray(parameterNames);
      this.calculatedValues = copyArray(calculatedValues);
      this.residuals = copyArray(residuals);
      this.weightedResiduals = copyArray(weightedResiduals);
      this.rootMeanSquareError = rootMeanSquareError;
      this.meanAbsoluteError = meanAbsoluteError;
      this.weightedRootMeanSquareError = weightedRootMeanSquareError;
      this.reducedChiSquare = reducedChiSquare;
      this.objectiveFunctionType = objectiveFunctionType;
      this.objectiveValue = objectiveValue;
      this.robustIterations = robustIterations;
      this.validationCalculatedValues = copyArray(validationCalculatedValues);
      this.validationResiduals = copyArray(validationResiduals);
      this.validationWeightedResiduals = copyArray(validationWeightedResiduals);
      this.validationRootMeanSquareError = validationRootMeanSquareError;
      this.validationMeanAbsoluteError = validationMeanAbsoluteError;
      this.validationWeightedRootMeanSquareError = validationWeightedRootMeanSquareError;
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

    /**
     * Returns the reduced chi-square for the fitting data.
     *
     * @return reduced chi-square
     */
    public double getReducedChiSquare() {
      return reducedChiSquare;
    }

    /**
     * Returns the objective function type.
     *
     * @return objective function type
     */
    public ObjectiveFunctionType getObjectiveFunctionType() {
      return objectiveFunctionType;
    }

    /**
     * Returns the final objective value.
     *
     * @return final objective value
     */
    public double getObjectiveValue() {
      return objectiveValue;
    }

    /**
     * Returns the number of robust reweighting passes completed.
     *
     * @return robust reweighting pass count
     */
    public int getRobustIterations() {
      return robustIterations;
    }

    /**
     * Returns validation calculated values.
     *
     * @return validation calculated values, or empty array if no validation set was used
     */
    public double[] getValidationCalculatedValues() {
      return copyArray(validationCalculatedValues);
    }

    /**
     * Returns validation residuals.
     *
     * @return validation residuals, or empty array if no validation set was used
     */
    public double[] getValidationResiduals() {
      return copyArray(validationResiduals);
    }

    /**
     * Returns validation weighted residuals.
     *
     * @return validation weighted residuals, or empty array if no validation set was used
     */
    public double[] getValidationWeightedResiduals() {
      return copyArray(validationWeightedResiduals);
    }

    /**
     * Returns validation root mean square error.
     *
     * @return validation root mean square error, or NaN if no validation set was used
     */
    public double getValidationRootMeanSquareError() {
      return validationRootMeanSquareError;
    }

    /**
     * Returns validation mean absolute error.
     *
     * @return validation mean absolute error, or NaN if no validation set was used
     */
    public double getValidationMeanAbsoluteError() {
      return validationMeanAbsoluteError;
    }

    /**
     * Returns validation weighted root mean square error.
     *
     * @return validation weighted root mean square error, or NaN if no validation set was used
     */
    public double getValidationWeightedRootMeanSquareError() {
      return validationWeightedRootMeanSquareError;
    }
  }
}
