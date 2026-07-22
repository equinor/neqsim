package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.automation.AgenticProcessOptimizer;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Closed-loop optimizer for a two-stage utility-gas compression package (instrument air, fuel gas, or an export
 * booster).
 *
 * <p>
 * The class builds a real NeqSim flowsheet &mdash; feed stream &rarr; first-stage {@link Compressor} &rarr; intercooler
 * {@link Cooler} &rarr; second-stage {@link Compressor} &rarr; delivery &mdash; and drives the interstage pressure with
 * the agentic {@link AgenticProcessOptimizer} to minimize the total shaft power. With equal intercooling temperature
 * and equal stage efficiency, the analytic optimum interstage pressure is the geometric mean of the inlet and delivery
 * pressures, {@code sqrt(pIn * pOut)}; the optimizer rediscovers it from the live flowsheet without being told.
 * </p>
 *
 * <p>
 * This couples the deterministic utility-design package to the agentic optimization stack: the flowsheet is the
 * black-box reward model, the interstage pressure is the single decision variable, and the summed compressor power is
 * the cost. The result is schema-versioned and reproducible for a fixed seed.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * UtilityCompressionOptimizer opt = new UtilityCompressionOptimizer("Instrument Air");
 * opt.setInletPressureBara(5.0);
 * opt.setDeliveryPressureBara(60.0);
 * opt.optimize();
 * double pMid = opt.getOptimumInterstagePressureBara();
 * double power = opt.getMinTotalPowerKW();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class UtilityCompressionOptimizer implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(UtilityCompressionOptimizer.class);

  /** Address of the interstage-pressure decision variable. */
  private static final String INTERSTAGE_ADDRESS = "Stage 1 Compressor.outletPressure";

  /** Address of the first-stage compressor power read-back. */
  private static final String STAGE1_POWER_ADDRESS = "Stage 1 Compressor.power";

  /** Address of the second-stage compressor power read-back. */
  private static final String STAGE2_POWER_ADDRESS = "Stage 2 Compressor.power";

  /** Package name. */
  private String name = "Utility Compression";

  /** Optional user-supplied fluid; when null a default dry-air fluid is used. */
  private SystemInterface fluid = null;

  /** Compressor suction pressure [bara]. */
  private double inletPressureBara = 5.0;

  /** Delivery (final) pressure [bara]. */
  private double deliveryPressureBara = 60.0;

  /** Feed temperature [degC]. */
  private double inletTempC = 30.0;

  /** Intercooler outlet temperature [degC]. */
  private double intercoolerTempC = 30.0;

  /** Utility-gas mass flow [kg/hr]. */
  private double flowRateKgh = 5000.0;

  /** Random seed for the deterministic optimizer. */
  private long seed = 42L;

  /** Maximum optimizer evaluations. */
  private int maxEvaluations = 60;

  // --- results ---

  /** Optimized interstage pressure [bara]. */
  private double optimumInterstagePressureBara = Double.NaN;

  /** Minimum total shaft power at the optimum [kW]. */
  private double minTotalPowerKW = Double.NaN;

  /** Analytic geometric-mean interstage pressure [bara]. */
  private double geometricMeanPressureBara = Double.NaN;

  /** Number of optimizer evaluations performed. */
  private int evaluations = 0;

  /** Whether the optimization succeeded and produced a feasible point. */
  private boolean feasible = false;

  /** The raw optimizer result. */
  private transient AgenticProcessOptimizer.OptimizationResult result = null;

  /**
   * Creates an optimizer with the default name.
   */
  public UtilityCompressionOptimizer() {
  }

  /**
   * Creates an optimizer with the given name.
   *
   * @param name the package name; must not be null
   */
  public UtilityCompressionOptimizer(String name) {
    if (name != null) {
      this.name = name;
    }
  }

  /**
   * Sets the compressor suction pressure.
   *
   * @param inletPressureBara suction pressure [bara]; must be positive and below the delivery pressure
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code inletPressureBara} is not positive
   */
  public UtilityCompressionOptimizer setInletPressureBara(double inletPressureBara) {
    if (inletPressureBara <= 0.0) {
      throw new IllegalArgumentException("inletPressureBara must be positive");
    }
    this.inletPressureBara = inletPressureBara;
    return this;
  }

  /**
   * Sets the delivery (final) pressure.
   *
   * @param deliveryPressureBara delivery pressure [bara]; must be positive
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code deliveryPressureBara} is not positive
   */
  public UtilityCompressionOptimizer setDeliveryPressureBara(double deliveryPressureBara) {
    if (deliveryPressureBara <= 0.0) {
      throw new IllegalArgumentException("deliveryPressureBara must be positive");
    }
    this.deliveryPressureBara = deliveryPressureBara;
    return this;
  }

  /**
   * Sets the feed temperature.
   *
   * @param inletTempC feed temperature [degC]
   * @return this optimizer, for chaining
   */
  public UtilityCompressionOptimizer setInletTempC(double inletTempC) {
    this.inletTempC = inletTempC;
    return this;
  }

  /**
   * Sets the intercooler outlet temperature.
   *
   * @param intercoolerTempC intercooler outlet temperature [degC]
   * @return this optimizer, for chaining
   */
  public UtilityCompressionOptimizer setIntercoolerTempC(double intercoolerTempC) {
    this.intercoolerTempC = intercoolerTempC;
    return this;
  }

  /**
   * Sets the utility-gas mass flow.
   *
   * @param flowRateKgh mass flow [kg/hr]; must be positive
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code flowRateKgh} is not positive
   */
  public UtilityCompressionOptimizer setFlowRateKgh(double flowRateKgh) {
    if (flowRateKgh <= 0.0) {
      throw new IllegalArgumentException("flowRateKgh must be positive");
    }
    this.flowRateKgh = flowRateKgh;
    return this;
  }

  /**
   * Supplies a custom utility-gas fluid; when not set a default dry-air fluid is used. The fluid is cloned internally.
   *
   * @param fluid the fluid to compress, or null to use the default dry-air fluid
   * @return this optimizer, for chaining
   */
  public UtilityCompressionOptimizer setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Sets the deterministic optimizer seed.
   *
   * @param seed the random seed
   * @return this optimizer, for chaining
   */
  public UtilityCompressionOptimizer setSeed(long seed) {
    this.seed = seed;
    return this;
  }

  /**
   * Sets the optimizer evaluation budget.
   *
   * @param maxEvaluations maximum number of evaluations; must be at least 2
   * @return this optimizer, for chaining
   * @throws IllegalArgumentException if {@code maxEvaluations < 2}
   */
  public UtilityCompressionOptimizer setMaxEvaluations(int maxEvaluations) {
    if (maxEvaluations < 2) {
      throw new IllegalArgumentException("maxEvaluations must be at least 2");
    }
    this.maxEvaluations = maxEvaluations;
    return this;
  }

  /**
   * Builds the two-stage compression flowsheet and runs the agentic optimizer to minimize total shaft power over the
   * interstage pressure.
   *
   * @return this optimizer, with results populated
   * @throws IllegalStateException if the inlet pressure is not below the delivery pressure
   */
  public UtilityCompressionOptimizer optimize() {
    if (inletPressureBara >= deliveryPressureBara) {
      throw new IllegalStateException("inletPressureBara (" + inletPressureBara
          + ") must be below deliveryPressureBara (" + deliveryPressureBara + ")");
    }

    geometricMeanPressureBara = Math.sqrt(inletPressureBara * deliveryPressureBara);

    SystemInterface workingFluid = fluid != null ? fluid.clone() : defaultAirFluid();

    Stream feed = new Stream("feed", workingFluid);
    feed.setFlowRate(flowRateKgh, "kg/hr");
    feed.setTemperature(inletTempC, "C");
    feed.setPressure(inletPressureBara, "bara");

    Compressor stage1 = new Compressor("Stage 1 Compressor", feed);
    stage1.setOutletPressure(geometricMeanPressureBara);

    Cooler intercooler = new Cooler("Intercooler", stage1.getOutletStream());
    intercooler.setOutletTemperature(intercoolerTempC, "C");

    Compressor stage2 = new Compressor("Stage 2 Compressor", intercooler.getOutletStream());
    stage2.setOutletPressure(deliveryPressureBara);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(stage1);
    process.add(intercooler);
    process.add(stage2);
    process.run();

    ProcessAutomation automation = process.getAutomation();
    AgenticProcessOptimizer optimizer = automation.newOptimizer();
    double lowerBound = inletPressureBara * 1.05;
    double upperBound = deliveryPressureBara * 0.95;
    optimizer.addVariable(INTERSTAGE_ADDRESS, lowerBound, upperBound, "bara");
    optimizer.addWatch(STAGE1_POWER_ADDRESS, "kW");
    optimizer.addWatch(STAGE2_POWER_ADDRESS, "kW");
    optimizer.setObjectiveFunction(new TotalPowerObjective());
    optimizer.setSeed(seed).setMaxEvaluations(maxEvaluations);

    result = optimizer.optimize();
    evaluations = result.getEvaluations();
    feasible = result.isFeasible();
    if (result.getBestSetpoints() != null && result.getBestSetpoints().containsKey(INTERSTAGE_ADDRESS)) {
      optimumInterstagePressureBara = result.getBestSetpoints().get(INTERSTAGE_ADDRESS).doubleValue();
    }
    minTotalPowerKW = result.getBestObjective();

    logger.info("UtilityCompressionOptimizer '{}' optimum interstage {} bara, total power {} kW (geometric mean {})",
        name, optimumInterstagePressureBara, minTotalPowerKW, geometricMeanPressureBara);
    return this;
  }

  /**
   * Builds the default dry-air working fluid (79 mol% N2, 21 mol% O2).
   *
   * @return a configured SRK air fluid
   */
  private SystemInterface defaultAirFluid() {
    SystemInterface air = new SystemSrkEos(273.15 + inletTempC, inletPressureBara);
    air.addComponent("nitrogen", 0.79);
    air.addComponent("oxygen", 0.21);
    air.setMixingRule("classic");
    return air;
  }

  /**
   * Returns the optimized interstage pressure.
   *
   * @return interstage pressure [bara]
   */
  public double getOptimumInterstagePressureBara() {
    return optimumInterstagePressureBara;
  }

  /**
   * Returns the minimum total shaft power found.
   *
   * @return total power [kW]
   */
  public double getMinTotalPowerKW() {
    return minTotalPowerKW;
  }

  /**
   * Returns the analytic geometric-mean interstage pressure {@code sqrt(pIn * pOut)}.
   *
   * @return geometric-mean pressure [bara]
   */
  public double getGeometricMeanPressureBara() {
    return geometricMeanPressureBara;
  }

  /**
   * Returns the number of optimizer evaluations performed.
   *
   * @return evaluation count
   */
  public int getEvaluations() {
    return evaluations;
  }

  /**
   * Returns whether the optimization produced a feasible point.
   *
   * @return true if feasible
   */
  public boolean isFeasible() {
    return feasible;
  }

  /**
   * Returns the underlying optimizer result, or null if {@link #optimize()} has not been called.
   *
   * @return the optimization result
   */
  public AgenticProcessOptimizer.OptimizationResult getResult() {
    return result;
  }

  /**
   * Returns the results as an ordered map.
   *
   * @return a map of result fields
   */
  public Map<String, Object> toResultsMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", SCHEMA_VERSION);
    map.put("name", name);
    map.put("inletPressureBara", inletPressureBara);
    map.put("deliveryPressureBara", deliveryPressureBara);
    map.put("flowRateKgh", flowRateKgh);
    map.put("optimumInterstagePressureBara", optimumInterstagePressureBara);
    map.put("geometricMeanPressureBara", geometricMeanPressureBara);
    map.put("minTotalPowerKW", minTotalPowerKW);
    map.put("evaluations", evaluations);
    map.put("feasible", feasible);
    return map;
  }

  /**
   * Serializes the results to pretty-printed JSON.
   *
   * @return the JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toResultsMap());
  }

  /**
   * Custom optimizer objective: the summed first- and second-stage compressor power. Missing read-backs degrade
   * gracefully to a large finite cost so the optimizer never crashes.
   */
  private static final class TotalPowerObjective implements Function<Map<String, Double>, Double>, Serializable {
    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** Fallback cost when a power read-back is unavailable. */
    private static final double FALLBACK_COST = 1.0e8;

    /**
     * Computes the total compression power from the read-back map.
     *
     * @param readMap the address &rarr; value read-back map
     * @return the summed stage power, or a large fallback cost if a read-back is missing
     */
    @Override
    public Double apply(Map<String, Double> readMap) {
      Double p1 = readMap.get(STAGE1_POWER_ADDRESS);
      Double p2 = readMap.get(STAGE2_POWER_ADDRESS);
      if (p1 == null || p2 == null) {
        return Double.valueOf(FALLBACK_COST);
      }
      return Double.valueOf(p1.doubleValue() + p2.doubleValue());
    }
  }
}
