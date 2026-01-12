package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Monte Carlo simulation runner for uncertainty quantification in field development.
 *
 * <p>
 * Supports various probability distributions (triangular, normal, lognormal, uniform) and
 * integrates with CashFlowEngine for economic uncertainty analysis. Typical use cases include:
 * <ul>
 * <li>CAPEX/OPEX uncertainty analysis</li>
 * <li>Oil price sensitivity</li>
 * <li>Production rate uncertainty</li>
 * <li>NPV risk assessment (P10, P50, P90)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * MonteCarloRunner mc = new MonteCarloRunner(cashFlowEngine);
 * mc.addVariable("oilPrice", DistributionType.TRIANGULAR, 50.0, 70.0, 120.0);
 * mc.addVariable("capex", DistributionType.NORMAL, 500.0, 75.0);
 * mc.setIterations(10000);
 * mc.run();
 *
 * double p10 = mc.getPercentile("npv", 10);
 * double p50 = mc.getPercentile("npv", 50);
 * double p90 = mc.getPercentile("npv", 90);
 * }
 * </pre>
 *
 * @author AGAS
 * @version 1.0
 */
public class MonteCarloRunner implements Serializable {

  /** Serial version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(MonteCarloRunner.class);

  /**
   * Probability distribution types supported by the Monte Carlo runner.
   */
  public enum DistributionType {
    /** Triangular distribution with min, mode, max. */
    TRIANGULAR,
    /** Normal (Gaussian) distribution with mean, std dev. */
    NORMAL,
    /** Log-normal distribution with mean, std dev of underlying normal. */
    LOGNORMAL,
    /** Uniform distribution with min, max. */
    UNIFORM,
    /** Fixed value (no uncertainty). */
    FIXED
  }

  /**
   * Definition of an uncertain variable.
   */
  public static class UncertainVariable implements Serializable {
    private static final long serialVersionUID = 1001L;

    private String name;
    private DistributionType distribution;
    private double param1;
    private double param2;
    private double param3;

    /**
     * Creates a new uncertain variable.
     *
     * @param name variable name
     * @param distribution distribution type
     * @param param1 first parameter (interpretation depends on distribution)
     * @param param2 second parameter
     * @param param3 third parameter (only for triangular)
     */
    public UncertainVariable(String name, DistributionType distribution, double param1,
        double param2, double param3) {
      this.name = name;
      this.distribution = distribution;
      this.param1 = param1;
      this.param2 = param2;
      this.param3 = param3;
    }

    /**
     * Get the variable name.
     *
     * @return variable name
     */
    public String getName() {
      return name;
    }

    /**
     * Get the distribution type.
     *
     * @return distribution type
     */
    public DistributionType getDistribution() {
      return distribution;
    }

    /**
     * Get the first parameter.
     *
     * @return param1 value
     */
    public double getParam1() {
      return param1;
    }

    /**
     * Get the second parameter.
     *
     * @return param2 value
     */
    public double getParam2() {
      return param2;
    }

    /**
     * Get the third parameter.
     *
     * @return param3 value
     */
    public double getParam3() {
      return param3;
    }
  }

  /**
   * Results from a single Monte Carlo iteration.
   */
  public static class IterationResult implements Serializable {
    private static final long serialVersionUID = 1002L;

    private Map<String, Double> inputs;
    private double npv;
    private double irr;
    private double paybackYears;
    private double profitabilityIndex;
    private boolean converged;

    /**
     * Creates a new iteration result.
     */
    public IterationResult() {
      this.inputs = new HashMap<String, Double>();
      this.converged = true;
    }

    /**
     * Set an input value.
     *
     * @param name input variable name
     * @param value sampled value
     */
    public void setInput(String name, double value) {
      inputs.put(name, Double.valueOf(value));
    }

    /**
     * Get all input values.
     *
     * @return map of input names to values
     */
    public Map<String, Double> getInputs() {
      return inputs;
    }

    /**
     * Get NPV for this iteration.
     *
     * @return NPV in MUSD
     */
    public double getNpv() {
      return npv;
    }

    /**
     * Set NPV for this iteration.
     *
     * @param npv NPV in MUSD
     */
    public void setNpv(double npv) {
      this.npv = npv;
    }

