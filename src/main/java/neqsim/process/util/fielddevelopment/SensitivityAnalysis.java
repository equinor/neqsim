package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;

/**
 * Monte Carlo sensitivity analysis for field development uncertainty quantification.
 *
 * <p>
 * This class provides comprehensive uncertainty analysis capabilities including:
 * <ul>
 * <li>Monte Carlo simulation with configurable probability distributions</li>
 * <li>P10/P50/P90 calculation for key outputs</li>
 * <li>Tornado diagram generation for sensitivity ranking</li>
 * <li>Spider plot data generation</li>
 * <li>Parallel execution for performance</li>
 * <li>Integration with {@link ProductionOptimizer} for process simulation</li>
 * </ul>
 *
 * <h2>Uncertainty Quantification Concepts</h2>
 * <p>
 * Field development decisions are made under uncertainty. Key uncertain parameters include:
 * <ul>
 * <li>Reservoir properties (permeability, porosity, STOIIP)</li>
 * <li>Fluid properties (GOR, API gravity, viscosity)</li>
 * <li>Facility performance (equipment efficiencies, capacities)</li>
 * <li>Operating conditions (temperatures, pressures)</li>
 * <li>Economic factors (oil price, CAPEX, OPEX)</li>
 * </ul>
 *
 * <h2>Probability Distributions</h2>
 * <p>
 * The following distributions are supported:
 * <ul>
 * <li>{@link DistributionType#NORMAL} - Symmetric uncertainty around mean</li>
 * <li>{@link DistributionType#LOGNORMAL} - Positive-valued, right-skewed</li>
 * <li>{@link DistributionType#TRIANGULAR} - Defined by min, mode, max</li>
 * <li>{@link DistributionType#UNIFORM} - Equal probability in range</li>
 * </ul>
 *
 * <h2>Sensitivity Analysis Methods</h2>
 * <p>
 * The class supports multiple sensitivity analysis approaches:
 * <ul>
 * <li><b>Monte Carlo</b>: Random sampling of all parameters simultaneously</li>
 * <li><b>Tornado (One-at-a-time)</b>: Vary each parameter from P10 to P90 individually</li>
 * <li><b>Spider Plot</b>: Systematic variation of each parameter across its range</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * SensitivityAnalysis sensitivity = new SensitivityAnalysis(facilityProcess);
 *
 * // Add uncertain parameters
 * sensitivity.addParameter(UncertainParameter.triangular("feedTemperature", 15.0, 20.0, 25.0,
 *     (proc, val) -> feedStream.setTemperature(val, "C")));
 *
 * sensitivity.addParameter(UncertainParameter.lognormal("reservoirPressure", 180.0, 200.0, 230.0,
 *     (proc, val) -> feedStream.setPressure(val, "bara")));
 *
 * // Run Monte Carlo simulation
 * SensitivityConfig config = new SensitivityConfig().numberOfTrials(1000).parallel(true);
 *
 * MonteCarloResult result = sensitivity.runMonteCarloOptimization(feedStream, 1000.0, 50000.0,
 *     "kg/hr", opt -> opt.getOptimalRate(), config);
 *
 * System.out.printf("P10: %.0f, P50: %.0f, P90: %.0f%n", result.getP10(), result.getP50(),
 *     result.getP90());
 * System.out.println(result.toTornadoMarkdown());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ProductionOptimizer
 * @see ProductionProfile
 */
