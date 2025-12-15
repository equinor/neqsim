package neqsim.process.safety.risk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.RiskEvent.ConsequenceCategory;
import neqsim.process.safety.risk.RiskResult.EventResult;

/**
 * Probabilistic risk model for process safety analysis.
 *
 * <p>
 * Provides Monte Carlo simulation, event tree analysis, and sensitivity analysis capabilities for
 * quantitative risk assessment (QRA). Integrates with NeqSim's process simulation and safety
 * scenario framework.
 *
 * <p>
 * <b>Typical Usage:</b>
 * 
 * <pre>
 * {@code
 * RiskModel model = new RiskModel("HP Separator Study");
 * model.setProcessSystem(processSystem);
 *
 * // Add initiating events with frequencies (per year)
 * model.addEvent(RiskEvent.builder().name("Small Leak").initiatingEvent(InitiatingEvent.LEAK_SMALL)
 *     .frequency(1e-3).consequenceCategory(ConsequenceCategory.MINOR).build());
 *
 * // Run Monte Carlo analysis
 * RiskResult result = model.runMonteCarloAnalysis(10000);
 * result.exportToCSV("risk_results.csv");
 * }
 * </pre>
 *
 * @author NeqSim team
 */
public class RiskModel {

  private final String name;
  private final List<RiskEvent> events;
  private ProcessSystem processSystem;
  private long randomSeed;
  private boolean storeMonteCarloSamples;

  // Uncertainty parameters (for Monte Carlo)
  private double frequencyUncertaintyFactor; // Log-normal error factor
  private double probabilityUncertaintyStdDev; // Normal std dev for probabilities

  /**
   * Creates a new risk model.
   *
   * @param name name of the risk study
   */
  public RiskModel(String name) {
    this.name = name;
    this.events = new ArrayList<>();
    this.randomSeed = System.currentTimeMillis();
    this.storeMonteCarloSamples = false;
    this.frequencyUncertaintyFactor = 3.0; // Typical for OREDA data
    this.probabilityUncertaintyStdDev = 0.1;
  }

  /**
   * Builder pattern for RiskModel.
   *
   * @return new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for RiskModel.
   */
  public static class Builder {
    private String name = "Risk Study";
    private ProcessSystem processSystem = null;
    private long seed = System.currentTimeMillis();
    private double frequencyUncertainty = 3.0;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder processSystem(ProcessSystem system) {
      this.processSystem = system;
      return this;
    }

    public Builder seed(long seed) {
      this.seed = seed;
      return this;
    }

    public Builder frequencyUncertaintyFactor(double factor) {
      this.frequencyUncertainty = factor;
      return this;
    }

    public RiskModel build() {
      RiskModel model = new RiskModel(name);
      model.processSystem = this.processSystem;
      model.randomSeed = this.seed;
      model.frequencyUncertaintyFactor = this.frequencyUncertainty;
      // Future: Initialize safety analyzer for simulation-based analysis
      // if (processSystem != null) {
      // model.safetyAnalyzer = new ProcessSafetyAnalyzer(processSystem);
      // }
      return model;
    }
  }

  // Configuration methods

  public void setProcessSystem(ProcessSystem system) {
    this.processSystem = system;
    // Future: Initialize safety analyzer for simulation-based analysis
    // this.safetyAnalyzer = new ProcessSafetyAnalyzer(system);
  }

  public void setRandomSeed(long seed) {
    this.randomSeed = seed;
  }

  public void setFrequencyUncertaintyFactor(double factor) {
    this.frequencyUncertaintyFactor = factor;
  }

  public void setProbabilityUncertaintyStdDev(double stdDev) {
    this.probabilityUncertaintyStdDev = stdDev;
  }

  public void setStoreMonteCarloSamples(boolean store) {
    this.storeMonteCarloSamples = store;
  }

  // Event management

  /**
   * Adds a risk event to the model.
   *
   * @param event the risk event to add
   */
  public void addEvent(RiskEvent event) {
    events.add(event);
  }

  /**
   * Creates and adds a simple initiating event.
   *
   * @param name event name
   * @param frequency annual frequency
   * @param category consequence category
   * @return the created event
   */
  public RiskEvent addInitiatingEvent(String name, double frequency, ConsequenceCategory category) {
    RiskEvent event =
        RiskEvent.builder().name(name).frequency(frequency).consequenceCategory(category).build();
    events.add(event);
    return event;
  }

  /**
   * Adds a conditional event (branch in event tree).
   *
   * @param name event name
   * @param parentEvent the parent event
   * @param probability conditional probability
   * @param category consequence category if this branch occurs
   * @return the created event
   */
  public RiskEvent addConditionalEvent(String name, RiskEvent parentEvent, double probability,
      ConsequenceCategory category) {
    RiskEvent event = RiskEvent.builder().name(name).parentEvent(parentEvent)
        .conditionalProbability(probability).consequenceCategory(category).build();
    events.add(event);
    return event;
  }