    /**
     * Get IRR for this iteration.
     *
     * @return IRR as decimal
     */
    public double getIrr() {
      return irr;
    }

    /**
     * Set IRR for this iteration.
     *
     * @param irr IRR as decimal
     */
    public void setIrr(double irr) {
      this.irr = irr;
    }

    /**
     * Get payback period for this iteration.
     *
     * @return payback period in years
     */
    public double getPaybackYears() {
      return paybackYears;
    }

    /**
     * Set payback period for this iteration.
     *
     * @param paybackYears payback period in years
     */
    public void setPaybackYears(double paybackYears) {
      this.paybackYears = paybackYears;
    }

    /**
     * Get profitability index for this iteration.
     *
     * @return profitability index
     */
    public double getProfitabilityIndex() {
      return profitabilityIndex;
    }

    /**
     * Set profitability index for this iteration.
     *
     * @param profitabilityIndex profitability index
     */
    public void setProfitabilityIndex(double profitabilityIndex) {
      this.profitabilityIndex = profitabilityIndex;
    }

    /**
     * Check if iteration converged.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return converged;
    }

    /**
     * Set convergence status.
     *
     * @param converged convergence status
     */
    public void setConverged(boolean converged) {
      this.converged = converged;
    }
  }

  /** Cash flow engine for economic calculations. */
  private CashFlowEngine cashFlowEngine;

  /** List of uncertain variables. */
  private List<UncertainVariable> variables;

  /** Results from all iterations. */
  private List<IterationResult> results;

  /** Number of Monte Carlo iterations. */
  private int iterations;

  /** Random number generator. */
  private Random random;

  /** Discount rate for NPV calculation. */
  private double discountRate;

  /**
   * Creates a new Monte Carlo runner.
   *
   * @param engine cash flow engine for economic calculations
   */
  public MonteCarloRunner(CashFlowEngine engine) {
    this.cashFlowEngine = engine;
    this.variables = new ArrayList<UncertainVariable>();
    this.results = new ArrayList<IterationResult>();
    this.iterations = 1000;
    this.random = new Random();
    this.discountRate = 0.08;
  }

  /**
   * Creates a new Monte Carlo runner with specified seed for reproducibility.
   *
   * @param engine cash flow engine
   * @param seed random seed
   */
  public MonteCarloRunner(CashFlowEngine engine, long seed) {
    this(engine);
    this.random = new Random(seed);
  }

  /**
   * Add an uncertain variable with triangular distribution.
   *
   * @param name variable name (e.g., "oilPrice", "capex", "opex")
   * @param min minimum value
   * @param mode most likely value
   * @param max maximum value
   */
  public void addTriangular(String name, double min, double mode, double max) {
    addVariable(name, DistributionType.TRIANGULAR, min, mode, max);
  }

  /**
   * Add an uncertain variable with normal distribution.
   *
   * @param name variable name
   * @param mean mean value
   * @param stdDev standard deviation
   */
  public void addNormal(String name, double mean, double stdDev) {
    addVariable(name, DistributionType.NORMAL, mean, stdDev, 0.0);
  }

  /**
   * Add an uncertain variable with lognormal distribution.
   *
   * @param name variable name
   * @param meanOfLog mean of underlying normal distribution
   * @param stdDevOfLog standard deviation of underlying normal
   */
  public void addLognormal(String name, double meanOfLog, double stdDevOfLog) {
    addVariable(name, DistributionType.LOGNORMAL, meanOfLog, stdDevOfLog, 0.0);
  }

  /**
   * Add an uncertain variable with uniform distribution.
   *
   * @param name variable name
   * @param min minimum value
   * @param max maximum value
   */
  public void addUniform(String name, double min, double max) {
    addVariable(name, DistributionType.UNIFORM, min, max, 0.0);
  }

  /**
   * Add an uncertain variable with specified distribution.
   *
   * @param name variable name
   * @param distribution distribution type
   * @param param1 first parameter (min for triangular/uniform, mean for normal/lognormal)
   * @param param2 second parameter (mode for triangular, max for uniform, stddev for normal)
   * @param param3 third parameter (max for triangular, unused for others)
   */
  public void addVariable(String name, DistributionType distribution, double param1, double param2,
      double param3) {
    variables.add(new UncertainVariable(name, distribution, param1, param2, param3));
  }

