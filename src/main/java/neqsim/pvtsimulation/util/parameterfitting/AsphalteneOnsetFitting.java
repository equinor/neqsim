package neqsim.pvtsimulation.util.parameterfitting;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;

/**
 * Fits CPA asphaltene model parameters to measured asphaltene onset points.
 *
 * <p>
 * This class provides a convenient interface for tuning asphaltene CPA parameters to match
 * experimental onset pressure data at various temperatures. The fitting uses the
 * Levenberg-Marquardt algorithm to minimize the difference between calculated and measured onset
 * pressures.
 * </p>
 *
 * <p>
 * Typical usage:
 * </p>
 * 
 * <pre>
 * // Create fluid system with asphaltene
 * SystemInterface fluid = new SystemSrkCPAstatoil(373.15, 200.0);
 * fluid.addComponent("methane", 0.40);
 * fluid.addTBPfraction("C7", 0.30, 0.100, 0.75);
 * fluid.addComponent("asphaltene", 0.05);
 * fluid.setMixingRule("classic");
 *
 * // Create fitter
 * AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);
 *
 * // Add experimental onset points (temperature_K, pressure_bara)
 * fitter.addOnsetPoint(353.15, 350.0); // 80°C, 350 bar
 * fitter.addOnsetPoint(373.15, 320.0); // 100°C, 320 bar
 * fitter.addOnsetPoint(393.15, 280.0); // 120°C, 280 bar
 *
 * // Set initial parameter guesses
 * fitter.setInitialGuess(3500.0, 0.005); // epsilon/R, kappa
 *
 * // Run fitting
 * fitter.solve();
 *
 * // Get fitted parameters
 * double[] params = fitter.getFittedParameters();
 * System.out.println("Fitted epsilon/R: " + params[0] + " K");
 * System.out.println("Fitted kappa: " + params[1]);
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Li, Z., Firoozabadi, A. (2010). Energy Fuels, 24, 1106-1113</li>
 * <li>Vargas, F.M., et al. (2009). Energy Fuels, 23, 1140-1146</li>
 * <li>Gonzalez, D.L., et al. (2007). Energy Fuels, 21, 1231-1242</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneOnsetFitting {

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AsphalteneOnsetFitting.class);

  /** The thermodynamic system containing asphaltene. */
  private SystemInterface system;

  /** The fitting function. */
  private AsphalteneOnsetFunction function;

  /** The Levenberg-Marquardt optimizer. */
  private LevenbergMarquardt optimizer;

  /** List of experimental onset data points. */
  private List<OnsetDataPoint> onsetData;

  /** Fitted parameters after solving. */
  private double[] fittedParameters;

  /** Flag indicating if fitting was successful. */
  private boolean solved = false;

  /** Standard deviation for pressure measurements (bara). */
  private double pressureStdDev = 5.0;

  /**
   * Inner class to hold an onset data point.
   */
  public static class OnsetDataPoint {
    /** Temperature in Kelvin. */
    public double temperatureK;
    /** Measured onset pressure in bara. */
    public double pressureBara;
    /** Standard deviation of pressure measurement. */
    public double stdDev;

    /**
     * Constructor for OnsetDataPoint.
     *
     * @param temperatureK temperature in Kelvin
     * @param pressureBara onset pressure in bara
     * @param stdDev standard deviation of measurement
     */
    public OnsetDataPoint(double temperatureK, double pressureBara, double stdDev) {
      this.temperatureK = temperatureK;
      this.pressureBara = pressureBara;
      this.stdDev = stdDev;
    }
  }

  /**
   * Constructor for AsphalteneOnsetFitting.
   *
   * @param system the thermodynamic system (must contain asphaltene component)
   */
  public AsphalteneOnsetFitting(SystemInterface system) {
    this.system = system.clone();
    this.function = new AsphalteneOnsetFunction();
    this.function.setThermodynamicSystem(this.system);
    this.onsetData = new ArrayList<>();
    this.optimizer = new LevenbergMarquardt();
  }

  /**
   * Adds an experimental onset point.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara measured onset pressure in bara
   */
  public void addOnsetPoint(double temperatureK, double pressureBara) {
    addOnsetPoint(temperatureK, pressureBara, pressureStdDev);
  }

  /**
   * Adds an experimental onset point with specified uncertainty.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara measured onset pressure in bara
   * @param stdDev standard deviation of measurement
   */
  public void addOnsetPoint(double temperatureK, double pressureBara, double stdDev) {
    onsetData.add(new OnsetDataPoint(temperatureK, pressureBara, stdDev));
  }

  /**
   * Adds onset point with temperature in Celsius.
   *
   * @param temperatureC temperature in Celsius
   * @param pressureBara measured onset pressure in bara
   */
  public void addOnsetPointCelsius(double temperatureC, double pressureBara) {
    addOnsetPoint(temperatureC + 273.15, pressureBara);
  }

  /**
   * Clears all experimental data points.
   */
  public void clearData() {
    onsetData.clear();
    solved = false;
  }

  /**
   * Sets the initial parameter guess for association parameters.
   *
   * @param epsilonOverR association energy epsilon/R in Kelvin
   * @param kappa association volume (dimensionless)
   */
  public void setInitialGuess(double epsilonOverR, double kappa) {
    double[] guess = {epsilonOverR, kappa};
    function.setInitialGuess(guess);
  }

  /**
   * Sets single parameter initial guess.
   *
   * @param value initial guess value
   */
  public void setInitialGuess(double value) {
    double[] guess = {value};
    function.setInitialGuess(guess);
  }

  /**
   * Sets the pressure search range for onset calculation.
   *
   * @param startPressure starting pressure in bara
   * @param minPressure minimum pressure in bara
   * @param pressureStep pressure step in bara
   */
  public void setPressureRange(double startPressure, double minPressure, double pressureStep) {
    function.setPressureRange(startPressure, minPressure, pressureStep);
  }

  /**
   * Sets the type of parameters to fit.
   *
   * @param type the parameter type
   */
  public void setParameterType(AsphalteneOnsetFunction.FittingParameterType type) {
    function.setParameterType(type);
  }

  /**
   * Sets the default standard deviation for pressure measurements.
   *
   * @param stdDev standard deviation in bara
   */
  public void setPressureStdDev(double stdDev) {
    this.pressureStdDev = stdDev;
  }

  /**
   * Runs the parameter fitting.
   *
   * @return true if fitting converged successfully
   */
  public boolean solve() {
    if (onsetData.isEmpty()) {
      logger.error("No experimental data points added");
      return false;
    }

    if (function.getFittingParams() == null) {
      logger.error("Initial guess not set. Call setInitialGuess() first.");
      return false;
    }

    // Create sample set from experimental data
    ArrayList<SampleValue> sampleList = new ArrayList<>();

    for (OnsetDataPoint point : onsetData) {
      double[] independentVars = {point.temperatureK};
      double[] stdDevs = {0.1}; // Temperature uncertainty

      SampleValue sample =
          new SampleValue(point.pressureBara, point.stdDev, independentVars, stdDevs);
      sample.setFunction(function);
      sample.setThermodynamicSystem(system.clone());
      sampleList.add(sample);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optimizer.setSampleSet(sampleSet);

    try {
      optimizer.solve();
      fittedParameters = function.getFittingParams().clone();
      solved = true;

      logger.info("Fitting converged successfully");
      printResults();
      return true;

    } catch (Exception e) {
      logger.error("Fitting failed: {}", e.getMessage());
      solved = false;
      return false;
    }
  }

  /**
   * Prints the fitting results.
   */
  public void printResults() {
    System.out.println("\n========================================");
    System.out.println("ASPHALTENE ONSET FITTING RESULTS");
    System.out.println("========================================\n");

    System.out.println("Fitted Parameters:");
    if (fittedParameters != null && fittedParameters.length >= 2) {
      System.out.printf("  Association Energy (ε/R): %.1f K%n", fittedParameters[0]);
      System.out.printf("  Association Volume (κ):   %.6f%n", fittedParameters[1]);
    } else if (fittedParameters != null && fittedParameters.length == 1) {
      System.out.printf("  Fitted parameter: %.4f%n", fittedParameters[0]);
    }

    System.out.println("\nExperimental vs Calculated:");
    System.out.println("T [K]     | P_exp [bar] | P_calc [bar] | Error [%]");
    System.out.println("----------|-------------|--------------|----------");

    double totalError = 0.0;
    int n = 0;

    for (OnsetDataPoint point : onsetData) {
      double[] temp = {point.temperatureK};
      double calcPressure = function.calcValue(temp);
      double error = 100.0 * (calcPressure - point.pressureBara) / point.pressureBara;
      totalError += Math.abs(error);
      n++;

      System.out.printf("%9.2f | %11.1f | %12.1f | %+7.2f%%%n", point.temperatureK,
          point.pressureBara, calcPressure, error);
    }

    System.out.println("----------|-------------|--------------|----------");
    System.out.printf("Average Absolute Error: %.2f%%%n", totalError / n);
    System.out.println();
  }

  /**
   * Gets the fitted parameters.
   *
   * @return array of fitted parameters, or null if not solved
   */
  public double[] getFittedParameters() {
    return fittedParameters;
  }

  /**
   * Gets the fitted association energy.
   *
   * @return association energy epsilon/R in Kelvin
   */
  public double getFittedAssociationEnergy() {
    if (fittedParameters != null && fittedParameters.length >= 1) {
      return fittedParameters[0];
    }
    return Double.NaN;
  }

  /**
   * Gets the fitted association volume.
   *
   * @return association volume kappa
   */
  public double getFittedAssociationVolume() {
    if (fittedParameters != null && fittedParameters.length >= 2) {
      return fittedParameters[1];
    }
    return Double.NaN;
  }

  /**
   * Checks if fitting has been performed.
   *
   * @return true if solve() was called successfully
   */
  public boolean isSolved() {
    return solved;
  }

  /**
   * Calculates onset pressure at a given temperature using fitted parameters.
   *
   * @param temperatureK temperature in Kelvin
   * @return calculated onset pressure in bara
   */
  public double calculateOnsetPressure(double temperatureK) {
    if (!solved) {
      logger.warn("Parameters not fitted yet. Using initial guess.");
    }
    double[] temp = {temperatureK};
    return function.calcValue(temp);
  }

  /**
   * Gets the optimizer for advanced configuration.
   *
   * @return the Levenberg-Marquardt optimizer
   */
  public LevenbergMarquardt getOptimizer() {
    return optimizer;
  }

  /**
   * Displays a curve fit plot.
   */
  public void displayCurveFit() {
    if (solved) {
      optimizer.displayCurveFit();
    }
  }

  /**
   * Gets the fitting function for advanced configuration.
   *
   * @return the asphaltene onset function
   */
  public AsphalteneOnsetFunction getFunction() {
    return function;
  }

  /**
   * Gets the number of experimental data points.
   *
   * @return number of data points
   */
  public int getNumberOfDataPoints() {
    return onsetData.size();
  }
}