  /**
   * Gets all events in the model.
   *
   * @return list of events
   */
  public List<RiskEvent> getEvents() {
    return new ArrayList<>(events);
  }

  /**
   * Gets only initiating events (no parent).
   *
   * @return list of initiating events
   */
  public List<RiskEvent> getInitiatingEvents() {
    List<RiskEvent> initiating = new ArrayList<>();
    for (RiskEvent event : events) {
      if (event.isInitiatingEvent()) {
        initiating.add(event);
      }
    }
    return initiating;
  }

  // Analysis methods

  /**
   * Runs deterministic risk calculation using point estimates.
   *
   * @return risk result with point estimates
   */
  public RiskResult runDeterministicAnalysis() {
    RiskResult result = new RiskResult(name + " (Deterministic)", 1, randomSeed);

    double totalRisk = 0.0;
    double totalFreq = 0.0;
    double maxConsequence = 0.0;
    double sumConsequence = 0.0;

    for (RiskEvent event : events) {
      double freq = event.getAbsoluteFrequency();
      double severity = event.getConsequenceCategory().getSeverity();
      double risk = freq * severity;

      totalRisk += risk;
      totalFreq += freq;
      maxConsequence = Math.max(maxConsequence, severity);
      sumConsequence += severity;

      // Update category frequency
      double catFreq = result.getCategoryFrequency(event.getConsequenceCategory());
      result.setCategoryFrequency(event.getConsequenceCategory(), catFreq + freq);

      // Add event result
      result.addEventResult(new EventResult(event.getName(), freq,
          event.getConditionalProbability(), risk, event.getConsequenceCategory()));
    }

    result.setTotalRiskIndex(totalRisk);
    result.setMeanConsequence(events.isEmpty() ? 0 : sumConsequence / events.size());
    result.setMaxConsequence(maxConsequence);
    result.setPercentile95(maxConsequence); // Same for deterministic
    result.setPercentile99(maxConsequence);

    return result;
  }

  /**
   * Runs Monte Carlo simulation with uncertainty propagation.
   *
   * <p>
   * Frequencies are sampled from log-normal distributions. Probabilities are sampled from truncated
   * normal distributions.
   * </p>
   *
   * @param iterations number of Monte Carlo iterations
   * @return risk result with statistical metrics
   */
  public RiskResult runMonteCarloAnalysis(int iterations) {
    Random random = new Random(randomSeed);
    RiskResult result = new RiskResult(name + " (Monte Carlo)", iterations, randomSeed);

    double[] riskSamples = new double[iterations];
    double[] frequencySamples = new double[iterations];

    // Category frequency accumulators
    double[] catFreqs = new double[ConsequenceCategory.values().length];

    for (int iter = 0; iter < iterations; iter++) {
      double iterRisk = 0.0;
      double iterFreq = 0.0;

      for (RiskEvent event : events) {
        // Sample frequency from log-normal
        double sampledFreq =
            sampleLogNormal(random, event.getFrequency(), frequencyUncertaintyFactor);

        // Sample probability from truncated normal
        double sampledProb = sampleTruncatedNormal(random, event.getConditionalProbability(),
            probabilityUncertaintyStdDev);

        // Calculate absolute frequency for this sample
        double absFreq = sampledFreq;
        if (event.getParentEvent() != null) {
          // For conditional events, we need the parent's sampled frequency
          // Simplified: use point estimate for parent
          absFreq = event.getParentEvent().getAbsoluteFrequency() * sampledProb;
        }

        double severity = event.getConsequenceCategory().getSeverity();
        iterRisk += absFreq * severity;
        iterFreq += absFreq;

        // Accumulate category frequencies
        catFreqs[event.getConsequenceCategory().ordinal()] += absFreq / iterations;
      }

      riskSamples[iter] = iterRisk;
      frequencySamples[iter] = iterFreq;
    }

    // Calculate statistics
    Arrays.sort(riskSamples);

    double sumRisk = 0.0;
    for (double r : riskSamples) {
      sumRisk += r;
    }
    double meanRisk = sumRisk / iterations;

    result.setTotalRiskIndex(meanRisk);
    result.setMeanConsequence(meanRisk);
    result.setMaxConsequence(riskSamples[iterations - 1]);
    result.setPercentile95(riskSamples[(int) (0.95 * iterations)]);
    result.setPercentile99(riskSamples[(int) (0.99 * iterations)]);

    // Set category frequencies
    for (ConsequenceCategory cat : ConsequenceCategory.values()) {
      result.setCategoryFrequency(cat, catFreqs[cat.ordinal()]);
    }

    // Add event results (using point estimates for individual events)
    for (RiskEvent event : events) {
      double freq = event.getAbsoluteFrequency();
      double risk = freq * event.getConsequenceCategory().getSeverity();
      result.addEventResult(new EventResult(event.getName(), freq,
          event.getConditionalProbability(), risk, event.getConsequenceCategory()));
    }

    if (storeMonteCarloSamples) {
      result.setSamples(riskSamples);
    }

    return result;
  }

