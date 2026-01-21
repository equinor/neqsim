package neqsim.process.safety.risk;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.risk.RiskEvent.ConsequenceCategory;

/**
 * Container for probabilistic risk analysis results.
 *
 * <p>
 * Stores Monte Carlo simulation results, frequency distributions, and risk metrics for reporting
 * and export to external tools.
 * </p>
 *
 * @author NeqSim team
 */
public class RiskResult {

  private final String analysisName;
  private final int iterations;
  private final long seed;

  // Summary statistics
  private double totalRiskIndex;
  private double meanConsequence;
  private double maxConsequence;
  private double percentile95Consequence;
  private double percentile99Consequence;

  // Frequency by consequence category
  private final Map<ConsequenceCategory, Double> frequencyByCategory;

  // Individual event results
  private final List<EventResult> eventResults;

  // Monte Carlo samples (if stored)
  private double[] samples;

  /**
   * Results for a single risk event.
   */
  public static class EventResult {
    private final String eventName;
    private final double frequency;
    private final double probability;
    private final double riskContribution;
    private final ConsequenceCategory category;

    public EventResult(String name, double freq, double prob, double risk,
        ConsequenceCategory cat) {
      this.eventName = name;
      this.frequency = freq;
      this.probability = prob;
      this.riskContribution = risk;
      this.category = cat;
    }

    public String getEventName() {
      return eventName;
    }

    public double getFrequency() {
      return frequency;
    }

    public double getProbability() {
      return probability;
    }

    public double getRiskContribution() {
      return riskContribution;
    }

    public ConsequenceCategory getCategory() {
      return category;
    }
  }

  /**
   * Creates a new risk result container.
   *
   * @param analysisName name of the analysis
   * @param iterations number of Monte Carlo iterations
   * @param seed random seed used
   */
  public RiskResult(String analysisName, int iterations, long seed) {
    this.analysisName = analysisName;
    this.iterations = iterations;
    this.seed = seed;
    this.frequencyByCategory = new HashMap<>();
    this.eventResults = new ArrayList<>();

    // Initialize category frequencies
    for (ConsequenceCategory cat : ConsequenceCategory.values()) {
      frequencyByCategory.put(cat, 0.0);
    }
  }

  // Package-private setters for RiskModel

  void setTotalRiskIndex(double value) {
    this.totalRiskIndex = value;
  }

  void setMeanConsequence(double value) {
    this.meanConsequence = value;
  }

  void setMaxConsequence(double value) {
    this.maxConsequence = value;
  }

  void setPercentile95(double value) {
    this.percentile95Consequence = value;
  }

  void setPercentile99(double value) {
    this.percentile99Consequence = value;
  }

  void setCategoryFrequency(ConsequenceCategory category, double frequency) {
    frequencyByCategory.put(category, frequency);
  }

  void addEventResult(EventResult result) {
    eventResults.add(result);
  }

  void setSamples(double[] samples) {
    this.samples = Arrays.copyOf(samples, samples.length);
  }

  // Public getters

  public String getAnalysisName() {
    return analysisName;
  }

  public int getIterations() {
    return iterations;
  }

  public long getSeed() {
    return seed;
  }

  public double getTotalRiskIndex() {
    return totalRiskIndex;
  }

  public double getMeanConsequence() {
    return meanConsequence;
  }

  public double getMaxConsequence() {
    return maxConsequence;
  }

  public double getPercentile95() {
    return percentile95Consequence;
  }

  public double getPercentile99() {
    return percentile99Consequence;
  }

  public double getCategoryFrequency(ConsequenceCategory category) {
    return frequencyByCategory.getOrDefault(category, 0.0);
  }

  public List<EventResult> getEventResults() {
    return new ArrayList<>(eventResults);
  }

  public double[] getSamples() {
    return samples != null ? Arrays.copyOf(samples, samples.length) : new double[0];
  }

