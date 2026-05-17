package neqsim.process.chemistry.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Monte Carlo + tornado uncertainty analyser tailored to chemistry-package models.
 *
 * <p>
 * Use it to sample MIC, scavenger stoichiometry, kinetic-order, base-corrosion-rate, etc., and
 * report the P10/P50/P90 of an arbitrary scalar output, plus a tornado-style sensitivity ranking.
 * The user provides:
 * </p>
 * <ol>
 * <li>an ordered list of {@code UncertainParameter} entries (name, distribution sampler, low/base/
 * high for tornado);</li>
 * <li>a model function {@code f(double[] sample) -&gt; double} which receives the sampled values in
 * the same order they were registered and returns the scalar of interest;</li>
 * <li>the number of Monte Carlo trials (default 1000).</li>
 * </ol>
 *
 * <p>
 * The class is deliberately framework-light (no NeqSim {@code MonteCarloSimulator} coupling) so it
 * can wrap any {@code evaluate()/get*()} pair from this package without change.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemistryUncertaintyAnalyzer implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * One uncertain input parameter.
   */
  public static class UncertainParameter implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Parameter display name. */
    private final String name;

    /** Random sampler — returns one realisation per call. */
    private final transient DoubleSupplier sampler;

    /** Low value for tornado. */
    private final double low;

    /** Base value for tornado. */
    private final double base;

    /** High value for tornado. */
    private final double high;

    /**
     * Builds an uncertain parameter.
     *
     * @param name display name
     * @param sampler random sampler
     * @param low low value (tornado)
     * @param base base value (tornado)
     * @param high high value (tornado)
     */
    public UncertainParameter(String name, DoubleSupplier sampler, double low, double base,
        double high) {
      this.name = name;
      this.sampler = sampler;
      this.low = low;
      this.base = base;
      this.high = high;
    }

    /**
     * Builds an uncertain parameter from a triangular distribution defined by low/base/high.
     *
     * @param name display name
     * @param low low value
     * @param base base value
     * @param high high value
     * @param random random source
     * @return parameter
     */
    public static UncertainParameter triangular(String name, double low, double base, double high,
        Random random) {
      DoubleSupplier sampler = new DoubleSupplier() {
        @Override
        public double getAsDouble() {
          double u = random.nextDouble();
          double f = (base - low) / (high - low);
          if (u < f) {
            return low + Math.sqrt(u * (high - low) * (base - low));
          }
          return high - Math.sqrt((1.0 - u) * (high - low) * (high - base));
        }
      };
      return new UncertainParameter(name, sampler, low, base, high);
    }

    /**
     * Returns the parameter name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Samples one realisation from the distribution.
     *
     * @return sample
     */
    public double sample() {
      return sampler.getAsDouble();
    }

    /**
     * Returns the low value.
     *
     * @return low
     */
    public double getLow() {
      return low;
    }

    /**
     * Returns the base value.
     *
     * @return base
     */
    public double getBase() {
      return base;
    }

    /**
     * Returns the high value.
     *
     * @return high
     */
    public double getHigh() {
      return high;
    }
  }

  // ─── Inputs ─────────────────────────────────────────────

  private final List<UncertainParameter> parameters = new ArrayList<UncertainParameter>();

  private int numberOfTrials = 1000;

  private long randomSeed = 1234L;

  // ─── Outputs ────────────────────────────────────────────

  private double p10;

  private double p50;

  private double p90;

  private double mean;

  private double std;

  private final List<Map<String, Object>> tornado = new ArrayList<Map<String, Object>>();

  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public ChemistryUncertaintyAnalyzer() {}

  /**
   * Adds an uncertain parameter.
   *
   * @param parameter parameter
   */
  public void addParameter(UncertainParameter parameter) {
    parameters.add(parameter);
  }

  /**
   * Sets the number of Monte Carlo trials.
   *
   * @param n trials
   */
  public void setNumberOfTrials(int n) {
    this.numberOfTrials = n;
  }

  /**
   * Sets the random seed.
   *
   * @param seed random seed
   */
  public void setRandomSeed(long seed) {
    this.randomSeed = seed;
  }

  /**
   * Runs the Monte Carlo and tornado analysis.
   *
   * @param model function that takes the sampled vector and returns the scalar of interest
   */
  public void run(java.util.function.ToDoubleFunction<double[]> model) {
    // Monte Carlo
    double[] outputs = new double[numberOfTrials];
    double[] sample = new double[parameters.size()];
    for (int i = 0; i < numberOfTrials; i++) {
      for (int j = 0; j < parameters.size(); j++) {
        sample[j] = parameters.get(j).sample();
      }
      outputs[i] = model.applyAsDouble(sample);
    }
    double[] sorted = outputs.clone();
    Arrays.sort(sorted);
    p10 = percentile(sorted, 0.10);
    p50 = percentile(sorted, 0.50);
    p90 = percentile(sorted, 0.90);
    double sum = 0.0;
    for (double v : outputs) {
      sum += v;
    }
    mean = sum / outputs.length;
    double sq = 0.0;
    for (double v : outputs) {
      sq += (v - mean) * (v - mean);
    }
    std = Math.sqrt(sq / Math.max(1, outputs.length - 1));

    // Tornado: hold others at base, vary one to low/high
    tornado.clear();
    double[] base = new double[parameters.size()];
    for (int j = 0; j < parameters.size(); j++) {
      base[j] = parameters.get(j).getBase();
    }
    double baseOutput = model.applyAsDouble(base);
    List<Map<String, Object>> tmp = new ArrayList<Map<String, Object>>();
    for (int j = 0; j < parameters.size(); j++) {
      double[] lo = base.clone();
      double[] hi = base.clone();
      lo[j] = parameters.get(j).getLow();
      hi[j] = parameters.get(j).getHigh();
      double outLo = model.applyAsDouble(lo);
      double outHi = model.applyAsDouble(hi);
      double swing = Math.abs(outHi - outLo);
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("parameter", parameters.get(j).getName());
      entry.put("low", parameters.get(j).getLow());
      entry.put("high", parameters.get(j).getHigh());
      entry.put("outputAtLow", outLo);
      entry.put("outputAtHigh", outHi);
      entry.put("swing", swing);
      tmp.add(entry);
    }
    Collections.sort(tmp, new java.util.Comparator<Map<String, Object>>() {
      @Override
      public int compare(Map<String, Object> a, Map<String, Object> b) {
        return Double.compare((Double) b.get("swing"), (Double) a.get("swing"));
      }
    });
    tornado.addAll(tmp);
    // Record base output too
    Map<String, Object> baseEntry = new LinkedHashMap<String, Object>();
    baseEntry.put("baseOutput", baseOutput);
    tornado.add(0, baseEntry);
    evaluated = true;
  }

  /**
   * Convenience: scalar map output that picks one key from the model's result map.
   *
   * @param outputKey key to extract
   * @param mapModel function returning a Map per sample
   * @return tornado-ready scalar function
   */
  public static java.util.function.ToDoubleFunction<double[]> mapOutput(final String outputKey,
      final java.util.function.Function<double[], Map<String, Object>> mapModel) {
    return new java.util.function.ToDoubleFunction<double[]>() {
      @Override
      public double applyAsDouble(double[] x) {
        Map<String, Object> m = mapModel.apply(x);
        Object v = m.get(outputKey);
        if (v == null) {
          return Double.NaN;
        }
        return ((Number) v).doubleValue();
      }
    };
  }

  /**
   * Linear-interpolation percentile of a sorted array.
   *
   * @param sorted sorted array
   * @param frac percentile fraction (0-1)
   * @return value
   */
  private static double percentile(double[] sorted, double frac) {
    if (sorted.length == 0) {
      return Double.NaN;
    }
    double idx = frac * (sorted.length - 1);
    int lo = (int) Math.floor(idx);
    int hi = (int) Math.ceil(idx);
    if (lo == hi) {
      return sorted[lo];
    }
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
  }

  /**
   * Returns P10.
   *
   * @return p10
   */
  public double getP10() {
    return p10;
  }

  /**
   * Returns P50.
   *
   * @return p50
   */
  public double getP50() {
    return p50;
  }

  /**
   * Returns P90.
   *
   * @return p90
   */
  public double getP90() {
    return p90;
  }

  /**
   * Returns mean.
   *
   * @return mean
   */
  public double getMean() {
    return mean;
  }

  /**
   * Returns standard deviation.
   *
   * @return std
   */
  public double getStd() {
    return std;
  }

  /**
   * Returns the tornado entries.
   *
   * @return list of tornado rows
   */
  public List<Map<String, Object>> getTornado() {
    return new ArrayList<Map<String, Object>>(tornado);
  }

  /**
   * Returns whether run() has been called.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns the structured result map.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("numberOfTrials", numberOfTrials);
    map.put("randomSeed", randomSeed);
    map.put("p10", p10);
    map.put("p50", p50);
    map.put("p90", p90);
    map.put("mean", mean);
    map.put("std", std);
    map.put("tornado", tornado);
    return map;
  }

  /**
   * Returns the result as JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    return gson.toJson(toMap());
  }

  /**
   * Helper to build a triangular sampler with this analyser's seeded random.
   *
   * @param name parameter name
   * @param low low
   * @param base base
   * @param high high
   * @return uncertain parameter
   */
  public UncertainParameter triangular(String name, double low, double base, double high) {
    return UncertainParameter.triangular(name, low, base, high, new Random(randomSeed++));
  }

  /**
   * Returns the registered parameter list.
   *
   * @return list (copy) of parameters
   */
  public List<UncertainParameter> getParameters() {
    return new ArrayList<UncertainParameter>(parameters);
  }

  /**
   * Convenience overload: identity model (sum of inputs). For tests only.
   *
   * @return identity unary operator
   */
  public static DoubleUnaryOperator identity() {
    return new DoubleUnaryOperator() {
      @Override
      public double applyAsDouble(double x) {
        return x;
      }
    };
  }
}