public class SensitivityAnalysis implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default number of Monte Carlo trials. */
  public static final int DEFAULT_NUMBER_OF_TRIALS = 1000;

  /** Base process system for simulation. */
  private final ProcessSystem baseProcess;

  /** List of uncertain parameters. */
  private final List<UncertainParameter> parameters;

  /** Random number generator. */
  private transient Random rng;

  /**
   * Probability distribution types for uncertain parameters.
   *
   * <p>
   * Each distribution is parameterized by P10, P50, and P90 percentiles, which are converted to the
   * native distribution parameters internally.
   */
  public enum DistributionType {
    /**
     * Normal (Gaussian) distribution.
     * <p>
     * Symmetric distribution defined by mean (P50) and standard deviation. The P10-P90 range spans
     * approximately 2.56 standard deviations.
     */
    NORMAL,

    /**
     * Log-normal distribution.
     * <p>
     * Right-skewed distribution for strictly positive values. Useful for reservoir parameters,
     * costs, and other non-negative quantities.
     */
    LOGNORMAL,

    /**
     * Triangular distribution.
     * <p>
     * Defined by minimum (P10), mode (P50), and maximum (P90). Simple to elicit from experts and
     * widely used in subsurface uncertainty.
     */
    TRIANGULAR,

    /**
     * Uniform distribution.
     * <p>
     * Equal probability between minimum and maximum. Used when only range is known, not likely
     * value.
     */
    UNIFORM
  }

  /**
   * Uncertain parameter definition.
   *
   * <p>
   * Encapsulates a parameter that varies between simulation runs, including its probability
   * distribution and how to apply values to the process model.
   */
  public static final class UncertainParameter implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final DistributionType distribution;
    private final double p10;
    private final double p50;
    private final double p90;
    private final String unit;
    private final transient BiConsumer<ProcessSystem, Double> setter;

    /**
     * Creates an uncertain parameter.
     *
     * @param name parameter name for reporting
     * @param p10 10th percentile (low case)
     * @param p50 50th percentile (base case)
     * @param p90 90th percentile (high case)
     * @param distribution probability distribution type
     * @param unit engineering unit (for reporting)
     * @param setter function to apply parameter value to process
     */
    public UncertainParameter(String name, double p10, double p50, double p90,
        DistributionType distribution, String unit, BiConsumer<ProcessSystem, Double> setter) {
      this.name = Objects.requireNonNull(name, "Parameter name is required");
      this.p10 = p10;
      this.p50 = p50;
      this.p90 = p90;
      this.distribution = Objects.requireNonNull(distribution, "Distribution is required");
      this.unit = unit;
      this.setter = Objects.requireNonNull(setter, "Setter is required");

      if (p10 > p50 || p50 > p90) {
        throw new IllegalArgumentException("Percentiles must be ordered: P10 <= P50 <= P90");
      }
    }

    /**
     * Creates a triangular distribution parameter.
     *
     * @param name parameter name
     * @param min minimum value (P10)
     * @param mode most likely value (P50)
     * @param max maximum value (P90)
     * @param setter function to apply value
     * @return uncertain parameter
     */
    public static UncertainParameter triangular(String name, double min, double mode, double max,
        BiConsumer<ProcessSystem, Double> setter) {
      return new UncertainParameter(name, min, mode, max, DistributionType.TRIANGULAR, null,
          setter);
    }

    /**
     * Creates a triangular distribution parameter with unit.
     *
     * @param name parameter name
     * @param min minimum value (P10)
     * @param mode most likely value (P50)
     * @param max maximum value (P90)
     * @param unit engineering unit
     * @param setter function to apply value
     * @return uncertain parameter
     */
    public static UncertainParameter triangular(String name, double min, double mode, double max,
        String unit, BiConsumer<ProcessSystem, Double> setter) {
      return new UncertainParameter(name, min, mode, max, DistributionType.TRIANGULAR, unit,
          setter);
    }

    /**
     * Creates a normal distribution parameter.
     *
     * @param name parameter name
     * @param p10 10th percentile
     * @param p50 50th percentile (mean)
     * @param p90 90th percentile
     * @param setter function to apply value
     * @return uncertain parameter
     */
    public static UncertainParameter normal(String name, double p10, double p50, double p90,
        BiConsumer<ProcessSystem, Double> setter) {
      return new UncertainParameter(name, p10, p50, p90, DistributionType.NORMAL, null, setter);
    }

    /**
     * Creates a log-normal distribution parameter.
     *
     * @param name parameter name
     * @param p10 10th percentile
     * @param p50 50th percentile
     * @param p90 90th percentile
     * @param setter function to apply value
     * @return uncertain parameter
     */
    public static UncertainParameter lognormal(String name, double p10, double p50, double p90,
        BiConsumer<ProcessSystem, Double> setter) {
      return new UncertainParameter(name, p10, p50, p90, DistributionType.LOGNORMAL, null, setter);
    }

    /**
     * Creates a uniform distribution parameter.
     *
     * @param name parameter name
     * @param min minimum value
     * @param max maximum value
     * @param setter function to apply value
     * @return uncertain parameter
     */
    public static UncertainParameter uniform(String name, double min, double max,
        BiConsumer<ProcessSystem, Double> setter) {
      double mid = (min + max) / 2.0;
      return new UncertainParameter(name, min, mid, max, DistributionType.UNIFORM, null, setter);
    }

    /**
     * Samples a random value from this parameter's distribution.
     *
     * @param rng random number generator
     * @return sampled value
     */
    public double sample(Random rng) {
      switch (distribution) {
        case NORMAL:
          return sampleNormal(rng);
        case LOGNORMAL:
          return sampleLognormal(rng);
        case TRIANGULAR:
          return sampleTriangular(rng);
        case UNIFORM:
          return sampleUniform(rng);
        default:
          return p50;
      }
    }

    private double sampleNormal(Random rng) {
      // Convert P10/P90 to standard deviation
      // P10 = mean - 1.28*sigma, P90 = mean + 1.28*sigma
      double mean = p50;
      double sigma = (p90 - p10) / (2 * 1.28155);
      return mean + sigma * rng.nextGaussian();
    }

    private double sampleLognormal(Random rng) {
      // Convert P10/P50/P90 to lognormal parameters
      double logP10 = Math.log(p10);
      double logP50 = Math.log(p50);
      double logP90 = Math.log(p90);

      double mu = logP50;
      double sigma = (logP90 - logP10) / (2 * 1.28155);

      double logValue = mu + sigma * rng.nextGaussian();
      return Math.exp(logValue);
    }

    private double sampleTriangular(Random rng) {
      double u = rng.nextDouble();
      double a = p10; // min
      double c = p50; // mode
      double b = p90; // max

      if (b == a) {
        return a;
      }

      double fc = (c - a) / (b - a);

      if (u < fc) {
        return a + Math.sqrt(u * (b - a) * (c - a));
      } else {
        return b - Math.sqrt((1 - u) * (b - a) * (b - c));
      }
    }

    private double sampleUniform(Random rng) {
      return p10 + rng.nextDouble() * (p90 - p10);
    }

    /**
     * Applies a value to the process system.
     *
     * @param process process to modify
     * @param value value to apply
     */
    public void apply(ProcessSystem process, double value) {
      setter.accept(process, value);
    }

    /**
     * Gets the parameter name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the distribution type.
     *
     * @return distribution
     */
    public DistributionType getDistribution() {
      return distribution;
    }

    /**
     * Gets the P10 value.
     *
     * @return P10
     */
    public double getP10() {
      return p10;
    }

    /**
     * Gets the P50 value.
     *
     * @return P50
     */
    public double getP50() {
      return p50;
    }

    /**
     * Gets the P90 value.
     *
     * @return P90
     */
    public double getP90() {
      return p90;
    }

    /**
     * Gets the unit.
     *
     * @return unit or null
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the range (P90 - P10).
     *
     * @return range
     */
    public double getRange() {
      return p90 - p10;
    }

    @Override
    public String toString() {
      return String.format("UncertainParameter[%s: P10=%.2f, P50=%.2f, P90=%.2f, %s]", name, p10,
          p50, p90, distribution);
    }
  }

  /**
   * Single Monte Carlo trial result.
   *
   * <p>
   * Contains the sampled parameter values and resulting output for one trial.
   */
  public static final class TrialResult implements Serializable, Comparable<TrialResult> {
    private static final long serialVersionUID = 1000L;

    private final int trialNumber;
    private final Map<String, Double> sampledParameters;
    private final double outputValue;
    private final String bottleneck;
    private final boolean feasible;
    private final boolean converged;

    /**
     * Creates a trial result.
     *
     * @param trialNumber trial index
     * @param sampledParameters map of parameter name to sampled value
     * @param outputValue output metric value
     * @param bottleneck bottleneck equipment name (may be null)
     * @param feasible true if solution was feasible
     * @param converged true if simulation converged
     */
    public TrialResult(int trialNumber, Map<String, Double> sampledParameters, double outputValue,
        String bottleneck, boolean feasible, boolean converged) {
      this.trialNumber = trialNumber;
      this.sampledParameters = new LinkedHashMap<>(sampledParameters);
      this.outputValue = outputValue;
      this.bottleneck = bottleneck;
      this.feasible = feasible;
      this.converged = converged;
    }

    /**
     * Gets the trial number.
     *
     * @return trial number
     */
    public int getTrialNumber() {
      return trialNumber;
    }

    /**
     * Gets the sampled parameter values.
     *
     * @return map of parameter name to value
     */
    public Map<String, Double> getSampledParameters() {
      return Collections.unmodifiableMap(sampledParameters);
    }

    /**
     * Gets the output value.
     *
     * @return output metric value
     */
    public double getOutputValue() {
      return outputValue;
    }

    /**
     * Gets the bottleneck equipment name.
     *
     * @return bottleneck name or null
     */
    public String getBottleneck() {
      return bottleneck;
    }

    /**
     * Checks if the trial was feasible.
     *
     * @return true if feasible
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Checks if the trial converged.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return converged;
    }

    @Override
    public int compareTo(TrialResult other) {
      return Double.compare(this.outputValue, other.outputValue);
    }
  }

  /**
   * Complete Monte Carlo analysis result.
   *
   * <p>
   * Contains all trial results and computed statistics including percentiles, mean, standard
   * deviation, and sensitivity rankings.
   */
  public static final class MonteCarloResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<TrialResult> trials;
    private final double p10;
    private final double p50;
    private final double p90;
    private final double mean;
    private final double stdDev;
    private final double min;
    private final double max;
    private final Map<String, Double> tornadoSensitivities;
    private final List<String> mostSensitiveParameters;
    private final int feasibleCount;
    private final int convergedCount;
    private final String outputName;
    private final String outputUnit;

    /**
     * Creates a Monte Carlo result.
     *
     * @param trials list of trial results
     * @param tornadoSensitivities map of parameter name to sensitivity
     * @param outputName name of output metric
     * @param outputUnit unit of output metric
     */
    public MonteCarloResult(List<TrialResult> trials, Map<String, Double> tornadoSensitivities,
        String outputName, String outputUnit) {
      this.trials = new ArrayList<>(trials);
      this.tornadoSensitivities = new LinkedHashMap<>(tornadoSensitivities);
      this.outputName = outputName;
      this.outputUnit = outputUnit;

      // Sort trials by output value for percentile calculation
      List<TrialResult> sorted = new ArrayList<>(trials);
      Collections.sort(sorted);

      // Calculate statistics
      this.min = sorted.isEmpty() ? 0 : sorted.get(0).getOutputValue();
      this.max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1).getOutputValue();
      this.p10 = getPercentileFromSorted(sorted, 0.10);
      this.p50 = getPercentileFromSorted(sorted, 0.50);
      this.p90 = getPercentileFromSorted(sorted, 0.90);

      double sum = 0;
      double sumSq = 0;
      int feasible = 0;
      int converged = 0;

      for (TrialResult trial : trials) {
        sum += trial.getOutputValue();
        sumSq += trial.getOutputValue() * trial.getOutputValue();
        if (trial.isFeasible()) {
          feasible++;
        }
        if (trial.isConverged()) {
          converged++;
        }
      }

      int n = trials.size();
      this.mean = n > 0 ? sum / n : 0;
      this.stdDev = n > 1 ? Math.sqrt((sumSq - sum * sum / n) / (n - 1)) : 0;
      this.feasibleCount = feasible;
      this.convergedCount = converged;

      // Sort parameters by sensitivity
      this.mostSensitiveParameters = tornadoSensitivities.entrySet().stream()
          .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).map(Map.Entry::getKey)
          .collect(Collectors.toList());
    }

    private double getPercentileFromSorted(List<TrialResult> sorted, double percentile) {
      if (sorted.isEmpty()) {
        return 0;
      }
      int index = (int) Math.ceil(percentile * sorted.size()) - 1;
      index = Math.max(0, Math.min(index, sorted.size() - 1));
      return sorted.get(index).getOutputValue();
    }

    /**
     * Gets all trial results.
     *
     * @return unmodifiable list of trials
     */
    public List<TrialResult> getTrials() {
      return Collections.unmodifiableList(trials);
    }

    /**
     * Gets the P10 value.
     *
     * @return 10th percentile
     */
    public double getP10() {
      return p10;
    }

    /**
     * Gets the P50 value.
     *
     * @return 50th percentile (median)
     */
    public double getP50() {
      return p50;
    }

    /**
     * Gets the P90 value.
     *
     * @return 90th percentile
     */
    public double getP90() {
      return p90;
    }

    /**
     * Gets the mean value.
     *
     * @return arithmetic mean
     */
    public double getMean() {
      return mean;
    }

    /**
     * Gets the standard deviation.
     *
     * @return sample standard deviation
     */
    public double getStdDev() {
      return stdDev;
    }

    /**
     * Gets the minimum value.
     *
     * @return minimum
     */
    public double getMin() {
      return min;
    }

    /**
     * Gets the maximum value.
     *
     * @return maximum
     */
    public double getMax() {
      return max;
    }

    /**
     * Gets a specific percentile.
     *
     * @param percentile percentile (0-1)
     * @return value at percentile
     */
    public double getPercentile(double percentile) {
      List<TrialResult> sorted = new ArrayList<>(trials);
      Collections.sort(sorted);
      return getPercentileFromSorted(sorted, percentile);
    }

    /**
     * Gets tornado sensitivities.
     *
     * @return map of parameter name to sensitivity magnitude
     */
    public Map<String, Double> getTornadoSensitivities() {
      return Collections.unmodifiableMap(tornadoSensitivities);
    }

    /**
     * Gets parameters ranked by sensitivity.
     *
     * @return list of parameter names from most to least sensitive
     */
    public List<String> getMostSensitiveParameters() {
      return Collections.unmodifiableList(mostSensitiveParameters);
    }

    /**
     * Gets the number of feasible trials.
     *
     * @return feasible count
     */
    public int getFeasibleCount() {
      return feasibleCount;
    }

    /**
     * Gets the number of converged trials.
     *
     * @return converged count
     */
    public int getConvergedCount() {
      return convergedCount;
    }

    /**
     * Gets the output metric name.
     *
     * @return output name
     */
    public String getOutputName() {
      return outputName;
    }

    /**
     * Gets the output unit.
     *
     * @return output unit
     */
    public String getOutputUnit() {
      return outputUnit;
    }

    /**
     * Generates histogram bin data for plotting.
     *
     * @param numBins number of bins
     * @return array of [binCenter, count] pairs
     */
    public double[][] getHistogramData(int numBins) {
      if (trials.isEmpty() || min >= max) {
        return new double[0][0];
      }

      double binWidth = (max - min) / numBins;
      int[] counts = new int[numBins];

      for (TrialResult trial : trials) {
        int bin = (int) ((trial.getOutputValue() - min) / binWidth);
        bin = Math.min(bin, numBins - 1);
        counts[bin]++;
      }

      double[][] result = new double[numBins][2];
      for (int i = 0; i < numBins; i++) {
        result[i][0] = min + (i + 0.5) * binWidth;
        result[i][1] = counts[i];
      }

      return result;
    }

    /**
     * Generates tornado diagram as Markdown.
     *
     * @return Markdown formatted tornado table
     */
    public String toTornadoMarkdown() {
      StringBuilder sb = new StringBuilder();
      sb.append("## Tornado Diagram - Sensitivity Analysis\n\n");
      sb.append("| Parameter | Sensitivity | Rank |\n");
      sb.append("|---|---|---|\n");

      int rank = 1;
      for (String param : mostSensitiveParameters) {
        double sens = tornadoSensitivities.getOrDefault(param, 0.0);
        sb.append(String.format("| %s | %.2f | %d |\n", param, sens, rank++));
      }

      return sb.toString();
    }

    /**
     * Generates statistics summary as Markdown.
     *
     * @return Markdown formatted summary
     */
    public String toSummaryMarkdown() {
      StringBuilder sb = new StringBuilder();
      sb.append("## Monte Carlo Results Summary\n\n");
      sb.append(String.format("- **Output**: %s%s\n", outputName,
          outputUnit != null ? " (" + outputUnit + ")" : ""));
      sb.append(String.format("- **Trials**: %d (converged: %d, feasible: %d)\n", trials.size(),
          convergedCount, feasibleCount));
      sb.append("\n### Statistics\n\n");
      sb.append(String.format("| Statistic | Value |\n"));
      sb.append("|---|---|\n");
      sb.append(String.format("| P10 | %.2f |\n", p10));
      sb.append(String.format("| P50 (Median) | %.2f |\n", p50));
      sb.append(String.format("| P90 | %.2f |\n", p90));
      sb.append(String.format("| Mean | %.2f |\n", mean));
      sb.append(String.format("| Std Dev | %.2f |\n", stdDev));
      sb.append(String.format("| Min | %.2f |\n", min));
      sb.append(String.format("| Max | %.2f |\n", max));

      return sb.toString();
    }

    /**
     * Exports trial data to CSV format.
     *
     * @param parameterNames list of parameter names for columns
     * @return CSV string
     */
    public String toCSV(List<String> parameterNames) {
      StringBuilder sb = new StringBuilder();

      // Header
      sb.append("Trial,Output,Feasible,Bottleneck");
      for (String param : parameterNames) {
        sb.append(",").append(param);
      }
      sb.append("\n");

      // Data rows
      for (TrialResult trial : trials) {
        sb.append(trial.getTrialNumber()).append(",");
        sb.append(trial.getOutputValue()).append(",");
        sb.append(trial.isFeasible()).append(",");
        sb.append(trial.getBottleneck() != null ? trial.getBottleneck() : "");
        for (String param : parameterNames) {
          sb.append(",").append(trial.getSampledParameters().getOrDefault(param, Double.NaN));
        }
        sb.append("\n");
      }

      return sb.toString();
    }
  }

  /**
   * Configuration for sensitivity analysis.
   *
   * <p>
   * Builder-style configuration for Monte Carlo simulation parameters.
   */
  public static final class SensitivityConfig implements Serializable {
    private static final long serialVersionUID = 1000L;

    private int numberOfTrials = DEFAULT_NUMBER_OF_TRIALS;
    private long randomSeed = System.currentTimeMillis();
    private boolean useFixedSeed = false;
    private boolean parallel = true;
    private int parallelThreads = Runtime.getRuntime().availableProcessors();
    private boolean includeBaseCase = true;
    private double convergenceTolerance = 0.01;

    /**
     * Creates a default configuration.
     */
    public SensitivityConfig() {}

    /**
     * Sets the number of Monte Carlo trials.
     *
     * @param trials number of trials
     * @return this config
     */
    public SensitivityConfig numberOfTrials(int trials) {
      if (trials <= 0) {
        throw new IllegalArgumentException("Number of trials must be positive");
      }
      this.numberOfTrials = trials;
      return this;
    }

    /**
     * Sets the random seed for reproducibility.
     *
     * @param seed random seed
     * @return this config
     */
    public SensitivityConfig randomSeed(long seed) {
      this.randomSeed = seed;
      this.useFixedSeed = true;
      return this;
    }

    /**
     * Enables or disables parallel execution.
     *
     * @param parallel true to enable parallel
     * @return this config
     */
    public SensitivityConfig parallel(boolean parallel) {
      this.parallel = parallel;
      return this;
    }

    /**
     * Sets the number of parallel threads.
     *
     * @param threads thread count
     * @return this config
     */
    public SensitivityConfig parallelThreads(int threads) {
      if (threads <= 0) {
        throw new IllegalArgumentException("Thread count must be positive");
      }
      this.parallelThreads = threads;
      return this;
    }

    /**
     * Enables or disables base case inclusion in trials.
     *
     * @param include true to include base case
     * @return this config
     */
    public SensitivityConfig includeBaseCase(boolean include) {
      this.includeBaseCase = include;
      return this;
    }

    /**
     * Gets the number of trials.
     *
     * @return number of trials
     */
    public int getNumberOfTrials() {
      return numberOfTrials;
    }

    /**
     * Gets the random seed.
     *
     * @return seed
     */
    public long getRandomSeed() {
      return randomSeed;
    }

    /**
     * Checks if using a fixed seed.
     *
     * @return true if fixed seed
     */
    public boolean isUseFixedSeed() {
      return useFixedSeed;
    }

    /**
     * Checks if parallel execution is enabled.
     *
     * @return true if parallel
     */
    public boolean isParallel() {
      return parallel;
    }

    /**
     * Gets the parallel thread count.
     *
     * @return thread count
     */
    public int getParallelThreads() {
      return parallelThreads;
    }

    /**
     * Checks if base case is included.
     *
     * @return true if included
     */
    public boolean isIncludeBaseCase() {
      return includeBaseCase;
    }
  }

  /**
   * Spider plot data point.
   *
   * <p>
   * Contains a parameter value and corresponding output value for spider plots.
   */
  public static final class SpiderPoint implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double parameterValue;
    private final double normalizedParameter; // -1 to 1 scale (P10 to P90)
    private final double outputValue;

    /**
     * Creates a spider point.
     *
     * @param parameterValue actual parameter value
     * @param normalizedParameter normalized value (-1 to 1)
     * @param outputValue resulting output
     */
    public SpiderPoint(double parameterValue, double normalizedParameter, double outputValue) {
      this.parameterValue = parameterValue;
      this.normalizedParameter = normalizedParameter;
      this.outputValue = outputValue;
    }

    public double getParameterValue() {
      return parameterValue;
    }

    public double getNormalizedParameter() {
      return normalizedParameter;
    }

    public double getOutputValue() {
      return outputValue;
    }
  }

  /**
   * Creates a sensitivity analysis for a process system.
   *
   * @param process base process system
   */
  public SensitivityAnalysis(ProcessSystem process) {
    this(process, new Random());
  }

  /**
   * Creates a sensitivity analysis with specified RNG.
   *
   * @param process base process system
   * @param rng random number generator
   */
  public SensitivityAnalysis(ProcessSystem process, Random rng) {
    this.baseProcess = Objects.requireNonNull(process, "Process is required");
    this.rng = Objects.requireNonNull(rng, "Random generator is required");
    this.parameters = new ArrayList<>();
  }

  /**
   * Adds an uncertain parameter.
   *
   * @param param parameter to add
   * @return this for chaining
   */
  public SensitivityAnalysis addParameter(UncertainParameter param) {
    parameters.add(Objects.requireNonNull(param, "Parameter is required"));
    return this;
  }

  /**
   * Removes all parameters.
   *
   * @return this for chaining
   */
  public SensitivityAnalysis clearParameters() {
    parameters.clear();
    return this;
  }

  /**
   * Gets the list of parameters.
   *
   * @return unmodifiable list of parameters
   */
  public List<UncertainParameter> getParameters() {
    return Collections.unmodifiableList(parameters);
  }

  /**
   * Runs Monte Carlo simulation on feed rate optimization.
   *
   * <p>
   * For each trial:
   * <ol>
   * <li>Sample all uncertain parameters</li>
   * <li>Apply parameters to the process</li>
   * <li>Run production optimization</li>
   * <li>Extract the output metric</li>
   * </ol>
   *
   * @param feedStream feed stream for optimization
   * @param lowerBound lower bound for rate
   * @param upperBound upper bound for rate
   * @param rateUnit rate unit
   * @param outputMetric function to extract output from optimization result
   * @param config simulation configuration
   * @return Monte Carlo result with statistics
   */
  public MonteCarloResult runMonteCarloOptimization(StreamInterface feedStream, double lowerBound,
      double upperBound, String rateUnit, ToDoubleFunction<OptimizationResult> outputMetric,
      SensitivityConfig config) {
    Objects.requireNonNull(feedStream, "Feed stream is required");
    Objects.requireNonNull(outputMetric, "Output metric is required");
    Objects.requireNonNull(config, "Config is required");

    // Initialize RNG
    Random localRng = config.isUseFixedSeed() ? new Random(config.getRandomSeed()) : getRng();

    List<TrialResult> results;

    if (config.isParallel() && config.getNumberOfTrials() > 10) {
      results = runParallelTrials(feedStream, lowerBound, upperBound, rateUnit, outputMetric,
          config, localRng);
    } else {
      results = runSequentialTrials(feedStream, lowerBound, upperBound, rateUnit, outputMetric,
          config, localRng);
    }

    // Run tornado analysis
    Map<String, Double> tornadoSensitivities =
        runTornadoAnalysisInternal(feedStream, lowerBound, upperBound, rateUnit, outputMetric);

    return new MonteCarloResult(results, tornadoSensitivities, "OptimalRate", rateUnit);
  }

  /**
   * Runs trials sequentially.
   */
  private List<TrialResult> runSequentialTrials(StreamInterface feedStream, double lowerBound,
      double upperBound, String rateUnit, ToDoubleFunction<OptimizationResult> outputMetric,
      SensitivityConfig config, Random localRng) {

    List<TrialResult> results = new ArrayList<>();
    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig optConfig =
        new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);

    for (int trial = 0; trial < config.getNumberOfTrials(); trial++) {
      try {
        // Sample parameters
        Map<String, Double> sampled = new LinkedHashMap<>();
        for (UncertainParameter param : parameters) {
          double value = param.sample(localRng);
          sampled.put(param.getName(), value);
          param.apply(baseProcess, value);
        }

        // Run optimization
        OptimizationResult optResult = optimizer.optimize(baseProcess, feedStream, optConfig,
            Collections.emptyList(), Collections.emptyList());

        double output = outputMetric.applyAsDouble(optResult);
        String bottleneck =
            optResult.getBottleneck() != null ? optResult.getBottleneck().getName() : null;

        results
            .add(new TrialResult(trial, sampled, output, bottleneck, optResult.isFeasible(), true));

      } catch (Exception e) {
        // Record failed trial
        Map<String, Double> sampled = new LinkedHashMap<>();
        results.add(new TrialResult(trial, sampled, Double.NaN, null, false, false));
      }
    }

    return results;
  }

  /**
   * Runs trials in parallel.
   */
  private List<TrialResult> runParallelTrials(StreamInterface feedStream, double lowerBound,
      double upperBound, String rateUnit, ToDoubleFunction<OptimizationResult> outputMetric,
      SensitivityConfig config, Random localRng) {

    // Pre-generate all random samples (thread-safe)
    List<Map<String, Double>> allSamples = new ArrayList<>();
    for (int trial = 0; trial < config.getNumberOfTrials(); trial++) {
      Map<String, Double> sampled = new LinkedHashMap<>();
      for (UncertainParameter param : parameters) {
        sampled.put(param.getName(), param.sample(localRng));
      }
      allSamples.add(sampled);
    }

    ExecutorService executor = Executors.newFixedThreadPool(config.getParallelThreads());
    List<Future<TrialResult>> futures = new ArrayList<>();

    for (int trial = 0; trial < config.getNumberOfTrials(); trial++) {
      final int trialNum = trial;
      final Map<String, Double> sampled = allSamples.get(trial);

      futures.add(executor.submit(() -> runSingleTrial(trialNum, sampled, feedStream, lowerBound,
          upperBound, rateUnit, outputMetric)));
    }

    List<TrialResult> results = new ArrayList<>();
    for (Future<TrialResult> future : futures) {
      try {
        results.add(future.get());
      } catch (Exception e) {
        results.add(new TrialResult(results.size(), Collections.emptyMap(), Double.NaN, null, false,
            false));
      }
    }

    executor.shutdown();
    return results;
  }

  /**
   * Runs a single trial (used for parallel execution).
   */
  private TrialResult runSingleTrial(int trialNum, Map<String, Double> sampled,
      StreamInterface feedStream, double lowerBound, double upperBound, String rateUnit,
      ToDoubleFunction<OptimizationResult> outputMetric) {
    try {
      // Apply parameters (note: this modifies shared state, may need cloning for true parallelism)
      for (UncertainParameter param : parameters) {
        Double value = sampled.get(param.getName());
        if (value != null) {
          param.apply(baseProcess, value);
        }
      }

      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig optConfig =
          new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);

      OptimizationResult optResult = optimizer.optimize(baseProcess, feedStream, optConfig,
          Collections.emptyList(), Collections.emptyList());

      double output = outputMetric.applyAsDouble(optResult);
      String bottleneck =
          optResult.getBottleneck() != null ? optResult.getBottleneck().getName() : null;

      return new TrialResult(trialNum, sampled, output, bottleneck, optResult.isFeasible(), true);

    } catch (Exception e) {
      return new TrialResult(trialNum, sampled, Double.NaN, null, false, false);
    }
  }

  /**
   * Runs one-at-a-time sensitivity analysis (tornado diagram).
   *
   * <p>
   * For each parameter:
   * <ol>
   * <li>Set all parameters to P50 (base case)</li>
   * <li>Vary target parameter to P10, run, record output</li>
   * <li>Vary target parameter to P90, run, record output</li>
   * <li>Calculate sensitivity as |output_P90 - output_P10|</li>
   * </ol>
   *
   * @param feedStream feed stream for optimization
   * @param lowerBound lower bound for rate
   * @param upperBound upper bound for rate
   * @param rateUnit rate unit
   * @param outputMetric function to extract output
   * @return map of parameter name to sensitivity magnitude
   */
  public Map<String, Double> runTornadoAnalysis(StreamInterface feedStream, double lowerBound,
      double upperBound, String rateUnit, ToDoubleFunction<OptimizationResult> outputMetric) {
    return runTornadoAnalysisInternal(feedStream, lowerBound, upperBound, rateUnit, outputMetric);
  }

  private Map<String, Double> runTornadoAnalysisInternal(StreamInterface feedStream,
      double lowerBound, double upperBound, String rateUnit,
      ToDoubleFunction<OptimizationResult> outputMetric) {

    Map<String, Double> sensitivities = new LinkedHashMap<>();
    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig optConfig =
        new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);

    // Set all to base case (P50)
    for (UncertainParameter param : parameters) {
      param.apply(baseProcess, param.getP50());
    }

    // Get base case output
    double baseOutput = 0;
    try {
      OptimizationResult baseResult = optimizer.optimize(baseProcess, feedStream, optConfig,
          Collections.emptyList(), Collections.emptyList());
      baseOutput = outputMetric.applyAsDouble(baseResult);
    } catch (Exception e) {
      // Use 0 if base case fails
    }

    // Vary each parameter
    for (UncertainParameter param : parameters) {
      try {
        // Set to P10
        param.apply(baseProcess, param.getP10());
        OptimizationResult lowResult = optimizer.optimize(baseProcess, feedStream, optConfig,
            Collections.emptyList(), Collections.emptyList());
        double lowOutput = outputMetric.applyAsDouble(lowResult);

        // Set to P90
        param.apply(baseProcess, param.getP90());
        OptimizationResult highResult = optimizer.optimize(baseProcess, feedStream, optConfig,
            Collections.emptyList(), Collections.emptyList());
        double highOutput = outputMetric.applyAsDouble(highResult);

        // Reset to P50
        param.apply(baseProcess, param.getP50());

        // Calculate sensitivity
        double sensitivity = Math.abs(highOutput - lowOutput);
        sensitivities.put(param.getName(), sensitivity);

      } catch (Exception e) {
        sensitivities.put(param.getName(), 0.0);
      }
    }

    return sensitivities;
  }

  /**
   * Generates spider plot data for each parameter.
   *
   * <p>
   * Varies each parameter systematically from P10 to P90 while holding others at P50, recording the
   * output at each step.
   *
   * @param feedStream feed stream for optimization
   * @param lowerBound lower bound for rate
   * @param upperBound upper bound for rate
   * @param rateUnit rate unit
   * @param stepsPerParameter number of steps from P10 to P90
   * @param outputMetric function to extract output
   * @return map of parameter name to list of spider points
   */
  public Map<String, List<SpiderPoint>> runSpiderAnalysis(StreamInterface feedStream,
      double lowerBound, double upperBound, String rateUnit, int stepsPerParameter,
      ToDoubleFunction<OptimizationResult> outputMetric) {

    Map<String, List<SpiderPoint>> spiderData = new LinkedHashMap<>();
    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig optConfig =
        new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);

    for (UncertainParameter param : parameters) {
      List<SpiderPoint> points = new ArrayList<>();

      // Set all to P50
      for (UncertainParameter p : parameters) {
        p.apply(baseProcess, p.getP50());
      }

      // Vary this parameter
      for (int step = 0; step <= stepsPerParameter; step++) {
        double fraction = (double) step / stepsPerParameter;
        double value = param.getP10() + fraction * (param.getP90() - param.getP10());
        double normalized = -1.0 + 2.0 * fraction; // -1 at P10, +1 at P90

        param.apply(baseProcess, value);

        try {
          OptimizationResult result = optimizer.optimize(baseProcess, feedStream, optConfig,
              Collections.emptyList(), Collections.emptyList());
          double output = outputMetric.applyAsDouble(result);
          points.add(new SpiderPoint(value, normalized, output));
        } catch (Exception e) {
          points.add(new SpiderPoint(value, normalized, Double.NaN));
        }
      }

      // Reset to P50
      param.apply(baseProcess, param.getP50());

      spiderData.put(param.getName(), points);
    }

    return spiderData;
  }

  /**
   * Gets the random number generator.
   *
   * @return RNG (creates if null)
   */
  private Random getRng() {
    if (rng == null) {
      rng = new Random();
    }
    return rng;
  }

  /**
   * Sets the random number generator.
   *
   * @param rng new RNG
   */
  public void setRng(Random rng) {
    this.rng = Objects.requireNonNull(rng);
  }

  /**
   * Gets the base process system.
   *
   * @return base process
   */
  public ProcessSystem getBaseProcess() {
    return baseProcess;
  }
}