  /**
   * Calculates total frequency across all consequence categories.
   *
   * @return total annual frequency
   */
  public double getTotalFrequency() {
    return frequencyByCategory.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  /**
   * Gets the F-N curve data points (Frequency vs Number affected).
   *
   * <p>
   * Returns pairs of (N, F) where F is the cumulative frequency of events with N or more
   * consequences.
   * </p>
   *
   * @return array of [N, cumulative_frequency] pairs
   */
  public double[][] getFNCurveData() {
    ConsequenceCategory[] categories = ConsequenceCategory.values();
    double[][] fnData = new double[categories.length][2];

    double cumulativeFreq = 0.0;
    for (int i = categories.length - 1; i >= 0; i--) {
      cumulativeFreq += frequencyByCategory.get(categories[i]);
      fnData[i][0] = categories[i].getSeverity();
      fnData[i][1] = cumulativeFreq;
    }

    return fnData;
  }

  /**
   * Exports results to CSV format.
   *
   * @param filename output file path
   */
  public void exportToCSV(String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("# Risk Analysis Results: " + analysisName);
      writer.println("# Iterations: " + iterations);
      writer.println("# Seed: " + seed);
      writer.println();

      // Summary metrics
      writer.println("Metric,Value");
      writer.println("Total Risk Index," + totalRiskIndex);
      writer.println("Mean Consequence," + meanConsequence);
      writer.println("Max Consequence," + maxConsequence);
      writer.println("95th Percentile," + percentile95Consequence);
      writer.println("99th Percentile," + percentile99Consequence);
      writer.println("Total Frequency (per year)," + getTotalFrequency());
      writer.println();

      // Frequency by category
      writer.println("Category,Severity,Frequency (per year)");
      for (ConsequenceCategory cat : ConsequenceCategory.values()) {
        writer.printf("%s,%d,%.6e%n", cat.name(), cat.getSeverity(), frequencyByCategory.get(cat));
      }
      writer.println();

      // Event results
      writer.println("Event,Frequency,Probability,Risk Contribution,Category");
      for (EventResult er : eventResults) {
        writer.printf("%s,%.6e,%.6f,%.6e,%s%n", er.eventName, er.frequency, er.probability,
            er.riskContribution, er.category.name());
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to export risk results: " + e.getMessage(), e);
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
      writer.println("  \"iterations\": " + iterations + ",");
      writer.println("  \"seed\": " + seed + ",");
      writer.println("  \"summary\": {");
      writer.println("    \"totalRiskIndex\": " + totalRiskIndex + ",");
      writer.println("    \"meanConsequence\": " + meanConsequence + ",");
      writer.println("    \"maxConsequence\": " + maxConsequence + ",");
      writer.println("    \"percentile95\": " + percentile95Consequence + ",");
      writer.println("    \"percentile99\": " + percentile99Consequence + ",");
      writer.println("    \"totalFrequency\": " + getTotalFrequency());
      writer.println("  },");

      writer.println("  \"frequencyByCategory\": {");
      ConsequenceCategory[] cats = ConsequenceCategory.values();
      for (int i = 0; i < cats.length; i++) {
        String comma = (i < cats.length - 1) ? "," : "";
        writer.printf("    \"%s\": %.6e%s%n", cats[i].name(), frequencyByCategory.get(cats[i]),
            comma);
      }
      writer.println("  },");

      writer.println("  \"events\": [");
      for (int i = 0; i < eventResults.size(); i++) {
        EventResult er = eventResults.get(i);
        String comma = (i < eventResults.size() - 1) ? "," : "";
        writer.println("    {");
        writer.println("      \"name\": \"" + er.eventName + "\",");
        writer.printf("      \"frequency\": %.6e,%n", er.frequency);
        writer.printf("      \"probability\": %.6f,%n", er.probability);
        writer.printf("      \"riskContribution\": %.6e,%n", er.riskContribution);
        writer.println("      \"category\": \"" + er.category.name() + "\"");
        writer.println("    }" + comma);
      }
      writer.println("  ]");

      writer.println("}");

    } catch (IOException e) {
      throw new RuntimeException("Failed to export risk results: " + e.getMessage(), e);
    }
  }

  /**
   * Returns a summary string suitable for display.
   *
   * @return formatted summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Risk Analysis: ").append(analysisName).append(" ===\n");
    sb.append(String.format("Iterations: %d (seed: %d)%n", iterations, seed));
    sb.append(String.format("Total Risk Index: %.4f%n", totalRiskIndex));
    sb.append(String.format("Total Frequency: %.4e /year%n", getTotalFrequency()));
    sb.append(String.format("Mean Consequence: %.2f%n", meanConsequence));
    sb.append(String.format("95th Percentile: %.2f%n", percentile95Consequence));
    sb.append(String.format("99th Percentile: %.2f%n", percentile99Consequence));
    sb.append("\nFrequency by Category:\n");
    for (ConsequenceCategory cat : ConsequenceCategory.values()) {
      sb.append(String.format("  %s: %.4e /year%n", cat.name(), frequencyByCategory.get(cat)));
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("RiskResult[%s, %d iterations, risk=%.4f, freq=%.4e/yr]", analysisName,
        iterations, totalRiskIndex, getTotalFrequency());
  }
}
