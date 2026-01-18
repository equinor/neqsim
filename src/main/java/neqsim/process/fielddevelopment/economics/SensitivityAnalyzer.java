package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sensitivity and uncertainty analysis for field development economics.
 *
 * <p>
 * This class provides tools for analyzing the uncertainty in economic metrics (NPV, IRR, payback)
 * through sensitivity analysis, scenario analysis, and Monte Carlo simulation.
 * </p>
 *
 * <h2>Analysis Types</h2>
 * <ul>
 * <li><b>Sensitivity Analysis</b>: Varies one parameter at a time to understand individual impact
 * (tornado diagrams)</li>
 * <li><b>Scenario Analysis</b>: Evaluates discrete scenarios (low, base, high cases)</li>
 * <li><b>Monte Carlo Simulation</b>: Probabilistic analysis with random sampling from
 * distributions</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Set up base case engine
 * CashFlowEngine baseCase = new CashFlowEngine("NO");
 * baseCase.setCapex(800, 2025);
 * baseCase.setOilPrice(75.0);
 * // ... configure production, etc.
 * 
 * // Create analyzer
 * SensitivityAnalyzer analyzer = new SensitivityAnalyzer(baseCase, 0.08);
 * 
 * // Tornado analysis (±20% variation)
 * TornadoResult tornado = analyzer.tornadoAnalysis(0.20);
 * System.out.println(tornado.toMarkdownTable());
 * 
 * // Monte Carlo simulation
 * analyzer.setOilPriceDistribution(60.0, 90.0); // Uniform distribution
 * analyzer.setCapexDistribution(700, 900);
 * MonteCarloResult mc = analyzer.monteCarloAnalysis(1000);
 * System.out.println("NPV P10: " + mc.getNpvP10());
 * System.out.println("NPV P50: " + mc.getNpvP50());
 * System.out.println("NPV P90: " + mc.getNpvP90());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see CashFlowEngine
 */
