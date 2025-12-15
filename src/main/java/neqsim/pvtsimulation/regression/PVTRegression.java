package neqsim.pvtsimulation.regression;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.simulation.SeparatorTest;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;

/**
 * PVT Regression Framework for automatic EOS model tuning.
 *
 * <p>
 * This class provides a comprehensive framework for tuning equation of state parameters to match
 * experimental PVT data. It supports multi-objective regression fitting CCE, CVD, DLE, and
 * separator test data simultaneously.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * PVTRegression regression = new PVTRegression(fluid);
 * regression.addCCEData(pressures, relativeVolumes, temperature);
 * regression.addDLEData(pressures, Rs, Bo, oilDensity, temperature);
 * regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10, 0.03);
 * RegressionResult result = regression.runRegression();
 * SystemInterface tunedFluid = result.getTunedFluid();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PVTRegression {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PVTRegression.class);

  private SystemInterface baseFluid;
  private SystemInterface tunedFluid;

  // Experimental data storage
  private List<CCEDataPoint> cceData = new ArrayList<>();
  private List<CVDDataPoint> cvdData = new ArrayList<>();
  private List<DLEDataPoint> dleData = new ArrayList<>();
  private List<SeparatorDataPoint> separatorData = new ArrayList<>();

  // Regression parameters
  private List<RegressionParameterConfig> regressionParameters = new ArrayList<>();

  // Experiment weights for multi-objective optimization
  private EnumMap<ExperimentType, Double> experimentWeights = new EnumMap<>(ExperimentType.class);

  // Optimization settings
  private int maxIterations = 100;
  private double tolerance = 1e-6;
  private boolean verbose = true;

  // Results
  private RegressionResult lastResult;

  /**
   * Create a new PVT regression framework.
   *
   * @param fluid the base fluid to tune
   */
  public PVTRegression(SystemInterface fluid) {
    this.baseFluid = fluid.clone();
    this.tunedFluid = fluid.clone();

    // Default weights
    experimentWeights.put(ExperimentType.CCE, 1.0);
    experimentWeights.put(ExperimentType.CVD, 1.0);
    experimentWeights.put(ExperimentType.DLE, 1.0);
    experimentWeights.put(ExperimentType.SEPARATOR, 1.0);
    experimentWeights.put(ExperimentType.VISCOSITY, 0.5);
  }

  /**
   * Add CCE (Constant Composition Expansion) experimental data.
   *
   * @param pressures pressure values in bar
   * @param relativeVolumes relative volume values (V/Vsat)
   * @param temperature experiment temperature in K
   */
  public void addCCEData(double[] pressures, double[] relativeVolumes, double temperature) {
    for (int i = 0; i < pressures.length; i++) {
      cceData.add(new CCEDataPoint(pressures[i], relativeVolumes[i], temperature));
    }
  }

  /**
   * Add CCE data with Y-factor (for gas phase above saturation).
   *
   * @param pressures pressure values in bar
   * @param relativeVolumes relative volume values
   * @param yFactors Y-factor values (optional, use Double.NaN if not available)
   * @param temperature experiment temperature in K
   */
  public void addCCEData(double[] pressures, double[] relativeVolumes, double[] yFactors,
      double temperature) {
    for (int i = 0; i < pressures.length; i++) {
      CCEDataPoint point = new CCEDataPoint(pressures[i], relativeVolumes[i], temperature);
      point.setYFactor(yFactors[i]);
      cceData.add(point);
    }
  }

  /**
   * Add CVD (Constant Volume Depletion) experimental data.
   *
   * @param pressures pressure values in bar
   * @param liquidDropout liquid dropout values (volume %)
   * @param zFactors gas compressibility factors
   * @param temperature experiment temperature in K
   */
  public void addCVDData(double[] pressures, double[] liquidDropout, double[] zFactors,
      double temperature) {
    for (int i = 0; i < pressures.length; i++) {
      cvdData.add(new CVDDataPoint(pressures[i], liquidDropout[i], zFactors[i], temperature));
    }
  }

  /**
   * Add DLE (Differential Liberation Expansion) experimental data.
   *
   * @param pressures pressure values in bar
   * @param rs solution gas-oil ratio (Sm³/Sm³)
   * @param bo oil formation volume factor (m³/Sm³)
   * @param oilDensity oil density (kg/m³)
   * @param temperature experiment temperature in K
   */
  public void addDLEData(double[] pressures, double[] rs, double[] bo, double[] oilDensity,
      double temperature) {
    for (int i = 0; i < pressures.length; i++) {
      dleData.add(new DLEDataPoint(pressures[i], rs[i], bo[i], oilDensity[i], temperature));
    }
  }

  /**
   * Add separator test experimental data.
   *
   * @param gor gas-oil ratio (Sm³/Sm³)
   * @param bo oil formation volume factor
   * @param apiGravity API gravity
   * @param separatorPressure separator pressure in bar
   * @param separatorTemperature separator temperature in K
   * @param reservoirTemperature reservoir temperature in K
   */
  public void addSeparatorData(double gor, double bo, double apiGravity, double separatorPressure,
      double separatorTemperature, double reservoirTemperature) {
    separatorData.add(new SeparatorDataPoint(gor, bo, apiGravity, separatorPressure,
        separatorTemperature, reservoirTemperature));
  }

  /**
   * Add a regression parameter with bounds.
   *
   * @param parameter the parameter type to regress
   * @param lowerBound lower bound for the parameter
   * @param upperBound upper bound for the parameter
   * @param initialGuess initial guess for the parameter
   */
  public void addRegressionParameter(RegressionParameter parameter, double lowerBound,
      double upperBound, double initialGuess) {
    regressionParameters
        .add(new RegressionParameterConfig(parameter, lowerBound, upperBound, initialGuess));
  }

  /**
   * Add a regression parameter with default bounds.
   *
   * @param parameter the parameter type to regress
   */
  public void addRegressionParameter(RegressionParameter parameter) {
    double[] defaults = parameter.getDefaultBounds();
    regressionParameters
        .add(new RegressionParameterConfig(parameter, defaults[0], defaults[1], defaults[2]));
  }

  /**
   * Set the weight for an experiment type in the multi-objective function.
   *
   * @param type experiment type
   * @param weight weight value (default 1.0)
   */
  public void setExperimentWeight(ExperimentType type, double weight) {
    experimentWeights.put(type, weight);
  }

  /**
   * Set maximum number of iterations for optimization.
   *
   * @param maxIterations maximum iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Set convergence tolerance.
   *
   * @param tolerance convergence tolerance
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Enable or disable verbose output.
   *
   * @param verbose true for verbose output
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Run the regression optimization.
   *
   * @return regression result containing tuned fluid and statistics
   */
  public RegressionResult runRegression() {
    if (regressionParameters.isEmpty()) {
      throw new IllegalStateException("No regression parameters defined");
    }

    if (cceData.isEmpty() && cvdData.isEmpty() && dleData.isEmpty() && separatorData.isEmpty()) {
      throw new IllegalStateException("No experimental data provided");
    }

    if (verbose) {
      logger.info("Starting PVT regression with {} parameters", regressionParameters.size());
      logger.info("Data points: CCE={}, CVD={}, DLE={}, Separator={}", cceData.size(),
          cvdData.size(), dleData.size(), separatorData.size());
    }

    // Create sample set for Levenberg-Marquardt
    ArrayList<SampleValue> sampleList = new ArrayList<>();
    PVTRegressionFunction function = createRegressionFunction();

    // Add all experimental data points as samples
    addCCESamples(sampleList, function);
    addCVDSamples(sampleList, function);
    addDLESamples(sampleList, function);
    addSeparatorSamples(sampleList, function);

    SampleSet sampleSet = new SampleSet(sampleList);

    // Run optimization
    LevenbergMarquardt optimizer = new LevenbergMarquardt();
    optimizer.setMaxNumberOfIterations(maxIterations);
    optimizer.setSampleSet(sampleSet);
    optimizer.solve();

    // Apply optimized parameters to fluid
    applyOptimizedParameters(function.getFittingParams());

    // Calculate final objective function values
    Map<ExperimentType, Double> objectiveValues = calculateObjectiveValues();

    // Calculate final chi-square
    double finalChiSquare = optimizer.calcChiSquare();

    // Calculate uncertainty if covariance matrix is available
    UncertaintyAnalysis uncertainty = calculateUncertainty(optimizer, function, finalChiSquare);

    lastResult = new RegressionResult(tunedFluid.clone(), objectiveValues, regressionParameters,
        uncertainty, function.getFittingParams(), finalChiSquare);

    if (verbose) {
      logger.info("Regression completed. Final chi-square: {}", finalChiSquare);
      for (int i = 0; i < regressionParameters.size(); i++) {
        logger.info("Parameter {}: {} = {}", i, regressionParameters.get(i).getParameter().name(),
            function.getFittingParams()[i]);
      }
    }

    return lastResult;
  }

  /**
   * Create the regression function with initial parameters.
   */
  private PVTRegressionFunction createRegressionFunction() {
    double[] initialGuess = new double[regressionParameters.size()];
    double[][] bounds = new double[regressionParameters.size()][2];

    for (int i = 0; i < regressionParameters.size(); i++) {
      RegressionParameterConfig config = regressionParameters.get(i);
      initialGuess[i] = config.getInitialGuess();
      bounds[i][0] = config.getLowerBound();
      bounds[i][1] = config.getUpperBound();
    }

    PVTRegressionFunction function =
        new PVTRegressionFunction(baseFluid, regressionParameters, experimentWeights);
    function.setInitialGuess(initialGuess);
    function.setBounds(bounds);

    return function;
  }

  /**
   * Add CCE samples to the sample list.
   */
  private void addCCESamples(ArrayList<SampleValue> sampleList, PVTRegressionFunction function) {
    double weight = experimentWeights.getOrDefault(ExperimentType.CCE, 1.0);
    for (CCEDataPoint point : cceData) {
      double[] dependentValues =
          {point.getPressure(), point.getTemperature(), ExperimentType.CCE.ordinal(), 0}; // 0 =
                                                                                          // relative
                                                                                          // volume
      SampleValue sample =
          new SampleValue(point.getRelativeVolume(), 0.01 / weight, dependentValues);
      sample.setFunction(function.clone());
      sampleList.add(sample);
    }
  }

  /**
   * Add CVD samples to the sample list.
   */
  private void addCVDSamples(ArrayList<SampleValue> sampleList, PVTRegressionFunction function) {
    double weight = experimentWeights.getOrDefault(ExperimentType.CVD, 1.0);
    for (CVDDataPoint point : cvdData) {
      // Liquid dropout
      double[] dependentValues =
          {point.getPressure(), point.getTemperature(), ExperimentType.CVD.ordinal(), 0};
      SampleValue sample =
          new SampleValue(point.getLiquidDropout(), 0.01 / weight, dependentValues);
      sample.setFunction(function.clone());
      sampleList.add(sample);

      // Z-factor
      double[] dependentValues2 =
          {point.getPressure(), point.getTemperature(), ExperimentType.CVD.ordinal(), 1};
      SampleValue sample2 = new SampleValue(point.getZFactor(), 0.01 / weight, dependentValues2);
      sample2.setFunction(function.clone());
      sampleList.add(sample2);
    }
  }

  /**
   * Add DLE samples to the sample list.
   */
  private void addDLESamples(ArrayList<SampleValue> sampleList, PVTRegressionFunction function) {
    double weight = experimentWeights.getOrDefault(ExperimentType.DLE, 1.0);
    for (DLEDataPoint point : dleData) {
      // Rs
      double[] dependentValues =
          {point.getPressure(), point.getTemperature(), ExperimentType.DLE.ordinal(), 0};
      SampleValue sample = new SampleValue(point.getRs(), 0.01 / weight, dependentValues);
      sample.setFunction(function.clone());
      sampleList.add(sample);

      // Bo
      double[] dependentValues2 =
          {point.getPressure(), point.getTemperature(), ExperimentType.DLE.ordinal(), 1};
      SampleValue sample2 = new SampleValue(point.getBo(), 0.01 / weight, dependentValues2);
      sample2.setFunction(function.clone());
      sampleList.add(sample2);

      // Oil density
      double[] dependentValues3 =
          {point.getPressure(), point.getTemperature(), ExperimentType.DLE.ordinal(), 2};
      SampleValue sample3 = new SampleValue(point.getOilDensity(), 0.01 / weight, dependentValues3);
      sample3.setFunction(function.clone());
      sampleList.add(sample3);
    }
  }

  /**
   * Add separator samples to the sample list.
   */
  private void addSeparatorSamples(ArrayList<SampleValue> sampleList,
      PVTRegressionFunction function) {
    double weight = experimentWeights.getOrDefault(ExperimentType.SEPARATOR, 1.0);
    for (SeparatorDataPoint point : separatorData) {
      // GOR
      double[] dependentValues = {point.getSeparatorPressure(), point.getSeparatorTemperature(),
          ExperimentType.SEPARATOR.ordinal(), 0, point.getReservoirTemperature()};
      SampleValue sample = new SampleValue(point.getGor(), 0.01 / weight, dependentValues);
      sample.setFunction(function.clone());
      sampleList.add(sample);

      // Bo
      double[] dependentValues2 = {point.getSeparatorPressure(), point.getSeparatorTemperature(),
          ExperimentType.SEPARATOR.ordinal(), 1, point.getReservoirTemperature()};
      SampleValue sample2 = new SampleValue(point.getBo(), 0.01 / weight, dependentValues2);
      sample2.setFunction(function.clone());
      sampleList.add(sample2);
    }
  }

  /**
   * Apply optimized parameters to the tuned fluid.
   */
  private void applyOptimizedParameters(double[] optimizedParams) {
    tunedFluid = baseFluid.clone();

    for (int i = 0; i < regressionParameters.size(); i++) {
      RegressionParameter param = regressionParameters.get(i).getParameter();
      double value = optimizedParams[i];
      param.applyToFluid(tunedFluid, value);
    }

    tunedFluid.init(0);
    tunedFluid.init(1);
  }

  /**
   * Calculate objective function values for each experiment type.
   */
  private Map<ExperimentType, Double> calculateObjectiveValues() {
    Map<ExperimentType, Double> objectives = new HashMap<>();

    if (!cceData.isEmpty()) {
      objectives.put(ExperimentType.CCE, calculateCCEObjective());
    }
    if (!cvdData.isEmpty()) {
      objectives.put(ExperimentType.CVD, calculateCVDObjective());
    }
    if (!dleData.isEmpty()) {
      objectives.put(ExperimentType.DLE, calculateDLEObjective());
    }
    if (!separatorData.isEmpty()) {
      objectives.put(ExperimentType.SEPARATOR, calculateSeparatorObjective());
    }

    return objectives;
  }

  private double calculateCCEObjective() {
    double objective = 0.0;
    if (cceData.isEmpty()) {
      return 0.0;
    }

    ConstantMassExpansion cme = new ConstantMassExpansion(tunedFluid.clone());
    double[] pressures = cceData.stream().mapToDouble(CCEDataPoint::getPressure).toArray();
    cme.setPressures(pressures);
    cme.setTemperature(cceData.get(0).getTemperature(), "K");
    cme.runCalc();

    double[] calcRelVol = cme.getRelativeVolume();
    for (int i = 0; i < cceData.size(); i++) {
      double exp = cceData.get(i).getRelativeVolume();
      double calc = calcRelVol[i];
      objective += Math.pow((calc - exp) / exp, 2);
    }

    return objective / cceData.size();
  }

  private double calculateCVDObjective() {
    double objective = 0.0;
    if (cvdData.isEmpty()) {
      return 0.0;
    }

    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(tunedFluid.clone());
    double[] pressures = cvdData.stream().mapToDouble(CVDDataPoint::getPressure).toArray();
    cvd.setPressures(pressures);
    cvd.setTemperature(cvdData.get(0).getTemperature(), "K");
    cvd.runCalc();

    double[] calcLiquidDropout = cvd.getLiquidRelativeVolume();
    double[] calcZgas = cvd.getZgas();

    for (int i = 0; i < cvdData.size(); i++) {
      double expDropout = cvdData.get(i).getLiquidDropout();
      double expZ = cvdData.get(i).getZFactor();
      if (calcLiquidDropout != null && i < calcLiquidDropout.length) {
        objective += Math.pow((calcLiquidDropout[i] - expDropout) / Math.max(expDropout, 0.01), 2);
      }
      if (calcZgas != null && i < calcZgas.length) {
        objective += Math.pow((calcZgas[i] - expZ) / expZ, 2);
      }
    }

    return objective / (2 * cvdData.size());
  }

  private double calculateDLEObjective() {
    double objective = 0.0;
    if (dleData.isEmpty()) {
      return 0.0;
    }

    DifferentialLiberation dle = new DifferentialLiberation(tunedFluid.clone());
    double[] pressures = dleData.stream().mapToDouble(DLEDataPoint::getPressure).toArray();
    dle.setPressures(pressures);
    dle.setTemperature(dleData.get(0).getTemperature(), "K");
    dle.runCalc();

    double[] calcRs = dle.getRs();
    double[] calcBo = dle.getBo();
    double[] calcDensity = dle.getOilDensity();

    for (int i = 0; i < dleData.size(); i++) {
      double expRs = dleData.get(i).getRs();
      double expBo = dleData.get(i).getBo();
      double expDensity = dleData.get(i).getOilDensity();

      if (calcRs != null && i < calcRs.length && expRs > 0) {
        objective += Math.pow((calcRs[i] - expRs) / expRs, 2);
      }
      if (calcBo != null && i < calcBo.length && expBo > 0) {
        objective += Math.pow((calcBo[i] - expBo) / expBo, 2);
      }
      if (calcDensity != null && i < calcDensity.length && expDensity > 0) {
        objective += Math.pow((calcDensity[i] - expDensity) / expDensity, 2);
      }
    }

    return objective / (3 * dleData.size());
  }

  private double calculateSeparatorObjective() {
    double objective = 0.0;
    if (separatorData.isEmpty()) {
      return 0.0;
    }

    for (SeparatorDataPoint point : separatorData) {
      SeparatorTest sepTest = new SeparatorTest(tunedFluid.clone());
      sepTest.setSeparatorConditions(new double[] {point.getSeparatorTemperature()},
          new double[] {point.getSeparatorPressure()});

      sepTest.runCalc();

      // SeparatorTest returns arrays since it supports multiple separator stages
      double[] gorArray = sepTest.getGOR();
      double[] boArray = sepTest.getBofactor();

      if (gorArray != null && gorArray.length > 0 && point.getGor() > 0) {
        double calcGOR = gorArray[0];
        objective += Math.pow((calcGOR - point.getGor()) / point.getGor(), 2);
      }
      if (boArray != null && boArray.length > 0 && point.getBo() > 0) {
        double calcBo = boArray[0];
        objective += Math.pow((calcBo - point.getBo()) / point.getBo(), 2);
      }
    }

    return objective / (2 * separatorData.size());
  }

  /**
   * Calculate uncertainty analysis from the optimization results.
   */
  private UncertaintyAnalysis calculateUncertainty(LevenbergMarquardt optimizer,
      PVTRegressionFunction function, double finalChiSquare) {
    int nParams = regressionParameters.size();
    int nData = cceData.size() + 2 * cvdData.size() + 3 * dleData.size() + 2 * separatorData.size();

    double[] parameterValues = function.getFittingParams();
    double[] standardErrors = new double[nParams];
    double[][] correlationMatrix = new double[nParams][nParams];
    double[] confidenceIntervals95 = new double[nParams];

    // Estimate standard errors from residual variance
    double residualVariance = finalChiSquare;
    int degreesOfFreedom = Math.max(1, nData - nParams);

    for (int i = 0; i < nParams; i++) {
      // Approximate standard error (simplified)
      double paramRange =
          regressionParameters.get(i).getUpperBound() - regressionParameters.get(i).getLowerBound();
      standardErrors[i] = Math.sqrt(residualVariance / degreesOfFreedom) * paramRange * 0.1;

      // 95% confidence interval (t-distribution approximation)
      double tValue = 1.96; // Approximate for large degrees of freedom
      confidenceIntervals95[i] = tValue * standardErrors[i];

      // Identity correlation for diagonal
      correlationMatrix[i][i] = 1.0;
    }

    return new UncertaintyAnalysis(parameterValues, standardErrors, correlationMatrix,
        confidenceIntervals95, degreesOfFreedom, residualVariance);
  }

  /**
   * Get the base (untuned) fluid.
   *
   * @return base fluid
   */
  public SystemInterface getBaseFluid() {
    return baseFluid;
  }

  /**
   * Get the tuned fluid after regression.
   *
   * @return tuned fluid
   */
  public SystemInterface getTunedFluid() {
    return tunedFluid;
  }

  /**
   * Get the last regression result.
   *
   * @return last result or null if not run
   */
  public RegressionResult getLastResult() {
    return lastResult;
  }

  /**
   * Get the CCE data points.
   *
   * @return list of CCE data points
   */
  public List<CCEDataPoint> getCCEData() {
    return cceData;
  }

  /**
   * Get the CVD data points.
   *
   * @return list of CVD data points
   */
  public List<CVDDataPoint> getCVDData() {
    return cvdData;
  }

  /**
   * Get the DLE data points.
   *
   * @return list of DLE data points
   */
  public List<DLEDataPoint> getDLEData() {
    return dleData;
  }

  /**
   * Get the separator data points.
   *
   * @return list of separator data points
   */
  public List<SeparatorDataPoint> getSeparatorData() {
    return separatorData;
  }

  /**
   * Clear all experimental data.
   */
  public void clearData() {
    cceData.clear();
    cvdData.clear();
    dleData.clear();
    separatorData.clear();
  }

  /**
   * Clear all regression parameters.
   */
  public void clearRegressionParameters() {
    regressionParameters.clear();
  }
}
