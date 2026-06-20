package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reservoir-to-market production optimiser.
 *
 * <p>
 * Optimises the field-wide operating point of an {@link IntegratedProductionModel} - the per-well choke openings and,
 * optionally, the export-header pressure - to maximise an economic objective (revenue or total rate) subject to a
 * facility throughput capacity constraint. This closes the loop between reservoir deliverability, gathering hydraulics
 * and the topside/market boundary, the same coupling performed by Petex GAP and Schlumberger Pipesim network
 * optimisation.
 * </p>
 *
 * <p>
 * The search is a self-contained bounded coordinate-ascent over the choke decision variables (each in [0, 1]) with a
 * quadratic penalty for exceeding the facility capacity. Every candidate is evaluated by a full network solve, but the
 * well deliverability surrogates keep each evaluation cheap. The optimiser never throws and returns a schema-versioned
 * JSON result, so it is safe to drive from an agentic loop.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see IntegratedProductionModel
 */
public class ReservoirToMarketOptimizer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Optimisation objective. */
  public enum Objective {
    /** Maximise revenue (rate &times; price). */
    REVENUE,
    /** Maximise total field rate. */
    RATE
  }

  /**
   * Result of a reservoir-to-market optimisation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class OptimizationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final boolean feasible;
    private final double objectiveValue;
    private final double fieldRate;
    private final double revenue;
    private final Map<String, Double> chokeSettings;
    private final Map<String, Double> wellRates;
    private final int evaluations;

    /**
     * Creates an optimisation result.
     *
     * @param feasible whether the capacity constraint is satisfied
     * @param objectiveValue best objective value found
     * @param fieldRate field rate at the optimum in Sm3/day
     * @param revenue revenue at the optimum in currency per day
     * @param chokeSettings per-well choke openings (0..1)
     * @param wellRates per-well rate in Sm3/day
     * @param evaluations number of network solves performed
     */
    public OptimizationResult(boolean feasible, double objectiveValue, double fieldRate, double revenue,
	Map<String, Double> chokeSettings, Map<String, Double> wellRates, int evaluations) {
      this.feasible = feasible;
      this.objectiveValue = objectiveValue;
      this.fieldRate = fieldRate;
      this.revenue = revenue;
      this.chokeSettings = chokeSettings;
      this.wellRates = wellRates;
      this.evaluations = evaluations;
    }

    /**
     * Returns whether the solution is feasible.
     *
     * @return true if within capacity
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Returns the objective value.
     *
     * @return objective value
     */
    public double getObjectiveValue() {
      return objectiveValue;
    }

    /**
     * Returns the field rate at the optimum.
     *
     * @return field rate in Sm3/day
     */
    public double getFieldRate() {
      return fieldRate;
    }

    /**
     * Returns the revenue at the optimum.
     *
     * @return revenue in currency per day
     */
    public double getRevenue() {
      return revenue;
    }

    /**
     * Returns the per-well choke openings.
     *
     * @return map of well name to choke opening (0..1)
     */
    public Map<String, Double> getChokeSettings() {
      return chokeSettings;
    }

    /**
     * Returns the per-well rates.
     *
     * @return map of well name to rate in Sm3/day
     */
    public Map<String, Double> getWellRates() {
      return wellRates;
    }

    /**
     * Returns the number of network evaluations performed.
     *
     * @return evaluation count
     */
    public int getEvaluations() {
      return evaluations;
    }

    /**
     * Returns a schema-versioned JSON summary.
     *
     * @return JSON string
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"schemaVersion\":\"1.0\",\"feasible\":").append(feasible).append(",");
      sb.append("\"objective\":").append(num(objectiveValue)).append(",");
      sb.append("\"fieldRateSm3PerDay\":").append(num(fieldRate)).append(",");
      sb.append("\"revenue\":").append(num(revenue)).append(",");
      sb.append("\"evaluations\":").append(evaluations).append(",");
      sb.append("\"chokes\":{");
      appendMap(sb, chokeSettings);
      sb.append("},\"wellRates\":{");
      appendMap(sb, wellRates);
      sb.append("}}");
      return sb.toString();
    }

    /**
     * Appends a string-double map as JSON key-values.
     *
     * @param sb target builder
     * @param map map to append
     */
    private void appendMap(StringBuilder sb, Map<String, Double> map) {
      int i = 0;
      for (Map.Entry<String, Double> e : map.entrySet()) {
	if (i++ > 0) {
	  sb.append(",");
	}
	sb.append("\"").append(e.getKey()).append("\":").append(num(e.getValue()));
      }
    }

    /**
     * Formats a double for JSON.
     *
     * @param v value
     * @return formatted string
     */
    private String num(double v) {
      if (Double.isNaN(v) || Double.isInfinite(v)) {
	return "null";
      }
      return String.format(java.util.Locale.US, "%.6g", v);
    }
  }

  private final IntegratedProductionModel model;
  private Objective objective = Objective.REVENUE;
  private double facilityCapacity = Double.MAX_VALUE; // Sm3/day
  /**
   * Dimensionless penalty multiplier. The capacity penalty slope is this multiple of the marginal objective value, so
   * values &gt; 1 make the throughput constraint bind tightly (the optimiser always prefers backing off a choke to
   * exceeding capacity).
   */
  private double capacityPenaltyWeight = 50.0;
  private int maxIterations = 40;

  /**
   * Creates an optimiser for an integrated production model.
   *
   * @param model the integrated production model to optimise
   */
  public ReservoirToMarketOptimizer(IntegratedProductionModel model) {
    this.model = model;
  }

  /**
   * Sets the optimisation objective.
   *
   * @param objective objective to maximise
   * @return this optimiser for chaining
   */
  public ReservoirToMarketOptimizer setObjective(Objective objective) {
    this.objective = objective;
    return this;
  }

  /**
   * Sets the facility throughput capacity constraint.
   *
   * @param capacitySm3PerDay maximum total field rate in Sm3/day
   * @return this optimiser for chaining
   */
  public ReservoirToMarketOptimizer setFacilityCapacity(double capacitySm3PerDay) {
    this.facilityCapacity = capacitySm3PerDay;
    return this;
  }

  /**
   * Sets the maximum number of coordinate-ascent sweeps.
   *
   * @param maxIterations sweep count
   * @return this optimiser for chaining
   */
  public ReservoirToMarketOptimizer setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }

  /**
   * Runs the optimisation. Never throws; on failure returns an infeasible result.
   *
   * @return the optimisation result
   */
  public OptimizationResult optimize() {
    try {
      return optimizeInternal();
    } catch (RuntimeException ex) {
      return new OptimizationResult(false, Double.NEGATIVE_INFINITY, 0.0, 0.0, new LinkedHashMap<String, Double>(),
	  new LinkedHashMap<String, Double>(), 0);
    }
  }

  /**
   * Internal optimisation routine.
   *
   * @return the optimisation result
   */
  private OptimizationResult optimizeInternal() {
    List<IntegratedProductionModel.WellUnit> wells = model.getWells();
    int n = wells.size();
    double[] choke = new double[n];
    for (int i = 0; i < n; i++) {
      choke[i] = 1.0; // start fully open
      wells.get(i).getWellBranch().setChokeFactor(1.0);
    }
    int[] evalCount = new int[1];
    double bestScore = score(evalCount);
    double step = 0.5;
    for (int sweep = 0; sweep < maxIterations; sweep++) {
      boolean improved = false;
      for (int i = 0; i < n; i++) {
	double current = choke[i];
	double[] candidates = new double[] { clamp(current + step), clamp(current - step) };
	for (int c = 0; c < candidates.length; c++) {
	  double cand = candidates[c];
	  if (cand == current) {
	    continue;
	  }
	  wells.get(i).getWellBranch().setChokeFactor(cand);
	  double s = score(evalCount);
	  if (s > bestScore + 1.0e-9) {
	    bestScore = s;
	    choke[i] = cand;
	    current = cand;
	    improved = true;
	  } else {
	    wells.get(i).getWellBranch().setChokeFactor(current);
	  }
	}
      }
      if (!improved) {
	step *= 0.5;
	if (step < 1.0e-3) {
	  break;
	}
      }
    }
    // Final evaluation at the best chokes.
    for (int i = 0; i < n; i++) {
      wells.get(i).getWellBranch().setChokeFactor(choke[i]);
    }
    IntegratedSolveResult res = model.solve();
    Map<String, Double> chokeMap = new LinkedHashMap<String, Double>();
    for (int i = 0; i < n; i++) {
      chokeMap.put(wells.get(i).getName(), choke[i]);
    }
    boolean feasible = res.getFieldRate() <= facilityCapacity * 1.0001;
    double objVal = objective == Objective.REVENUE ? res.getRevenue() : res.getFieldRate();
    return new OptimizationResult(feasible, objVal, res.getFieldRate(), res.getRevenue(), chokeMap, res.getWellRates(),
	evalCount[0]);
  }

  /**
   * Scores the current choke configuration with a capacity penalty.
   *
   * <p>
   * The penalty is expressed in objective units (revenue or rate) and uses a steep linear term whose slope is
   * {@code capacityPenaltyWeight} times the marginal objective value, plus a smooth quadratic term. Because the linear
   * slope exceeds the marginal gain from opening a choke, the optimiser drives the field rate down onto the capacity
   * bound instead of overshooting it.
   * </p>
   *
   * @param evalCount single-element evaluation counter, incremented per call
   * @return penalised objective value
   */
  private double score(int[] evalCount) {
    evalCount[0]++;
    IntegratedSolveResult res = model.solve();
    double fieldRate = res.getFieldRate();
    double base = objective == Objective.REVENUE ? res.getRevenue() : fieldRate;
    double over = fieldRate - facilityCapacity;
    if (over > 0.0) {
      // Marginal objective value per unit field rate (revenue: price; rate: 1.0).
      double marginal = objective == Objective.REVENUE ? (fieldRate > 0.0 ? res.getRevenue() / fieldRate : 0.0) : 1.0;
      if (marginal <= 0.0) {
	marginal = 1.0;
      }
      double overFraction = over / Math.max(1.0, facilityCapacity);
      base -= capacityPenaltyWeight * marginal * over + capacityPenaltyWeight * marginal * over * overFraction;
    }
    return base;
  }

  /**
   * Clamps a choke opening to the valid range.
   *
   * @param v candidate value
   * @return value clamped to [0, 1]
   */
  private double clamp(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }
}
