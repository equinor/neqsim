package neqsim.process.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Published comparison points and range-based validation for LNG process simulations.
 *
 * <p>
 * The mixed-refrigerant reference points are from Pereira et al., Energy Conversion and Management 272 (2022) 116364,
 * doi:10.1016/j.enconman.2022.116364. Their common 20,000 kg/h Aspen HYSYS comparison reported 0.2561, 0.2548, and
 * 0.2456 kWh/kg LNG for optimized SMR, C3MR, and DMR cases. The nitrogen-expander point is based on the optimized
 * parallel nitrogen expansion case reported by He et al., Energy 167 (2019) 1-12, doi:10.1016/j.energy.2018.10.169.
 * </p>
 *
 * <p>
 * These values are comparison points, not universal acceptance limits. Feed composition, ambient temperature, pressure,
 * product flash specification, driver efficiency, and process complexity materially affect specific energy. The default
 * validation band is therefore deliberately wider than the optimization tolerance used in the source studies.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class LNGProcessBenchmark {
  /** Reference benchmark data indexed by process cycle. */
  private static final Map<LNGProcessCycle, Benchmark> BENCHMARKS = new EnumMap<LNGProcessCycle, Benchmark>(
      LNGProcessCycle.class);

  static {
    BENCHMARKS.put(LNGProcessCycle.SMR, new Benchmark(0.2561, 0.19, 0.43, -164.0, -157.0, 0.90,
        "Pereira et al. (2022), doi:10.1016/j.enconman.2022.116364"));
    BENCHMARKS.put(LNGProcessCycle.C3MR, new Benchmark(0.2548, 0.19, 0.40, -164.0, -157.0, 0.90,
        "Pereira et al. (2022), doi:10.1016/j.enconman.2022.116364"));
    BENCHMARKS.put(LNGProcessCycle.DMR, new Benchmark(0.2456, 0.18, 0.39, -164.0, -157.0, 0.90,
        "Pereira et al. (2022), doi:10.1016/j.enconman.2022.116364"));
    BENCHMARKS.put(LNGProcessCycle.NITROGEN_EXPANDER,
        new Benchmark(0.6180, 0.40, 0.80, -164.0, -157.0, 0.85, "He et al. (2019), doi:10.1016/j.energy.2018.10.169"));
  }

  /** Utility class. */
  private LNGProcessBenchmark() {
  }

  /**
   * Returns benchmark data for a cycle.
   *
   * @param cycle process cycle
   * @return immutable benchmark data
   */
  public static Benchmark get(LNGProcessCycle cycle) {
    if (cycle == null) {
      throw new IllegalArgumentException("cycle cannot be null");
    }
    return BENCHMARKS.get(cycle);
  }

  /**
   * Returns all benchmark definitions.
   *
   * @return unmodifiable benchmark map
   */
  public static Map<LNGProcessCycle, Benchmark> getAll() {
    return Collections.unmodifiableMap(BENCHMARKS);
  }

  /**
   * Compares a simulated process result with its literature envelope.
   *
   * @param cycle process cycle
   * @param result evaluated simulation result
   * @return assessment with individual checks and diagnostic messages
   */
  public static Assessment assess(LNGProcessCycle cycle, LNGProcessModel.Result result) {
    if (result == null) {
      throw new IllegalArgumentException("result cannot be null");
    }
    Benchmark benchmark = get(cycle);
    List<String> messages = new ArrayList<String>();

    double sec = result.getSpecificEnergyKWhPerKgLNG();
    boolean energyOk = Double.isFinite(sec) && sec >= benchmark.getMinimumSpecificEnergy()
        && sec <= benchmark.getMaximumSpecificEnergy();
    if (!energyOk) {
      messages.add("Specific energy " + sec + " kWh/kg LNG is outside the literature envelope "
          + benchmark.getMinimumSpecificEnergy() + "-" + benchmark.getMaximumSpecificEnergy());
    }

    double productTemperature = result.getProductTemperatureC();
    boolean temperatureOk = Double.isFinite(productTemperature)
        && productTemperature >= benchmark.getMinimumProductTemperatureC()
        && productTemperature <= benchmark.getMaximumProductTemperatureC();
    if (!temperatureOk) {
      messages.add("Product temperature " + productTemperature + " C is outside the atmospheric-LNG comparison range "
          + benchmark.getMinimumProductTemperatureC() + " to " + benchmark.getMaximumProductTemperatureC());
    }

    double yield = result.getLNGYield();
    boolean yieldOk = Double.isFinite(yield) && yield >= benchmark.getMinimumLNGYield() && yield <= 1.000001;
    if (!yieldOk) {
      messages.add("LNG yield " + yield + " is below the screening minimum " + benchmark.getMinimumLNGYield());
    }

    double mita = result.getMinimumInternalTemperatureApproachC();
    boolean mitaOk = !Double.isFinite(mita) || mita >= 0.0;
    if (!mitaOk) {
      messages.add("Negative minimum internal temperature approach indicates a temperature cross");
    }

    return new Assessment(energyOk, temperatureOk, yieldOk, mitaOk, sec - benchmark.getReferenceSpecificEnergy(),
        messages);
  }

  /**
   * Immutable benchmark definition.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class Benchmark implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double referenceSpecificEnergy;
    private final double minimumSpecificEnergy;
    private final double maximumSpecificEnergy;
    private final double minimumProductTemperatureC;
    private final double maximumProductTemperatureC;
    private final double minimumLNGYield;
    private final String source;

    /**
     * Creates a benchmark definition.
     *
     * @param referenceSpecificEnergy reference specific energy in kWh/kg LNG
     * @param minimumSpecificEnergy lower screening bound in kWh/kg LNG
     * @param maximumSpecificEnergy upper screening bound in kWh/kg LNG
     * @param minimumProductTemperatureC lower product temperature bound in Celsius
     * @param maximumProductTemperatureC upper product temperature bound in Celsius
     * @param minimumLNGYield minimum liquid mass yield
     * @param source literature source
     */
    private Benchmark(double referenceSpecificEnergy, double minimumSpecificEnergy, double maximumSpecificEnergy,
        double minimumProductTemperatureC, double maximumProductTemperatureC, double minimumLNGYield, String source) {
      this.referenceSpecificEnergy = referenceSpecificEnergy;
      this.minimumSpecificEnergy = minimumSpecificEnergy;
      this.maximumSpecificEnergy = maximumSpecificEnergy;
      this.minimumProductTemperatureC = minimumProductTemperatureC;
      this.maximumProductTemperatureC = maximumProductTemperatureC;
      this.minimumLNGYield = minimumLNGYield;
      this.source = source;
    }

    /** @return reference specific energy in kWh/kg LNG */
    public double getReferenceSpecificEnergy() {
      return referenceSpecificEnergy;
    }

    /** @return minimum screening value in kWh/kg LNG */
    public double getMinimumSpecificEnergy() {
      return minimumSpecificEnergy;
    }

    /** @return maximum screening value in kWh/kg LNG */
    public double getMaximumSpecificEnergy() {
      return maximumSpecificEnergy;
    }

    /** @return minimum product temperature in Celsius */
    public double getMinimumProductTemperatureC() {
      return minimumProductTemperatureC;
    }

    /** @return maximum product temperature in Celsius */
    public double getMaximumProductTemperatureC() {
      return maximumProductTemperatureC;
    }

    /** @return minimum liquid mass yield */
    public double getMinimumLNGYield() {
      return minimumLNGYield;
    }

    /** @return source citation */
    public String getSource() {
      return source;
    }
  }

  /**
   * Result of comparison against a benchmark envelope.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class Assessment implements Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean energyWithinRange;
    private final boolean productTemperatureWithinRange;
    private final boolean yieldWithinRange;
    private final boolean temperatureApproachValid;
    private final double specificEnergyDeviation;
    private final List<String> messages;

    /**
     * Creates an assessment.
     *
     * @param energyWithinRange energy check
     * @param productTemperatureWithinRange product-temperature check
     * @param yieldWithinRange liquid-yield check
     * @param temperatureApproachValid temperature-cross check
     * @param specificEnergyDeviation deviation from reference in kWh/kg LNG
     * @param messages diagnostic messages
     */
    private Assessment(boolean energyWithinRange, boolean productTemperatureWithinRange, boolean yieldWithinRange,
        boolean temperatureApproachValid, double specificEnergyDeviation, List<String> messages) {
      this.energyWithinRange = energyWithinRange;
      this.productTemperatureWithinRange = productTemperatureWithinRange;
      this.yieldWithinRange = yieldWithinRange;
      this.temperatureApproachValid = temperatureApproachValid;
      this.specificEnergyDeviation = specificEnergyDeviation;
      this.messages = Collections.unmodifiableList(new ArrayList<String>(messages));
    }

    /** @return true when every benchmark check passes */
    public boolean isWithinRange() {
      return energyWithinRange && productTemperatureWithinRange && yieldWithinRange && temperatureApproachValid;
    }

    /** @return true when specific energy is inside the screening envelope */
    public boolean isEnergyWithinRange() {
      return energyWithinRange;
    }

    /** @return true when product temperature is inside the screening envelope */
    public boolean isProductTemperatureWithinRange() {
      return productTemperatureWithinRange;
    }

    /** @return true when liquid yield meets the screening minimum */
    public boolean isYieldWithinRange() {
      return yieldWithinRange;
    }

    /** @return true when no negative exchanger approach was detected */
    public boolean isTemperatureApproachValid() {
      return temperatureApproachValid;
    }

    /** @return simulated minus reference specific energy in kWh/kg LNG */
    public double getSpecificEnergyDeviation() {
      return specificEnergyDeviation;
    }

    /** @return immutable diagnostic messages */
    public List<String> getMessages() {
      return messages;
    }
  }
}
