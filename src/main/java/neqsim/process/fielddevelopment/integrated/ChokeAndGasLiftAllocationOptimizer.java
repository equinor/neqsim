package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Co-optimises choke openings and lift-gas allocation across a gas-lifted well fleet under multiple shared facility
 * constraints (NIP-3).
 *
 * <p>
 * Existing NeqSim optimisers each solve part of this problem: {@link GasLiftNetworkOptimizer} allocates a lift-gas
 * budget by the equal-slope criterion, and {@code ReservoirToMarketOptimizer} tunes choke openings under a single
 * facility-throughput cap. A real "strupe/&oslash;ke" (choke-back / open-up) decision needs both at once, together with
 * discrete on/off locks and several simultaneous facility ceilings (gas handling, produced-water / re-injection, and
 * the lift-gas budget). This optimiser fills that gap.
 * </p>
 *
 * <h2>Method</h2>
 * <ol>
 * <li>Force-shut wells are excluded from production.</li>
 * <li>Lift gas is allocated among the open wells at full choke by the equal-slope criterion (reusing
 * {@link GasLiftNetworkOptimizer}), respecting the lift-gas budget.</li>
 * <li>Each open well is opened to its maximum allowed choke, then choked back if its own produced-gas ceiling would be
 * exceeded.</li>
 * <li>While a shared facility constraint (total gas or total produced water) is violated, the well that relieves the
 * binding constraint with the least oil sacrifice is choked back one step at a time - the highest-GOR well for a gas
 * constraint, the highest-water-cut well for a water constraint. This is the greedy equivalent of the operator ordering
 * a strupe list from the least valuable barrels down.</li>
 * </ol>
 *
 * <p>
 * The search is deterministic, bounded, and never throws; the result is exposed as a schema-versioned JSON object so it
 * is safe to drive from an agentic loop. It is a screening-grade allocator, not a rigorous coupled network solve.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see ChokeableGasLiftWell
 * @see GasLiftNetworkOptimizer
 * @see StrupeOkeReport
 */