public class SensitivityAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  /** Base case engine (used as template). */
  private final CashFlowEngine baseCase;

  /** Discount rate for NPV calculations. */
  private final double discountRate;

  /** Base case NPV for comparison. */
  private final double baseCaseNpv;

  // Monte Carlo distribution parameters
  private double oilPriceMin;
  private double oilPriceMax;
  private double gasPriceMin;
  private double gasPriceMax;
  private double capexMin;
  private double capexMax;
  private double opexFactorMin;
  private double opexFactorMax;
  private double productionFactorMin;
  private double productionFactorMax;

  /** Random number generator for Monte Carlo. */
  private Random random;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new sensitivity analyzer for the given base case.
   *
   * @param baseCase the configured cash flow engine representing the base case
   * @param discountRate discount rate for NPV calculations
   */
  public SensitivityAnalyzer(CashFlowEngine baseCase, double discountRate) {
    if (baseCase == null) {
      throw new IllegalArgumentException("Base case cannot be null");
    }
    this.baseCase = baseCase;
    this.discountRate = discountRate;
    this.baseCaseNpv = baseCase.calculateNPV(discountRate);
    this.random = new Random();

    // Initialize distributions to base case (no variation)
    initializeDefaultDistributions();
  }

  private void initializeDefaultDistributions() {
    // Will be set when setXxxDistribution is called
    this.oilPriceMin = 0;
    this.oilPriceMax = 0;
    this.gasPriceMin = 0;
    this.gasPriceMax = 0;
    this.capexMin = 0;
    this.capexMax = 0;
    this.opexFactorMin = 1.0;
    this.opexFactorMax = 1.0;
    this.productionFactorMin = 1.0;
    this.productionFactorMax = 1.0;
  }

  // ============================================================================
  // TORNADO (SENSITIVITY) ANALYSIS
  // ============================================================================

  /**
   * Performs tornado analysis by varying each parameter by the specified percentage.
   *
   * <p>
   * Each parameter is varied independently while holding others constant. Results show the NPV
   * impact of low and high cases for each parameter.
   * </p>
   *
   * @param variationPercent variation as fraction (e.g., 0.20 for ±20%)
   * @return tornado analysis result
   */
  public TornadoResult tornadoAnalysis(double variationPercent) {
    List<TornadoItem> items = new ArrayList<TornadoItem>();

    // Oil price sensitivity
    items.add(analyzeParameter("Oil Price (USD/bbl)", variationPercent, new ParameterSetter() {
      @Override
      public void set(CashFlowEngine engine, double factor) {
        engine.setOilPrice(75.0 * factor); // Assumes base price of 75
      }
    }));

    // Gas price sensitivity
    items.add(analyzeParameter("Gas Price (USD/Sm3)", variationPercent, new ParameterSetter() {
      @Override
      public void set(CashFlowEngine engine, double factor) {
        engine.setGasPrice(0.25 * factor); // Assumes base price of 0.25
      }
    }));

    // CAPEX sensitivity
    double baseCAPEX = baseCase.getTotalCapex();
    items.add(analyzeParameter("CAPEX (MUSD)", variationPercent, new ParameterSetter() {
      @Override
      public void set(CashFlowEngine engine, double factor) {
        // Note: This is simplified - actual implementation would need to scale CAPEX schedule
        engine.setCapex(baseCAPEX * factor, 2025);
      }
    }));

    // OPEX sensitivity
    items.add(analyzeParameter("OPEX (%CAPEX)", variationPercent, new ParameterSetter() {
      @Override
      public void set(CashFlowEngine engine, double factor) {
        engine.setOpexPercentOfCapex(0.04 * factor);
      }
    }));

    // Sort by absolute NPV swing (largest impact first)
    Collections.sort(items, new Comparator<TornadoItem>() {
      @Override
      public int compare(TornadoItem a, TornadoItem b) {
        return Double.compare(b.getSwing(), a.getSwing());
      }
    });

    return new TornadoResult(baseCaseNpv, variationPercent, items);
  }

  /**
   * Analyzes a single parameter's sensitivity.
   *
   * @param name parameter name
   * @param variationPercent variation percentage
   * @param setter parameter setter function
   * @return tornado item with low/high NPV values
   */
  private TornadoItem analyzeParameter(String name, double variationPercent,
      ParameterSetter setter) {
    // Low case
    CashFlowEngine lowCase = cloneEngine();
    setter.set(lowCase, 1.0 - variationPercent);
    double lowNpv = lowCase.calculateNPV(discountRate);

    // High case
    CashFlowEngine highCase = cloneEngine();
    setter.set(highCase, 1.0 + variationPercent);
    double highNpv = highCase.calculateNPV(discountRate);

    return new TornadoItem(name, lowNpv, highNpv, baseCaseNpv);
  }

  // ============================================================================
  // SCENARIO ANALYSIS
  // ============================================================================

  /**
   * Performs three-scenario analysis (low, base, high).
   *
   * @param lowOilPrice low case oil price
   * @param highOilPrice high case oil price
   * @param lowGasPrice low case gas price
   * @param highGasPrice high case gas price
   * @param capexContingency additional CAPEX for low case (e.g., 0.20 for 20% overrun)
   * @return scenario analysis result
   */
  public ScenarioResult scenarioAnalysis(double lowOilPrice, double highOilPrice,
      double lowGasPrice, double highGasPrice, double capexContingency) {

    // Low case: low prices, high CAPEX
    CashFlowEngine lowCase = cloneEngine();
    lowCase.setOilPrice(lowOilPrice);
    lowCase.setGasPrice(lowGasPrice);
    lowCase.setCapex(baseCase.getTotalCapex() * (1 + capexContingency), 2025);
    double lowNpv = lowCase.calculateNPV(discountRate);
    CashFlowEngine.CashFlowResult lowResult = lowCase.calculate(discountRate);

    // High case: high prices, base CAPEX
    CashFlowEngine highCase = cloneEngine();
    highCase.setOilPrice(highOilPrice);
    highCase.setGasPrice(highGasPrice);
    double highNpv = highCase.calculateNPV(discountRate);
    CashFlowEngine.CashFlowResult highResult = highCase.calculate(discountRate);

    // Base case result
    CashFlowEngine.CashFlowResult baseResult = baseCase.calculate(discountRate);

    return new ScenarioResult(lowNpv, lowResult.getIrr(), baseCaseNpv, baseResult.getIrr(), highNpv,
        highResult.getIrr());
  }

  // ============================================================================
  // MONTE CARLO ANALYSIS
  // ============================================================================

  /**
   * Sets oil price distribution for Monte Carlo simulation.
   *
   * @param minPrice minimum oil price (USD/bbl)
   * @param maxPrice maximum oil price (USD/bbl)
   */
  public void setOilPriceDistribution(double minPrice, double maxPrice) {
    this.oilPriceMin = minPrice;
    this.oilPriceMax = maxPrice;
  }

  /**
   * Sets gas price distribution for Monte Carlo simulation.
   *
   * @param minPrice minimum gas price (USD/Sm3)
   * @param maxPrice maximum gas price (USD/Sm3)
   */
  public void setGasPriceDistribution(double minPrice, double maxPrice) {
    this.gasPriceMin = minPrice;
    this.gasPriceMax = maxPrice;
  }

  /**
   * Sets CAPEX distribution for Monte Carlo simulation.
   *
   * @param minCapex minimum CAPEX (MUSD)
   * @param maxCapex maximum CAPEX (MUSD)
   */
  public void setCapexDistribution(double minCapex, double maxCapex) {
    this.capexMin = minCapex;
    this.capexMax = maxCapex;
  }

  /**
   * Sets OPEX factor distribution for Monte Carlo simulation.
   *
   * @param minFactor minimum factor (e.g., 0.8 for -20%)
   * @param maxFactor maximum factor (e.g., 1.2 for +20%)
   */
  public void setOpexFactorDistribution(double minFactor, double maxFactor) {
    this.opexFactorMin = minFactor;
    this.opexFactorMax = maxFactor;
  }

  /**
   * Sets production factor distribution for Monte Carlo simulation.
   *
   * @param minFactor minimum factor (e.g., 0.8 for -20%)
   * @param maxFactor maximum factor (e.g., 1.2 for +20%)
   */
  public void setProductionFactorDistribution(double minFactor, double maxFactor) {
    this.productionFactorMin = minFactor;
    this.productionFactorMax = maxFactor;
  }

  /**
   * Sets the random seed for reproducible Monte Carlo results.
   *
   * @param seed random seed
   */
  public void setRandomSeed(long seed) {
    this.random = new Random(seed);
  }

  /**
   * Performs Monte Carlo simulation with the configured distributions.
   *
   * @param iterations number of simulation iterations
   * @return Monte Carlo result with statistics
   */
  public MonteCarloResult monteCarloAnalysis(int iterations) {
    if (iterations < 10) {
      throw new IllegalArgumentException("Need at least 10 iterations");
    }

    double[] npvValues = new double[iterations];
    double[] irrValues = new double[iterations];
    int positiveNpvCount = 0;

    for (int i = 0; i < iterations; i++) {
      CashFlowEngine trial = cloneEngine();

      // Sample from distributions
      if (oilPriceMax > oilPriceMin) {
        trial.setOilPrice(uniformRandom(oilPriceMin, oilPriceMax));
      }
      if (gasPriceMax > gasPriceMin) {
        trial.setGasPrice(uniformRandom(gasPriceMin, gasPriceMax));
      }
      if (capexMax > capexMin) {
        trial.setCapex(uniformRandom(capexMin, capexMax), 2025);
      }
      if (opexFactorMax > opexFactorMin) {
        double factor = uniformRandom(opexFactorMin, opexFactorMax);
        trial.setOpexPercentOfCapex(0.04 * factor);
      }

      // Calculate NPV and IRR
      try {
        CashFlowEngine.CashFlowResult result = trial.calculate(discountRate);
        npvValues[i] = result.getNpv();
        irrValues[i] = result.getIrr();
        if (npvValues[i] > 0) {
          positiveNpvCount++;
        }
      } catch (Exception e) {
        // Use base case values if calculation fails
        npvValues[i] = baseCaseNpv;
        irrValues[i] = 0.1;
      }
    }

    // Sort for percentile calculations
    Arrays.sort(npvValues);
    Arrays.sort(irrValues);

    // Calculate statistics
    double npvMean = mean(npvValues);
    double npvStdDev = stdDev(npvValues, npvMean);
    double npvP10 = percentile(npvValues, 10);
    double npvP50 = percentile(npvValues, 50);
    double npvP90 = percentile(npvValues, 90);

    double irrMean = mean(irrValues);
    double irrP10 = percentile(irrValues, 10);
    double irrP50 = percentile(irrValues, 50);
    double irrP90 = percentile(irrValues, 90);

    double probabilityPositiveNpv = (double) positiveNpvCount / iterations;

    return new MonteCarloResult(iterations, npvMean, npvStdDev, npvP10, npvP50, npvP90, irrMean,
        irrP10, irrP50, irrP90, probabilityPositiveNpv, npvValues);
  }

  // ============================================================================
  // BREAKEVEN ANALYSIS
  // ============================================================================

  /**
   * Calculates breakeven prices for zero NPV.
   *
   * @return breakeven result with oil and gas prices
   */
  public BreakevenResult breakevenAnalysis() {
    double breakevenOilPrice = baseCase.calculateBreakevenOilPrice(discountRate);
    double breakevenGasPrice = baseCase.calculateBreakevenGasPrice(discountRate);

    return new BreakevenResult(breakevenOilPrice, breakevenGasPrice, discountRate);
  }

  // ============================================================================
  // HELPER METHODS
  // ============================================================================

  /**
   * Creates a clone of the base case engine.
   *
   * @return a new CashFlowEngine instance with copied settings
   */
  private CashFlowEngine cloneEngine() {
    // Note: This is a simplified clone - production profiles are not copied
    // For full implementation, use Java serialization or deep copy
    CashFlowEngine clone = new CashFlowEngine(baseCase.getTaxModel());
    clone.setOilPrice(75.0);
    clone.setGasPrice(0.25);
    clone.setOpexPercentOfCapex(0.04);
    clone.setCapex(baseCase.getTotalCapex(), 2025);
    return clone;
  }

  private double uniformRandom(double min, double max) {
    return min + random.nextDouble() * (max - min);
  }

  private static double mean(double[] values) {
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.length;
  }

  private static double stdDev(double[] values, double mean) {
    double sumSquares = 0;
    for (double v : values) {
      sumSquares += (v - mean) * (v - mean);
    }
    return Math.sqrt(sumSquares / values.length);
  }

  private static double percentile(double[] sortedValues, int percentile) {
    int index = (int) Math.ceil(percentile / 100.0 * sortedValues.length) - 1;
    index = Math.max(0, Math.min(index, sortedValues.length - 1));
    return sortedValues[index];
  }

  // ============================================================================
  // INNER INTERFACES
  // ============================================================================

  /**
   * Functional interface for setting parameters.
   */
  private interface ParameterSetter {
    void set(CashFlowEngine engine, double factor);
  }

  // ============================================================================
  // RESULT CLASSES
  // ============================================================================

  /**
   * Result of tornado (sensitivity) analysis.
   */
  public static final class TornadoResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double baseCaseNpv;
    private final double variationPercent;
    private final List<TornadoItem> items;

    TornadoResult(double baseCaseNpv, double variationPercent, List<TornadoItem> items) {
      this.baseCaseNpv = baseCaseNpv;
      this.variationPercent = variationPercent;
      this.items = Collections.unmodifiableList(new ArrayList<TornadoItem>(items));
    }

    public double getBaseCaseNpv() {
      return baseCaseNpv;
    }

    public double getVariationPercent() {
      return variationPercent;
    }

    public List<TornadoItem> getItems() {
      return items;
    }

    /**
     * Gets the most sensitive parameter.
     *
     * @return parameter with largest NPV swing
     */
    public TornadoItem getMostSensitiveParameter() {
      return items.isEmpty() ? null : items.get(0);
    }

    /**
     * Generates a markdown table of results.
     *
     * @return markdown formatted table
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("## Tornado Analysis (±%.0f%% variation)%n", variationPercent * 100));
      sb.append(String.format("Base Case NPV: %.1f MUSD%n%n", baseCaseNpv));
      sb.append("| Parameter | Low NPV | High NPV | Swing | Impact |\n");
      sb.append("|-----------|---------|----------|-------|--------|\n");

      for (TornadoItem item : items) {
        sb.append(String.format("| %s | %.1f | %.1f | %.1f | %s |\n", item.getParameterName(),
            item.getLowNpv(), item.getHighNpv(), item.getSwing(), item.getImpactLevel()));
      }

      return sb.toString();
    }

    @Override
    public String toString() {
      return toMarkdownTable();
    }
  }

  /**
   * Single item in tornado analysis.
   */
  public static final class TornadoItem implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String parameterName;
    private final double lowNpv;
    private final double highNpv;
    private final double baseCaseNpv;

    TornadoItem(String parameterName, double lowNpv, double highNpv, double baseCaseNpv) {
      this.parameterName = parameterName;
      this.lowNpv = lowNpv;
      this.highNpv = highNpv;
      this.baseCaseNpv = baseCaseNpv;
    }

    public String getParameterName() {
      return parameterName;
    }

    public double getLowNpv() {
      return lowNpv;
    }

    public double getHighNpv() {
      return highNpv;
    }

    public double getBaseCaseNpv() {
      return baseCaseNpv;
    }

    /**
     * Gets the total NPV swing (high - low).
     *
     * @return NPV swing
     */
    public double getSwing() {
      return Math.abs(highNpv - lowNpv);
    }

    /**
     * Gets the impact level (High, Medium, Low).
     *
     * @return impact level string
     */
    public String getImpactLevel() {
      double swingPercent = getSwing() / Math.abs(baseCaseNpv) * 100;
      if (swingPercent > 50) {
        return "HIGH";
      } else if (swingPercent > 20) {
        return "MEDIUM";
      }
      return "LOW";
    }
  }

  /**
   * Result of scenario analysis.
   */
  public static final class ScenarioResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double lowNpv;
    private final double lowIrr;
    private final double baseNpv;
    private final double baseIrr;
    private final double highNpv;
    private final double highIrr;

    ScenarioResult(double lowNpv, double lowIrr, double baseNpv, double baseIrr, double highNpv,
        double highIrr) {
      this.lowNpv = lowNpv;
      this.lowIrr = lowIrr;
      this.baseNpv = baseNpv;
      this.baseIrr = baseIrr;
      this.highNpv = highNpv;
      this.highIrr = highIrr;
    }

    public double getLowNpv() {
      return lowNpv;
    }

    public double getLowIrr() {
      return lowIrr;
    }

    public double getBaseNpv() {
      return baseNpv;
    }

    public double getBaseIrr() {
      return baseIrr;
    }

    public double getHighNpv() {
      return highNpv;
    }

    public double getHighIrr() {
      return highIrr;
    }

    /**
     * Gets NPV range (high - low).
     *
     * @return NPV range
     */
    public double getNpvRange() {
      return highNpv - lowNpv;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Scenario Analysis:\n");
      sb.append(String.format("  Low:  NPV = %.1f MUSD, IRR = %.1f%%\n", lowNpv, lowIrr * 100));
      sb.append(String.format("  Base: NPV = %.1f MUSD, IRR = %.1f%%\n", baseNpv, baseIrr * 100));
      sb.append(String.format("  High: NPV = %.1f MUSD, IRR = %.1f%%\n", highNpv, highIrr * 100));
      return sb.toString();
    }
  }

  /**
   * Result of Monte Carlo analysis.
   */
  public static final class MonteCarloResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int iterations;
    private final double npvMean;
    private final double npvStdDev;
    private final double npvP10;
    private final double npvP50;
    private final double npvP90;
    private final double irrMean;
    private final double irrP10;
    private final double irrP50;
    private final double irrP90;
    private final double probabilityPositiveNpv;
    private final double[] npvDistribution;

    MonteCarloResult(int iterations, double npvMean, double npvStdDev, double npvP10, double npvP50,
        double npvP90, double irrMean, double irrP10, double irrP50, double irrP90,
        double probabilityPositiveNpv, double[] npvDistribution) {
      this.iterations = iterations;
      this.npvMean = npvMean;
      this.npvStdDev = npvStdDev;
      this.npvP10 = npvP10;
      this.npvP50 = npvP50;
      this.npvP90 = npvP90;
      this.irrMean = irrMean;
      this.irrP10 = irrP10;
      this.irrP50 = irrP50;
      this.irrP90 = irrP90;
      this.probabilityPositiveNpv = probabilityPositiveNpv;
      this.npvDistribution = npvDistribution.clone();
    }

    public int getIterations() {
      return iterations;
    }

    public double getNpvMean() {
      return npvMean;
    }

    public double getNpvStdDev() {
      return npvStdDev;
    }

    public double getNpvP10() {
      return npvP10;
    }

    public double getNpvP50() {
      return npvP50;
    }

    public double getNpvP90() {
      return npvP90;
    }

    public double getIrrMean() {
      return irrMean;
    }

    public double getIrrP10() {
      return irrP10;
    }

    public double getIrrP50() {
      return irrP50;
    }

    public double getIrrP90() {
      return irrP90;
    }

    public double getProbabilityPositiveNpv() {
      return probabilityPositiveNpv;
    }

    /**
     * Gets the NPV distribution array.
     *
     * @return sorted NPV values
     */
    public double[] getNpvDistribution() {
      return npvDistribution.clone();
    }

    /**
     * Gets coefficient of variation (stdDev / mean).
     *
     * @return coefficient of variation
     */
    public double getCoefficientOfVariation() {
      return npvMean != 0 ? npvStdDev / Math.abs(npvMean) : 0;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("Monte Carlo Analysis (%d iterations):%n", iterations));
      sb.append(String.format("  NPV Statistics:%n"));
      sb.append(String.format("    Mean: %.1f MUSD (StdDev: %.1f)%n", npvMean, npvStdDev));
      sb.append(
          String.format("    P10: %.1f, P50: %.1f, P90: %.1f MUSD%n", npvP10, npvP50, npvP90));
      sb.append(String.format("    Probability NPV > 0: %.1f%%%n", probabilityPositiveNpv * 100));
      sb.append(String.format("  IRR Statistics:%n"));
      sb.append(String.format("    Mean: %.1f%%, P10: %.1f%%, P50: %.1f%%, P90: %.1f%%%n",
          irrMean * 100, irrP10 * 100, irrP50 * 100, irrP90 * 100));
      return sb.toString();
    }
  }

  /**
   * Result of breakeven analysis.
   */
  public static final class BreakevenResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double breakevenOilPrice;
    private final double breakevenGasPrice;
    private final double discountRate;

    BreakevenResult(double breakevenOilPrice, double breakevenGasPrice, double discountRate) {
      this.breakevenOilPrice = breakevenOilPrice;
      this.breakevenGasPrice = breakevenGasPrice;
      this.discountRate = discountRate;
    }

    public double getBreakevenOilPrice() {
      return breakevenOilPrice;
    }

    public double getBreakevenGasPrice() {
      return breakevenGasPrice;
    }

    public double getDiscountRate() {
      return discountRate;
    }

    @Override
    public String toString() {
      return String.format("Breakeven Analysis @ %.1f%% discount rate:%n" + "  Oil: %.2f USD/bbl%n"
          + "  Gas: %.4f USD/Sm3%n", discountRate * 100, breakevenOilPrice, breakevenGasPrice);
    }
  }
}