  /**
   * Runs sensitivity analysis on event frequencies.
   *
   * <p>
   * Varies each event frequency by the specified factors and observes the effect on total risk.
   * </p>
   *
   * @param lowFactor multiplier for low case (e.g., 0.1)
   * @param highFactor multiplier for high case (e.g., 10.0)
   * @return sensitivity analysis result
   */
  public SensitivityResult runSensitivityAnalysis(double lowFactor, double highFactor) {
    return runSensitivityAnalysis(lowFactor, highFactor, 5);
  }

  /**
   * Runs sensitivity analysis with specified number of points.
   *
   * @param lowFactor multiplier for low case
   * @param highFactor multiplier for high case
   * @param numPoints number of points between low and high (including endpoints)
   * @return sensitivity analysis result
   */
  public SensitivityResult runSensitivityAnalysis(double lowFactor, double highFactor,
      int numPoints) {
    SensitivityResult result = new SensitivityResult(name + " Sensitivity", "Base Case");

    // Calculate base case risk
    double baseRisk = calculateTotalRisk();
    result.setBaseRiskIndex(baseRisk);
    result.setBaseFrequency(getTotalFrequency());

    // Generate multiplier values
    double[] multipliers = new double[numPoints];
    double logLow = Math.log10(lowFactor);
    double logHigh = Math.log10(highFactor);
    for (int i = 0; i < numPoints; i++) {
      double logVal = logLow + (logHigh - logLow) * i / (numPoints - 1);
      multipliers[i] = Math.pow(10, logVal);
    }

    // Vary each event's frequency
    for (RiskEvent event : events) {
      if (!event.isInitiatingEvent()) {
        continue; // Only vary initiating events
      }

      double baseFreq = event.getFrequency();
      double[] values = new double[numPoints];
      double[] risks = new double[numPoints];

      for (int i = 0; i < numPoints; i++) {
        event.setFrequency(baseFreq * multipliers[i]);
        values[i] = baseFreq * multipliers[i];
        risks[i] = calculateTotalRisk();
      }

      // Restore base frequency
      event.setFrequency(baseFreq);

      result.addParameterSensitivity(event.getName() + " frequency", values, risks);
    }

    return result;
  }

  /**
   * Runs simulation-based analysis for events with attached scenarios.
   *
   * <p>
   * For events that have ProcessSafetyScenario attached, uses the scenario consequence category.
   * Future implementation could integrate with ProcessScenarioRunner for dynamic simulation.
   * </p>
   *
   * @return risk result with scenario-based consequences
   */
  public RiskResult runSimulationBasedAnalysis() {
    RiskResult result = new RiskResult(name + " (Simulation)", 1, randomSeed);
    double totalRisk = 0.0;

    for (RiskEvent event : events) {
      double freq = event.getAbsoluteFrequency();
      double severity = event.getConsequenceCategory().getSeverity();

      // Note: Future enhancement could use ProcessScenarioRunner to
      // dynamically simulate scenarios and extract consequence metrics
      // if (event.getScenario() != null && processSystem != null) {
      // ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);
      // ScenarioExecutionSummary summary = runner.runScenario(...);
      // severity = extractSeverityFromSummary(summary);
      // }

      double risk = freq * severity;
      totalRisk += risk;

      // Update category frequency
      double catFreq = result.getCategoryFrequency(event.getConsequenceCategory());
      result.setCategoryFrequency(event.getConsequenceCategory(), catFreq + freq);

      result.addEventResult(new EventResult(event.getName(), freq,
          event.getConditionalProbability(), risk, event.getConsequenceCategory()));
    }

    result.setTotalRiskIndex(totalRisk);

    return result;
  }

  // Helper methods

  private double calculateTotalRisk() {
    double totalRisk = 0.0;
    for (RiskEvent event : events) {
      totalRisk += event.getAbsoluteFrequency() * event.getConsequenceCategory().getSeverity();
    }
    return totalRisk;
  }

  private double getTotalFrequency() {
    double total = 0.0;
    for (RiskEvent event : events) {
      total += event.getAbsoluteFrequency();
    }
    return total;
  }

  private double sampleLogNormal(Random random, double median, double errorFactor) {
    // Log-normal: X = median * EF^Z where Z ~ N(0,1)
    double sigma = Math.log(errorFactor) / 1.645; // 90% confidence
    double z = random.nextGaussian();
    return median * Math.exp(sigma * z);
  }

  private double sampleTruncatedNormal(Random random, double mean, double stdDev) {
    // Truncated to [0, 1] for probabilities
    double sample;
    do {
      sample = mean + stdDev * random.nextGaussian();
    } while (sample < 0 || sample > 1);
    return sample;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return String.format("RiskModel[%s, %d events, total risk=%.4e]", name, events.size(),
        calculateTotalRisk());
  }
}
