package neqsim.process.safety.risk;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Container for sensitivity analysis results.
 *
 * <p>
 * Stores the results of varying one or more parameters and observing the effect on risk metrics.
 * Supports tornado diagrams, spider plots, and one-way sensitivity charts.
 * </p>
 *
 * @author NeqSim team
 */
public class SensitivityResult {

  private final String analysisName;
  private final String baseCase;

  // Parameter sensitivities: parameter name -> array of (value, risk) pairs
  private final Map<String, double[][]> parameterSensitivities;

  // Tornado chart data: parameter name -> [low_risk, base_risk, high_risk]
  private final Map<String, double[]> tornadoData;

  // Base case values
  private double baseRiskIndex;
  private double baseFrequency;

  /**
   * Creates a new sensitivity result container.
   *
   * @param analysisName name of the analysis
   * @param baseCase description of base case
   */
  public SensitivityResult(String analysisName, String baseCase) {
    this.analysisName = analysisName;
    this.baseCase = baseCase;
    this.parameterSensitivities = new LinkedHashMap<>();
    this.tornadoData = new LinkedHashMap<>();
  }

  // Package-private setters for RiskModel

  void setBaseRiskIndex(double value) {
    this.baseRiskIndex = value;
  }

  void setBaseFrequency(double value) {
    this.baseFrequency = value;
  }

  /**
   * Adds sensitivity data for a parameter.
   *
   * @param parameterName name of the parameter varied
   * @param values array of parameter values tested
   * @param risks corresponding risk values at each parameter value
   */
  void addParameterSensitivity(String parameterName, double[] values, double[] risks) {
    if (values.length != risks.length) {
      throw new IllegalArgumentException("Values and risks arrays must have same length");
    }

    double[][] data = new double[values.length][2];
    for (int i = 0; i < values.length; i++) {
      data[i][0] = values[i];
      data[i][1] = risks[i];
    }
    parameterSensitivities.put(parameterName, data);

    // Also compute tornado data (min, base, max)
    double minRisk = Arrays.stream(risks).min().orElse(0);
    double maxRisk = Arrays.stream(risks).max().orElse(0);
    tornadoData.put(parameterName, new double[] {minRisk, baseRiskIndex, maxRisk});
  }

  // Public getters

  public String getAnalysisName() {
    return analysisName;
  }

  public String getBaseCase() {
    return baseCase;
  }

  public double getBaseRiskIndex() {
    return baseRiskIndex;
  }

  public double getBaseFrequency() {
    return baseFrequency;
  }

  /**
   * Gets the parameter names that were varied.
   *
   * @return array of parameter names
   */
  public String[] getParameterNames() {
    return parameterSensitivities.keySet().toArray(new String[0]);
  }

  /**
   * Gets the sensitivity data for a specific parameter.
   *
   * @param parameterName parameter to query
   * @return array of [value, risk] pairs, or null if not found
   */
  public double[][] getParameterSensitivity(String parameterName) {
    double[][] data = parameterSensitivities.get(parameterName);
    if (data == null) {
      return null;
    }
    // Return a deep copy
    double[][] copy = new double[data.length][2];
    for (int i = 0; i < data.length; i++) {
      copy[i][0] = data[i][0];
      copy[i][1] = data[i][1];
    }
    return copy;
  }