  /**
   * Set number of Monte Carlo iterations.
   *
   * @param iterations number of iterations (typically 1000-10000)
   */
  public void setIterations(int iterations) {
    if (iterations < 100) {
      logger.warn("Low iteration count may produce unreliable statistics: {}", iterations);
    }
    this.iterations = iterations;
  }

  /**
   * Get number of iterations.
   *
   * @return number of iterations
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Set discount rate for NPV calculations.
   *
   * @param rate discount rate as decimal (e.g., 0.08 for 8%)
   */
  public void setDiscountRate(double rate) {
    this.discountRate = rate;
  }

  /**
   * Get discount rate.
   *
   * @return discount rate
   */
  public double getDiscountRate() {
    return discountRate;
  }

  /**
   * Set random seed for reproducibility.
   *
   * @param seed random seed
   */
  public void setSeed(long seed) {
    this.random = new Random(seed);
  }

  /**
   * Sample a value from the specified distribution.
   *
   * @param var uncertain variable definition
   * @return sampled value
   */
  private double sample(UncertainVariable var) {
    switch (var.getDistribution()) {
      case TRIANGULAR:
        return sampleTriangular(var.getParam1(), var.getParam2(), var.getParam3());
      case NORMAL:
        return sampleNormal(var.getParam1(), var.getParam2());
      case LOGNORMAL:
        return sampleLognormal(var.getParam1(), var.getParam2());
      case UNIFORM:
        return sampleUniform(var.getParam1(), var.getParam2());
      case FIXED:
        return var.getParam1();
      default:
        return var.getParam1();
    }
  }

  /**
   * Sample from triangular distribution.
   *
   * @param min minimum
   * @param mode most likely
   * @param max maximum
   * @return sampled value
   */
  private double sampleTriangular(double min, double mode, double max) {
    double u = random.nextDouble();
    double fc = (mode - min) / (max - min);

    if (u < fc) {
      return min + Math.sqrt(u * (max - min) * (mode - min));
    } else {
      return max - Math.sqrt((1 - u) * (max - min) * (max - mode));
    }
  }

  /**
   * Sample from normal distribution.
   *
   * @param mean mean
   * @param stdDev standard deviation
   * @return sampled value
   */
  private double sampleNormal(double mean, double stdDev) {
    return mean + random.nextGaussian() * stdDev;
  }

  /**
   * Sample from lognormal distribution.
   *
   * @param meanOfLog mean of underlying normal
   * @param stdDevOfLog std dev of underlying normal
   * @return sampled value
   */
  private double sampleLognormal(double meanOfLog, double stdDevOfLog) {
    double normal = sampleNormal(meanOfLog, stdDevOfLog);
    return Math.exp(normal);
  }

  /**
   * Sample from uniform distribution.
   *
   * @param min minimum
   * @param max maximum
   * @return sampled value
   */
  private double sampleUniform(double min, double max) {
    return min + random.nextDouble() * (max - min);
  }

  /**
   * Run the Monte Carlo simulation.
   *
   * <p>
   * Executes the specified number of iterations, sampling from each uncertain variable and
   * calculating NPV for each scenario.
   * </p>
   *
   * @return true if simulation completed successfully
   */
  public boolean run() {
    results.clear();
    int convergedCount = 0;

    logger.info("Starting Monte Carlo simulation with {} iterations", iterations);

    for (int i = 0; i < iterations; i++) {
      IterationResult result = new IterationResult();

      // Sample all variables
      for (UncertainVariable var : variables) {
        double value = sample(var);
        result.setInput(var.getName(), value);

        // Apply to cash flow engine
        applyVariable(var.getName(), value);
      }

      // Calculate economics
      try {
        CashFlowResult cfResult = cashFlowEngine.calculate(discountRate);
        result.setNpv(cfResult.getNpv());
        result.setIrr(cfResult.getIrr());
        result.setPaybackYears(cfResult.getPaybackYears());
        // Profitability Index = NPV / CAPEX + 1
        double totalCapex = cfResult.getTotalCapex();
        double pi = totalCapex > 0 ? (cfResult.getNpv() / totalCapex) + 1.0 : 0.0;
        result.setProfitabilityIndex(pi);
        result.setConverged(true);
        convergedCount++;;
      } catch (Exception e) {
        logger.debug("Iteration {} failed: {}", i, e.getMessage());
        result.setConverged(false);
        result.setNpv(Double.NaN);
      }

      results.add(result);
    }

    logger.info("Monte Carlo complete: {} of {} iterations converged", convergedCount, iterations);
    return convergedCount > iterations * 0.9;
  }