public class ChokeAndGasLiftAllocationOptimizer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Schema version for the JSON result contract. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Optimisation objective. */
  public enum Objective {
    /** Maximise total oil rate. */
    OIL,
    /** Maximise revenue (oil rate &times; oil price). */
    REVENUE
  }

  private final List<ChokeableGasLiftWell> wells = new ArrayList<ChokeableGasLiftWell>();
  private double gasHandlingLimitSm3PerDay = Double.NaN;
  private double waterHandlingLimitSm3PerDay = Double.NaN;
  private double liftGasBudgetSm3PerDay = Double.NaN;
  private Objective objective = Objective.OIL;
  private double oilPrice = 1.0;
  private double chokeStep = 0.01;

  /**
   * Registers a chokeable gas-lifted well.
   *
   * @param well the well to register (must be non-null)
   * @return this optimiser for chaining
   */
  public ChokeAndGasLiftAllocationOptimizer addWell(ChokeableGasLiftWell well) {
    if (well != null) {
      wells.add(well);
    }
    return this;
  }

  /**
   * Sets the total gas-handling ceiling for the facility.
   *
   * @param gasHandlingLimitSm3PerDay maximum total produced gas in Sm3/day; {@code Double.NaN} for unlimited
   * @return this optimiser for chaining
   */
  public ChokeAndGasLiftAllocationOptimizer setGasHandlingLimit(double gasHandlingLimitSm3PerDay) {
    this.gasHandlingLimitSm3PerDay = gasHandlingLimitSm3PerDay;
    return this;
  }

  /**
   * Sets the total produced-water (or re-injection) ceiling for the facility.
   *
   * @param waterHandlingLimitSm3PerDay maximum total produced water in Sm3/day; {@code Double.NaN} for unlimited
   * @return this optimiser for chaining
   */
  public ChokeAndGasLiftAllocationOptimizer setWaterHandlingLimit(double waterHandlingLimitSm3PerDay) {
    this.waterHandlingLimitSm3PerDay = waterHandlingLimitSm3PerDay;
    return this;
  }

  /**
   * Sets the total lift-gas budget.
   *
   * @param liftGasBudgetSm3PerDay maximum total lift gas in Sm3/day; {@code Double.NaN} to inject each well at its own
   * optimum
   * @return this optimiser for chaining
   */
  public ChokeAndGasLiftAllocationOptimizer setLiftGasBudget(double liftGasBudgetSm3PerDay) {
    this.liftGasBudgetSm3PerDay = liftGasBudgetSm3PerDay;
    return this;
  }

  /**
   * Sets the optimisation objective.
   *
   * @param objective the objective (OIL or REVENUE)
   * @return this optimiser for chaining
   */
  public ChokeAndGasLiftAllocationOptimizer setObjective(Objective objective) {
    if (objective != null) {
      this.objective = objective;
    }
    return this;
  }

  /**
   * Sets the oil price used for the REVENUE objective.
   *
   * @param oilPrice oil price per Sm3 (any consistent currency)
   * @return this optimiser for chaining
   */
  public ChokeAndGasLiftAllocationOptimizer setOilPrice(double oilPrice) {
    this.oilPrice = oilPrice;
    return this;
  }

  /**
   * Runs the choke-and-lift allocation.
   *
   * @return the allocation result (never null; never throws)
   */
  public AllocationResult optimize() {
    AllocationResult result = new AllocationResult();
    int n = wells.size();
    if (n == 0) {
      return result;
    }

    // 1. Lift-gas allocation among open wells at full choke (equal-slope).
    List<ChokeableGasLiftWell> open = new ArrayList<ChokeableGasLiftWell>();
    for (ChokeableGasLiftWell w : wells) {
      if (!w.isForcedShut() && w.getMaxChokeFraction() > 0.0) {
        open.add(w);
      }
    }
    double budget = liftGasBudgetSm3PerDay;
    if (Double.isNaN(budget)) {
      budget = 0.0;
      for (ChokeableGasLiftWell w : open) {
        budget += w.getCurve().optimalLiftRate();
      }
    }
    GasLiftNetworkOptimizer liftOpt = new GasLiftNetworkOptimizer();
    for (ChokeableGasLiftWell w : open) {
      liftOpt.addWell(w.getName(), w.getCurve());
    }
    Map<String, Double> liftByName = new LinkedHashMap<String, Double>();
    if (!open.isEmpty()) {
      liftByName = liftOpt.allocate(budget).getLiftRates();
    }

    // 2. Per-well state at full choke, then honour per-well gas ceiling.
    Map<String, Double> lift = new LinkedHashMap<String, Double>();
    Map<String, Double> scale = new LinkedHashMap<String, Double>(); // choke scale in [0, 1]
    Map<String, Double> oilFull = new LinkedHashMap<String, Double>(); // oil at full choke and allocated lift
    Map<String, String> binding = new LinkedHashMap<String, String>();
    for (ChokeableGasLiftWell w : wells) {
      String name = w.getName();
      if (w.isForcedShut() || w.getMaxChokeFraction() <= 0.0) {
        lift.put(name, 0.0);
        scale.put(name, 0.0);
        oilFull.put(name, 0.0);
        binding.put(name, w.isForcedShut() ? "shut" : "choke_max");
        continue;
      }
      double ql = liftByName.containsKey(name) ? liftByName.get(name) : 0.0;
      double oFull = w.getCurve().oilRateAt(ql);
      lift.put(name, ql);
      oilFull.put(name, oFull);
      double s = 1.0;
      // Per-well gas ceiling: reduce choke so produced gas == limit.
      double limit = w.getGasHandlingLimit();
      if (!Double.isNaN(limit) && oFull > 0.0 && w.getGor() > 0.0) {
        double gasAtFull = oFull * w.getGor() + ql;
        if (gasAtFull > limit) {
          s = Math.max(0.0, (limit - ql) / (oFull * w.getGor()));
          s = Math.min(1.0, s);
          binding.put(name, "gas_ceiling");
        }
      }
      scale.put(name, s);
      if (!binding.containsKey(name)) {
        binding.put(name, "choke_max");
      }
    }

    // 3. Greedy relief of shared facility constraints.
    int maxIter = 200 * Math.max(1, n) + 500;
    for (int iter = 0; iter < maxIter; iter++) {
      double totalGas = totalGas(lift, scale, oilFull);
      double totalWater = totalWater(scale, oilFull);
      boolean gasOver = !Double.isNaN(gasHandlingLimitSm3PerDay) && totalGas > gasHandlingLimitSm3PerDay + 1.0e-6;
      boolean waterOver = !Double.isNaN(waterHandlingLimitSm3PerDay)
          && totalWater > waterHandlingLimitSm3PerDay + 1.0e-6;
      if (!gasOver && !waterOver) {
        break;
      }
      // Choose the more-violated constraint (by relative excess).
      double gasExcess = gasOver ? (totalGas - gasHandlingLimitSm3PerDay) / Math.max(1.0, gasHandlingLimitSm3PerDay)
          : 0.0;
      double waterExcess = waterOver
          ? (totalWater - waterHandlingLimitSm3PerDay) / Math.max(1.0, waterHandlingLimitSm3PerDay)
          : 0.0;
      boolean relieveGas = gasExcess >= waterExcess;
      ChokeableGasLiftWell victim = pickChokeVictim(open, scale, oilFull, relieveGas);
      if (victim == null) {
        break; // no well can relieve the binding constraint
      }
      String name = victim.getName();
      double s = Math.max(0.0, scale.get(name) - chokeStep);
      scale.put(name, s);
      binding.put(name, relieveGas ? "facility_gas" : "facility_water");
    }

    // 4. Assemble result.
    double totalOil = 0.0;
    double totalLift = 0.0;
    double totalGas = 0.0;
    double totalWater = 0.0;
    for (ChokeableGasLiftWell w : wells) {
      String name = w.getName();
      double s = scale.get(name);
      double ql = lift.get(name);
      double oil = s * oilFull.get(name);
      double gas = w.producedGasRate(oil, ql);
      double water = w.producedWaterRate(oil);
      double chokeFrac = s * w.getMaxChokeFraction();
      result.wells.put(name, new WellAllocation(name, chokeFrac, ql, oil, gas, water, binding.get(name)));
      totalOil += oil;
      totalLift += ql;
      totalGas += gas;
      totalWater += water;
    }
    result.totalOil = totalOil;
    result.totalLift = totalLift;
    result.totalGas = totalGas;
    result.totalWater = totalWater;
    result.objectiveValue = objective == Objective.REVENUE ? totalOil * oilPrice : totalOil;
    result.gasFeasible = Double.isNaN(gasHandlingLimitSm3PerDay) || totalGas <= gasHandlingLimitSm3PerDay + 1.0;
    result.waterFeasible = Double.isNaN(waterHandlingLimitSm3PerDay) || totalWater <= waterHandlingLimitSm3PerDay + 1.0;
    result.liftFeasible = Double.isNaN(liftGasBudgetSm3PerDay) || totalLift <= liftGasBudgetSm3PerDay + 1.0;
    result.feasible = result.gasFeasible && result.waterFeasible && result.liftFeasible;
    return result;
  }

  /**
   * Returns the total produced gas for the current allocation.
   *
   * @param lift per-well lift rates
   * @param scale per-well choke scales
   * @param oilFull per-well oil rates at full choke
   * @return total produced gas in Sm3/day
   */
  private double totalGas(Map<String, Double> lift, Map<String, Double> scale, Map<String, Double> oilFull) {
    double total = 0.0;
    for (ChokeableGasLiftWell w : wells) {
      String name = w.getName();
      double oil = scale.get(name) * oilFull.get(name);
      total += w.producedGasRate(oil, lift.get(name));
    }
    return total;
  }

  /**
   * Returns the total produced water for the current allocation.
   *
   * @param scale per-well choke scales
   * @param oilFull per-well oil rates at full choke
   * @return total produced water in Sm3/day
   */
  private double totalWater(Map<String, Double> scale, Map<String, Double> oilFull) {
    double total = 0.0;
    for (ChokeableGasLiftWell w : wells) {
      String name = w.getName();
      double oil = scale.get(name) * oilFull.get(name);
      total += w.producedWaterRate(oil);
    }
    return total;
  }

  /**
   * Picks the open well that relieves the binding constraint with the least oil sacrifice.
   *
   * @param open the open wells
   * @param scale per-well choke scales
   * @param oilFull per-well oil rates at full choke
   * @param relieveGas true to relieve a gas constraint, false for a water constraint
   * @return the well to choke back, or null when none can relieve the constraint
   */
  private ChokeableGasLiftWell pickChokeVictim(List<ChokeableGasLiftWell> open, Map<String, Double> scale,
      Map<String, Double> oilFull, boolean relieveGas) {
    ChokeableGasLiftWell best = null;
    double bestOilPerResource = Double.POSITIVE_INFINITY;
    for (ChokeableGasLiftWell w : open) {
      String name = w.getName();
      if (scale.get(name) <= 0.0 || oilFull.get(name) <= 0.0) {
        continue;
      }
      double resourceMarginal = relieveGas ? oilFull.get(name) * w.getGor()
          : oilFull.get(name) * (w.getWaterCut() / Math.max(1.0e-9, 1.0 - w.getWaterCut()));
      if (resourceMarginal <= 0.0) {
        continue; // this well cannot relieve the binding constraint
      }
      double oilPerResource = oilFull.get(name) / resourceMarginal;
      if (oilPerResource < bestOilPerResource) {
        bestOilPerResource = oilPerResource;
        best = w;
      }
    }
    return best;
  }

  /**
   * Per-well allocation entry.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class WellAllocation implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double chokeFraction;
    private final double liftRate;
    private final double oilRate;
    private final double gasRate;
    private final double waterRate;
    private final String bindingConstraint;

    /**
     * Creates a well allocation entry.
     *
     * @param name well name
     * @param chokeFraction recommended choke opening in [0, 1]
     * @param liftRate lift-gas rate in Sm3/day
     * @param oilRate oil rate in Sm3/day
     * @param gasRate produced-gas rate in Sm3/day
     * @param waterRate produced-water rate in Sm3/day
     * @param bindingConstraint the constraint that limited this well
     */
    public WellAllocation(String name, double chokeFraction, double liftRate, double oilRate, double gasRate,
        double waterRate, String bindingConstraint) {
      this.name = name;
      this.chokeFraction = chokeFraction;
      this.liftRate = liftRate;
      this.oilRate = oilRate;
      this.gasRate = gasRate;
      this.waterRate = waterRate;
      this.bindingConstraint = bindingConstraint;
    }

    /**
     * Returns the well name.
     *
     * @return well name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the recommended choke opening.
     *
     * @return choke opening in [0, 1]
     */
    public double getChokeFraction() {
      return chokeFraction;
    }

    /**
     * Returns the lift-gas rate.
     *
     * @return lift-gas rate in Sm3/day
     */
    public double getLiftRate() {
      return liftRate;
    }

    /**
     * Returns the oil rate.
     *
     * @return oil rate in Sm3/day
     */
    public double getOilRate() {
      return oilRate;
    }

    /**
     * Returns the produced-gas rate.
     *
     * @return produced-gas rate in Sm3/day
     */
    public double getGasRate() {
      return gasRate;
    }

    /**
     * Returns the produced-water rate.
     *
     * @return produced-water rate in Sm3/day
     */
    public double getWaterRate() {
      return waterRate;
    }

    /**
     * Returns the binding constraint label.
     *
     * @return constraint that limited this well
     */
    public String getBindingConstraint() {
      return bindingConstraint;
    }
  }

  /**
   * Result of a choke-and-lift allocation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class AllocationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, WellAllocation> wells = new LinkedHashMap<String, WellAllocation>();
    private double totalOil = 0.0;
    private double totalLift = 0.0;
    private double totalGas = 0.0;
    private double totalWater = 0.0;
    private double objectiveValue = 0.0;
    private boolean feasible = true;
    private boolean gasFeasible = true;
    private boolean waterFeasible = true;
    private boolean liftFeasible = true;

    /**
     * Returns the per-well allocations keyed by well name.
     *
     * @return map of well name to allocation
     */
    public Map<String, WellAllocation> getWells() {
      return wells;
    }

    /**
     * Returns the total oil rate.
     *
     * @return total oil in Sm3/day
     */
    public double getTotalOil() {
      return totalOil;
    }

    /**
     * Returns the total lift-gas rate.
     *
     * @return total lift gas in Sm3/day
     */
    public double getTotalLift() {
      return totalLift;
    }

    /**
     * Returns the total produced-gas rate.
     *
     * @return total produced gas in Sm3/day
     */
    public double getTotalGas() {
      return totalGas;
    }

    /**
     * Returns the total produced-water rate.
     *
     * @return total produced water in Sm3/day
     */
    public double getTotalWater() {
      return totalWater;
    }

    /**
     * Returns the objective value (oil or revenue).
     *
     * @return objective value
     */
    public double getObjectiveValue() {
      return objectiveValue;
    }

    /**
     * Returns whether all facility constraints are satisfied.
     *
     * @return true when the allocation is feasible
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Returns whether the gas-handling constraint is satisfied.
     *
     * @return true when total gas is within the limit
     */
    public boolean isGasFeasible() {
      return gasFeasible;
    }

    /**
     * Returns whether the water-handling constraint is satisfied.
     *
     * @return true when total water is within the limit
     */
    public boolean isWaterFeasible() {
      return waterFeasible;
    }

    /**
     * Returns whether the lift-gas budget is satisfied.
     *
     * @return true when total lift gas is within the budget
     */
    public boolean isLiftFeasible() {
      return liftFeasible;
    }

    /**
     * Returns a schema-versioned JSON representation of the result.
     *
     * @return JSON string
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"schemaVersion\":\"").append(SCHEMA_VERSION).append("\",");
      sb.append("\"feasible\":").append(feasible).append(",");
      sb.append("\"gasFeasible\":").append(gasFeasible).append(",");
      sb.append("\"waterFeasible\":").append(waterFeasible).append(",");
      sb.append("\"liftFeasible\":").append(liftFeasible).append(",");
      sb.append("\"totalOil\":").append(fmt(totalOil)).append(",");
      sb.append("\"totalLift\":").append(fmt(totalLift)).append(",");
      sb.append("\"totalGas\":").append(fmt(totalGas)).append(",");
      sb.append("\"totalWater\":").append(fmt(totalWater)).append(",");
      sb.append("\"objectiveValue\":").append(fmt(objectiveValue)).append(",");
      sb.append("\"wells\":[");
      boolean first = true;
      for (WellAllocation w : wells.values()) {
        if (!first) {
          sb.append(",");
        }
        first = false;
        sb.append("{\"name\":\"").append(w.getName()).append("\",");
        sb.append("\"chokeFraction\":").append(fmt(w.getChokeFraction())).append(",");
        sb.append("\"liftRate\":").append(fmt(w.getLiftRate())).append(",");
        sb.append("\"oilRate\":").append(fmt(w.getOilRate())).append(",");
        sb.append("\"gasRate\":").append(fmt(w.getGasRate())).append(",");
        sb.append("\"waterRate\":").append(fmt(w.getWaterRate())).append(",");
        sb.append("\"bindingConstraint\":\"").append(w.getBindingConstraint()).append("\"}");
      }
      sb.append("]}");
      return sb.toString();
    }

    /**
     * Formats a double with a fixed precision for JSON output.
     *
     * @param value the value to format
     * @return formatted string
     */
    private static String fmt(double value) {
      if (Double.isNaN(value) || Double.isInfinite(value)) {
        return "null";
      }
      return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
  }
}
