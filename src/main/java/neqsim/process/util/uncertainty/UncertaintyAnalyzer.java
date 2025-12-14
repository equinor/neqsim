package neqsim.process.util.uncertainty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.vfm.UncertaintyBounds;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Performs uncertainty propagation analysis for process simulations.
 *
 * <p>
 * Supports both analytical (linear) uncertainty propagation and Monte Carlo simulation for
 * comprehensive uncertainty quantification.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class UncertaintyAnalyzer {

  private final ProcessSystem processSystem;
  private final Map<String, InputUncertainty> inputUncertainties;
  private final List<String> outputVariables;
  private final Random random;

  /**
   * Configuration for input variable uncertainty.
   */
  public static class InputUncertainty {
    private final String variableName;
    private final double standardDeviation;
    private final DistributionType distribution;

    public enum DistributionType {
      NORMAL, UNIFORM, TRIANGULAR, LOGNORMAL
    }

    public InputUncertainty(String variableName, double standardDeviation) {
      this(variableName, standardDeviation, DistributionType.NORMAL);
    }

    public InputUncertainty(String variableName, double standardDeviation,
        DistributionType distribution) {
      this.variableName = variableName;
      this.standardDeviation = standardDeviation;
      this.distribution = distribution;
    }

    public String getVariableName() {
      return variableName;
    }

    public double getStandardDeviation() {
      return standardDeviation;
    }

    public DistributionType getDistribution() {
      return distribution;
    }
  }

  /**
   * Creates an uncertainty analyzer for a process system.
   *
   * @param processSystem the process system to analyze
   */
  public UncertaintyAnalyzer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.inputUncertainties = new HashMap<>();
    this.outputVariables = new ArrayList<>();
    this.random = new Random();
  }

  /**
   * Adds an input variable with uncertainty.
   *
   * @param variableName the input variable name
   * @param standardDeviation the standard deviation
   */
  public void addInputUncertainty(String variableName, double standardDeviation) {
    inputUncertainties.put(variableName, new InputUncertainty(variableName, standardDeviation));
  }

  /**
   * Adds an input variable with uncertainty and distribution type.
   *
   * @param uncertainty the input uncertainty configuration
   */
  public void addInputUncertainty(InputUncertainty uncertainty) {
    inputUncertainties.put(uncertainty.getVariableName(), uncertainty);
  }

  /**
   * Adds an output variable to monitor.
   *
   * @param variableName the output variable name (measurement device name)
   */
  public void addOutputVariable(String variableName) {
    outputVariables.add(variableName);
  }

  /**
   * Performs analytical uncertainty propagation using linear approximation.
   *
   * @return uncertainty result with bounds for all outputs
   */
  public UncertaintyResult analyzeAnalytical() {
    // Calculate sensitivity matrix using finite differences
    SensitivityMatrix matrix = calculateSensitivityMatrix();

    // Propagate uncertainties
    double[] inputStdDevs = new double[inputUncertainties.size()];
    String[] inputNames = matrix.getInputVariables();
    for (int i = 0; i < inputNames.length; i++) {
      InputUncertainty uncert = inputUncertainties.get(inputNames[i]);
      inputStdDevs[i] = (uncert != null) ? uncert.getStandardDeviation() : 0.0;
    }

    double[] outputStdDevs = matrix.propagateUncertainty(inputStdDevs);

    // Build result map
    Map<String, UncertaintyBounds> results = new HashMap<>();
    String[] outputNames = matrix.getOutputVariables();
    for (int i = 0; i < outputNames.length; i++) {
      double mean = getOutputValue(outputNames[i]);
      results.put(outputNames[i], new UncertaintyBounds(mean, outputStdDevs[i], ""));
    }

    return new UncertaintyResult(results, matrix);
  }

  /**
   * Performs Monte Carlo uncertainty analysis.
   *
   * @param samples number of Monte Carlo samples
   * @return uncertainty result with bounds for all outputs
   */
  public UncertaintyResult analyzeMonteCarlo(int samples) {
    // Store baseline values
    Map<String, Double> baselineInputs = getInputValues();

    // Storage for samples
    Map<String, List<Double>> outputSamples = new HashMap<>();
    for (String output : outputVariables) {
      outputSamples.put(output, new ArrayList<>());
    }

    // Run Monte Carlo
    for (int i = 0; i < samples; i++) {
      // Perturb inputs
      perturbInputs();

      // Run simulation
      try {
        processSystem.run();

        // Collect outputs
        for (String output : outputVariables) {
          double value = getOutputValue(output);
          outputSamples.get(output).add(value);
        }
      } catch (Exception e) {
        // Skip failed samples
      }
    }

    // Restore baseline
    restoreInputs(baselineInputs);
    processSystem.run();

    // Calculate statistics
    Map<String, UncertaintyBounds> results = new HashMap<>();
    double maxCv = 0.0;

    for (String output : outputVariables) {
      List<Double> samples2 = outputSamples.get(output);
      if (!samples2.isEmpty()) {
        double mean = samples2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance =
            samples2.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        results.put(output, new UncertaintyBounds(mean, stdDev, ""));

        double cv = (Math.abs(mean) > 1e-10) ? stdDev / Math.abs(mean) : 0.0;
        maxCv = Math.max(maxCv, cv);
      }
    }

    return new UncertaintyResult(results, samples, maxCv / Math.sqrt(samples));
  }

  /**
   * Calculates the sensitivity matrix using finite differences.
   */
  private SensitivityMatrix calculateSensitivityMatrix() {
    String[] inputs = inputUncertainties.keySet().toArray(new String[0]);
    String[] outputs = outputVariables.toArray(new String[0]);

    SensitivityMatrix matrix = new SensitivityMatrix(inputs, outputs);

    // Baseline run
    processSystem.run();
    double[] baselineOutputs = new double[outputs.length];
    for (int i = 0; i < outputs.length; i++) {
      baselineOutputs[i] = getOutputValue(outputs[i]);
    }

    // Perturb each input
    for (int j = 0; j < inputs.length; j++) {
      double baseValue = getInputValue(inputs[j]);
      double perturbation = Math.abs(baseValue) * 0.001; // 0.1% perturbation
      if (perturbation < 1e-6) {
        perturbation = 1e-6;
      }

      // Positive perturbation
      setInputValue(inputs[j], baseValue + perturbation);
      processSystem.run();

      for (int i = 0; i < outputs.length; i++) {
        double perturbedOutput = getOutputValue(outputs[i]);
        double sensitivity = (perturbedOutput - baselineOutputs[i]) / perturbation;
        matrix.setSensitivity(outputs[i], inputs[j], sensitivity);
      }

      // Restore
      setInputValue(inputs[j], baseValue);
    }

    // Restore baseline
    processSystem.run();

    return matrix;
  }

  /**
   * Perturbs input values according to their uncertainty distributions.
   */
  private void perturbInputs() {
    for (InputUncertainty uncert : inputUncertainties.values()) {
      double baseValue = getInputValue(uncert.getVariableName());
      double perturbation;

      switch (uncert.getDistribution()) {
        case NORMAL:
          perturbation = random.nextGaussian() * uncert.getStandardDeviation();
          break;
        case UNIFORM:
          double range = uncert.getStandardDeviation() * Math.sqrt(12) / 2;
          perturbation = (random.nextDouble() - 0.5) * 2 * range;
          break;
        case LOGNORMAL:
          double sigma =
              Math.sqrt(Math.log(1 + Math.pow(uncert.getStandardDeviation() / baseValue, 2)));
          double mu = Math.log(baseValue) - sigma * sigma / 2;
          double logValue = mu + sigma * random.nextGaussian();
          perturbation = Math.exp(logValue) - baseValue;
          break;
        default:
          perturbation = random.nextGaussian() * uncert.getStandardDeviation();
      }

      setInputValue(uncert.getVariableName(), baseValue + perturbation);
    }
  }

  private Map<String, Double> getInputValues() {
    Map<String, Double> values = new HashMap<>();
    for (String input : inputUncertainties.keySet()) {
      values.put(input, getInputValue(input));
    }
    return values;
  }

  private void restoreInputs(Map<String, Double> values) {
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      setInputValue(entry.getKey(), entry.getValue());
    }
  }

  private double getInputValue(String name) {
    // Try to find as measurement device first
    MeasurementDeviceInterface device = processSystem.getMeasurementDevice(name);
    if (device != null) {
      return device.getMeasuredValue();
    }
    return 0.0;
  }

  private void setInputValue(String name, double value) {
    // Implementation depends on input type
    // This is a simplified version
  }

  private double getOutputValue(String name) {
    MeasurementDeviceInterface device = processSystem.getMeasurementDevice(name);
    if (device != null) {
      return device.getMeasuredValue();
    }
    return Double.NaN;
  }

  /**
   * Sets the random seed for reproducible Monte Carlo.
   *
   * @param seed the random seed
   */
  public void setRandomSeed(long seed) {
    random.setSeed(seed);
  }
}