  /**
   * Apply a sampled variable value to the cash flow engine.
   *
   * @param name variable name
   * @param value sampled value
   */
  private void applyVariable(String name, double value) {
    String lowerName = name.toLowerCase();

    if (lowerName.contains("oilprice") || lowerName.contains("oil_price")) {
      cashFlowEngine.setOilPrice(value);
    } else if (lowerName.contains("gasprice") || lowerName.contains("gas_price")) {
      cashFlowEngine.setGasPrice(value);
    } else if (lowerName.contains("capex")) {
      // Apply CAPEX multiplier to year 0 as default
      cashFlowEngine.setCapex(value, 0);
    } else if (lowerName.contains("opex")) {
      // Use opex percent of capex - assume value is a percentage
      cashFlowEngine.setOpexPercentOfCapex(value);
    } else if (lowerName.contains("production") || lowerName.contains("rate")) {
      // For production rate scaling, we'd need to adjust the profile
      // This is a placeholder for custom implementations
      logger.debug("Production variable {} = {} (custom handling required)", name, value);
    }
  }

  /**
   * Get the percentile value for a result metric.
   *
   * @param metric metric name ("npv", "irr", "payback")
   * @param percentile percentile (0-100)
   * @return percentile value
   */
  public double getPercentile(String metric, double percentile) {
    if (results.isEmpty()) {
      throw new IllegalStateException("No results available. Call run() first.");
    }

    List<Double> values = new ArrayList<Double>();
    for (IterationResult r : results) {
      if (r.isConverged()) {
        double value = getMetricValue(r, metric);
        if (!Double.isNaN(value)) {
          values.add(Double.valueOf(value));
        }
      }
    }

    if (values.isEmpty()) {
      return Double.NaN;
    }

    // Sort values
    Double[] sorted = values.toArray(new Double[0]);
    Arrays.sort(sorted);

    // Calculate percentile index
    double index = (percentile / 100.0) * (sorted.length - 1);
    int lower = (int) Math.floor(index);
    int upper = (int) Math.ceil(index);

    if (lower == upper) {
      return sorted[lower].doubleValue();
    }

    // Linear interpolation
    double fraction = index - lower;
    return sorted[lower].doubleValue() * (1 - fraction) + sorted[upper].doubleValue() * fraction;
  }

  /**
   * Get the P10 value (10th percentile - low case).
   *
   * @param metric metric name
   * @return P10 value
   */
  public double getP10(String metric) {
    return getPercentile(metric, 10);
  }

  /**
   * Get the P50 value (50th percentile - base case).
   *
   * @param metric metric name
   * @return P50 value
   */
  public double getP50(String metric) {
    return getPercentile(metric, 50);
  }

  /**
   * Get the P90 value (90th percentile - high case).
   *
   * @param metric metric name
   * @return P90 value
   */
  public double getP90(String metric) {
    return getPercentile(metric, 90);
  }

  /**
   * Get mean value for a metric.
   *
   * @param metric metric name
   * @return mean value
   */
  public double getMean(String metric) {
    if (results.isEmpty()) {
      return Double.NaN;
    }

    double sum = 0.0;
    int count = 0;

    for (IterationResult r : results) {
      if (r.isConverged()) {
        double value = getMetricValue(r, metric);
        if (!Double.isNaN(value)) {
          sum += value;
          count++;
        }
      }
    }

    return count > 0 ? sum / count : Double.NaN;
  }