  /**
   * Gets tornado chart data for all parameters.
   *
   * <p>
   * Returns a map of parameter names to [low_risk, base_risk, high_risk] arrays. Parameters are
   * ordered by swing (high - low) in descending order for proper tornado display.
   * </p>
   *
   * @return ordered map of tornado data
   */
  public Map<String, double[]> getTornadoData() {
    // Sort by swing (descending)
    return tornadoData.entrySet().stream()
        .sorted((a, b) -> Double.compare(Math.abs(b.getValue()[2] - b.getValue()[0]),
            Math.abs(a.getValue()[2] - a.getValue()[0])))
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
            (e1, e2) -> e1, LinkedHashMap::new));
  }

  /**
   * Calculates the sensitivity index for a parameter.
   *
   * <p>
   * The sensitivity index is the ratio of the swing in risk to the base risk: (max - min) / base
   * </p>
   *
   * @param parameterName parameter to analyze
   * @return sensitivity index, or 0 if parameter not found
   */
  public double getSensitivityIndex(String parameterName) {
    double[] data = tornadoData.get(parameterName);
    if (data == null || baseRiskIndex == 0) {
      return 0.0;
    }
    return Math.abs(data[2] - data[0]) / baseRiskIndex;
  }

  /**
   * Gets the most sensitive parameter.
   *
   * @return name of parameter with highest sensitivity index
   */
  public String getMostSensitiveParameter() {
    return tornadoData.entrySet().stream().max((a, b) -> {
      double swingA = Math.abs(a.getValue()[2] - a.getValue()[0]);
      double swingB = Math.abs(b.getValue()[2] - b.getValue()[0]);
      return Double.compare(swingA, swingB);
    }).map(Map.Entry::getKey).orElse(null);
  }

  /**
   * Exports results to CSV format.
   *
   * @param filename output file path
   */
  public void exportToCSV(String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("# Sensitivity Analysis: " + analysisName);
      writer.println("# Base Case: " + baseCase);
      writer.println("# Base Risk Index: " + baseRiskIndex);
      writer.println();

      // Tornado data
      writer.println("Parameter,Low Risk,Base Risk,High Risk,Swing,Sensitivity Index");
      for (Map.Entry<String, double[]> entry : getTornadoData().entrySet()) {
        double[] data = entry.getValue();
        double swing = data[2] - data[0];
        double sensitivity = getSensitivityIndex(entry.getKey());
        writer.printf("%s,%.6e,%.6e,%.6e,%.6e,%.4f%n", entry.getKey(), data[0], data[1], data[2],
            swing, sensitivity);
      }
      writer.println();

      // Detailed sensitivity curves
      for (Map.Entry<String, double[][]> entry : parameterSensitivities.entrySet()) {
        writer.println("# Parameter: " + entry.getKey());
        writer.println("Value,Risk");
        for (double[] point : entry.getValue()) {
          writer.printf("%.6e,%.6e%n", point[0], point[1]);
        }
        writer.println();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to export sensitivity results: " + e.getMessage(), e);
    }
  }

  /**
   * Exports results to JSON format.
   *
   * @param filename output file path
   */
  public void exportToJSON(String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("{");
      writer.println("  \"analysisName\": \"" + analysisName + "\",");
      writer.println("  \"baseCase\": \"" + baseCase + "\",");
      writer.println("  \"baseRiskIndex\": " + baseRiskIndex + ",");
      writer.println("  \"baseFrequency\": " + baseFrequency + ",");

      // Tornado data
      writer.println("  \"tornado\": {");
      String[] params = getTornadoData().keySet().toArray(new String[0]);
      for (int i = 0; i < params.length; i++) {
        double[] data = tornadoData.get(params[i]);
        String comma = (i < params.length - 1) ? "," : "";
        writer.printf("    \"%s\": {\"low\": %.6e, \"base\": %.6e, \"high\": %.6e}%s%n", params[i],
            data[0], data[1], data[2], comma);
      }
      writer.println("  },");

      // Sensitivity curves
      writer.println("  \"curves\": {");
      params = parameterSensitivities.keySet().toArray(new String[0]);
      for (int i = 0; i < params.length; i++) {
        double[][] data = parameterSensitivities.get(params[i]);
        String comma = (i < params.length - 1) ? "," : "";
        writer.println("    \"" + params[i] + "\": [");
        for (int j = 0; j < data.length; j++) {
          String ptComma = (j < data.length - 1) ? "," : "";
          writer.printf("      [%.6e, %.6e]%s%n", data[j][0], data[j][1], ptComma);
        }
        writer.println("    ]" + comma);
      }
      writer.println("  }");

      writer.println("}");

    } catch (IOException e) {
      throw new RuntimeException("Failed to export sensitivity results: " + e.getMessage(), e);
    }
  }

  @Override
  public String toString() {
    return String.format("SensitivityResult[%s, %d parameters, base risk=%.4e]", analysisName,
        parameterSensitivities.size(), baseRiskIndex);
  }
}
