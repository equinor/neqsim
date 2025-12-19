package neqsim.process.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.sensitivity.ProcessSensitivityAnalyzer;
import neqsim.process.util.uncertainty.SensitivityMatrix;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Objective function that bridges process simulation with the Levenberg-Marquardt optimizer.
 *
 * <p>
 * This class extends {@link LevenbergMarquardtFunction} to enable batch parameter estimation for
 * process simulations. It wraps a {@link ProcessSystem} and provides the objective function
 * interface expected by the L-M optimizer.
 * </p>
 *
 * <p>
 * <b>Key Features:</b>
 * </p>
 * <ul>
 * <li>Path-based access to process variables (equipment.property notation)</li>
 * <li>Optional analytical Jacobian via {@link ProcessSensitivityAnalyzer}</li>
 * <li>Automatic parameter bounds enforcement</li>
 * <li>Support for multiple measured outputs per data point</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * ProcessSimulationFunction function = new ProcessSimulationFunction(process);
 * function.addParameter("Pipe1.heatTransferCoefficient", 1.0, 100.0);
 * function.addMeasurement("Manifold.outletStream.temperature");
 * function.setInitialGuess(new double[] {15.0});
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see BatchParameterEstimator
 */
public class ProcessSimulationFunction extends LevenbergMarquardtFunction {
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessSimulationFunction.class);

  /** The process system to simulate. */
  private ProcessSystem processSystem;

  /** Parameter paths in the process (e.g., "Pipe1.heatTransferCoefficient"). */
  private List<String> parameterPaths;

  /** Measurement paths in the process (e.g., "Manifold.outletStream.temperature"). */
  private List<String> measurementPaths;

  /** Conditions to apply for each data point. */
  private List<Map<String, Double>> dataPointConditions;

  /** Current data point index being evaluated. */
  private int currentDataPointIndex = 0;

  /** Current measurement index being evaluated. */
  private int currentMeasurementIndex = 0;

  /** Whether to use ProcessSensitivityAnalyzer for Jacobian. */
  private boolean useAnalyticalJacobian = false;

  /** Cached sensitivity analyzer. */
  private transient ProcessSensitivityAnalyzer sensitivityAnalyzer;

  /** Cached Jacobian from last computation. */
  private transient SensitivityMatrix cachedJacobian;

  /**
   * Creates a new process simulation function.
   *
   * @param processSystem the process system to wrap
   */
  public ProcessSimulationFunction(ProcessSystem processSystem) {
    super();
    this.processSystem = processSystem;
    this.parameterPaths = new ArrayList<>();
    this.measurementPaths = new ArrayList<>();
    this.dataPointConditions = new ArrayList<>();
  }

  /**
   * Adds a tunable parameter.
   *
   * @param path path to the parameter (e.g., "Pipe1.heatTransferCoefficient")
   * @param lowerBound minimum allowed value
   * @param upperBound maximum allowed value
   * @return this function for chaining
   */
  public ProcessSimulationFunction addParameter(String path, double lowerBound, double upperBound) {
    parameterPaths.add(path);

    // Resize bounds array
    int n = parameterPaths.size();
    double[][] newBounds = new double[n][2];
    if (bounds != null) {
      for (int i = 0; i < bounds.length; i++) {
        newBounds[i][0] = bounds[i][0];
        newBounds[i][1] = bounds[i][1];
      }
    }
    newBounds[n - 1][0] = lowerBound;
    newBounds[n - 1][1] = upperBound;
    bounds = newBounds;

    // Resize params array
    double[] newParams = new double[n];
    if (params != null) {
      System.arraycopy(params, 0, newParams, 0, params.length);
    }
    params = newParams;

    return this;
  }

  /**
   * Adds a measured variable.
   *
   * @param path path to the measurement (e.g., "Manifold.outletStream.temperature")
   * @return this function for chaining
   */
  public ProcessSimulationFunction addMeasurement(String path) {
    measurementPaths.add(path);
    return this;
  }

  /**
   * Adds conditions for a data point (operating conditions to apply before simulation).
   *
   * @param conditions map of path -&gt; value for conditions
   */
  public void addDataPointConditions(Map<String, Double> conditions) {
    dataPointConditions.add(conditions);
  }

  /**
   * Sets the current data point and measurement indices for evaluation.
   *
   * @param dataPointIndex index of the data point
   * @param measurementIndex index of the measurement within the data point
   */
  public void setCurrentIndices(int dataPointIndex, int measurementIndex) {
    this.currentDataPointIndex = dataPointIndex;
    this.currentMeasurementIndex = measurementIndex;
  }

  /**
   * Enables or disables analytical Jacobian computation.
   *
   * <p>
   * When enabled, uses {@link ProcessSensitivityAnalyzer} to compute the Jacobian more efficiently,
   * potentially reusing Broyden Jacobians from recycle convergence.
   * </p>
   *
   * @param useAnalytical true to use analytical Jacobian
   */
  public void setUseAnalyticalJacobian(boolean useAnalytical) {
    this.useAnalyticalJacobian = useAnalytical;
    if (useAnalytical && sensitivityAnalyzer == null) {
      initSensitivityAnalyzer();
    }
  }

  /**
   * Initializes the sensitivity analyzer for Jacobian computation.
   */
  private void initSensitivityAnalyzer() {
    sensitivityAnalyzer = new ProcessSensitivityAnalyzer(processSystem);

    // Add all parameters as inputs
    for (String path : parameterPaths) {
      String[] parts = splitPath(path);
      sensitivityAnalyzer.withInput(parts[0], parts[1]);
    }

    // Add all measurements as outputs
    for (String path : measurementPaths) {
      String[] parts = splitPath(path);
      sensitivityAnalyzer.withOutput(parts[0], parts[1]);
    }

    sensitivityAnalyzer.withCentralDifferences(true);
  }

  /**
   * Splits a path like "Equipment.property" into parts.
   */
  private String[] splitPath(String path) {
    int dotIndex = path.indexOf('.');
    if (dotIndex < 0) {
      throw new IllegalArgumentException("Invalid path format: " + path
          + ". Expected 'Equipment.property' or 'Equipment.stream.property'");
    }
    return new String[] {path.substring(0, dotIndex), path.substring(dotIndex + 1)};
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the model prediction for the current data point and measurement. The dependentValues
   * array encodes [dataPointIndex, measurementIndex] to identify which prediction is requested.
   * </p>
   */
  @Override
  public double calcValue(double[] dependentValues) {
    // Decode indices from dependent values
    int dataPointIdx = (int) dependentValues[0];
    int measIdx = (int) dependentValues[1];

    // Apply current parameter values to the process
    applyParameters();

    // Apply conditions for this data point
    if (dataPointIdx < dataPointConditions.size()) {
      applyConditions(dataPointConditions.get(dataPointIdx));
    }

    // Run the simulation
    try {
      processSystem.run();
    } catch (Exception e) {
      logger.warn("Process simulation failed: " + e.getMessage());
      return Double.NaN;
    }

    // Get the predicted measurement value
    String measurementPath = measurementPaths.get(measIdx);
    return getPropertyValue(measurementPath);
  }

  /**
   * Applies current parameter values to the process system.
   */
  private void applyParameters() {
    for (int i = 0; i < parameterPaths.size(); i++) {
      setPropertyValue(parameterPaths.get(i), params[i]);
    }
  }

  /**
   * Applies operating conditions for a data point.
   */
  private void applyConditions(Map<String, Double> conditions) {
    for (Map.Entry<String, Double> entry : conditions.entrySet()) {
      setPropertyValue(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Gets a property value from the process using path-based access.
   *
   * @param path the property path (e.g., "Equipment.outletStream.temperature")
   * @return the property value
   */
  private double getPropertyValue(String path) {
    // Use reflection or ProcessVariableAccessor pattern
    try {
      String[] parts = path.split("\\.");
      Object current = processSystem.getUnit(parts[0]);

      for (int i = 1; i < parts.length; i++) {
        if (current == null) {
          throw new IllegalArgumentException("Cannot resolve path: " + path);
        }
        current = invokeGetter(current, parts[i]);
      }

      if (current instanceof Number) {
        return ((Number) current).doubleValue();
      }
      throw new IllegalArgumentException("Property at path " + path + " is not a number");
    } catch (Exception e) {
      logger.error("Failed to get property value for path: " + path, e);
      return Double.NaN;
    }
  }

  /**
   * Sets a property value in the process using path-based access.
   *
   * @param path the property path
   * @param value the value to set
   */
  private void setPropertyValue(String path, double value) {
    try {
      String[] parts = path.split("\\.");
      Object current = processSystem.getUnit(parts[0]);

      // Navigate to the parent of the final property
      for (int i = 1; i < parts.length - 1; i++) {
        if (current == null) {
          throw new IllegalArgumentException("Cannot resolve path: " + path);
        }
        current = invokeGetter(current, parts[i]);
      }

      // Set the final property
      if (current != null) {
        invokeSetter(current, parts[parts.length - 1], value);
      }
    } catch (Exception e) {
      logger.error("Failed to set property value for path: " + path, e);
    }
  }

  /**
   * Invokes a getter method on an object.
   */
  private Object invokeGetter(Object obj, String property) throws Exception {
    // Try getProperty() first
    String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    try {
      java.lang.reflect.Method method = obj.getClass().getMethod(getterName);
      return method.invoke(obj);
    } catch (NoSuchMethodException e) {
      // Try property() directly
      try {
        java.lang.reflect.Method method = obj.getClass().getMethod(property);
        return method.invoke(obj);
      } catch (NoSuchMethodException e2) {
        // Try getOutletStream() for stream access
        if (property.equals("outletStream")) {
          java.lang.reflect.Method method = obj.getClass().getMethod("getOutletStream");
          return method.invoke(obj);
        }
        throw e2;
      }
    }
  }

  /**
   * Invokes a setter method on an object.
   */
  private void invokeSetter(Object obj, String property, double value) throws Exception {
    String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    try {
      java.lang.reflect.Method method = obj.getClass().getMethod(setterName, double.class);
      method.invoke(obj, value);
    } catch (NoSuchMethodException e) {
      // Try with Double.class
      try {
        java.lang.reflect.Method method = obj.getClass().getMethod(setterName, Double.class);
        method.invoke(obj, value);
      } catch (NoSuchMethodException e2) {
        throw new IllegalArgumentException("No setter found for property: " + property);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }

  /**
   * Gets the parameter paths.
   *
   * @return list of parameter paths
   */
  public List<String> getParameterPaths() {
    return new ArrayList<>(parameterPaths);
  }

  /**
   * Gets the measurement paths.
   *
   * @return list of measurement paths
   */
  public List<String> getMeasurementPaths() {
    return new ArrayList<>(measurementPaths);
  }

  /**
   * Gets the number of parameters.
   *
   * @return number of tunable parameters
   */
  public int getParameterCount() {
    return parameterPaths.size();
  }

  /**
   * Gets the number of measurements per data point.
   *
   * @return number of measurements
   */
  public int getMeasurementCount() {
    return measurementPaths.size();
  }

  /**
   * Computes the Jacobian using ProcessSensitivityAnalyzer if enabled.
   *
   * <p>
   * This method is called by the L-M optimizer when analytical Jacobian is preferred over numerical
   * differentiation.
   * </p>
   *
   * @return the sensitivity matrix, or null if analytical Jacobian is disabled
   */
  public SensitivityMatrix computeAnalyticalJacobian() {
    if (!useAnalyticalJacobian || sensitivityAnalyzer == null) {
      return null;
    }

    applyParameters();
    cachedJacobian = sensitivityAnalyzer.compute();
    return cachedJacobian;
  }

  /**
   * Gets the sensitivity for a specific output/input pair from cached Jacobian.
   *
   * @param measurementIndex index of the measurement
   * @param parameterIndex index of the parameter
   * @return the sensitivity value, or NaN if not available
   */
  public double getSensitivity(int measurementIndex, int parameterIndex) {
    if (cachedJacobian == null) {
      return Double.NaN;
    }

    String outputPath = measurementPaths.get(measurementIndex);
    String inputPath = parameterPaths.get(parameterIndex);

    // Convert full paths to sensitivity analyzer format
    String[] outParts = splitPath(outputPath);
    String[] inParts = splitPath(inputPath);

    return cachedJacobian.getSensitivity(outParts[0] + "." + outParts[1],
        inParts[0] + "." + inParts[1]);
  }

  /**
   * Gets the underlying process system.
   *
   * @return the process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }
}