  /**
   * Get standard deviation for a metric.
   *
   * @param metric metric name
   * @return standard deviation
   */
  public double getStdDev(String metric) {
    if (results.isEmpty()) {
      return Double.NaN;
    }

    double mean = getMean(metric);
    double sumSq = 0.0;
    int count = 0;

    for (IterationResult r : results) {
      if (r.isConverged()) {
        double value = getMetricValue(r, metric);
        if (!Double.isNaN(value)) {
          sumSq += Math.pow(value - mean, 2);
          count++;
        }
      }
    }

    return count > 1 ? Math.sqrt(sumSq / (count - 1)) : 0.0;
  }

  /**
   * Get the probability of positive NPV.
   *
   * @return probability (0-1)
   */
  public double getProbabilityPositiveNpv() {
    if (results.isEmpty()) {
      return Double.NaN;
    }

    int positive = 0;
    int total = 0;

    for (IterationResult r : results) {
      if (r.isConverged() && !Double.isNaN(r.getNpv())) {
        total++;
        if (r.getNpv() > 0) {
          positive++;
        }
      }
    }

    return total > 0 ? (double) positive / total : Double.NaN;
  }

  /**
   * Get the probability that NPV exceeds a threshold.
   *
   * @param threshold NPV threshold in MUSD
   * @return probability (0-1)
   */
  public double getProbabilityNpvExceeds(double threshold) {
    if (results.isEmpty()) {
      return Double.NaN;
    }

    int exceeds = 0;
    int total = 0;

    for (IterationResult r : results) {
      if (r.isConverged() && !Double.isNaN(r.getNpv())) {
        total++;
        if (r.getNpv() > threshold) {
          exceeds++;
        }
      }
    }

    return total > 0 ? (double) exceeds / total : Double.NaN;
  }

  /**
   * Get metric value from an iteration result.
   *
   * @param result iteration result
   * @param metric metric name
   * @return metric value
   */
  private double getMetricValue(IterationResult result, String metric) {
    String lower = metric.toLowerCase();

    if (lower.equals("npv")) {
      return result.getNpv();
    } else if (lower.equals("irr")) {
      return result.getIrr();
    } else if (lower.equals("payback") || lower.equals("paybackyears")) {
      return result.getPaybackYears();
    } else if (lower.equals("pi") || lower.equals("profitabilityindex")) {
      return result.getProfitabilityIndex();
    } else if (result.getInputs().containsKey(metric)) {
      return result.getInputs().get(metric).doubleValue();
    }

    return Double.NaN;
  }

  /**
   * Get all iteration results.
   *
   * @return list of iteration results
   */
  public List<IterationResult> getResults() {
    return results;
  }

  /**
   * Get number of converged iterations.
   *
   * @return converged count
   */
  public int getConvergedCount() {
    int count = 0;
    for (IterationResult r : results) {
      if (r.isConverged()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Generate a summary report of the Monte Carlo results.
   *
   * @return formatted report string
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== MONTE CARLO SIMULATION RESULTS ===\n\n");

    sb.append("Configuration:\n");
    sb.append("  Iterations: ").append(iterations).append("\n");
    sb.append("  Converged:  ").append(getConvergedCount()).append("\n");
    sb.append("  Discount:   ").append(discountRate * 100).append("%\n\n");

    sb.append("Variables:\n");
    for (UncertainVariable var : variables) {
      sb.append(String.format("  %s: %s (%.2f, %.2f, %.2f)%n", var.getName(), var.getDistribution(),
          var.getParam1(), var.getParam2(), var.getParam3()));
    }
    sb.append("\n");

    sb.append("NPV Results (MUSD):\n");
    sb.append(String.format("  P10:  %.1f%n", getP10("npv")));
    sb.append(String.format("  P50:  %.1f%n", getP50("npv")));
    sb.append(String.format("  P90:  %.1f%n", getP90("npv")));
    sb.append(String.format("  Mean: %.1f%n", getMean("npv")));
    sb.append(String.format("  StdDev: %.1f%n", getStdDev("npv")));
    sb.append("\n");

    sb.append("Risk Metrics:\n");
    sb.append(String.format("  P(NPV > 0): %.1f%%%n", getProbabilityPositiveNpv() * 100));
    sb.append(String.format("  P(IRR > 8%%): N/A%n"));
    sb.append("\n");

    return sb.toString();
  }
}
