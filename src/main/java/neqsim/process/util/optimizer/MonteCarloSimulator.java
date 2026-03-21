package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Monte Carlo uncertainty analysis for process simulations.
 *
 * <p>
 * Runs N iterations of a process simulation with randomly sampled input parameters drawn from
 * triangular distributions. Computes P10/P50/P90 percentiles, mean, standard deviation, and
 * probability of exceeding thresholds. Also generates tornado sensitivity data by varying each
 * parameter individually between its low and high bounds.
 * </p>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * ProcessSystem process = ...;
 * process.run();
 *
 * MonteCarloSimulator mc = new MonteCarloSimulator(process, 200);
 * mc.addTriangularParameter("Gas Price", 0.8, 1.5, 2.5,
 *     (p, v) -&gt; { /* apply gas price to p *{@literal /} });
 * mc.addTriangularParameter("CAPEX multiplier", 0.85, 1.0, 1.4,
 *     (p, v) -&gt; { /* apply capex multiplier to p *{@literal /} });
 * mc.setOutputExtractor("NPV (MNOK)",
 *     p -&gt; calculateNPV(p));
 *
 * MonteCarloResult result = mc.run();
 * System.out.println("P10=" + result.getP10());
 * System.out.println("P50=" + result.getP50());
 * System.out.println("P90=" + result.getP90());
 * System.out.println(result.toJson());
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see SensitivityAnalysis
 * @see BatchStudy
 */
public class MonteCarloSimulator implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MonteCarloSimulator.class);

  /** Base process system to clone for each iteration. */
  private final ProcessSystem baseProcess;

  /** Number of Monte Carlo iterations. */
  private int iterations;

  /** Random number generator. */
  private Random random = new Random(42);

  /** Uncertain input parameters. */
  private final List<UncertainParameter> parameters = new ArrayList<>();

  /** Output extractor name. */
  private String outputName = "output";

  /** Output value extractor function. */
  private transient Function<ProcessSystem, Double> outputExtractor;

  /**
   * Functional interface for applying a parameter value to a process system.
   */
  public interface ParameterApplier extends Serializable {
    /**
     * Applies a parameter value to a process system.
     *
     * @param process the process system to modify
     * @param value the parameter value to apply
     */
    void apply(ProcessSystem process, double value);
  }

  /**
   * Creates a Monte Carlo simulator.
   *
   * @param baseProcess the base process system to clone for each iteration
   * @param iterations number of Monte Carlo iterations (recommended 200+ for NeqSim)
   */
  public MonteCarloSimulator(ProcessSystem baseProcess, int iterations) {
    if (baseProcess == null) {
      throw new IllegalArgumentException("Base process cannot be null");
    }
    this.baseProcess = baseProcess;
    this.iterations = Math.max(10, iterations);
  }

  /**
   * Sets the random seed for reproducibility.
   *
   * @param seed random seed
   * @return this for chaining
   */
  public MonteCarloSimulator setSeed(long seed) {
    this.random = new Random(seed);
    return this;
  }

  /**
   * Adds an uncertain parameter with a triangular distribution.
   *
   * @param name descriptive name (e.g., "Gas Price (NOK/Sm3)")
   * @param low minimum value
   * @param mode most likely value
   * @param high maximum value
   * @param applier function to apply the sampled value to a process system
   * @return this for chaining
   */
  public MonteCarloSimulator addTriangularParameter(String name, double low, double mode,
      double high, ParameterApplier applier) {
    parameters.add(new UncertainParameter(name, low, mode, high, "triangular", applier));
    return this;
  }

  /**
   * Adds an uncertain parameter with a uniform distribution.
   *
   * @param name descriptive name
   * @param low minimum value
   * @param high maximum value
   * @param applier function to apply the sampled value
   * @return this for chaining
   */
  public MonteCarloSimulator addUniformParameter(String name, double low, double high,
      ParameterApplier applier) {
    double mode = (low + high) / 2.0;
    parameters.add(new UncertainParameter(name, low, mode, high, "uniform", applier));
    return this;
  }

  /**
   * Sets the output variable to track.
   *
   * @param name output variable name
   * @param extractor function that extracts the output value from a run process system
   * @return this for chaining
   */
  public MonteCarloSimulator setOutputExtractor(String name,
      Function<ProcessSystem, Double> extractor) {
    this.outputName = name;
    this.outputExtractor = extractor;
    return this;
  }

  /**
   * Runs the Monte Carlo simulation.
   *
   * @return results with P10/P50/P90, tornado data, and all sampled values
   */
  public MonteCarloResult run() {
    if (outputExtractor == null) {
      throw new IllegalStateException("No output extractor defined. Call setOutputExtractor().");
    }
    if (parameters.isEmpty()) {
      throw new IllegalStateException("No uncertain parameters defined.");
    }

    double[] outputs = new double[iterations];
    int successCount = 0;
    int failCount = 0;

    for (int i = 0; i < iterations; i++) {
      if (i % 50 == 0) {
        logger.info("Monte Carlo iteration {}/{}", i + 1, iterations);
      }

      try {
        ProcessSystem copy = baseProcess.copy();

        for (UncertainParameter param : parameters) {
          double sampledValue = sampleValue(param);
          param.applier.apply(copy, sampledValue);
        }

        copy.run();
        double result = outputExtractor.apply(copy);
        outputs[successCount] = result;
        successCount++;
      } catch (Exception e) {
        failCount++;
        logger.debug("Monte Carlo iteration {} failed: {}", i + 1, e.getMessage());
      }
    }

    // Trim to successful results
    double[] validOutputs = Arrays.copyOf(outputs, successCount);
    Arrays.sort(validOutputs);

    // Compute tornado sensitivity
    List<TornadoEntry> tornado = computeTornado();

    return new MonteCarloResult(outputName, validOutputs, iterations, successCount, failCount,
        tornado, parameters);
  }

  /**
   * Samples a value from the parameter's distribution.
   *
   * @param param the parameter to sample
   * @return sampled value
   */
  private double sampleValue(UncertainParameter param) {
    if ("uniform".equals(param.distribution)) {
      return param.low + random.nextDouble() * (param.high - param.low);
    }
    // Triangular distribution
    return sampleTriangular(param.low, param.mode, param.high);
  }

  /**
   * Samples from a triangular distribution.
   *
   * @param low minimum value
   * @param mode most likely value
   * @param high maximum value
   * @return sampled value
   */
  private double sampleTriangular(double low, double mode, double high) {
    double u = random.nextDouble();
    double fc = (mode - low) / (high - low);

    if (u < fc) {
      return low + Math.sqrt(u * (high - low) * (mode - low));
    } else {
      return high - Math.sqrt((1 - u) * (high - low) * (high - mode));
    }
  }

  /**
   * Computes tornado sensitivity by varying each parameter low/high while holding others at mode.
   *
   * @return list of tornado entries sorted by swing (descending)
   */
  private List<TornadoEntry> computeTornado() {
    List<TornadoEntry> tornado = new ArrayList<>();

    for (int i = 0; i < parameters.size(); i++) {
      UncertainParameter param = parameters.get(i);

      try {
        // Run with parameter at LOW
        double lowResult = runWithSingleParamOverride(i, param.low);
        // Run with parameter at HIGH
        double highResult = runWithSingleParamOverride(i, param.high);

        double swing = Math.abs(highResult - lowResult);
        String label = param.name + " (" + param.low + "-" + param.high + ")";
        tornado.add(new TornadoEntry(label, lowResult, highResult, swing));
      } catch (Exception e) {
        logger.warn("Tornado calc failed for {}: {}", param.name, e.getMessage());
      }
    }

    // Sort by swing descending
    tornado.sort((a, b) -> Double.compare(b.swing, a.swing));
    return tornado;
  }

  /**
   * Runs with one parameter overridden and all others at their mode values.
   *
   * @param paramIndex index of the parameter to override
   * @param overrideValue value to use for the overridden parameter
   * @return output value
   * @throws Exception if simulation fails
   */
  private double runWithSingleParamOverride(int paramIndex, double overrideValue) throws Exception {
    ProcessSystem copy = baseProcess.copy();

    for (int i = 0; i < parameters.size(); i++) {
      UncertainParameter param = parameters.get(i);
      double value = (i == paramIndex) ? overrideValue : param.mode;
      param.applier.apply(copy, value);
    }

    copy.run();
    return outputExtractor.apply(copy);
  }

  // ============================================================
  // Data classes
  // ============================================================

  /**
   * An uncertain input parameter.
   */
  private static class UncertainParameter implements Serializable {
    private static final long serialVersionUID = 1L;
    final String name;
    final double low;
    final double mode;
    final double high;
    final String distribution;
    final ParameterApplier applier;

    /**
     * Creates an uncertain parameter.
     *
     * @param name parameter name
     * @param low minimum value
     * @param mode most likely value
     * @param high maximum value
     * @param distribution distribution type
     * @param applier applier function
     */
    UncertainParameter(String name, double low, double mode, double high, String distribution,
        ParameterApplier applier) {
      this.name = name;
      this.low = low;
      this.mode = mode;
      this.high = high;
      this.distribution = distribution;
      this.applier = applier;
    }
  }

  /**
   * A tornado sensitivity entry.
   */
  public static class TornadoEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Parameter description. */
    public final String parameter;
    /** Output value when parameter is at low end. */
    public final double lowResult;
    /** Output value when parameter is at high end. */
    public final double highResult;
    /** Absolute swing (|high - low|). */
    public final double swing;

    /**
     * Creates a tornado entry.
     *
     * @param parameter parameter description
     * @param lowResult result at low value
     * @param highResult result at high value
     * @param swing absolute swing
     */
    TornadoEntry(String parameter, double lowResult, double highResult, double swing) {
      this.parameter = parameter;
      this.lowResult = lowResult;
      this.highResult = highResult;
      this.swing = swing;
    }
  }

  /**
   * Results of a Monte Carlo simulation.
   */
  public static class MonteCarloResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String outputName;
    private final double[] sortedValues;
    private final int totalIterations;
    private final int successCount;
    private final int failCount;
    private final List<TornadoEntry> tornado;
    private final List<UncertainParameter> parameters;

    /**
     * Creates a Monte Carlo result.
     *
     * @param outputName name of the output variable
     * @param sortedValues sorted array of successful output values
     * @param totalIterations total iterations attempted
     * @param successCount successful iterations
     * @param failCount failed iterations
     * @param tornado tornado sensitivity entries
     * @param parameters input parameters
     */
    MonteCarloResult(String outputName, double[] sortedValues, int totalIterations,
        int successCount, int failCount, List<TornadoEntry> tornado,
        List<UncertainParameter> parameters) {
      this.outputName = outputName;
      this.sortedValues = sortedValues;
      this.totalIterations = totalIterations;
      this.successCount = successCount;
      this.failCount = failCount;
      this.tornado = tornado;
      this.parameters = parameters;
    }

    /**
     * Gets the P10 percentile (10th percentile).
     *
     * @return P10 value
     */
    public double getP10() {
      return getPercentile(10);
    }

    /**
     * Gets the P50 percentile (median).
     *
     * @return P50 value
     */
    public double getP50() {
      return getPercentile(50);
    }

    /**
     * Gets the P90 percentile (90th percentile).
     *
     * @return P90 value
     */
    public double getP90() {
      return getPercentile(90);
    }

    /**
     * Gets the mean of all successful outputs.
     *
     * @return mean value
     */
    public double getMean() {
      if (sortedValues.length == 0) {
        return Double.NaN;
      }
      double sum = 0;
      for (double v : sortedValues) {
        sum += v;
      }
      return sum / sortedValues.length;
    }

    /**
     * Gets the standard deviation of all successful outputs.
     *
     * @return standard deviation
     */
    public double getStdDev() {
      if (sortedValues.length < 2) {
        return Double.NaN;
      }
      double mean = getMean();
      double sumSq = 0;
      for (double v : sortedValues) {
        sumSq += (v - mean) * (v - mean);
      }
      return Math.sqrt(sumSq / (sortedValues.length - 1));
    }

    /**
     * Gets the probability that the output is below a threshold.
     *
     * @param threshold the threshold value
     * @return probability (0 to 1)
     */
    public double getProbabilityBelow(double threshold) {
      if (sortedValues.length == 0) {
        return Double.NaN;
      }
      int count = 0;
      for (double v : sortedValues) {
        if (v < threshold) {
          count++;
        }
      }
      return (double) count / sortedValues.length;
    }

    /**
     * Gets a specific percentile value.
     *
     * @param percentile the percentile (0-100)
     * @return the percentile value
     */
    public double getPercentile(double percentile) {
      if (sortedValues.length == 0) {
        return Double.NaN;
      }
      double index = (percentile / 100.0) * (sortedValues.length - 1);
      int lower = (int) Math.floor(index);
      int upper = (int) Math.ceil(index);
      if (lower == upper || upper >= sortedValues.length) {
        return sortedValues[Math.min(lower, sortedValues.length - 1)];
      }
      double fraction = index - lower;
      return sortedValues[lower] + fraction * (sortedValues[upper] - sortedValues[lower]);
    }

    /**
     * Gets the tornado sensitivity data, sorted by swing (descending).
     *
     * @return list of tornado entries
     */
    public List<TornadoEntry> getTornado() {
      return tornado;
    }

    /**
     * Converts results to JSON format.
     *
     * @return JSON representation
     */
    public String toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("outputName", outputName);
      json.addProperty("totalIterations", totalIterations);
      json.addProperty("successCount", successCount);
      json.addProperty("failCount", failCount);
      json.addProperty("P10", getP10());
      json.addProperty("P50", getP50());
      json.addProperty("P90", getP90());
      json.addProperty("mean", getMean());
      json.addProperty("stdDev", getStdDev());

      // Input parameters
      JsonArray paramArray = new JsonArray();
      for (UncertainParameter p : parameters) {
        JsonObject pj = new JsonObject();
        pj.addProperty("name", p.name);
        pj.addProperty("low", p.low);
        pj.addProperty("mode", p.mode);
        pj.addProperty("high", p.high);
        pj.addProperty("distribution", p.distribution);
        paramArray.add(pj);
      }
      json.add("inputParameters", paramArray);

      // Tornado
      JsonArray tornadoArray = new JsonArray();
      for (TornadoEntry te : tornado) {
        JsonObject tj = new JsonObject();
        tj.addProperty("parameter", te.parameter);
        tj.addProperty("lowResult", te.lowResult);
        tj.addProperty("highResult", te.highResult);
        tj.addProperty("swing", te.swing);
        tornadoArray.add(tj);
      }
      json.add("tornado", tornadoArray);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(json);
    }
  }
}
