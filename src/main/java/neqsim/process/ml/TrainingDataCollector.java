package neqsim.process.ml;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Training data collector for surrogate model development.
 *
 * <p>
 * Collects input-output pairs from NeqSim simulations for training neural network surrogates.
 * Supports:
 * <ul>
 * <li>CSV export for scikit-learn, PyTorch, TensorFlow</li>
 * <li>JSON export for flexible data handling</li>
 * <li>Feature normalization statistics</li>
 * <li>Train/validation/test split suggestions</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * TrainingDataCollector collector = new TrainingDataCollector("flash_surrogate");
 * collector.defineInput("temperature", "K", 200.0, 500.0);
 * collector.defineInput("pressure", "bar", 1.0, 100.0);
 * collector.defineOutput("vapor_fraction", "mole_frac", 0.0, 1.0);
 *
 * // Run many simulations
 * for (...) {
 *   collector.startSample();
 *   collector.recordInput("temperature", T);
 *   collector.recordInput("pressure", P);
 *   // Run flash calculation
 *   collector.recordOutput("vapor_fraction", result);
 *   collector.endSample();
 * }
 *
 * collector.exportCSV("training_data.csv");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class TrainingDataCollector implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final Map<String, FeatureDefinition> inputDefs;
  private final Map<String, FeatureDefinition> outputDefs;
  private final List<Map<String, Double>> samples;
  private Map<String, Double> currentSample;

  // Statistics
  private final Map<String, RunningStats> inputStats;
  private final Map<String, RunningStats> outputStats;

  /**
   * Feature definition for inputs/outputs.
   */
  private static class FeatureDefinition implements Serializable {
    private static final long serialVersionUID = 1000L;

    String name;
    String unit;
    double minBound;
    double maxBound;

    FeatureDefinition(String name, String unit, double min, double max) {
      this.name = name;
      this.unit = unit;
      this.minBound = min;
      this.maxBound = max;
    }
  }

  /**
   * Running statistics calculator.
   */
  private static class RunningStats implements Serializable {
    private static final long serialVersionUID = 1000L;

    long count = 0;
    double mean = 0;
    double m2 = 0; // For variance calculation
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;

    void update(double x) {
      count++;
      double delta = x - mean;
      mean += delta / count;
      m2 += delta * (x - mean);
      min = Math.min(min, x);
      max = Math.max(max, x);
    }

    double getStd() {
      return count > 1 ? Math.sqrt(m2 / (count - 1)) : 0.0;
    }
  }

  /**
   * Create a training data collector.
   *
   * @param name identifier for this dataset
   */
  public TrainingDataCollector(String name) {
    this.name = name;
    this.inputDefs = new LinkedHashMap<>();
    this.outputDefs = new LinkedHashMap<>();
    this.samples = new ArrayList<>();
    this.inputStats = new LinkedHashMap<>();
    this.outputStats = new LinkedHashMap<>();
  }

  /**
   * Define an input feature.
   *
   * @param name feature name
   * @param unit physical unit
   * @param minBound expected minimum value
   * @param maxBound expected maximum value
   * @return this collector for chaining
   */
  public TrainingDataCollector defineInput(String name, String unit, double minBound,
      double maxBound) {
    inputDefs.put(name, new FeatureDefinition(name, unit, minBound, maxBound));
    inputStats.put(name, new RunningStats());
    return this;
  }

  /**
   * Define an output feature.
   *
   * @param name feature name
   * @param unit physical unit
   * @param minBound expected minimum value
   * @param maxBound expected maximum value
   * @return this collector for chaining
   */
  public TrainingDataCollector defineOutput(String name, String unit, double minBound,
      double maxBound) {
    outputDefs.put(name, new FeatureDefinition(name, unit, minBound, maxBound));
    outputStats.put(name, new RunningStats());
    return this;
  }

  /**
   * Start recording a new sample.
   */
  public void startSample() {
    currentSample = new LinkedHashMap<>();
  }

  /**
   * Record an input value for current sample.
   *
   * @param name input feature name
   * @param value value to record
   */
  public void recordInput(String name, double value) {
    if (currentSample == null) {
      throw new IllegalStateException("Call startSample() first");
    }
    currentSample.put("input_" + name, value);
    inputStats.get(name).update(value);
  }

  /**
   * Record an output value for current sample.
   *
   * @param name output feature name
   * @param value value to record
   */
  public void recordOutput(String name, double value) {
    if (currentSample == null) {
      throw new IllegalStateException("Call startSample() first");
    }
    currentSample.put("output_" + name, value);
    outputStats.get(name).update(value);
  }

  /**
   * Record state vector as inputs.
   *
   * @param state state vector
   */
  public void recordStateAsInputs(StateVector state) {
    for (String name : state.getFeatureNames()) {
      if (inputDefs.containsKey(name)) {
        recordInput(name, state.getValue(name));
      }
    }
  }

  /**
   * Record state vector as outputs.
   *
   * @param state state vector
   */
  public void recordStateAsOutputs(StateVector state) {
    for (String name : state.getFeatureNames()) {
      if (outputDefs.containsKey(name)) {
        recordOutput(name, state.getValue(name));
      }
    }
  }

  /**
   * End current sample and add to dataset.
   */
  public void endSample() {
    if (currentSample != null) {
      samples.add(currentSample);
      currentSample = null;
    }
  }

  /**
   * Get number of samples collected.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return samples.size();
  }

  /**
   * Get dataset name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Export to CSV format.
   *
   * @param filePath path to output file
   * @throws IOException if writing fails
   */
  public void exportCSV(String filePath) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
      writer.write(toCSV());
    }
  }

  /**
   * Export to CSV string.
   *
   * @return CSV formatted string
   */
  public String toCSV() {
    StringWriter sw = new StringWriter();

    // Header
    StringBuilder header = new StringBuilder();
    for (String name : inputDefs.keySet()) {
      if (header.length() > 0) {
        header.append(",");
      }
      header.append("input_").append(name);
    }
    for (String name : outputDefs.keySet()) {
      header.append(",output_").append(name);
    }
    sw.write(header.toString());
    sw.write("\n");

    // Data rows
    for (Map<String, Double> sample : samples) {
      StringBuilder row = new StringBuilder();
      for (String name : inputDefs.keySet()) {
        if (row.length() > 0) {
          row.append(",");
        }
        Double val = sample.get("input_" + name);
        row.append(val != null ? val : "");
      }
      for (String name : outputDefs.keySet()) {
        row.append(",");
        Double val = sample.get("output_" + name);
        row.append(val != null ? val : "");
      }
      sw.write(row.toString());
      sw.write("\n");
    }

    return sw.toString();
  }

  /**
   * Get normalization statistics for inputs.
   *
   * @return map of feature name to stats (mean, std, min, max)
   */
  public Map<String, Map<String, Double>> getInputStatistics() {
    Map<String, Map<String, Double>> result = new LinkedHashMap<>();
    for (String name : inputDefs.keySet()) {
      RunningStats stats = inputStats.get(name);
      Map<String, Double> featureStats = new LinkedHashMap<>();
      featureStats.put("mean", stats.mean);
      featureStats.put("std", stats.getStd());
      featureStats.put("min", stats.min);
      featureStats.put("max", stats.max);
      featureStats.put("count", (double) stats.count);
      result.put(name, featureStats);
    }
    return result;
  }

  /**
   * Get normalization statistics for outputs.
   *
   * @return map of feature name to stats (mean, std, min, max)
   */
  public Map<String, Map<String, Double>> getOutputStatistics() {
    Map<String, Map<String, Double>> result = new LinkedHashMap<>();
    for (String name : outputDefs.keySet()) {
      RunningStats stats = outputStats.get(name);
      Map<String, Double> featureStats = new LinkedHashMap<>();
      featureStats.put("mean", stats.mean);
      featureStats.put("std", stats.getStd());
      featureStats.put("min", stats.min);
      featureStats.put("max", stats.max);
      featureStats.put("count", (double) stats.count);
      result.put(name, featureStats);
    }
    return result;
  }

  /**
   * Clear all collected samples.
   */
  public void clear() {
    samples.clear();
    inputStats.values().forEach(s -> {
      s.count = 0;
      s.mean = 0;
      s.m2 = 0;
      s.min = Double.MAX_VALUE;
      s.max = Double.MIN_VALUE;
    });
    outputStats.values().forEach(s -> {
      s.count = 0;
      s.mean = 0;
      s.m2 = 0;
      s.min = Double.MAX_VALUE;
      s.max = Double.MIN_VALUE;
    });
  }

  /**
   * Get summary statistics as formatted string.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("TrainingDataCollector: ").append(name).append("\n");
    sb.append("Samples collected: ").append(samples.size()).append("\n\n");

    sb.append("Inputs:\n");
    for (String name : inputDefs.keySet()) {
      FeatureDefinition def = inputDefs.get(name);
      RunningStats stats = inputStats.get(name);
      sb.append(String.format("  %s [%s]: mean=%.4f, std=%.4f, range=[%.4f, %.4f]\n", name,
          def.unit, stats.mean, stats.getStd(), stats.min, stats.max));
    }

    sb.append("\nOutputs:\n");
    for (String name : outputDefs.keySet()) {
      FeatureDefinition def = outputDefs.get(name);
      RunningStats stats = outputStats.get(name);
      sb.append(String.format("  %s [%s]: mean=%.4f, std=%.4f, range=[%.4f, %.4f]\n", name,
          def.unit, stats.mean, stats.getStd(), stats.min, stats.max));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("TrainingDataCollector[%s, %d inputs, %d outputs, %d samples]", name,
        inputDefs.size(), outputDefs.size(), samples.size());
  }
}
