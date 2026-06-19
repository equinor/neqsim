package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gas-lift allocation optimiser using the equal-slope (Lagrangian) criterion.
 *
 * <p>
 * Given a fleet of gas-lifted wells, each with its own {@link GasLiftPerformanceCurve}, and a limited total lift-gas
 * budget, the optimal allocation maximises total oil when the incremental oil response dq_oil/dq_lift is equal across
 * all wells that receive gas (interior optimum) or a well is driven to a bound. This is the classic result reproduced
 * by Petex GAP's gas-lift optimisation.
 * </p>
 *
 * <p>
 * The optimiser performs a bisection on the common marginal slope &lambda;. For a trial &lambda; each well's lift rate
 * is the point where its incremental slope equals &lambda; (found by 1-D search and clamped to its capacity). The total
 * allocated gas increases monotonically as &lambda; decreases, so bisection converges robustly to the budget. If the
 * unconstrained optimum (every well at its GLPC peak) uses less than the budget, the surplus is simply not injected.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see GasLiftPerformanceCurve
 */
public class GasLiftNetworkOptimizer implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** A well registered for gas-lift allocation. */
  private static class LiftWell implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final GasLiftPerformanceCurve curve;

    LiftWell(String name, GasLiftPerformanceCurve curve) {
      this.name = name;
      this.curve = curve;
    }
  }

  /**
   * Result of a gas-lift allocation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class AllocationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Double> liftRates;
    private final Map<String, Double> oilRates;
    private final double totalOil;
    private final double totalLift;

    /**
     * Creates an allocation result.
     *
     * @param liftRates per-well lift-gas allocation in Sm3/day
     * @param oilRates  per-well oil rate in Sm3/day
     * @param totalOil  total oil rate in Sm3/day
     * @param totalLift total lift gas used in Sm3/day
     */
    public AllocationResult(Map<String, Double> liftRates, Map<String, Double> oilRates, double totalOil,
	double totalLift) {
      this.liftRates = liftRates;
      this.oilRates = oilRates;
      this.totalOil = totalOil;
      this.totalLift = totalLift;
    }

    /**
     * Returns the per-well lift-gas allocation.
     *
     * @return map of well name to lift rate in Sm3/day
     */
    public Map<String, Double> getLiftRates() {
      return liftRates;
    }

    /**
     * Returns the per-well oil rates.
     *
     * @return map of well name to oil rate in Sm3/day
     */
    public Map<String, Double> getOilRates() {
      return oilRates;
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
     * Returns the total lift gas used.
     *
     * @return total lift gas in Sm3/day
     */
    public double getTotalLift() {
      return totalLift;
    }
  }

  private final List<LiftWell> wells = new ArrayList<LiftWell>();

  /**
   * Registers a gas-lifted well.
   *
   * @param name  unique well name
   * @param curve gas-lift performance curve for the well
   * @return this optimiser for chaining
   */
  public GasLiftNetworkOptimizer addWell(String name, GasLiftPerformanceCurve curve) {
    wells.add(new LiftWell(name, curve));
    return this;
  }

  /**
   * Allocates a total lift-gas budget across the registered wells to maximise total oil.
   *
   * @param totalLiftGasSm3PerDay total available lift gas in Sm3/day
   * @return the optimal allocation
   */
  public AllocationResult allocate(double totalLiftGasSm3PerDay) {
    Map<String, Double> lift = new LinkedHashMap<String, Double>();
    Map<String, Double> oil = new LinkedHashMap<String, Double>();
    if (wells.isEmpty()) {
      return new AllocationResult(lift, oil, 0.0, 0.0);
    }

    // Bracket lambda. Highest slope is near zero lift; lowest (most negative) at full capacity.
    double lambdaHigh = 0.0;
    for (LiftWell w : wells) {
      lambdaHigh = Math.max(lambdaHigh, w.curve.incrementalSlope(1.0e-3));
    }
    double lambdaLow = 0.0; // slope at the GLPC peak is zero; never inject past the peak.

    // Bisection: find lambda so the total allocated gas matches the budget.
    double lambda = 0.0;
    for (int iter = 0; iter < 80; iter++) {
      lambda = 0.5 * (lambdaLow + lambdaHigh);
      double total = 0.0;
      for (LiftWell w : wells) {
	total += liftForSlope(w.curve, lambda);
      }
      if (total > totalLiftGasSm3PerDay) {
	lambdaLow = lambda; // need higher slope -> less gas
      } else {
	lambdaHigh = lambda; // can afford lower slope -> more gas
      }
      if (Math.abs(lambdaHigh - lambdaLow) < 1.0e-9) {
	break;
      }
    }

    double totalOil = 0.0;
    double totalLift = 0.0;
    for (LiftWell w : wells) {
      double ql = liftForSlope(w.curve, lambda);
      double qo = w.curve.oilRateAt(ql);
      lift.put(w.name, ql);
      oil.put(w.name, qo);
      totalOil += qo;
      totalLift += ql;
    }
    return new AllocationResult(lift, oil, totalOil, totalLift);
  }

  /**
   * Finds the lift-gas rate at which a curve's incremental slope equals a target value.
   *
   * @param curve       gas-lift performance curve
   * @param targetSlope target incremental slope
   * @return lift-gas rate in Sm3/day (clamped to [0, peak])
   */
  private double liftForSlope(GasLiftPerformanceCurve curve, double targetSlope) {
    double peak = curve.optimalLiftRate();
    if (curve.incrementalSlope(1.0e-3) <= targetSlope) {
      return 0.0;
    }
    if (curve.incrementalSlope(peak) >= targetSlope) {
      return peak;
    }
    double lo = 0.0;
    double hi = peak;
    for (int iter = 0; iter < 60; iter++) {
      double mid = 0.5 * (lo + hi);
      double slope = curve.incrementalSlope(mid);
      if (slope > targetSlope) {
	lo = mid; // slope decreases with lift, so move right
      } else {
	hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }
}
